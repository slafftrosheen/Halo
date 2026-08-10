package com.halo.drivers.heycyan.protocol

enum class ButtonType {
    SINGLE_TAP,
    DOUBLE_TAP,
    LONG_PRESS,
    VOICE_BUTTON,
    CAMERA_BUTTON,
    UNKNOWN,
}

enum class HeyCyanMode(val value: Int) {
    PHOTO(0x01),
    VIDEO(0x02),
    VIDEO_STOP(0x03),
    AUDIO(0x04),
    AUDIO_STOP(0x05),
    AI_PHOTO(0x06),
    TRANSFER(0x07);

    companion object {
        fun fromValue(value: Int): HeyCyanMode? =
            entries.firstOrNull { it.value == value }
    }
}

sealed interface HeyCyanEvent {
    data object Ready : HeyCyanEvent

    data class ButtonPressed(
        val type: ButtonType,
    ) : HeyCyanEvent

    data class BatteryUpdated(
        val level: Int,
        val isCharging: Boolean,
    ) : HeyCyanEvent

    data class MediaCountsUpdated(
        val photoCount: Int,
        val videoCount: Int,
        val audioCount: Int,
        val mediaType: Int,
    ) : HeyCyanEvent

    data class VersionInfo(
        val hardwareVersion: String,
        val firmwareVersion: String,
        val wifiHardwareVersion: String,
        val wifiFirmwareVersion: String,
    ) : HeyCyanEvent

    data class ModeChanged(
        val mode: HeyCyanMode?,
        val rawMode: Int,
    ) : HeyCyanEvent

    data class WiFiCredentials(
        val ssid: String,
        val password: String,
    ) : HeyCyanEvent

    data class AIImageData(
        val payload: ByteArray,
    ) : HeyCyanEvent {
        override fun equals(other: Any?): Boolean =
            other is AIImageData && payload.contentEquals(other.payload)

        override fun hashCode(): Int = payload.contentHashCode()
    }

    data class Malformed(
        val payload: ByteArray,
        val reason: String,
    ) : HeyCyanEvent {
        override fun equals(other: Any?): Boolean =
            other is Malformed &&
                payload.contentEquals(other.payload) &&
                reason == other.reason

        override fun hashCode(): Int =
            31 * payload.contentHashCode() + reason.hashCode()
    }

    data class Unknown(
        val eventType: Int,
        val payload: ByteArray,
    ) : HeyCyanEvent {
        override fun equals(other: Any?): Boolean =
            other is Unknown &&
                eventType == other.eventType &&
                payload.contentEquals(other.payload)

        override fun hashCode(): Int =
            31 * eventType + payload.contentHashCode()
    }

    data class UnknownEvent(
        val payload: ByteArray,
        val characteristic: java.util.UUID? = null,
        val timestamp: Long = 0L,
    ) : HeyCyanEvent {
        override fun equals(other: Any?): Boolean =
            other is UnknownEvent &&
                payload.contentEquals(other.payload) &&
                characteristic == other.characteristic &&
                timestamp == other.timestamp

        override fun hashCode(): Int {
            var result = payload.contentHashCode()
            result = 31 * result + (characteristic?.hashCode() ?: 0)
            result = 31 * result + timestamp.hashCode()
            return result
        }
    }
}