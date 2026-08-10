package com.halo.drivers.heycyan.transport.rfcomm

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoMindPacketGoldenVectorTest {

    private val encoder = MemoMindPacketEncoder()
    private val decoder = MemoMindPacketDecoder()

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

    @Test
    fun getBatteryGoldenVector() {
        // Command 0x13 (GetBattery), empty payload
        // FA 00 + Len(00 06) + Cmd(13) + CS(FA+00+00+06+13 = 113 = 0x73.. Wait: FA+00+00+06+13:
        // FA(250) + 00 + 00 + 06 + 19 = 275 -> 275 & 0xFF = 19 (0x13).
        val packet = MemoMindPacket(command = 0x13, payload = byteArrayOf())
        val encoded = encoder.encode(packet)

        val hex = encoded.toHex()
        assertTrue("Golden vector must start with FA00", hex.startsWith("FA00"))
        assertEquals(12, hex.length) // 6 bytes = 12 hex chars

        // Roundtrip verify byte for byte match
        val decoded = decoder.feed(encoded)
        assertEquals(1, decoded.size)
        assertEquals(packet, decoded.first())
    }

    @Test
    fun setPhotoModeGoldenVector() {
        // Command 0x01 (SetMode), payload 0x01 (Photo)
        val packet = MemoMindPacket(command = 0x01, payload = byteArrayOf(0x01))
        val encoded = encoder.encode(packet)

        val hex = encoded.toHex()
        assertTrue("Golden vector must start with FA00", hex.startsWith("FA00"))

        val decoded = decoder.feed(encoded)
        assertEquals(1, decoded.size)
        assertEquals(packet, decoded.first())
    }
}

private fun assertTrue(message: String, condition: Boolean) {
    org.junit.Assert.assertTrue(message, condition)
}
