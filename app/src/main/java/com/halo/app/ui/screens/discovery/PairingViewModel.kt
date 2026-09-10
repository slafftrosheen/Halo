package com.halo.app.ui.screens.discovery

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.ble.DiscoveredBleDevice
import com.halo.sdk.host.ConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class PairingUiState(
    val devices: List<DiscoveredBleDevice> = emptyList(),
    val isScanning: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val selectedAddress: String? = null,
    val statusMessage: String? = null
)

class PairingViewModel(
    private val driver: RealHeyCyanDriver = com.halo.app.di.ManualDependencyGraph.heyCyanDriver,
    private val deviceRegistry: com.halo.sdk.runtime.embodiment.DeviceRegistry = com.halo.app.di.ManualDependencyGraph.deviceRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()
    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            com.halo.app.di.ManualDependencyGraph.wearableHost.connectivity.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    fun startScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(isScanning = true, devices = emptyList(), statusMessage = "Scanning for glasses...") }
        scanJob = viewModelScope.launch {
            val seen = mutableSetOf<String>()
            driver.scanner.scan()
                .catch { e ->
                    if (e is CancellationException) throw e
                    Log.e("PairingViewModel", "Scan failed", e)
                    _uiState.update { it.copy(isScanning = false, statusMessage = "Scan failed: ${e.message ?: "Unknown error"}") }
                }
                .collect { advertisement ->
                    if (seen.add(advertisement.address)) {
                        _uiState.update { state ->
                            state.copy(devices = state.devices + DiscoveredBleDevice(advertisement, System.currentTimeMillis()))
                        }
                    }
                }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(isScanning = false) }
    }

    fun selectDevice(device: DiscoveredBleDevice) {
        _uiState.update { it.copy(selectedAddress = device.advertisement.address) }
    }

    fun connect() {
        val address = _uiState.value.selectedAddress ?: return
        stopScan()
        _uiState.update { it.copy(statusMessage = "Connecting to $address...") }

        viewModelScope.launch {
            try {
                val host = com.halo.app.di.ManualDependencyGraph.wearableHost
                if (host !is com.halo.drivers.heycyan.HeyCyanHost) {
                    _uiState.update { it.copy(statusMessage = "HeyCyan host is unavailable") }
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
                    _uiState.update { it.copy(statusMessage = "Connection did not reach READY (state: $state)") }
                    return@launch
                }

                val discoveredDevice = _uiState.value.devices.find { it.advertisement.address == address }
                val deviceName = discoveredDevice?.advertisement?.name?.takeIf { it.isNotBlank() } ?: "HeyCyan Glasses"
                val existing = deviceRegistry.getDevice(address)
                deviceRegistry.saveDevice(
                    (existing ?: com.halo.sdk.runtime.embodiment.DeviceRecord(
                        identity = com.halo.sdk.runtime.DeviceIdentity(
                            deviceId = address,
                            displayName = deviceName,
                            deviceType = "HEYCYAN_GLASSES"
                        )
                    )).copy(
                        identity = (existing?.identity ?: com.halo.sdk.runtime.DeviceIdentity(address, deviceName, "HEYCYAN_GLASSES")).copy(displayName = deviceName),
                        lastSeenMs = System.currentTimeMillis()
                    )
                )
                _uiState.update { it.copy(statusMessage = "Connected and saved") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PairingViewModel", "Connection failed", e)
                _uiState.update { it.copy(statusMessage = "Connection failed: ${e.message ?: "Unknown error"}") }
            }
        }
    }

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents = _navigationEvents.asSharedFlow()

    fun navigateBack() {
        viewModelScope.launch { _navigationEvents.emit("back") }
    }
}
