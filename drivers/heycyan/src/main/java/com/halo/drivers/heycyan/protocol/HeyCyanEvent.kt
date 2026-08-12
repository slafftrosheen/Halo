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
    AUDIO(0x04),
    AI_PHOTO(0x06),
    TRANSFER(0x07);

    companion object {
        fun fromValue(value: Int): HeyCyanMode? =
            entries.firstOrNull { it.value == value }
    }
}

sealed interface HeyCyanEvent {
    data object Ready : HeyCyanEvent

    data object VoiceActivationStarted : HeyCyanEvent
    data object VoiceActivationEnded : HeyCyanEvent

    data class ButtonPressed(
        val type: ButtonType
    ) : HeyCyanEvent

    data class BatteryUpdated(
        val level: Int,
        val isCharging: Boolean
    ) : HeyCyanEvent

    data class MediaCountsUpdated(
        val photoCount: Int,
        val videoCount: Int,
        val audioCount: Int,
        val total: Int,
        val currIndex: Int,
        val mediaType: Int
    ) : HeyCyanEvent

    data class VersionUpdated(
        val hardwareVersion: String,
        val firmwareVersion: String,
        val wifiHardwareVersion: String,
        val wifiFirmwareVersion: String
    ) : HeyCyanEvent

    data class ModeChanged(
        val mode: Int
    ) : HeyCyanEvent

    data class WiFiHotspotReady(
        val ssid: String,
        val password: String
    ) : HeyCyanEvent

    data class WiFiIpReceived(
        val ip: String
    ) : HeyCyanEvent

    /**
     * Aproprietary HeyCyan frame that has been structurally decoded
     * but whose semantic meaning is still being established.
     */
    data class DecodedProprietaryFrame(
        val actionFamily: com.halo.drivers.heycyan.codec.HeyCyanActionFamily,
        val action: Byte,
        val length: Int,
        val isValidCrc: Boolean,
        val rawHex: String,
        val fields: Map<String, Int>,
        val confidence: String = "OBSERVED"
    ) : HeyCyanEvent

    data class ObservedProprietaryFrame(
        val header: Int,
        val family: Int,
        val subtype: Int,
        val payload: ByteArray
    ) : HeyCyanEvent {
        override fun equals(other: Any?): Boolean =
            other is ObservedProprietaryFrame &&
                header == other.header &&
                family == other.family &&
                subtype == other.subtype &&
                payload.contentEquals(other.payload)

        override fun hashCode(): Int {
            var result = header
            result = 31 * result + family
            result = 31 * result + subtype
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    class AiImageData(
        val bytes: ByteArray
    ) : HeyCyanEvent {
        override fun equals(other: Any?): Boolean =
            other is AiImageData && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int =
            bytes.contentHashCode()
    }

    class RawAudioFrame(
        val payload: ByteArray
    ) : HeyCyanEvent {
        override fun equals(other: Any?): Boolean =
            other is RawAudioFrame && payload.contentEquals(other.payload)

        override fun hashCode(): Int =
            payload.contentHashCode()
    }

    data class Unknown(
        val characteristicUuid: String?,
        val payload: ByteArray
    ) : HeyCyanEvent {
        override fun equals(other: Any?): Boolean =
            other is Unknown &&
                characteristicUuid == other.characteristicUuid &&
                payload.contentEquals(other.payload)

        override fun hashCode(): Int {
            var result = characteristicUuid?.hashCode() ?: 0
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }
}
