package com.halo.sdk.driver

import com.halo.libraries.common.core.Identifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface Driver {
    val id: Identifier
    val capabilities: DeviceCapabilities
    val state: StateFlow<DriverState>
    val events: Flow<DeviceEvent>

    suspend fun connect()
    suspend fun disconnect()
}
