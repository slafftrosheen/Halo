package com.halo.sdk.driver

import kotlinx.coroutines.flow.Flow

interface Driver {
    val events: Flow<DeviceEvent>
    suspend fun emit(event: DeviceEvent)
    suspend fun connect()
    suspend fun disconnect()
}

sealed interface DeviceEvent {
    val timestamp: Long

    data class Input(
        override val timestamp: Long,
        val payloadText: String? = null,
        val locale: String? = null
    ) : DeviceEvent

    data class Output(
        override val timestamp: Long,
        val payloadText: String
    ) : DeviceEvent
}
