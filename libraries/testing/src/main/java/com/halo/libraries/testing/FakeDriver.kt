package com.halo.libraries.testing

import com.halo.sdk.driver.DeviceEvent
import com.halo.sdk.driver.Driver
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

import com.halo.sdk.driver.DriverState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeDriver(
    private val deviceId: String
) : Driver {

    private val _events = MutableSharedFlow<DeviceEvent>(extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()
    
    private val _state = MutableStateFlow<DriverState>(DriverState.Disconnected)
    override val state: StateFlow<DriverState> = _state

    override suspend fun emit(event: DeviceEvent) {
        _events.emit(event)
    }

    override suspend fun initialize() {
        _state.value = DriverState.Disconnected
    }

    override suspend fun connect() {
        _state.value = DriverState.Connected
    }

    override suspend fun disconnect() {
        _state.value = DriverState.Disconnected
    }
    
    override suspend fun shutdown() {
        disconnect()
    }
}
