package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeyCyanEventDecoderTest {

    private val decoder = HeyCyanEventDecoder()

    @Test
    fun decodeBattery() {
        val packet = byteArrayOf(
            0x01,
            85,
            1,
        )

        val decoded = decoder.decode(packet)
        val event = decoded.event

        assertTrue(event is HeyCyanEvent.BatteryUpdated)
        event as HeyCyanEvent.BatteryUpdated
        assertEquals(85, event.level)
        assertTrue(event.isCharging)
        assertArrayEquals(packet, decoded.rawPayload)
    }

    @Test
    fun decodeBatteryNotCharging() {
        val packet = byteArrayOf(
            0x01,
            50.toByte(),
            0,
        )

        val decoded = decoder.decode(packet)
        val event = decoded.event

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
            0x01,
        )

        val decoded = decoder.decode(packet)
        val event = decoded.event

        assertTrue(event is HeyCyanEvent.MediaCountsUpdated)
        event as HeyCyanEvent.MediaCountsUpdated
        assertEquals(2, event.photoCount)
        assertEquals(3, event.videoCount)
        assertEquals(4, event.audioCount)
        assertEquals(1, event.mediaType)
    }

    @Test
    fun decodeMode() {
        val decoded = decoder.decode(
            byteArrayOf(
                0x04,
                0x02,
            )
        )
        val event = decoded.event

        assertTrue(event is HeyCyanEvent.ModeChanged)
        event as HeyCyanEvent.ModeChanged
        assertEquals(0x02, event.mode)
    }

    @Test
    fun unknownEventIsPreserved() {
        val payload = byteArrayOf(
            0x77.toByte(),
            0x01,
            0x02,
            0x03,
        )

        val decoded = decoder.decode(payload)
        val event = decoded.event

        assertTrue(event is HeyCyanEvent.Unknown)
        event as HeyCyanEvent.Unknown
        assertArrayEquals(payload, event.payload)
    }

    @Test
    fun tooShortBatteryPayloadReturnsUnknown() {
        // Fallback in decodeKnown returns Unknown if it fails
        val decoded = decoder.decode(
            byteArrayOf(
                0x01,
                50.toByte(),
            )
        )

        assertTrue(decoded.event is HeyCyanEvent.Unknown)
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

        val decoded = decoder.decode(packet)
        val event = decoded.event

        assertTrue(event is HeyCyanEvent.VersionUpdated)
        event as HeyCyanEvent.VersionUpdated
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

        val decoded = decoder.decode(packet)
        val event = decoded.event

        assertTrue(event is HeyCyanEvent.WiFiHotspotReady)
        event as HeyCyanEvent.WiFiHotspotReady
        assertEquals("HC1", event.ssid)
        assertEquals("123456789", event.password)
    }

    @Test
    fun decodeAiImage() {
        val payload = byteArrayOf(
            0x06,
            0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(),
        )

        val decoded = decoder.decode(payload)
        val event = decoded.event

        assertTrue(event is HeyCyanEvent.AiImageData)
        event as HeyCyanEvent.AiImageData
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()),
            event.bytes
        )
    }

    @Test
    fun emptyPayloadReturnsUnknownWithNullChar() {
        val decoded = decoder.decode(byteArrayOf())
        assertTrue(decoded.event is HeyCyanEvent.Unknown)
        assertNull((decoded.event as HeyCyanEvent.Unknown).characteristicUuid)
    }
}
