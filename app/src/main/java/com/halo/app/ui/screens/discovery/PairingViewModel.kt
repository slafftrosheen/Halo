package com.halo.app.ui.screens.discovery

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.di.ManualDependencyGraph
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.ble.DiscoveredBleDevice
import com.halo.sdk.driver.DriverState
import com.halo.sdk.host.ConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PairingUiState(
    val devices: List<DiscoveredBleDevice> = emptyList(),
    val isScanning: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val selectedAddress: String? = null,
    val statusMessage: String? = null
)

class PairingViewModel(
    private val driver: RealHeyCyanDriver = ManualDependencyGraph.heyCyanDriver
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            ManualDependencyGraph.wearableHost.connectivity.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    fun startScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(isScanning = true, devices = emptyList(), statusMessage = "Scanning for glasses...") }
        
        scanJob = viewModelScope.launch {
            val seen = mutableSetOf<String>()
            driver.scanner.scan().collect { advertisement ->
                if (seen.add(advertisement.address)) {
                    val device = DiscoveredBleDevice(advertisement, System.currentTimeMillis())
                    _uiState.update { state ->
                        state.copy(devices = state.devices + device)
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
        Log.d("PairingViewModel", "Connecting to: $address")
        stopScan()
        
        _uiState.update { it.copy(statusMessage = "Connecting to $address...") }
        
        viewModelScope.launch {
            try {
                val host = ManualDependencyGraph.wearableHost
                if (host is com.halo.drivers.heycyan.HeyCyanHost) {
                    Log.d("PairingViewModel", "Setting target device and connecting")
                    host.setTargetDevice(address)
                    host.connect()
                    _uiState.update { it.copy(statusMessage = "Connected successfully") }
                } else {
                    Log.w("PairingViewModel", "WearableHost is not HeyCyanHost: ${host::class.simpleName}")
                }
            } catch (e: Exception) {
                Log.e("PairingViewModel", "Connection failed", e)
                _uiState.update { it.copy(statusMessage = "Connection failed: ${e.message}") }
            }
        }
    }

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents = _navigationEvents.asSharedFlow()

    fun navigateBack() {
        viewModelScope.launch {
            _navigationEvents.emit("back")
        }
    }
}
