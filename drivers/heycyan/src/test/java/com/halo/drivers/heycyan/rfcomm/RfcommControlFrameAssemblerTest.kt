package com.halo.drivers.heycyan.rfcomm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RfcommControlFrameAssemblerTest {
    @Test
    fun assemblerWaitsForCompleteLengthPrefixedFrameAcrossChunks() {
        val assembler = RfcommControlFrameAssembler()
        val frame = byteArrayOf(
            0xFA.toByte(), 0x00, 0x00, 0x09,
            0x01, 0x02, 0x01, 0x01, 0x05
        )

        assertEquals(emptyList<ByteArray>(), assembler.append(frame.copyOfRange(0, 4)))
        val frames = assembler.append(frame.copyOfRange(4, frame.size))

        assertEquals(1, frames.size)
        assertArrayEquals(frame, frames.single())
    }

    @Test
    fun assemblerDropsLeadingNoiseButPreservesFollowingFrame() {
        val assembler = RfcommControlFrameAssembler()
        val frame = byteArrayOf(
            0xFA.toByte(), 0x00, 0x00, 0x09,
            0x01, 0x02, 0x06, 0x01, 0x0A
        )

        val frames = assembler.append(byteArrayOf(0x52, 0x91.toByte(), 0x00) + frame)

        assertEquals(1, frames.size)
        assertArrayEquals(frame, frames.single())
    }
}

private operator fun ByteArray.plus(other: ByteArray): ByteArray =
    ByteArray(size + other.size).also {
        copyInto(it)
        other.copyInto(it, destinationOffset = size)
    }

private fun ByteArray.copyOfRange(fromIndex: Int, toIndex: Int): ByteArray =
    java.util.Arrays.copyOfRange(this, fromIndex, toIndex)
