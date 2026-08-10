package com.halo.drivers.heycyan.transport.rfcomm

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoMindPacketEncoderTest {

    private val encoder = MemoMindPacketEncoder()

    @Test
    fun encodesHeaderPrefixFA00() {
        val packet = MemoMindPacket(command = 0x13, payload = byteArrayOf())
        val encoded = encoder.encode(packet)

        assertEquals(0xFA.toByte(), encoded[0])
        assertEquals(0x00.toByte(), encoded[1])
    }

    @Test
    fun encodesCorrectLengthAndCommand() {
        val payload = byteArrayOf(0x01, 0x02)
        val packet = MemoMindPacket(command = 0x01, payload = payload)
        val encoded = encoder.encode(packet)

        // Total len: FA 00 (2) + Len (2) + Cmd (1) + Payload (2) + CS (1) = 8 bytes
        assertEquals(8, encoded.size)
        assertEquals(0x00.toByte(), encoded[2])
        assertEquals(0x08.toByte(), encoded[3])
        assertEquals(0x01.toByte(), encoded[4])
        assertEquals(0x01.toByte(), encoded[5])
        assertEquals(0x02.toByte(), encoded[6])
    }
}
