package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.ButtonType
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoder for the event format documented by the HeyCyan SDK/CyanBridge.
 *
 * This decoder is intentionally separate from the raw button-stream
 * decoder because the physical device may expose several notification
 * characteristics carrying different protocol families.
 */
class HeyCyanSdkEventDecoder {

    fun decode(payload: ByteArray): HeyCyanEvent? {
        if (payload.isEmpty()) return null

        return runCatching {
            val buffer = ByteBuffer
                .wrap(payload)
                .order(ByteOrder.LITTLE_ENDIAN)

            when (buffer.get().toInt() and 0xFF) {
                EVENT_BATTERY -> decodeBattery(buffer)
                EVENT_MEDIA -> decodeMedia(buffer)
                EVENT_VERSION -> decodeVersion(buffer)
                EVENT_MODE -> decodeMode(buffer)
                EVENT_WIFI -> decodeWifi(buffer)
                EVENT_AI_IMAGE -> decodeAiImage(buffer)
                EVENT_WIFI_IP -> decodeWifiIp(buffer)
                else -> null
            }
        }.getOrNull()
    }

    private fun decodeBattery(buffer: ByteBuffer): HeyCyanEvent {
        require(buffer.remaining() >= 2)

        val level = buffer.get().toInt() and 0xFF
        val charging = buffer.get().toInt() != 0

        return HeyCyanEvent.BatteryUpdated(
            level = level.coerceIn(0, 100),
            isCharging = charging
        )
    }

    private fun decodeMedia(buffer: ByteBuffer): HeyCyanEvent {
        require(buffer.remaining() >= 7)

        val photoCount = buffer.short.toInt() and 0xFFFF
        val videoCount = buffer.short.toInt() and 0xFFFF
        val audioCount = buffer.short.toInt() and 0xFFFF
        
        val total = if (buffer.remaining() >= 4) buffer.short.toInt() and 0xFFFF else 0
        val currIndex = if (buffer.remaining() >= 2) buffer.short.toInt() and 0xFFFF else 0
        val mediaType = if (buffer.remaining() >= 1) buffer.get().toInt() and 0xFF else 0

        return HeyCyanEvent.MediaCountsUpdated(
            photoCount = photoCount,
            videoCount = videoCount,
            audioCount = audioCount,
            total = total,
            currIndex = currIndex,
            mediaType = mediaType
        )
    }

    private fun decodeVersion(buffer: ByteBuffer): HeyCyanEvent {
        val hardware = readLengthPrefixedString(buffer)
        val firmware = readLengthPrefixedString(buffer)
        val wifiHardware = readLengthPrefixedString(buffer)
        val wifiFirmware = readLengthPrefixedString(buffer)

        return HeyCyanEvent.VersionUpdated(
            hardwareVersion = hardware,
            firmwareVersion = firmware,
            wifiHardwareVersion = wifiHardware,
            wifiFirmwareVersion = wifiFirmware
        )
    }

    private fun decodeMode(buffer: ByteBuffer): HeyCyanEvent {
        require(buffer.remaining() >= 1)

        val mode = buffer.get().toInt() and 0xFF

        return HeyCyanEvent.ModeChanged(mode)
    }

    private fun decodeWifi(buffer: ByteBuffer): HeyCyanEvent {
        val ssid = readLengthPrefixedString(buffer)
        val password = readLengthPrefixedString(buffer)

        return HeyCyanEvent.WiFiHotspotReady(
            ssid = ssid,
            password = password
        )
    }

    private fun decodeAiImage(buffer: ByteBuffer): HeyCyanEvent {
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        return HeyCyanEvent.AiImageData(data)
    }

    private fun decodeWifiIp(buffer: ByteBuffer): HeyCyanEvent {
        // IP address starts at offset 7 of the payload
        // The event type is byte 0, so offset 7 in payload is index 7 of the underlying array,
        // which means we need to jump to buffer.position(7) or skip bytes.
        // We know buffer is at position 1 (because event type was read).
        require(buffer.capacity() >= 11)
        buffer.position(7)
        
        val ipPart1 = buffer.get().toInt() and 0xFF
        val ipPart2 = buffer.get().toInt() and 0xFF
        val ipPart3 = buffer.get().toInt() and 0xFF
        val ipPart4 = buffer.get().toInt() and 0xFF
        
        val ipString = "$ipPart1.$ipPart2.$ipPart3.$ipPart4"
        return HeyCyanEvent.WiFiIpReceived(ipString)
    }

    private fun readLengthPrefixedString(buffer: ByteBuffer): String {
        require(buffer.remaining() >= 1)

        val length = buffer.get().toInt() and 0xFF

        require(length <= buffer.remaining())

        val bytes = ByteArray(length)
        buffer.get(bytes)

        return bytes.toString(Charsets.UTF_8)
    }

    companion object {
        const val EVENT_BATTERY = 0x01
        const val EVENT_MEDIA = 0x02
        const val EVENT_VERSION = 0x03
        const val EVENT_MODE = 0x04
        const val EVENT_WIFI = 0x05
        const val EVENT_AI_IMAGE = 0x06
        const val EVENT_WIFI_IP = 0x08
    }
}