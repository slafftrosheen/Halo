package com.halo.drivers.heycyan

import com.halo.libraries.common.core.Identifier
import com.halo.sdk.driver.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class HeyCyanDriver : Driver {
    override val id = Identifier("driver.heycyan")
    
    override val capabilities = DeviceCapabilities(
        microphone = true,
        speaker = true,
        buttons = true,
        display = false,
        camera = true
    )

    private val _state = MutableStateFlow<DriverState>(DriverState.Disconnected)
    override val state: StateFlow<DriverState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DeviceEvent>()
    override val events: Flow<DeviceEvent> = _events.asSharedFlow()

    override suspend fun connect() {
        _state.value = DriverState.Pairing
        // Simulate connection
        _state.value = DriverState.Connected
        _state.value = DriverState.Ready
    }

    override suspend fun disconnect() {
        _state.value = DriverState.Disconnected
    }
}
