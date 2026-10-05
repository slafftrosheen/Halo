package com.halo.runtime.cognition.context

import com.halo.sdk.runtime.NativeMemoryPressure
import org.junit.Assert.*
import org.junit.Test

class LocalInferencePerformanceTest {
    private fun profile(ttft: Long, stable: Boolean = true) = LocalInferenceRuntimeProfile(
        "local", "model", "CPU", 4096, 100, ttft, 8.4, 1024, stable, 1)

    @Test fun latencyAndPressureMatrixNeverExpandsBaseline() {
        val cases = listOf(
            Triple(null, NativeMemoryPressure.NORMAL, 512 to 128),
            Triple(3000L, NativeMemoryPressure.NORMAL, 512 to 128),
            Triple(4000L, NativeMemoryPressure.NORMAL, 512 to 128),
            Triple(4001L, NativeMemoryPressure.NORMAL, 384 to 112),
            Triple(6000L, NativeMemoryPressure.NORMAL, 384 to 112),
            Triple(8000L, NativeMemoryPressure.NORMAL, 384 to 112),
            Triple(8001L, NativeMemoryPressure.NORMAL, 256 to 96),
            Triple(12000L, NativeMemoryPressure.NORMAL, 256 to 96),
            Triple(3000L, NativeMemoryPressure.LOW, 384 to 112),
            Triple(6000L, NativeMemoryPressure.LOW, 256 to 96),
            Triple(null, NativeMemoryPressure.LOW, 384 to 112),
            Triple(3000L, NativeMemoryPressure.CRITICAL, 256 to 96),
            Triple(null, NativeMemoryPressure.CRITICAL, 256 to 96),
        )
        for ((ttft, pressure, voiceExpected) in cases) for (voice in listOf(true, false)) {
            val decision = localInferencePerformance(true, 4096, voice, ttft?.let { profile(it) }, pressure)!!
            val scale = if (voice) 1 else 2
            assertEquals("ttft=$ttft pressure=$pressure voice=$voice", voiceExpected.first * scale, decision.history.maxConversationTokens)
            assertEquals(voiceExpected.second * scale, decision.outputTokens)
        }
    }
    @Test fun unprovenAndInvalidMeasurementsRemainUnknown() {
        for (p in listOf(profile(3000, false), profile(-1))) {
            val result = localInferencePerformance(true, 4096, true, p)!!
            assertEquals(LocalPerformanceTier.UNKNOWN, result.tier)
            assertFalse(result.measured)
        }
    }
    @Test fun remoteAndLargeContextModelsHaveNoSmallLocalBudget() {
        assertNull(localInferencePerformance(false, 128000, true, profile(12000), NativeMemoryPressure.CRITICAL))
        assertNull(localInferencePerformance(true, 8192, true, profile(12000)))
        assertNull(localInferencePerformance(true, null, true))
    }
}
