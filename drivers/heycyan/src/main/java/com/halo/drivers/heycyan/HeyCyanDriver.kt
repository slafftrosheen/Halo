package com.halo.drivers.heycyan

import com.halo.sdk.driver.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class HeyCyanDriver : Driver {
    private val _events = MutableSharedFlow<DeviceEvent>()
    override val events: Flow<DeviceEvent> = _events.asSharedFlow()

    override suspend fun emit(event: DeviceEvent) {
        _events.emit(event)
    }

    override suspend fun connect() {
    }

    override suspend fun disconnect() {
    }
}
