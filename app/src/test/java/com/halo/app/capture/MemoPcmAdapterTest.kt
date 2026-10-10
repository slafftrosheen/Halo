package com.halo.app.capture

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MemoPcmAdapterTest {
    private fun bytes(vararg samples: Int) = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { samples.forEach { putShort(it.toShort()) } }.array()
    @Test fun partialStereoReadsAreDownmixedWithoutLosingFrames() {
        val raw = bytes(1000, 3000, -1000, -3000)
        val adapter = MemoPcmAdapter(16000, 2)
        raw.forEach { adapter.accept(byteArrayOf(it)) }
        assertArrayEquals(shortArrayOf(2000, -2000), adapter.finish())
    }
    @Test fun fortyEightKhzBecomesSixteenKhzWithStableDurationAcrossChunks() {
        val adapter = MemoPcmAdapter(48000, 1)
        val raw = bytes(*IntArray(48000) { 3000 })
        raw.asList().chunked(137).forEach { adapter.accept(it.toByteArray()) }
        val result = adapter.finish()
        assertEquals(16000, result.size)
        assertTrue(result.all { it == 3000.toShort() })
    }
    @Test fun incompleteAndEmptyAudioAreRejected() {
        assertTrue(runCatching { MemoPcmAdapter(16000, 1).finish() }.isFailure)
        val adapter = MemoPcmAdapter(16000, 1)
        adapter.accept(byteArrayOf(1))
        assertTrue(runCatching { adapter.finish() }.isFailure)
        assertTrue(runCatching { MemoPcmAdapter(192000, 1) }.isFailure)
        assertTrue(runCatching { MemoPcmAdapter(16000, 6) }.isFailure)
    }
    @Test fun durationAndPerChunkAllocationAreBounded() {
        val adapter = MemoPcmAdapter(16000, 1)
        val oneSecond = ByteArray(32000)
        repeat(120) { adapter.accept(oneSecond) }
        assertTrue(runCatching { adapter.accept(byteArrayOf(0, 0)) }.isFailure)
        assertTrue(runCatching { MemoPcmAdapter(16000, 1).accept(ByteArray(1_048_577)) }.isFailure)
    }
}
