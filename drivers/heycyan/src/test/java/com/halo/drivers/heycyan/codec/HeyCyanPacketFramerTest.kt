package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeyCyanPacketFramerTest {

    @Test
    fun `frame TakePhoto produces golden vector`() {
        val encoder = HeyCyanCommandEncoder()
        val framer = HeyCyanPacketFramer()
        
        val encoded = encoder.encode(HeyCyanCommand.TakePhoto).getOrThrow()
        val framed = framer.frame(encoded).getOrThrow()
        
        val expectedGoldenVector = byteArrayOf(
            0xBC.toByte(), 0x41.toByte(), 0x03, 0x00, 0x10, 0x50, 0x02, 0x01, 0x01
        )
        
        assertArrayEquals(expectedGoldenVector, framed)
    }

    @Test
    fun `frame GetMediaCounts produces golden vector`() {
        val encoder = HeyCyanCommandEncoder()
        val framer = HeyCyanPacketFramer()
        
        val encoded = encoder.encode(HeyCyanCommand.GetMediaCounts).getOrThrow()
        val framed = framer.frame(encoded).getOrThrow()
        
        val expectedGoldenVector = byteArrayOf(
            0xBC.toByte(), 0x41.toByte(), 0x02, 0x00, 0x01, 0x13, 0x02, 0x04
        )
        
        assertArrayEquals(expectedGoldenVector, framed)
    }
}
