package com.halo.drivers.heycyan.codec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeyCyanCommandTest {

    @Test
    fun `photo command matches golden vector byte for byte`() {
        assertArrayEquals(HeyCyanWireFrameCodec.GOLDEN_PHOTO, HeyCyanCommand.photo())
    }

    @Test
    fun `videoStart command matches golden vector byte for byte`() {
        assertArrayEquals(HeyCyanWireFrameCodec.GOLDEN_VIDEO_START, HeyCyanCommand.videoStart())
    }

    @Test
    fun `videoStop command matches golden vector byte for byte`() {
        assertArrayEquals(HeyCyanWireFrameCodec.GOLDEN_VIDEO_STOP, HeyCyanCommand.videoStop())
    }

    @Test
    fun `prepareVoiceRouting command matches golden vector byte for byte`() {
        assertArrayEquals(HeyCyanWireFrameCodec.GOLDEN_VOICE_ROUTING, HeyCyanCommand.prepareVoiceRouting())
    }

    @Test
    fun `wifiTransferMode command matches golden vector byte for byte`() {
        assertArrayEquals(HeyCyanWireFrameCodec.GOLDEN_WIFI_TRANSFER, HeyCyanCommand.wifiTransferMode())
    }

    @Test
    fun `mediaCount command matches golden vector byte for byte`() {
        assertArrayEquals(HeyCyanWireFrameCodec.GOLDEN_MEDIA_COUNT, HeyCyanCommand.mediaCount())
    }

    @Test
    fun `enableClassicBluetooth builds valid wire frame`() {
        val bytes = HeyCyanCommand.enableClassicBluetooth()
        val result = HeyCyanWireFrameCodec.decode(bytes)

        assertTrue(result is WireFrameDecodeResult.Success)
        val success = result as WireFrameDecodeResult.Success
        assertEquals(0x49.toByte(), success.action)
        assertArrayEquals(byteArrayOf(0x02, 0x01), success.payload)
        assertTrue(success.isValidCrc)
    }

    @Test
    fun `ai command builds valid wire frame with thumbnail size`() {
        val bytes = HeyCyanCommand.ai(0)
        val result = HeyCyanWireFrameCodec.decode(bytes)

        assertTrue(result is WireFrameDecodeResult.Success)
        val success = result as WireFrameDecodeResult.Success
        assertEquals(HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL, success.action)
        assertEquals(6, success.payloadLength)
        assertArrayEquals(byteArrayOf(0x02, 0x01, 0x06, 0x00, 0x00, 0x02), success.payload)
        assertTrue(success.isValidCrc)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ai command throws for invalid thumbnail size out of range`() {
        HeyCyanCommand.ai(7)
    }

    @Test
    fun `aiThumbnail command default quality 3 encodes 5-byte payload`() {
        val bytes = HeyCyanCommand.aiThumbnail()
        val result = HeyCyanWireFrameCodec.decode(bytes)

        assertTrue(result is WireFrameDecodeResult.Success)
        val success = result as WireFrameDecodeResult.Success
        assertEquals(HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL, success.action)
        assertEquals(5, success.payloadLength)
        assertArrayEquals(byteArrayOf(0x02, 0x01, 0x06, 0x03, 0x03), success.payload)
        assertTrue(success.isValidCrc)
    }

    @Test
    fun `aiThumbnail command with custom quality encodes correct bytes`() {
        val bytes = HeyCyanCommand.aiThumbnail(quality = 5)
        val result = HeyCyanWireFrameCodec.decode(bytes)

        assertTrue(result is WireFrameDecodeResult.Success)
        val success = result as WireFrameDecodeResult.Success
        assertEquals(5, success.payloadLength)
        assertArrayEquals(byteArrayOf(0x02, 0x01, 0x06, 0x05, 0x05), success.payload)
        assertTrue(success.isValidCrc)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `aiThumbnail command throws for quality out of range`() {
        HeyCyanCommand.aiThumbnail(quality = 6)
    }
}
