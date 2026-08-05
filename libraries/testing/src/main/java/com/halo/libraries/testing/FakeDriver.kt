package com.halo.libraries.testing

import com.halo.sdk.driver.DeviceEvent
import com.halo.sdk.driver.Driver
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class FakeDriver(
    private val deviceId: String
) : Driver {

    private val _events = MutableSharedFlow<DeviceEvent>(extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()

    override suspend fun emit(event: DeviceEvent) {
        _events.emit(event)
    }

    override suspend fun connect() {
        // no-op for now
    }

    override suspend fun disconnect() {
        // no-op for now
    }
}
