package com.halo.drivers.heycyan

import com.halo.sdk.driver.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MockHeyCyanDriver : Driver {
    private val _events = MutableSharedFlow<DeviceEvent>()
    override val events: Flow<DeviceEvent> = _events.asSharedFlow()
    
    private val _state = MutableStateFlow<DriverState>(DriverState.Disconnected)
    override val state: StateFlow<DriverState> = _state

    private val scope = CoroutineScope(Dispatchers.IO)
    private var simulationJob: Job? = null

    override suspend fun emit(event: DeviceEvent) {
        _events.emit(event)
    }

    override suspend fun initialize() {
        _state.value = DriverState.Disconnected
    }

    override suspend fun connect() {
        _state.value = DriverState.Connecting
        delay(500) // Simulate connection delay
        _state.value = DriverState.Connected
        delay(200)
        _state.value = DriverState.Ready
        
        simulationJob = scope.launch {
            // Emulate device states
            _events.emit(DeviceEvent.WearState(System.currentTimeMillis(), true))
            _events.emit(DeviceEvent.BatteryLevel(System.currentTimeMillis(), 85))
        }
    }

    override suspend fun disconnect() {
        simulationJob?.cancel()
        _state.value = DriverState.Disconnected
    }
    
    override suspend fun shutdown() {
        disconnect()
    }
    
    suspend fun simulateButtonClick(buttonId: Int = 1) {
        _events.emit(DeviceEvent.ButtonClick(System.currentTimeMillis(), buttonId))
    }
}
