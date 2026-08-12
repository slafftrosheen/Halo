package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.DecodedEvent
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class HeyCyanEventDecoder(
    private val definitions: List<Definition> = emptyList(),
) {

    data class Definition(
        val id: String,
        val evidence: List<Any> = emptyList(),
        val matches: (ByteArray, UUID?) -> Boolean,
        val decode: (ByteArray, UUID?, Long) -> HeyCyanEvent,
    )

    fun decode(
        payload: ByteArray,
        characteristicUuid: UUID? = null,
        timestamp: Long = System.currentTimeMillis(),
    ): DecodedEvent {
        val event = if (payload.isEmpty()) {
            HeyCyanEvent.Unknown(
                characteristicUuid = characteristicUuid?.toString(),
                payload = byteArrayOf(),
            )
        } else {
            val matchedDef = definitions.firstOrNull { it.matches(payload, characteristicUuid) }
            if (matchedDef != null) {
                matchedDef.decode(payload, characteristicUuid, timestamp)
            } else {
                runCatching { decodeKnown(payload, characteristicUuid) }.getOrElse {
                    HeyCyanEvent.Unknown(
                        characteristicUuid = characteristicUuid?.toString(),
                        payload = payload.copyOf()
                    )
                }
            }
        }

        return DecodedEvent(
            event = event,
            rawPayload = payload.copyOf(),
            timestamp = timestamp,
            characteristicUuid = characteristicUuid,
        )
    }

    private fun decodeKnown(payload: ByteArray, characteristicUuid: UUID?): HeyCyanEvent {
        val type = payload[0].toInt() and 0xFF

        return when (type) {
            0x01 -> decodeBattery(payload)
            0x02 -> decodeMediaCounts(payload)
            0x03 -> decodeVersion(payload)
            0x04 -> decodeMode(payload)
            0x05 -> decodeWifiCredentials(payload)
            0x06 -> decodeAiImage(payload)
            else -> {
                HeyCyanEvent.Unknown(
                    characteristicUuid = characteristicUuid?.toString(),
                    payload = payload.copyOf(),
                )
            }
        }
    }

    private fun decodeBattery(payload: ByteArray): HeyCyanEvent {
        require(payload.size >= 3)
        val level = payload[1].toInt() and 0xFF
        val charging = payload[2].toInt() != 0
        return HeyCyanEvent.BatteryUpdated(level = level, isCharging = charging)
    }

    private fun decodeMediaCounts(payload: ByteArray): HeyCyanEvent {
        require(payload.size >= 8)
        val buffer = ByteBuffer.wrap(payload, 1, payload.size - 1).order(ByteOrder.LITTLE_ENDIAN)
        val photo = buffer.short.toInt() and 0xFFFF
        val video = buffer.short.toInt() and 0xFFFF
        val audio = buffer.short.toInt() and 0xFFFF
        val total = if (buffer.remaining() >= 4) buffer.short.toInt() and 0xFFFF else 0
        val currIndex = if (buffer.remaining() >= 2) buffer.short.toInt() and 0xFFFF else 0
        val mediaType = if (buffer.remaining() >= 1) buffer.get().toInt() and 0xFF else (payload[7].toInt() and 0xFF)
        return HeyCyanEvent.MediaCountsUpdated(
            photoCount = photo,
            videoCount = video,
            audioCount = audio,
            total = total,
            currIndex = currIndex,
            mediaType = mediaType,
        )
    }

    private fun decodeVersion(payload: ByteArray): HeyCyanEvent {
        val reader = LengthPrefixedReader(payload, 1)
        return HeyCyanEvent.VersionUpdated(
            hardwareVersion = reader.readUtf8(),
            firmwareVersion = reader.readUtf8(),
            wifiHardwareVersion = reader.readUtf8(),
            wifiFirmwareVersion = reader.readUtf8(),
        )
    }

    private fun decodeMode(payload: ByteArray): HeyCyanEvent {
        require(payload.size >= 2)
        val raw = payload[1].toInt() and 0xFF
        return HeyCyanEvent.ModeChanged(mode = raw)
    }

    private fun decodeWifiCredentials(payload: ByteArray): HeyCyanEvent {
        val reader = LengthPrefixedReader(payload, 1)
        return HeyCyanEvent.WiFiHotspotReady(
            ssid = reader.readUtf8(),
            password = reader.readUtf8(),
        )
    }

    private fun decodeAiImage(payload: ByteArray): HeyCyanEvent {
        return HeyCyanEvent.AiImageData(payload.copyOfRange(1, payload.size))
    }

    private class LengthPrefixedReader(
        private val bytes: ByteArray,
        startIndex: Int,
    ) {
        private var index = startIndex

        fun readUtf8(): String {
            if (index >= bytes.size) return ""
            val length = bytes[index].toInt() and 0xFF
            index++
            if (index + length > bytes.size) return ""
            val result = String(bytes, index, length, Charsets.UTF_8)
            index += length
            return result
        }
    }
}
