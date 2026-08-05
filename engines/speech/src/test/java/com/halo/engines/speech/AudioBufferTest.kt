package com.halo.engines.speech

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBufferTest {
    @Test
    fun snapshotIsDetachedFromFramesAndSurvivesClear() {
        val originalSamples = shortArrayOf(1, 2, 3)
        val buffer = AudioBuffer()
        buffer.append(
            AudioFrame(
                samples = originalSamples,
                sampleRate = 16_000,
                channels = 1,
                timestamp = 100L
            )
        )
        originalSamples[0] = 99

        val segment = buffer.snapshot()
        buffer.clear()

        assertArrayEquals(shortArrayOf(1, 2, 3), segment.pcm)
        assertEquals(100L, segment.startedAt)
        assertTrue(segment.finishedAt > segment.startedAt)
    }

    @Test
    fun segmentPcmAccessorReturnsDefensiveCopies() {
        val buffer = AudioBuffer()
        buffer.append(
            AudioFrame(
                samples = shortArrayOf(4, 5),
                sampleRate = 16_000,
                channels = 1,
                timestamp = 100L
            )
        )

        val segment = buffer.snapshot()
        val exposedPcm = segment.pcm
        exposedPcm[0] = 99

        assertArrayEquals(shortArrayOf(4, 5), segment.pcm)
    }
}
