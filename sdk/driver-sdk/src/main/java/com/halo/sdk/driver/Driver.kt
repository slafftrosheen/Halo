package com.halo.sdk.driver

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface Driver {
    val events: Flow<DeviceEvent>
    val state: StateFlow<DriverState>
    
    suspend fun initialize()
    suspend fun connect()
    suspend fun disconnect()
    suspend fun shutdown()
    
    suspend fun emit(event: DeviceEvent)
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

    data class ButtonClick(
        override val timestamp: Long,
        val buttonId: Int
    ) : DeviceEvent

    data class BatteryLevel(
        override val timestamp: Long,
        val level: Int
    ) : DeviceEvent

    data class WearState(
        override val timestamp: Long,
        val isWorn: Boolean
    ) : DeviceEvent
}
