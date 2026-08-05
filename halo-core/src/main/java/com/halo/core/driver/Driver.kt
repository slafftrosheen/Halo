package com.halo.core.driver

import kotlinx.coroutines.flow.Flow

interface Driver {
    suspend fun discover()
    suspend fun connect()
    suspend fun disconnect()

    val capabilities: Flow<DeviceCapabilities>
    val events: Flow<DeviceEvent>
}

interface DeviceCapabilities
interface DeviceEvent
