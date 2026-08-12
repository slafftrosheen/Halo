package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class HeyCyanSdkEventDecoderTest {

    private val decoder = HeyCyanSdkEventDecoder()

    @Test
    fun `battery event decodes`() {
        val payload = byteArrayOf(0x01, 85.toByte(), 0x01)

        val event = decoder.decode(payload)

        assertEquals(
            HeyCyanEvent.BatteryUpdated(
                level = 85,
                isCharging = true
            ),
            event
        )
    }

    @Test
    fun `media counts decode little endian`() {
        // photos=2, videos=5, audio=7, total=14, currIndex=1, mediaType=1
        val payload = byteArrayOf(
            0x02,           // event type
            0x02, 0x00,     // photoCount = 2 (little endian)
            0x05, 0x00,     // videoCount = 5 (little endian)
            0x07, 0x00,     // audioCount = 7 (little endian)
            0x0E, 0x00,     // total = 14 (little endian)
            0x01, 0x00,     // currIndex = 1 (little endian)
            0x01            // mediaType
        )

        val event = decoder.decode(payload)

        assertEquals(
            HeyCyanEvent.MediaCountsUpdated(
                photoCount = 2,
                videoCount = 5,
                audioCount = 7,
                total = 14,
                currIndex = 1,
                mediaType = 1
            ),
            event
        )
    }

    @Test
    fun `wifi ip decodes`() {
        val payload = byteArrayOf(
            0x08,                           // event type
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // padding / metadata up to index 6
            192.toByte(), 168.toByte(), 43.toByte(), 1.toByte() // IP address at bytes [7..10]
        )

        val event = decoder.decode(payload)

        assertEquals(
            HeyCyanEvent.WiFiIpReceived("192.168.43.1"),
            event
        )
    }

    @Test
    fun `mode event decodes`() {
        val event = decoder.decode(byteArrayOf(0x04, 0x01))

        assertEquals(
            HeyCyanEvent.ModeChanged(1),
            event
        )
    }

    @Test
    fun `version event decodes`() {
        // Build payload with length-prefixed strings
        val payload = mutableListOf<Byte>()
        payload.add(0x03) // event type
        // hw version "HW1"
        payload.add(3)
        payload.addAll("HW1".toByteArray().toList())
        // fw version "FW10"
        payload.add(4)
        payload.addAll("FW10".toByteArray().toList())
        // wifi hw version "WI0"
        payload.add(3)
        payload.addAll("WI0".toByteArray().toList())
        // wifi fw version "WF10"
        payload.add(4)
        payload.addAll("WF10".toByteArray().toList())

        val event = decoder.decode(payload.toByteArray())

        assertEquals(
            HeyCyanEvent.VersionUpdated(
                hardwareVersion = "HW1",
                firmwareVersion = "FW10",
                wifiHardwareVersion = "WI0",
                wifiFirmwareVersion = "WF10"
            ),
            event
        )
    }

    @Test
    fun `wifi credentials decode`() {
        val payload = mutableListOf<Byte>()
        payload.add(0x05) // event type
        // ssid "HEAC"
        payload.add(4)
        payload.addAll("HEAC".toByteArray().toList())
        // password "12345678"
        payload.add(8)
        payload.addAll("12345678".toByteArray().toList())

        val event = decoder.decode(payload.toByteArray())

        assertEquals(
            HeyCyanEvent.WiFiHotspotReady(
                ssid = "HEAC",
                password = "12345678"
            ),
            event
        )
    }

    @Test
    fun `ai image data decodes`() {
        val imageData = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte(), 0xFC.toByte())
        val payload = byteArrayOf(0x06) + imageData

        val event = decoder.decode(payload)

        assertTrue(event is HeyCyanEvent.AiImageData)
        val aiEvent = event as HeyCyanEvent.AiImageData
        // Use AiImageData's proper equals implementation
        assertEquals(HeyCyanEvent.AiImageData(imageData), aiEvent)
        // Also verify content directly
        assertArrayEquals(imageData, aiEvent.bytes)
    }

    @Test
    fun `unknown event type returns null`() {
        val payload = byteArrayOf(0xFF.toByte(), 0x01)

        val event = decoder.decode(payload)

        assertEquals(null, event)
    }

    @Test
    fun `empty payload returns null`() {
        val event = decoder.decode(byteArrayOf())

        assertEquals(null, event)
    }
}