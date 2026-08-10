package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.drivers.heycyan.protocol.HeyCyanMode
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
        assertEquals(HeyCyanMode.VIDEO, event.mode)
        assertEquals(0x02, event.rawMode)
    }

    @Test
    fun unknownModeIsNotCoerced() {
        val decoded = decoder.decode(
            byteArrayOf(
                0x04,
                0x7F,
            )
        )
        val event = decoded.event as HeyCyanEvent.ModeChanged

        assertNull(event.mode)
        assertEquals(0x7F, event.rawMode)
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
        assertEquals(0x77.toInt() and 0xFF, event.eventType)
        assertArrayEquals(payload.copyOfRange(1, payload.size), event.payload)
    }

    @Test
    fun unknownPayloadIsNeverDropped() {
        val payload = byteArrayOf(
            0xEE.toByte(),
            0xAA.toByte(),
            0xBB.toByte(),
        )

        val decoded = decoder.decode(payload)

        assertTrue(decoded.event is HeyCyanEvent.Unknown)
        assertArrayEquals(payload, decoded.rawPayload)
    }

    @Test
    fun malformedPayloadDoesNotTerminateDecoder() {
        val malformedPacket = byteArrayOf(
            0x01,
            120.toByte(), // Invalid level > 100
            0,
        )

        val decoded = decoder.decode(malformedPacket)

        assertTrue(decoded.event is HeyCyanEvent.Malformed)
        val malformed = decoded.event as HeyCyanEvent.Malformed
        assertArrayEquals(malformedPacket, malformed.payload)
        assertTrue(malformed.reason.isNotBlank())
    }

    @Test
    fun tooShortBatteryPayloadProducesMalformedEvent() {
        val decoded = decoder.decode(
            byteArrayOf(
                0x01,
                50.toByte(),
            )
        )

        assertTrue(decoded.event is HeyCyanEvent.Malformed)
    }

    @Test
    fun tooShortMediaCountsPayloadProducesMalformedEvent() {
        val decoded = decoder.decode(
            byteArrayOf(
                0x02,
                0x01, 0x00,
            )
        )

        assertTrue(decoded.event is HeyCyanEvent.Malformed)
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

        val decoded = decoder.decode(packet)
        val event = decoded.event

        assertTrue(event is HeyCyanEvent.WiFiCredentials)
        event as HeyCyanEvent.WiFiCredentials
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

        assertTrue(event is HeyCyanEvent.AIImageData)
        event as HeyCyanEvent.AIImageData
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()),
            event.payload
        )
    }

    @Test
    fun emptyPayloadReturnsUnknownWithMinusOne() {
        val decoded = decoder.decode(byteArrayOf())
        assertTrue(decoded.event is HeyCyanEvent.Unknown)
        assertEquals(-1, (decoded.event as HeyCyanEvent.Unknown).eventType)
    }
}