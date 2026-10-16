package com.halo.app.ui.screens.discovery

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.di.ManualDependencyGraph
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.ble.DiscoveredBleDevice
import com.halo.sdk.host.ConnectionState
import com.halo.sdk.runtime.DeviceIdentity
import com.halo.sdk.runtime.embodiment.DeviceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal fun canonicalHeyCyanIdentity(
    address: String,
    displayName: String,
    existing: DeviceIdentity? = null
): DeviceIdentity = (existing ?: DeviceIdentity(
    deviceId = address,
    deviceType = "HEYCYAN_GLASSES",
    displayName = displayName
)).copy(
    deviceId = address,
    deviceType = "HEYCYAN_GLASSES",
    // Do not replace a custom name with a BLE advertisement on reconnect.
    displayName = existing?.displayName?.takeIf {
        it.isNotBlank() && it != "HEYCYAN_GLASSES"
    } ?: displayName
)

internal fun pairingValidDisplayName(value: String): String? =
    value.trim().takeIf {
        it.isNotBlank() && it.length <= 48 &&
            it.none { char -> char.code < 32 || char.code == 127 }
    }

/**
 * One host can retain Ready from another target. Both the active BLE identity and
 * its own device-level READY evidence must agree before recording a successful pair.
 */
internal fun pairingTransportReadyForTarget(
    target: String,
    activeDeviceId: String?,
    deviceState: com.halo.drivers.heycyan.protocol.HeyCyanConnectionState,
    hostState: ConnectionState,
): Boolean = target.equals(activeDeviceId, ignoreCase = true) &&
    deviceState == com.halo.drivers.heycyan.protocol.HeyCyanConnectionState.READY &&
    hostState == ConnectionState.Ready

data class PairingUiState(
    val devices: List<DiscoveredBleDevice> = emptyList(),
    val isScanning: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val selectedAddress: String? = null,
    // The attempted and saved address, not a global BLE Ready signal, authorizes
    // "glasses ready" and the completed pairing animation.
    val attemptedAddress: String? = null,
    val completedAddress: String? = null,
    val completedName: String? = null,
    val statusMessage: String? = null,
    val bluetoothPrerequisite: BluetoothPrerequisite = BluetoothPrerequisite.READY
)

class PairingViewModel(
    private val driverProvider: () -> RealHeyCyanDriver = { ManualDependencyGraph.heyCyanDriver },
    private val deviceRegistry: DeviceRegistry = ManualDependencyGraph.deviceRegistry,
    context: Context = ManualDependencyGraph.applicationContext,
    private val bluetoothEnabled: StateFlow<Boolean> =
        ManualDependencyGraph.platformStateMonitor.bluetoothEnabled
) : ViewModel() {

    private val appContext = context.applicationContext
    private val driver by lazy { driverProvider() }

    private val _uiState = MutableStateFlow(
        PairingUiState(
            bluetoothPrerequisite = evaluateBluetoothPrerequisite(appContext)
        )
    )
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()
    private var scanJob: Job? = null

    init {
        refreshPrerequisites()

        viewModelScope.launch {
            bluetoothEnabled
                .drop(1)
                .collect {
                    refreshPrerequisites()
                }
        }

        viewModelScope.launch(Dispatchers.IO) {
            ManualDependencyGraph.wearableHost.connectivity.collect { state ->
                _uiState.update { current ->
                    current.copy(
                        connectionState = state,
                        statusMessage = when (state) {
                            ConnectionState.Discovering -> "Discovering device…"
                            ConnectionState.Connecting -> "Connecting to glasses…"
                            ConnectionState.Connected -> "Bluetooth connected · initializing…"
                            ConnectionState.Ready -> "Connected and ready"
                            ConnectionState.Disconnecting -> "Disconnecting…"
                            is ConnectionState.Failed -> state.reason
                            ConnectionState.Disconnected -> {
                                if (current.connectionState != ConnectionState.Disconnected) {
                                    "Disconnected"
                                } else {
                                    current.statusMessage
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    fun refreshPrerequisites() {
        val prerequisite = evaluateBluetoothPrerequisite(appContext)
        if (prerequisite != BluetoothPrerequisite.READY) {
            scanJob?.cancel()
        }

        _uiState.update { current ->
            val recovered =
                current.bluetoothPrerequisite != BluetoothPrerequisite.READY &&
                    prerequisite == BluetoothPrerequisite.READY
            current.copy(
                bluetoothPrerequisite = prerequisite,
                isScanning = if (prerequisite == BluetoothPrerequisite.READY) {
                    current.isScanning
                } else {
                    false
                },
                statusMessage = when {
                    prerequisite != BluetoothPrerequisite.READY -> prerequisite.userMessage()
                    recovered -> "Bluetooth ready · you can scan for glasses"
                    else -> current.statusMessage
                }
            )
        }
    }

    fun startScan() {
        val prerequisite = evaluateBluetoothPrerequisite(appContext)
        if (prerequisite != BluetoothPrerequisite.READY) {
            scanJob?.cancel()
            _uiState.update {
                it.copy(
                    bluetoothPrerequisite = prerequisite,
                    isScanning = false,
                    statusMessage = prerequisite.userMessage()
                )
            }
            return
        }

        scanJob?.cancel()
        _uiState.update {
            it.copy(
                bluetoothPrerequisite = BluetoothPrerequisite.READY,
                isScanning = true,
                devices = emptyList(),
                // A scan invalidates the previous discovery snapshot. Keep the selected
                // address only if the user explicitly selects it from fresh results.
                selectedAddress = null,
                attemptedAddress = null,
                completedAddress = null,
                completedName = null,
                statusMessage = "Scanning for glasses…" 
            )
        }

        scanJob = viewModelScope.launch {
            val seen = mutableSetOf<String>()
            driver.scanner.scan()
                .catch { error ->
                    if (error is CancellationException) throw error

                    val currentPrerequisite = evaluateBluetoothPrerequisite(appContext)
                    Log.e("PairingViewModel", "Scan failed", error)
                    _uiState.update {
                        it.copy(
                            bluetoothPrerequisite = currentPrerequisite,
                            isScanning = false,
                            statusMessage = if (currentPrerequisite != BluetoothPrerequisite.READY) {
                                currentPrerequisite.userMessage()
                            } else {
                                "Scan failed: ${error.message ?: "Unknown error"}"
                            }
                        )
                    }
                }
                .collect { advertisement ->
                    if (seen.add(advertisement.address)) {
                        _uiState.update { state ->
                            state.copy(
                                devices = state.devices + DiscoveredBleDevice(
                                    advertisement = advertisement,
                                    firstSeenAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { current ->
            current.copy(
                isScanning = false,
                statusMessage = if (current.statusMessage?.startsWith("Scanning") == true) {
                    null
                } else {
                    current.statusMessage
                }
            )
        }
    }

    fun selectDevice(device: DiscoveredBleDevice) {
        _uiState.update {
            it.copy(
                selectedAddress = device.advertisement.address,
                attemptedAddress = null,
                completedAddress = null,
                completedName = null,
                statusMessage = null
            )
        }
    }

    fun connect() {
        val prerequisite = evaluateBluetoothPrerequisite(appContext)
        if (prerequisite != BluetoothPrerequisite.READY) {
            _uiState.update {
                it.copy(
                    bluetoothPrerequisite = prerequisite,
                    statusMessage = prerequisite.userMessage()
                )
            }
            return
        }

        val address = _uiState.value.selectedAddress ?: return
        stopScan()
        _uiState.update {
            it.copy(
                bluetoothPrerequisite = BluetoothPrerequisite.READY,
                attemptedAddress = address,
                completedAddress = null,
                completedName = null,
                statusMessage = "Connecting to $address…"
            )
        }

        viewModelScope.launch {
            try {
                val host = ManualDependencyGraph.wearableHost
                if (host !is com.halo.drivers.heycyan.HeyCyanHost) {
                    _uiState.update {
                        it.copy(statusMessage = "HeyCyan host is unavailable")
                    }
                    return@launch
                }

                host.setTargetDevice(address)
                host.connect()

                val ready = withTimeoutOrNull(20_000L) {
                    combine(driver.deviceState, host.connectivity) { deviceState, hostState ->
                        pairingTransportReadyForTarget(
                            target = address,
                            activeDeviceId = driver.activeDeviceId,
                            deviceState = deviceState.connection,
                            hostState = hostState,
                        )
                    }.first { it }
                } ?: false

                if (!ready) {
                    val state = host.connectivity.value
                    _uiState.update {
                        it.copy(
                            statusMessage = "Connection did not reach READY · ${state.toUserLabel()}"
                        )
                    }
                    return@launch
                }

                val discoveredDevice =
                    _uiState.value.devices.find { it.advertisement.address == address }
                val deviceName = discoveredDevice
                    ?.advertisement
                    ?.name
                    ?.takeIf { it.isNotBlank() }
                    ?: "HeyCyan Glasses"
                val existing = deviceRegistry.getDevice(address)
                val savedRecord =
                    (existing ?: com.halo.sdk.runtime.embodiment.DeviceRecord(
                        identity = canonicalHeyCyanIdentity(
                            address = address,
                            displayName = deviceName
                        )
                    )).copy(
                        identity = canonicalHeyCyanIdentity(
                            address = address,
                            displayName = deviceName,
                            existing = existing?.identity
                        ),
                        lastSeenMs = System.currentTimeMillis()
                    )
                deviceRegistry.saveDevice(savedRecord)
                _uiState.update { current ->
                    // A previous connection attempt may have been superseded.
                    // Never claim success for anything but the selected target.
                    if (current.selectedAddress == address && current.attemptedAddress == address)
                        current.copy(completedAddress = address,
                            completedName = savedRecord.identity.displayName,
                            statusMessage = "Connected and saved")
                    else current
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val currentPrerequisite = evaluateBluetoothPrerequisite(appContext)
                Log.e("PairingViewModel", "Connection failed", error)
                _uiState.update {
                    it.copy(
                        bluetoothPrerequisite = currentPrerequisite,
                        statusMessage = if (currentPrerequisite != BluetoothPrerequisite.READY) {
                            currentPrerequisite.userMessage()
                        } else {
                            "Connection failed: ${error.message ?: "Unknown error"}"
                        }
                    )
                }
            }
        }
    }

    /** Rename only the exact saved target after a confirmed READY handshake. */
    fun renameCompletedDevice(value: String) {
        val name = pairingValidDisplayName(value) ?: return
        val current = _uiState.value
        val address = current.completedAddress ?: return
        if (current.selectedAddress != address || current.connectionState != ConnectionState.Ready) return
        viewModelScope.launch {
            try {
                val latest = _uiState.value
                if (latest.completedAddress != address || latest.selectedAddress != address ||
                    latest.connectionState != ConnectionState.Ready) return@launch
                val saved = deviceRegistry.getDevice(address) ?: return@launch
                if (saved.descriptor.kind != com.halo.sdk.runtime.embodiment.DeviceKind.HEYCYAN_GLASSES)
                    return@launch
                deviceRegistry.saveDevice(saved.copy(identity = saved.identity.copy(displayName = name)))
                _uiState.update { state ->
                    if (state.completedAddress == address && state.selectedAddress == address)
                        state.copy(completedName = name, statusMessage = "Name saved") else state
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { state ->
                    if (state.completedAddress == address)
                        state.copy(statusMessage = "Could not save name") else state
                }
            }
        }
    }

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents = _navigationEvents.asSharedFlow()

    fun navigateBack() {
        viewModelScope.launch { _navigationEvents.emit("back") }
    }

    override fun onCleared() {
        scanJob?.cancel()
        super.onCleared()
    }
}

private fun ConnectionState.toUserLabel(): String = when (this) {
    ConnectionState.Disconnected -> "Disconnected"
    ConnectionState.Discovering -> "Discovering"
    ConnectionState.Connecting -> "Connecting"
    ConnectionState.Connected -> "Connected"
    ConnectionState.Ready -> "Ready"
    ConnectionState.Disconnecting -> "Disconnecting"
    is ConnectionState.Failed -> reason
}
