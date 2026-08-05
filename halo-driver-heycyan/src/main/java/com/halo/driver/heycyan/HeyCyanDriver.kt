package com.halo.driver.heycyan

import com.halo.core.driver.Driver
import com.halo.core.driver.DeviceCapabilities
import com.halo.core.driver.DeviceEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class HeyCyanDriver : Driver {
    override suspend fun discover() { TODO("Not yet implemented") }
    override suspend fun connect() { TODO("Not yet implemented") }
    override suspend fun disconnect() { TODO("Not yet implemented") }

    override val capabilities: Flow<DeviceCapabilities> = emptyFlow()
    override val events: Flow<DeviceEvent> = emptyFlow()
}
