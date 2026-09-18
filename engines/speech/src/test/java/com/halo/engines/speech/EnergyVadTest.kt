package com.halo.engines.speech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyVadTest {

    @Test
    fun `hangover follows audio duration instead of frame count`() {
        val vad = EnergyVAD(threshold = 1000, hangoverMillis = 100L)
        val speech = frame(amplitude = 2000)
        val silence = frame(amplitude = 0)

        assertTrue(vad.isSpeech(speech))

        // Five 20 ms frames consume a 100 ms hangover while remaining speech.
        repeat(5) {
            assertTrue(vad.isSpeech(silence))
        }
        assertFalse(vad.isSpeech(silence))
    }

    @Test
    fun `reset clears pending hangover`() {
        val vad = EnergyVAD(threshold = 1000, hangoverMillis = 600L)
        val speech = frame(amplitude = 2000)
        val silence = frame(amplitude = 0)

        assertTrue(vad.isSpeech(speech))
        vad.reset()

        assertFalse(vad.isSpeech(silence))
    }

    private fun frame(amplitude: Short): AudioFrame = AudioFrame(
        samples = ShortArray(320) { amplitude },
        sampleRate = 16_000,
        channels = 1,
        timestamp = 0L
    )
}
