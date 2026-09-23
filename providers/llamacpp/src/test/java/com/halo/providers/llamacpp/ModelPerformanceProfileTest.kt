package com.halo.providers.llamacpp

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelPerformanceProfileTest {

    @Test
    fun `fast load and high throughput yields EXCELLENT voice suitability`() {
        val suitability = calculateVoiceSuitability(loadMs = 1000, generationTokensPerSecond = 20f)
        assertEquals(VoiceModelSuitability.EXCELLENT, suitability)
    }

    @Test
    fun `moderate load and throughput yields GOOD voice suitability`() {
        val suitability = calculateVoiceSuitability(loadMs = 2500, generationTokensPerSecond = 10f)
        assertEquals(VoiceModelSuitability.GOOD, suitability)
    }

    @Test
    fun `low throughput yields SLOW voice suitability`() {
        val suitability = calculateVoiceSuitability(loadMs = 2000, generationTokensPerSecond = 5f)
        assertEquals(VoiceModelSuitability.SLOW, suitability)
    }

    @Test
    fun `very low throughput yields UNSUITABLE voice suitability`() {
        val suitability = calculateVoiceSuitability(loadMs = 5000, generationTokensPerSecond = 2f)
        assertEquals(VoiceModelSuitability.UNSUITABLE, suitability)
    }
}
