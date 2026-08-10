package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.drivers.heycyan.protocol.HeyCyanMode
import org.junit.Test
import org.junit.Assert.*

class HeyCyanEventDecoderTest {

    private val decoder = HeyCyanEventDecoder()

    @Test
    fun decodeBattery() {
        val packet = byteArrayOf(
            0x01,
            85,
            1
        )

        val event = decoder.decode(packet)

        assertTrue(event is HeyCyanEvent.BatteryUpdated)
        event as HeyCyanEvent.BatteryUpdated
        assertEquals(85, event.level)
        assertTrue(event.isCharging)
    }

    @Test
    fun decodeBatteryNotCharging() {
        val packet = byteArrayOf(
            0x01,
            50.toByte(),
            0
        )

        val event = decoder.decode(packet)

        assertTrue(event is HeyCyanEvent.BatteryUpdated)
        event as HeyCyanEvent.BatteryUpdated
        assertEquals(50, event.level)
        assertFalse(event.isCharging)
    }

    @Test
    fun decodeMediaCounts() {
        val packet = byteArrayOf(
            0x02,
            0x02, 0x00, // 2 photos (little endian)
            0x03, 0x00, // 3 videos
            0x04, 0x00, // 4 audio
            0x01
        )

        val event = decoder.decode(packet)

        assertTrue(event is HeyCyanEvent.MediaCountsUpdated)
        event as HeyCyanEvent.MediaCountsUpdated
        assertEquals(2, event.photoCount)
        assertEquals(3, event.videoCount)
        assertEquals(4, event.audioCount)
        assertEquals(1, event.mediaType)
    }

    @Test
    fun decodeMode() {
        val event = decoder.decode(
            byteArrayOf(
                0x04,
                0x02
            )
        )

        assertTrue(event is HeyCyanEvent.ModeChanged)
        event as HeyCyanEvent.ModeChanged
        assertEquals(HeyCyanMode.VIDEO, event.mode)
        assertEquals(0x02, event.rawMode)
    }

    @Test
    fun unknownModeIsNotCoerced() {
        val event = decoder.decode(
            byteArrayOf(
                0x04,
                0x7F
            )
        ) as HeyCyanEvent.ModeChanged

        assertNull(event.mode)
        assertEquals(0x7F, event.rawMode)
    }

    @Test
    fun unknownEventIsPreserved() {
        val payload = byteArrayOf(
            0x77.toByte(),
            0x01,
            0x02,
            0x03
        )

        val event = decoder.decode(payload)

        assertTrue(event is HeyCyanEvent.Unknown)
        event as HeyCyanEvent.Unknown
        assertEquals(0x77.toInt() and 0xFF, event.eventType)
        assertArrayEquals(payload.copyOfRange(1, payload.size), event.payload)
    }

    @Test
    fun malformedBatteryFails() {
        assertFailsWith<IllegalArgumentException> {
            decoder.decode(
                byteArrayOf(
                    0x01,
                    120.toByte(), // Invalid level
                    0
                )
            )
        }
    }

    @Test
    fun decodeVersion() {
        val packet = byteArrayOf(
            0x03,
            0x02, 'H'.code.toByte(), 'W'.code.toByte(),
            0x02, 'F'.code.toByte(), 'W'.code.toByte(),
            0x03, 'W'.code.toByte(), 'H'.code.toByte(), '1'.code.toByte(),
            0x03, 'W'.code.toByte(), 'F'.code.toByte(), '1'.code.toByte(),
        )

        val event = decoder.decode(packet)

        assertTrue(event is HeyCyanEvent.VersionInfo)
        event as HeyCyanEvent.VersionInfo
        assertEquals("HW", event.hardwareVersion)
        assertEquals("FW", event.firmwareVersion)
        assertEquals("WH1", event.wifiHardwareVersion)
        assertEquals("WF1", event.wifiFirmwareVersion)
    }

    @Test
    fun decodeWifiCredentials() {
        val packet = byteArrayOf(
            0x05,
            0x03, 'H'.code.toByte(), 'C'.code.toByte(), '1'.code.toByte(),
            0x09, 
            '1'.code.toByte(), '2'.code.toByte(), '3'.code.toByte(),
            '4'.code.toByte(), '5'.code.toByte(), '6'.code.toByte(),
            '7'.code.toByte(), '8'.code.toByte(), '9'.code.toByte(),
        )

        val event = decoder.decode(packet)

        assertTrue(event is HeyCyanEvent.WiFiCredentials)
        event as HeyCyanEvent.WiFiCredentials
        assertEquals("HC1", event.ssid)
        assertEquals("123456789", event.password)
    }

    @Test
    fun decodeAiImage() {
        val payload = byteArrayOf(
            0x06,
            0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), // image data
        )

        val event = decoder.decode(payload)

        assertTrue(event is HeyCyanEvent.AIImageData)
        event as HeyCyanEvent.AIImageData
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()),
            event.payload
        )
    }

    @Test
    fun emptyPayloadReturnsNull() {
        assertNull(decoder.decode(byteArrayOf()))
    }

    @Test
    fun tooShortBatteryPayloadFails() {
        assertFailsWith<IllegalArgumentException> {
            decoder.decode(
                byteArrayOf(
                    0x01,
                    50.toByte()
                )
            )
        }
    }

    @Test
    fun tooShortMediaCountsPayloadFails() {
        assertFailsWith<IllegalArgumentException> {
            decoder.decode(
                byteArrayOf(
                    0x02,
                    0x01, 0x00
                )
            )
        }
    }
}