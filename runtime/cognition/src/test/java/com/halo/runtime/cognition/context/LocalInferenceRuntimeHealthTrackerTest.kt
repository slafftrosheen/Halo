package com.halo.runtime.cognition.context

import com.halo.sdk.runtime.NativeMemoryPressure
import org.junit.Assert.*
import org.junit.Test

class LocalInferenceRuntimeHealthTrackerTest {
    private fun profile(ttft: Long) = LocalInferenceRuntimeProfile("local", "model", "CPU", 4096, 0, ttft, null, null, true, 0)
    private fun warm(t: LocalInferenceRuntimeHealthTracker, ms: Long, backend: String = "CPU", model: String = "model") =
        t.accept(LocalInferenceObservation("local", model, backend, ttftMs = ms, success = true, coldStart = false))
    @Test fun coldAndUnknownDoNotPoisonWarmEvidence() {
        val t = LocalInferenceRuntimeHealthTracker()
        t.accept(LocalInferenceObservation("local", "model", "CPU", ttftMs = 20000, success = true, coldStart = true))
        t.accept(LocalInferenceObservation("local", "model", "CPU", ttftMs = 20000, success = true))
        repeat(3) { warm(t, 3000) }
        val h = t.snapshot("local", "model", "CPU")!!
        assertEquals(1, h.coldSamples); assertEquals(3, h.successfulWarmSamples)
        assertEquals(3000.0, h.warmTtftEwmaMs!!, 0.001)
        assertNull(t.snapshot("local", "other", "CPU"))
        warm(t, 20000, "GPU")
        assertEquals(3000.0, t.snapshot("local", "model", "CPU")!!.warmTtftEwmaMs!!, 0.001)
    }
    @Test fun sampleGateDegradationRecoveryAndPressure() {
        val t = LocalInferenceRuntimeHealthTracker()
        t.baseline("local", "model", "CPU", null, profile(3000))
        repeat(2) { warm(t, 10000) }
        assertEquals(LocalPerformanceTier.GOOD, localInferencePerformance(true, 4096, true, profile(3000), health = t.snapshot("local", "model", "CPU"))!!.tier)
        warm(t, 10000)
        assertEquals(LocalPerformanceTier.VERY_SLOW, t.snapshot("local", "model", "CPU")!!.tier)
        repeat(4) { warm(t, 3000) }
        assertEquals(LocalPerformanceTier.VERY_SLOW, t.snapshot("local", "model", "CPU")!!.tier)
        warm(t, 3000)
        assertEquals(LocalPerformanceTier.SLOW, t.snapshot("local", "model", "CPU")!!.tier)
        repeat(10) { warm(t, 3000) }
        val h = t.snapshot("local", "model", "CPU")!!
        assertEquals(LocalPerformanceTier.GOOD, h.tier)
        assertEquals(LocalPerformanceTier.VERY_SLOW, localInferencePerformance(true, 4096, true, profile(3000), NativeMemoryPressure.CRITICAL, h)!!.tier)
        assertEquals(512, localInferencePerformance(true, 4096, true, profile(3000), health = h)!!.history.maxConversationTokens)
    }
    @Test fun pessimisticCalibrationRecoversGraduallyAndFailuresExcludeControlFlow() {
        val t = LocalInferenceRuntimeHealthTracker()
        t.baseline("local", "model", "CPU", null, profile(11000))
        repeat(3) { warm(t, 3000) }
        assertEquals(LocalPerformanceTier.VERY_SLOW, t.snapshot("local", "model", "CPU")!!.tier)
        repeat(2) { warm(t, 3000) }
        assertEquals(LocalPerformanceTier.SLOW, t.snapshot("local", "model", "CPU")!!.tier)
        repeat(5) { warm(t, 3000) }
        assertEquals(LocalPerformanceTier.GOOD, t.snapshot("local", "model", "CPU")!!.tier)
        for (outcome in listOf(LocalInferenceObservationOutcome.CANCELLED, LocalInferenceObservationOutcome.PREEMPTED, LocalInferenceObservationOutcome.RESOURCE_BUSY))
            t.accept(LocalInferenceObservation("local", "model", "CPU", success = false, outcome = outcome))
        assertEquals(0, t.snapshot("local", "model", "CPU")!!.recentFailureStreak)
        t.accept(LocalInferenceObservation("local", "model", "CPU", success = false))
        assertEquals(LocalPerformanceTier.GOOD, t.snapshot("local", "model", "CPU")!!.tier)
        t.accept(LocalInferenceObservation("local", "model", "CPU", success = false))
        assertEquals(LocalPerformanceTier.SLOW, t.snapshot("local", "model", "CPU")!!.tier)
        t.accept(LocalInferenceObservation("local", "model", "CPU", success = false))
        assertEquals(LocalPerformanceTier.VERY_SLOW, t.snapshot("local", "model", "CPU")!!.tier)
        warm(t, 3000)
        assertEquals(0, t.snapshot("local", "model", "CPU")!!.recentFailureStreak)
        assertEquals(LocalPerformanceTier.VERY_SLOW, t.snapshot("local", "model", "CPU")!!.tier)
    }
}
