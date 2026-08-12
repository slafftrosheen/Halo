package com.halo.drivers.heycyan.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeyCyanCaptureCorpusTest {

    @Test
    fun `validates capture frame 1 with 14 byte payload and CRC 0x789B`() {
        val raw = byteArrayOf(
            0xBC.toByte(), 0x73, 0x0E, 0x00,
            0x9B.toByte(), 0x78,
            0x12, 0x01, 0x00, 0x10, 0x05, 0x02, 0x00, 0x0F, 0x0F, 0x03, 0x00, 0x10, 0x0A, 0x01
        )

        val result = HeyCyanWireFrameCodec.decode(raw)
        assertTrue("Decode must succeed", result is WireFrameDecodeResult.Success)

        val success = result as WireFrameDecodeResult.Success
        assertEquals(HeyCyanWireFrameCodec.ACTION_DEVICE_DATA_REPORTING, success.action)
        assertEquals(14, success.payloadLength)
        assertEquals(14, success.payload.size)
        assertEquals(0x789B, success.crc16)
        assertTrue("CRC16 must match received CRC", success.isValidCrc)
    }

    @Test
    fun `validates capture frame 2 with 14 byte payload and CRC 0xB91A`() {
        val raw = byteArrayOf(
            0xBC.toByte(), 0x73, 0x0E, 0x00,
            0x1A.toByte(), 0xB9.toByte(),
            0x12, 0x01, 0x00, 0x10, 0x05, 0x02, 0x00, 0x0F, 0x0F, 0x03, 0x00, 0x10, 0x0A, 0x03
        )

        val result = HeyCyanWireFrameCodec.decode(raw)
        assertTrue("Decode must succeed", result is WireFrameDecodeResult.Success)

        val success = result as WireFrameDecodeResult.Success
        assertEquals(HeyCyanWireFrameCodec.ACTION_DEVICE_DATA_REPORTING, success.action)
        assertEquals(14, success.payloadLength)
        assertEquals(14, success.payload.size)
        assertEquals(0xB91A, success.crc16)
        assertTrue("CRC16 must match received CRC", success.isValidCrc)
    }
}
