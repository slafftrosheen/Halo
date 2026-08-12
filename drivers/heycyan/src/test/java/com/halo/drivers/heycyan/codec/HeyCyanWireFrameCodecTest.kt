package com.halo.drivers.heycyan.codec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeyCyanWireFrameCodecTest {

    @Test
    fun `calculates crc16 for photo payload matching vendor golden vector`() {
        val crc = HeyCyanWireFrameCodec.calculateCrc16(HeyCyanWireFrameCodec.PAYLOAD_PHOTO)
        assertEquals(0x5010, crc)
    }

    @Test
    fun `calculates crc16 for video start payload matching vendor golden vector`() {
        val crc = HeyCyanWireFrameCodec.calculateCrc16(HeyCyanWireFrameCodec.PAYLOAD_VIDEO_START)
        assertEquals(0x5150, crc)
    }

    @Test
    fun `calculates crc16 for video stop payload matching vendor golden vector`() {
        val crc = HeyCyanWireFrameCodec.calculateCrc16(HeyCyanWireFrameCodec.PAYLOAD_VIDEO_STOP)
        assertEquals(0x9191, crc)
    }

    @Test
    fun `calculates crc16 for voice routing payload matching vendor golden vector`() {
        val crc = HeyCyanWireFrameCodec.calculateCrc16(HeyCyanWireFrameCodec.PAYLOAD_VOICE_ROUTING)
        assertEquals(0x5790, crc)
    }

    @Test
    fun `calculates crc16 for wifi transfer payload matching vendor golden vector`() {
        val crc = HeyCyanWireFrameCodec.calculateCrc16(HeyCyanWireFrameCodec.PAYLOAD_WIFI_TRANSFER)
        assertEquals(0x5C93, crc)
    }

    @Test
    fun `calculates crc16 for media count payload matching vendor golden vector`() {
        val crc = HeyCyanWireFrameCodec.calculateCrc16(HeyCyanWireFrameCodec.PAYLOAD_MEDIA_COUNT)
        assertEquals(0x1301, crc)
    }

    @Test
    fun `encodes photo command matching golden vector byte for byte`() {
        val encoded = HeyCyanWireFrameCodec.encode(
            action = HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL,
            payload = HeyCyanWireFrameCodec.PAYLOAD_PHOTO
        )
        assertArrayEquals(HeyCyanWireFrameCodec.GOLDEN_PHOTO, encoded)
    }

    @Test
    fun `encodes video start command matching golden vector byte for byte`() {
        val encoded = HeyCyanWireFrameCodec.encode(
            action = HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL,
            payload = HeyCyanWireFrameCodec.PAYLOAD_VIDEO_START
        )
        assertArrayEquals(HeyCyanWireFrameCodec.GOLDEN_VIDEO_START, encoded)
    }

    @Test
    fun `encodes voice routing command matching golden vector byte for byte`() {
        val encoded = HeyCyanWireFrameCodec.encode(
            action = HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL,
            payload = HeyCyanWireFrameCodec.PAYLOAD_VOICE_ROUTING
        )
        assertArrayEquals(HeyCyanWireFrameCodec.GOLDEN_VOICE_ROUTING, encoded)
    }

    @Test
    fun `decodes photo golden vector correctly with valid CRC`() {
        val result = HeyCyanWireFrameCodec.decode(HeyCyanWireFrameCodec.GOLDEN_PHOTO)
        assertTrue(result is WireFrameDecodeResult.Success)

        val success = result as WireFrameDecodeResult.Success
        assertEquals(HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL, success.action)
        assertEquals(3, success.payloadLength)
        assertEquals(0x5010, success.crc16)
        assertArrayEquals(HeyCyanWireFrameCodec.PAYLOAD_PHOTO, success.payload)
        assertTrue(success.isValidCrc)
    }

    @Test
    fun `decodes BC73 device reporting frame correctly as length 14`() {
        // BC 73 0E 00 ... payload of 14 bytes
        val dummy14BytePayload = ByteArray(14) { (it + 1).toByte() }
        val encoded = HeyCyanWireFrameCodec.encode(
            action = HeyCyanWireFrameCodec.ACTION_DEVICE_DATA_REPORTING,
            payload = dummy14BytePayload
        )

        val result = HeyCyanWireFrameCodec.decode(encoded)
        assertTrue(result is WireFrameDecodeResult.Success)

        val success = result as WireFrameDecodeResult.Success
        assertEquals(HeyCyanWireFrameCodec.ACTION_DEVICE_DATA_REPORTING, success.action)
        assertEquals(14, success.payloadLength)
        assertArrayEquals(dummy14BytePayload, success.payload)
        assertTrue(success.isValidCrc)
    }

    @Test
    fun `detects corrupted CRC`() {
        val corruptedFrame = HeyCyanWireFrameCodec.GOLDEN_PHOTO.copyOf()
        corruptedFrame[4] = (corruptedFrame[4].toInt() xor 0xFF).toByte()

        val result = HeyCyanWireFrameCodec.decode(corruptedFrame)
        assertTrue(result is WireFrameDecodeResult.Success)
        assertFalse((result as WireFrameDecodeResult.Success).isValidCrc)
        assertFalse(HeyCyanWireFrameCodec.validateCrc(corruptedFrame))
    }

    @Test
    fun `returns incomplete result for truncated bytes`() {
        val truncated = HeyCyanWireFrameCodec.GOLDEN_PHOTO.copyOfRange(0, 5)
        val result = HeyCyanWireFrameCodec.decode(truncated)
        assertTrue(result is WireFrameDecodeResult.Invalid)
    }
}
