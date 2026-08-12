package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.debug.HeyCyanLogger
import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test

class HeyCyanPacketFramerTest {

    @Before
    fun setup() {
        HeyCyanLogger.logger = { tag, msg -> println("$tag: $msg") }
    }

    @Test
    fun `frame TakePhoto produces golden vector`() {
        val encoder = HeyCyanCommandEncoder()
        val framer = HeyCyanPacketFramer()
        
        val encoded = encoder.encode(HeyCyanCommand.TakePhoto).getOrThrow()
        val framed = framer.frame(encoded).getOrThrow()
        
        assertArrayEquals(HeyCyanWireFrameCodec.GOLDEN_PHOTO, framed)
    }

    @Test
    fun `frame GetMediaCounts produces golden vector`() {
        val encoder = HeyCyanCommandEncoder()
        val framer = HeyCyanPacketFramer()
        
        val encoded = encoder.encode(HeyCyanCommand.GetMediaCounts).getOrThrow()
        val framed = framer.frame(encoded).getOrThrow()
        
        assertArrayEquals(HeyCyanWireFrameCodec.GOLDEN_MEDIA_COUNT, framed)
    }
}
