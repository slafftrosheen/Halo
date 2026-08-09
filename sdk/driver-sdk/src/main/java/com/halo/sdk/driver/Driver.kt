package com.halo.sdk.driver

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

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

    data class ChargingState(
        override val timestamp: Long,
        val isCharging: Boolean
    ) : DeviceEvent

    data class WearState(
        override val timestamp: Long,
        val isWorn: Boolean
    ) : DeviceEvent

    data class Ready(
        override val timestamp: Long
    ) : DeviceEvent

    /** Raw device data that was intentionally not interpreted by the driver. */
    class ProtocolUnknown(
        override val timestamp: Long,
        val characteristic: UUID?,
        payload: ByteArray,
        val reason: String
    ) : DeviceEvent {
        val payload: ByteArray = payload.copyOf()

        init {
            require(reason.isNotBlank()) { "Unknown protocol event reason must not be blank" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ProtocolUnknown) return false
            return timestamp == other.timestamp &&
                characteristic == other.characteristic &&
                payload.contentEquals(other.payload) &&
                reason == other.reason
        }

        override fun hashCode(): Int {
            var result = timestamp.hashCode()
            result = 31 * result + (characteristic?.hashCode() ?: 0)
            result = 31 * result + payload.contentHashCode()
            result = 31 * result + reason.hashCode()
            return result
        }

        override fun toString(): String =
            "ProtocolUnknown(timestamp=$timestamp, characteristic=$characteristic, " +
                "payload=${payload.contentToString()}, reason=$reason)"
    }
}
