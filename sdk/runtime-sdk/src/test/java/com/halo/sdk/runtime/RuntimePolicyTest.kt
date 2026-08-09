package com.halo.sdk.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePolicyTest {
    @Test
    fun `power policies expose deterministic optimization decisions`() {
        val lowPower = RuntimePowerPolicy.forMode(RuntimePowerMode.LOW_POWER)
        val charging = RuntimePowerPolicy.forMode(RuntimePowerMode.CHARGING)

        assertEquals(50L, lowPower.audioFrameIntervalMillis)
        assertFalse(lowPower.allowBackgroundInference)
        assertFalse(lowPower.retainLoadedModels)
        assertTrue(charging.allowBackgroundInference)
        assertTrue(charging.retainLoadedModels)
    }

    @Test
    fun `metrics sink records counters and stage latency`() {
        val metrics = InMemoryRuntimeMetrics()

        metrics.increment("recognition.started")
        metrics.increment("recognition.started", 2)
        metrics.recordLatency(RuntimeStage.INTERPRETATION, 18L)

        assertEquals(3L, metrics.counters()["recognition.started"])
        assertEquals(listOf(18L), metrics.latencies()[RuntimeStage.INTERPRETATION])
    }
}