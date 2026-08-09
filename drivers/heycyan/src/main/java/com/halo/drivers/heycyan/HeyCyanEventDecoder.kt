package com.halo.drivers.heycyan

import java.util.UUID

/**
 * Verified event decoder for HeyCyan BLE notifications.
 *
 * Event types extracted from CyanBridge `MemoMindGattClient.kt` and `GlassesWiFiHandler.m`:
 * - 0x01: Battery (level + charging)
 * - 0x02: Media Counts (photos, videos, audios, type)
 * - 0x03: Version Info (4 strings)
 * - 0x04: Mode Change (current mode)
 * - 0x05: WiFi Credentials (SSID + password)
 * - 0x06: AI Image Data (multi-packet)
 *
 * Unknown event types are preserved as HeyCyanBleEvent.Unknown.
 */
class HeyCyanEventDecoder {

    fun decode(
        payload: ByteArray,
        characteristic: UUID,
        timestamp: Long
    ): HeyCyanBleEvent {
        if (payload.isEmpty()) {
            return HeyCyanBleEvent.Unknown(
                eventType = -1,
                rawPayload = payload,
                characteristic = characteristic,
                timestamp = timestamp
            )
        }

        val eventType = payload[0].toInt() and 0xFF

        return when (eventType) {
            0x01 -> decodeBattery(payload, characteristic, timestamp)
            0x02 -> decodeMediaCounts(payload, characteristic, timestamp)
            0x03 -> decodeVersion(payload, characteristic, timestamp)
            0x04 -> decodeModeChange(payload, characteristic, timestamp)
            0x05 -> decodeWiFiCredentials(payload, characteristic, timestamp)
            0x06 -> decodeAIImageChunk(payload, characteristic, timestamp)
            0xFF -> HeyCyanBleEvent.Error(
                code = if (payload.size > 1) (payload[1].toInt() and 0xFF) else 0,
                characteristic = characteristic,
                timestamp = timestamp
            )
            else -> HeyCyanBleEvent.Unknown(
                eventType = eventType,
                rawPayload = payload,
                characteristic = characteristic,
                timestamp = timestamp
            )
        }
    }

    private fun decodeBattery(
        payload: ByteArray,
        characteristic: UUID,
        timestamp: Long
    ): HeyCyanBleEvent {
        // Payload: [0x01][level: UInt8][charging: Bool]
        if (payload.size < 3) {
            return HeyCyanBleEvent.Unknown(0x01, payload, characteristic, timestamp)
        }
        val level = (payload[1].toInt() and 0xFF).coerceIn(0, 100)
        val isCharging = (payload[2].toInt() and 0xFF) != 0
        return HeyCyanBleEvent.Battery(
            level = level,
            isCharging = isCharging,
            characteristic = characteristic,
            timestamp = timestamp
        )
    }

    private fun decodeMediaCounts(
        payload: ByteArray,
        characteristic: UUID,
        timestamp: Long
    ): HeyCyanBleEvent {
        // Payload: [0x02][photo: UInt16 LE][video: UInt16 LE][audio: UInt16 LE][type: UInt8]
        if (payload.size < 8) {
            return HeyCyanBleEvent.Unknown(0x02, payload, characteristic, timestamp)
        }
        val photos = readUInt16LE(payload, 1)
        val videos = readUInt16LE(payload, 3)
        val audios = readUInt16LE(payload, 5)
        val mediaType = (payload[7].toInt() and 0xFF)
        return HeyCyanBleEvent.MediaCounts(
            photos = photos,
            videos = videos,
            audios = audios,
            mediaType = mediaType,
            characteristic = characteristic,
            timestamp = timestamp
        )
    }

    private fun decodeVersion(
        payload: ByteArray,
        characteristic: UUID,
        timestamp: Long
    ): HeyCyanBleEvent {
        // Payload: [0x03][len1][str1][len2][str2][len3][str3][len4][str4]
        // All strings UTF-8, length-prefixed (1 byte)
        var offset = 1
        val strings = mutableListOf<String>()

        repeat(4) {
            if (offset >= payload.size) {
                return HeyCyanBleEvent.Unknown(0x03, payload, characteristic, timestamp)
            }
            val len = (payload[offset].toInt() and 0xFF)
            offset++
            if (offset + len > payload.size) {
                return HeyCyanBleEvent.Unknown(0x03, payload, characteristic, timestamp)
            }
            val str = String(payload.copyOfRange(offset, offset + len), kotlin.text.Charsets.UTF_8)
            strings.add(str)
            offset += len
        }

        if (strings.size == 4) {
            return HeyCyanBleEvent.Version(
                hardwareVersion = strings[0],
                firmwareVersion = strings[1],
                wifiHardwareVersion = strings[2],
                wifiFirmwareVersion = strings[3],
                characteristic = characteristic,
                timestamp = timestamp
            )
        } else {
            return HeyCyanBleEvent.Unknown(0x03, payload, characteristic, timestamp)
        }
    }

    private fun decodeModeChange(
        payload: ByteArray,
        characteristic: UUID,
        timestamp: Long
    ): HeyCyanBleEvent {
        // Payload: [0x04][current_mode: UInt8]
        if (payload.size < 2) {
            return HeyCyanBleEvent.Unknown(0x04, payload, characteristic, timestamp)
        }
        val modeByte = payload[1].toByte()
        val mode = HeyCyanMode.values().firstOrNull { it.byteValue == modeByte }
            ?: HeyCyanMode.PHOTO // Default fallback
        return HeyCyanBleEvent.ModeChanged(
            mode = mode,
            characteristic = characteristic,
            timestamp = timestamp
        )
    }

    private fun decodeWiFiCredentials(
        payload: ByteArray,
        characteristic: UUID,
        timestamp: Long
    ): HeyCyanBleEvent {
        // Payload: [0x05][ssid_len][ssid_bytes][pwd_len][pwd_bytes]
        var offset = 1
        if (offset >= payload.size) {
            return HeyCyanBleEvent.Unknown(0x05, payload, characteristic, timestamp)
        }

        val ssidLen = (payload[offset].toInt() and 0xFF)
        offset++
        if (offset + ssidLen > payload.size) {
            return HeyCyanBleEvent.Unknown(0x05, payload, characteristic, timestamp)
        }
        val ssid = String(payload.copyOfRange(offset, offset + ssidLen), kotlin.text.Charsets.UTF_8)
        offset += ssidLen

        if (offset >= payload.size) {
            return HeyCyanBleEvent.Unknown(0x05, payload, characteristic, timestamp)
        }
        val pwdLen = (payload[offset].toInt() and 0xFF)
        offset++
        if (offset + pwdLen > payload.size) {
            return HeyCyanBleEvent.Unknown(0x05, payload, characteristic, timestamp)
        }
        val password = String(payload.copyOfRange(offset, offset + pwdLen), kotlin.text.Charsets.UTF_8)

        return HeyCyanBleEvent.WiFiCredentials(
            ssid = ssid,
            password = password,
            characteristic = characteristic,
            timestamp = timestamp
        )
    }

    private fun decodeAIImageChunk(
        payload: ByteArray,
        characteristic: UUID,
        timestamp: Long
    ): HeyCyanBleEvent {
        // Payload: [0x06][total_chunks: UInt8][chunk_index: UInt8][data...]
        if (payload.size < 3) {
            return HeyCyanBleEvent.Unknown(0x06, payload, characteristic, timestamp)
        }
        val totalChunks = (payload[1].toInt() and 0xFF)
        val chunkIndex = (payload[2].toInt() and 0xFF)
        val data = payload.copyOfRange(3, payload.size)

        return HeyCyanBleEvent.AIImageChunk(
            totalChunks = totalChunks,
            chunkIndex = chunkIndex,
            data = data,
            characteristic = characteristic,
            timestamp = timestamp
        )
    }

    private fun readUInt16LE(payload: ByteArray, offset: Int): Int {
        if (offset + 1 >= payload.size) return 0
        return ((payload[offset + 1].toInt() and 0xFF) shl 8) or (payload[offset].toInt() and 0xFF)
    }
}

/**
 * Events decoded from verified HeyCyan BLE notifications.
 */
sealed class HeyCyanBleEvent(
    open val characteristic: UUID,
    open val timestamp: Long
) {
    data class Battery(
        val level: Int,
        val isCharging: Boolean,
        override val characteristic: UUID,
        override val timestamp: Long
    ) : HeyCyanBleEvent(characteristic, timestamp)

    data class MediaCounts(
        val photos: Int,
        val videos: Int,
        val audios: Int,
        val mediaType: Int,
        override val characteristic: UUID,
        override val timestamp: Long
    ) : HeyCyanBleEvent(characteristic, timestamp)

    data class Version(
        val hardwareVersion: String,
        val firmwareVersion: String,
        val wifiHardwareVersion: String,
        val wifiFirmwareVersion: String,
        override val characteristic: UUID,
        override val timestamp: Long
    ) : HeyCyanBleEvent(characteristic, timestamp)

    data class ModeChanged(
        val mode: HeyCyanMode,
        override val characteristic: UUID,
        override val timestamp: Long
    ) : HeyCyanBleEvent(characteristic, timestamp)

    data class WiFiCredentials(
        val ssid: String,
        val password: String,
        override val characteristic: UUID,
        override val timestamp: Long
    ) : HeyCyanBleEvent(characteristic, timestamp)

    data class AIImageChunk(
        val totalChunks: Int,
        val chunkIndex: Int,
        val data: ByteArray,
        override val characteristic: UUID,
        override val timestamp: Long
    ) : HeyCyanBleEvent(characteristic, timestamp)

    data class Error(
        val code: Int,
        override val characteristic: UUID,
        override val timestamp: Long
    ) : HeyCyanBleEvent(characteristic, timestamp)

    data class Unknown(
        val eventType: Int,
        val rawPayload: ByteArray,
        override val characteristic: UUID,
        override val timestamp: Long
    ) : HeyCyanBleEvent(characteristic, timestamp)
}

enum class HeyCyanMode(val byteValue: Byte) {
    PHOTO(0x01),
    VIDEO(0x02),
    VIDEO_STOP(0x03),
    AUDIO(0x04),
    AUDIO_STOP(0x05),
    AI_PHOTO(0x06),
    TRANSFER(0x07)
}