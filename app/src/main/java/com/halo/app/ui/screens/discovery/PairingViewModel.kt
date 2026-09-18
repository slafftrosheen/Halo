package com.halo.app.ui.screens.discovery

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.di.ManualDependencyGraph
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.ble.DiscoveredBleDevice
import com.halo.sdk.host.ConnectionState
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class PairingUiState(
    val devices: List<DiscoveredBleDevice> = emptyList(),
    val isScanning: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val selectedAddress: String? = null,
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
                    host.connectivity.filter { it == ConnectionState.Ready }.first()
                    true
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
                deviceRegistry.saveDevice(
                    (existing ?: com.halo.sdk.runtime.embodiment.DeviceRecord(
                        identity = com.halo.sdk.runtime.DeviceIdentity(
                            deviceId = address,
                            displayName = deviceName,
                            deviceType = "HEYCYAN_GLASSES"
                        )
                    )).copy(
                        identity = (
                            existing?.identity
                                ?: com.halo.sdk.runtime.DeviceIdentity(
                                    deviceId = address,
                                    deviceType = "HEYCYAN_GLASSES",
                                    displayName = deviceName
                                )
                            ).copy(displayName = deviceName),
                        lastSeenMs = System.currentTimeMillis()
                    )
                )
                _uiState.update { it.copy(statusMessage = "Connected and saved") }
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
