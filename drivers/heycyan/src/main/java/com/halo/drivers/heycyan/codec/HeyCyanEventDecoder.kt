package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.drivers.heycyan.protocol.HeyCyanMode
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HeyCyanEventDecoder {

    fun decode(payload: ByteArray): HeyCyanEvent? {
        if (payload.isEmpty()) return null

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
                    eventType = type,
                    payload = payload.copyOf()
                )
            }
        }
    }

    private fun decodeBattery(
        payload: ByteArray
    ): HeyCyanEvent {
        require(payload.size >= 3) {
            "Battery event requires 3 bytes, got ${payload.size}"
        }

        val level = payload[1].toInt() and 0xFF
        val charging = payload[2].toInt() != 0

        require(level in 0..100) {
            "Invalid HeyCyan battery level: $level"
        }

        return HeyCyanEvent.BatteryUpdated(
            level = level,
            isCharging = charging
        )
    }

    private fun decodeMediaCounts(
        payload: ByteArray
    ): HeyCyanEvent {
        require(payload.size >= 8) {
            "Media count event requires 8 bytes, got ${payload.size}"
        }

        val buffer = ByteBuffer
            .wrap(payload, 1, 6)
            .order(ByteOrder.LITTLE_ENDIAN)

        val photos = buffer.short.toInt() and 0xFFFF
        val videos = buffer.short.toInt() and 0xFFFF
        val audio = buffer.short.toInt() and 0xFFFF

        val mediaType = payload[7].toInt() and 0xFF

        return HeyCyanEvent.MediaCountsUpdated(
            photoCount = photos,
            videoCount = videos,
            audioCount = audio,
            mediaType = mediaType
        )
    }

    private fun decodeVersion(
        payload: ByteArray
    ): HeyCyanEvent {
        val reader = LengthPrefixedReader(payload, 1)

        return HeyCyanEvent.VersionInfo(
            hardwareVersion = reader.readUtf8(),
            firmwareVersion = reader.readUtf8(),
            wifiHardwareVersion = reader.readUtf8(),
            wifiFirmwareVersion = reader.readUtf8(),
        )
    }

    private fun decodeMode(
        payload: ByteArray
    ): HeyCyanEvent {
        require(payload.size >= 2) {
            "Mode event requires 2 bytes"
        }

        val raw = payload[1].toInt() and 0xFF

        return HeyCyanEvent.ModeChanged(
            mode = HeyCyanMode.fromValue(raw),
            rawMode = raw,
        )
    }

    private fun decodeWifiCredentials(
        payload: ByteArray
    ): HeyCyanEvent {
        val reader = LengthPrefixedReader(payload, 1)

        return HeyCyanEvent.WiFiCredentials(
            ssid = reader.readUtf8(),
            password = reader.readUtf8(),
        )
    }

    private fun decodeAiImage(
        payload: ByteArray
    ): HeyCyanEvent {
        return HeyCyanEvent.AIImageData(
            payload = payload.copyOfRange(1, payload.size)
        )
    }

    private class LengthPrefixedReader(
        private val bytes: ByteArray,
        startIndex: Int,
    ) {
        private var index = startIndex

        fun readUtf8(): String {
            require(index < bytes.size) {
                "Missing length prefix at index $index"
            }

            val length = bytes[index].toInt() and 0xFF
            index++

            require(index + length <= bytes.size) {
                "Length-prefixed string exceeds payload: " +
                    "index=$index length=$length size=${bytes.size}"
            }

            val result = bytes
                .copyOfRange(index, index + length)
                .toString(Charsets.UTF_8)

            index += length

            return result
        }
    }
}