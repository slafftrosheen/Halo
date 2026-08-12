package com.halo.drivers.heycyan.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

class RawHeyCyanFrameTest {

    private val charId = UUID.randomUUID()

    @Test
    fun `equality handles byte arrays correctly`() {
        val f1 = RawHeyCyanFrame(100L, PacketDirection.TX, charId, byteArrayOf(0x01, 0x02))
        val f2 = RawHeyCyanFrame(100L, PacketDirection.TX, charId, byteArrayOf(0x01, 0x02))
        val f3 = RawHeyCyanFrame(100L, PacketDirection.TX, charId, byteArrayOf(0x01, 0x03))

        assertEquals(f1, f2)
        assertEquals(f1.hashCode(), f2.hashCode())
        assertNotEquals(f1, f3)
    }

    @Test
    fun `hex formatting is correct`() {
        val frame = RawHeyCyanFrame(100L, PacketDirection.RX, charId, byteArrayOf(0xBC.toByte(), 0x73, 0x0E))
        assertEquals("BC730E", frame.hex())
    }
}
