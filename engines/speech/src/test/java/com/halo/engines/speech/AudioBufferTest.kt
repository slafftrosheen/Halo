package com.halo.engines.speech

import com.halo.sdk.host.AudioFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBufferTest {
    @Test
    fun `buffer trims oldest frames to configured duration`() {
        val buffer = AudioBuffer(maxMillis = 400)

        repeat(60) { index ->
            buffer.append(
                AudioFrame(
                    samples = ShortArray(160),
                    sampleRate = 16_000,
                    channels = 1,
                    timestamp = index * 10_000_000L
                )
            )
        }

        assertTrue(buffer.duration() <= 400)
        assertEquals(40, buffer.frames().size)
        assertEquals(200_000_000L, buffer.frames().first().timestamp)
        assertEquals(590_000_000L, buffer.frames().last().timestamp)
    }

    @Test
    fun `buffer keeps a single frame even when frame exceeds limit`() {
        val buffer = AudioBuffer(maxMillis = 100)

        buffer.append(
            AudioFrame(
                samples = ShortArray(3_200),
                sampleRate = 16_000,
                channels = 1,
                timestamp = 0
            )
        )

        assertEquals(1, buffer.frames().size)
        assertEquals(200L, buffer.duration())
    }
}
