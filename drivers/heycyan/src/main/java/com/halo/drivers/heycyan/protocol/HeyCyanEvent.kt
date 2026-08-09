package com.halo.drivers.heycyan.protocol

import java.util.UUID

/**
 * The types of button interactions supported by HeyCyan hardware.
 */
enum class ButtonType {
    SINGLE_TAP,
    DOUBLE_TAP,
    LONG_PRESS,
    VOICE_BUTTON,
    CAMERA_BUTTON
}

/**
 * Notifications and callbacks emitted by the HeyCyan glasses over BLE.
 */
sealed interface HeyCyanEvent {
    /** Emitted only by an evidence-backed decoder definition. */
    data object Ready : HeyCyanEvent

    // Hardware Inputs
    data class ButtonPressed(val type: ButtonType) : HeyCyanEvent
    
    // Status Updates
    data class BatteryUpdated(val level: Int, val isCharging: Boolean) : HeyCyanEvent
    data class MediaCountsUpdated(val photos: Int, val videos: Int, val audio: Int) : HeyCyanEvent
    data class DeviceInfoReceived(val hardwareVersion: String, val firmwareVersion: String) : HeyCyanEvent
    
    // Capture Callbacks
    data object PhotoCaptured : HeyCyanEvent
    data object RecordingStarted : HeyCyanEvent
    data object RecordingStopped : HeyCyanEvent
    
    // Connectivity
    data class WiFiHotspotReady(val ssid: String, val pass: String) : HeyCyanEvent

    /** Raw notification retained when no verified protocol definition matches it. */
    class UnknownEvent(
        payload: ByteArray,
        val characteristic: UUID? = null,
        val timestamp: Long? = null,
        val reason: String = "No evidence-backed protocol definition matched"
    ) : HeyCyanEvent {
        val payload: ByteArray = payload.copyOf()

        init {
            require(reason.isNotBlank()) { "Unknown event reason must not be blank" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is UnknownEvent) return false
            return payload.contentEquals(other.payload) &&
                characteristic == other.characteristic &&
                timestamp == other.timestamp &&
                reason == other.reason
        }

        override fun hashCode(): Int {
            var result = payload.contentHashCode()
            result = 31 * result + (characteristic?.hashCode() ?: 0)
            result = 31 * result + (timestamp?.hashCode() ?: 0)
            result = 31 * result + reason.hashCode()
            return result
        }
    }
}
