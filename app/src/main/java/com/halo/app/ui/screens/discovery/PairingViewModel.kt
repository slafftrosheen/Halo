package com.halo.app.ui.screens.discovery

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.wearables.NativeWearableHost
import com.halo.drivers.heycyan.HeyCyanHost
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.ble.DiscoveredBleDevice
import com.halo.drivers.heycyan.ble.inferGlassesFamilyFromAdvertisement
import com.halo.sdk.host.ConnectionState
import com.halo.sdk.runtime.DeviceIdentity
import com.halo.sdk.runtime.embodiment.*
import com.halo.sdk.runtime.wearables.NativeSnapshot
import com.halo.sdk.runtime.wearables.NativeStage
import com.halo.sdk.runtime.wearables.NativeWearableSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal fun canonicalHeyCyanIdentity(
    address: String,
    displayName: String,
    existing: DeviceIdentity? = null,
): DeviceIdentity = (existing ?: DeviceIdentity(address, "HEYCYAN_GLASSES", displayName)).copy(
    deviceId = address,
    deviceType = "HEYCYAN_GLASSES",
    displayName = existing?.displayName?.takeIf { it.isNotBlank() && it != "HEYCYAN_GLASSES" } ?: displayName,
)

internal fun pairingValidDisplayName(value: String): String? = value.trim().takeIf {
    it.isNotBlank() && it.length <= 48 && it.none { char -> char.code < 32 || char.code == 127 }
}

/** Transport READY is meaningful only for the explicitly selected endpoint. */
internal fun pairingTransportReadyForTarget(
    target: String,
    activeDeviceId: String?,
    deviceState: com.halo.drivers.heycyan.protocol.HeyCyanConnectionState,
    hostState: ConnectionState,
): Boolean = target.equals(activeDeviceId, ignoreCase = true) &&
    deviceState == com.halo.drivers.heycyan.protocol.HeyCyanConnectionState.READY && hostState == ConnectionState.Ready

data class PairingUiState(
    val devices: List<DiscoveredBleDevice> = emptyList(),
    val isScanning: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val selectedAddress: String? = null,
    val attemptedAddress: String? = null,
    val completedAddress: String? = null,
    val completedName: String? = null,
    val statusMessage: String? = null,
    val bluetoothPrerequisite: BluetoothPrerequisite = BluetoothPrerequisite.READY,
    // BLE discovery and Classic transport identities can differ on AB Mate.
    val completedDeviceId: String? = null,
    val familyOverride: DeviceKind? = null,
    val isConnecting: Boolean = false,
) {
    val selectedFamily: DeviceKind get() = familyOverride ?: devices.firstOrNull {
        it.advertisement.address.equals(selectedAddress, ignoreCase = true)
    }?.let { inferGlassesFamilyFromAdvertisement(it.advertisement) } ?: DeviceKind.UNKNOWN
}

internal fun pairingFamilyConnectable(kind: DeviceKind): Boolean = kind in
    setOf(DeviceKind.UNKNOWN, DeviceKind.HEYCYAN_GLASSES) || NativeWearableHost.supports(kind)

class PairingViewModel(
    private val driverProvider: () -> RealHeyCyanDriver = { ManualDependencyGraph.heyCyanDriver },
    private val deviceRegistry: DeviceRegistry = ManualDependencyGraph.deviceRegistry,
    context: Context = ManualDependencyGraph.applicationContext,
    private val bluetoothEnabled: StateFlow<Boolean> = ManualDependencyGraph.platformStateMonitor.bluetoothEnabled,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val driver by lazy { driverProvider() }
    private val nativeHost get() = ManualDependencyGraph.deviceAutoConnectCoordinator.nativeWearables
    private val mutableState = MutableStateFlow(PairingUiState(bluetoothPrerequisite = evaluateBluetoothPrerequisite(appContext)))
    val uiState: StateFlow<PairingUiState> = mutableState.asStateFlow()
    private var scanJob: Job? = null
    private var connectJob: Job? = null
    private var nativeStateJob: Job? = null
    private val navigation = MutableSharedFlow<String>()
    val navigationEvents = navigation.asSharedFlow()

    init {
        viewModelScope.launch { bluetoothEnabled.drop(1).collect { refreshPrerequisites() } }
        viewModelScope.launch {
            ManualDependencyGraph.wearableHost.connectivity.collect { state ->
                mutableState.update { current ->
                    // An old HeyCyan session cannot finish a native vendor pairing.
                    if (NativeWearableHost.supports(current.selectedFamily)) current
                    else current.copy(connectionState = state)
                }
            }
        }
    }

    fun refreshPrerequisites() {
        val prerequisite = evaluateBluetoothPrerequisite(appContext)
        if (prerequisite != BluetoothPrerequisite.READY) {
            scanJob?.cancel()
            connectJob?.cancel()
        }
        mutableState.update { current ->
            current.copy(bluetoothPrerequisite = prerequisite,
                isScanning = prerequisite == BluetoothPrerequisite.READY && current.isScanning,
                statusMessage = if (prerequisite != BluetoothPrerequisite.READY) prerequisite.userMessage()
                    else current.statusMessage)
        }
    }

    fun startScan() {
        if (connectJob?.isActive == true) return
        refreshPrerequisites()
        if (mutableState.value.bluetoothPrerequisite != BluetoothPrerequisite.READY) return
        scanJob?.cancel()
        nativeStateJob?.cancel()
        mutableState.update { it.copy(isScanning = true, devices = emptyList(), selectedAddress = null,
            attemptedAddress = null, completedAddress = null, completedDeviceId = null, completedName = null,
            familyOverride = null, statusMessage = "Scanning for glasses…", connectionState = ConnectionState.Disconnected) }
        scanJob = viewModelScope.launch {
            driver.scanner.scan().catch { error ->
                if (error is CancellationException) throw error
                Log.w("PairingViewModel", "Scan failed", error)
                mutableState.update { it.copy(isScanning = false, statusMessage = "Scan failed: check Bluetooth access") }
            }.collect { advertisement ->
                mutableState.update { state ->
                    val index = state.devices.indexOfFirst { it.advertisement.address.equals(advertisement.address, true) }
                    val device = DiscoveredBleDevice(advertisement,
                        if (index < 0) System.currentTimeMillis() else state.devices[index].firstSeenAt)
                    state.copy(devices = if (index < 0) state.devices + device
                        else state.devices.toMutableList().also { it[index] = device })
                }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel(); scanJob = null
        mutableState.update { it.copy(isScanning = false,
            statusMessage = if (it.statusMessage?.startsWith("Scanning") == true) null else it.statusMessage) }
    }

    fun selectDevice(device: DiscoveredBleDevice) {
        if (connectJob?.isActive == true) return
        nativeStateJob?.cancel()
        mutableState.update { it.copy(selectedAddress = device.advertisement.address,
            familyOverride = null, attemptedAddress = null, completedAddress = null,
            completedDeviceId = null, completedName = null, statusMessage = null, connectionState = ConnectionState.Disconnected) }
    }

    /** Explicit correction of discovery hints, never a bypass of protocol validation. */
    fun selectFamily(family: DeviceKind?) {
        if (connectJob?.isActive == true || mutableState.value.completedAddress != null) return
        mutableState.update { it.copy(familyOverride = family, attemptedAddress = null, statusMessage = null) }
    }

    fun connect() {
        if (connectJob?.isActive == true) return
        refreshPrerequisites()
        val state = mutableState.value
        if (state.bluetoothPrerequisite != BluetoothPrerequisite.READY) return
        val selected = state.devices.firstOrNull { it.advertisement.address.equals(state.selectedAddress, true) } ?: return
        val kind = state.selectedFamily
        if (!pairingFamilyConnectable(kind)) {
            mutableState.update { it.copy(statusMessage = "This family needs its vendor adapter; no HeyCyan commands were sent") }
            return
        }
        val address = selected.advertisement.address
        stopScan()
        mutableState.update { it.copy(isConnecting = true, attemptedAddress = address,
            completedAddress = null, completedDeviceId = null, completedName = null,
            connectionState = ConnectionState.Connecting, statusMessage = "Connecting to the selected glasses…") }
        connectJob = viewModelScope.launch {
            try {
                val saved = withTimeout(150_000L) {
                    if (NativeWearableHost.supports(kind)) nativeHost.pair(kind, selected.advertisement) { snapshot ->
                        showNativeState(address, snapshot)
                    } else connectHeyCyan(selected)
                }
                ensureActive()
                mutableState.update { current ->
                    if (current.selectedAddress == address && current.attemptedAddress == address)
                        current.copy(completedAddress = address, completedDeviceId = saved.identity.deviceId,
                            completedName = saved.identity.displayName, connectionState = ConnectionState.Ready,
                            statusMessage = "Connected and saved") else current
                }
                if (NativeWearableHost.supports(kind)) {
                    val session = ManualDependencyGraph.deviceSessionManager.getSession(saved.identity.deviceId) as? NativeWearableSession
                    nativeStateJob?.cancel()
                    nativeStateJob = viewModelScope.launch { session?.driver?.state?.collect { showNativeState(address, it) } }
                }
            } catch (timeout: TimeoutCancellationException) {
                mutableState.update { it.copy(connectionState = ConnectionState.Failed("Pairing timed out"),
                    statusMessage = "Connection did not reach READY · reconnect and retry") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.w("PairingViewModel", "Selected device pairing failed", error)
                mutableState.update { it.copy(connectionState = ConnectionState.Failed("Pairing did not complete"),
                    statusMessage = "Connection failed: ${error.message?.take(200) ?: "Check Bluetooth and the selected device"}") }
            } finally { mutableState.update { it.copy(isConnecting = false) } }
        }
    }

    private fun showNativeState(address: String, snapshot: NativeSnapshot) {
        mutableState.update { current ->
            if (current.selectedAddress != address || !NativeWearableHost.supports(current.selectedFamily)) current
            else current.copy(connectionState = when (snapshot.stage) {
                NativeStage.DISCONNECTED -> ConnectionState.Disconnected
                NativeStage.CONNECTING -> ConnectionState.Connecting
                NativeStage.HANDSHAKE -> ConnectionState.Connected
                NativeStage.READY -> ConnectionState.Ready
                NativeStage.ERROR -> ConnectionState.Failed("Native glasses connection failed")
            }, statusMessage = when (snapshot.stage) {
                NativeStage.HANDSHAKE -> "Bluetooth connected · checking device responses…"
                NativeStage.ERROR -> snapshot.error
                else -> current.statusMessage
            })
        }
    }

    private suspend fun connectHeyCyan(selected: DiscoveredBleDevice): DeviceRecord {
        val address = selected.advertisement.address
        val host = ManualDependencyGraph.wearableHost as? HeyCyanHost ?: error("HeyCyan host is unavailable")
        host.setTargetDevice(address)
        host.connect()
        val ready = withTimeoutOrNull(30_000L) {
            combine(driver.deviceState, host.connectivity) { deviceState, hostState ->
                pairingTransportReadyForTarget(address, driver.activeDeviceId, deviceState.connection, hostState)
            }.first { it }
        } ?: false
        check(ready) { "Connection did not reach READY" }
        val name = selected.advertisement.name?.let(::pairingValidDisplayName) ?: "HeyCyan Glasses"
        val existing = deviceRegistry.getDevice(address)
        check(existing == null || existing.descriptor.kind == DeviceKind.HEYCYAN_GLASSES) {
            "Saved identity belongs to a different device family"
        }
        val saved = (existing ?: DeviceRecord(canonicalHeyCyanIdentity(address, name))).copy(
            identity = canonicalHeyCyanIdentity(address, name, existing?.identity),
            lastSeenMs = System.currentTimeMillis(),
        )
        deviceRegistry.saveDevice(saved)
        ManualDependencyGraph.deviceSessionManager.refreshSavedDevices()
        return saved
    }

    fun renameCompletedDevice(value: String) {
        val name = pairingValidDisplayName(value) ?: return
        val state = mutableState.value
        val selected = state.completedAddress ?: return
        val id = state.completedDeviceId ?: selected
        if (state.selectedAddress != selected || state.connectionState != ConnectionState.Ready) return
        viewModelScope.launch {
            try {
                val latest = mutableState.value
                if (latest.selectedAddress != selected || latest.completedAddress != selected ||
                    latest.connectionState != ConnectionState.Ready) return@launch
                val saved = deviceRegistry.getDevice(id) ?: return@launch
                if (saved.descriptor.kind != DeviceKind.HEYCYAN_GLASSES && !NativeWearableHost.supports(saved.descriptor.kind)) return@launch
                deviceRegistry.saveDevice(saved.copy(identity = saved.identity.copy(displayName = name)))
                ManualDependencyGraph.deviceSessionManager.refreshSavedDevices()
                mutableState.update { if (it.completedAddress == selected) it.copy(completedName = name, statusMessage = "Name saved") else it }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(statusMessage = "Could not save name") } }
        }
    }

    fun navigateBack() {
        connectJob?.cancel()
        viewModelScope.launch { navigation.emit("back") }
    }
    override fun onCleared() { scanJob?.cancel(); connectJob?.cancel(); nativeStateJob?.cancel(); super.onCleared() }
}
