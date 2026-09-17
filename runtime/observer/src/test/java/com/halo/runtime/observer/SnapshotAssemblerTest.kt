package com.halo.runtime.observer

import com.halo.sdk.runtime.EngineHealth
import com.halo.sdk.runtime.EngineMetrics
import com.halo.sdk.runtime.EngineSnapshot
import com.halo.sdk.runtime.EngineState
import com.halo.sdk.runtime.RuntimeState
import com.halo.sdk.runtime.SpeechSessionState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotAssemblerTest {
    private val now = Instant.ofEpochMilli(10_000L)
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val healthEvaluator = DefaultRuntimeHealthEvaluator(clock)
    private val assembler = DefaultSnapshotAssembler(healthEvaluator, clock)

    @Test
    fun `created canonical engine keeps runtime booting`() {
        val snapshot = assemble(
            listOf(engine("engine.interpretation.core", EngineState.Created, EngineHealth.Healthy))
        )

        assertEquals(RuntimeState.Booting, snapshot.runtimeState)
        assertFalse(snapshot.health.healthy)
    }

    @Test
    fun `running canonical engines make runtime healthy`() {
        val snapshot = assemble(
            listOf(
                engine("engine.interpretation.core", EngineState.Running, EngineHealth.Healthy),
                engine("engine.reasoning.core", EngineState.Running, EngineHealth.Healthy),
                engine("engine.planning.core", EngineState.Running, EngineHealth.Healthy),
                engine("engine.execution.core", EngineState.Running, EngineHealth.Healthy)
            )
        )

        assertEquals(RuntimeState.Running, snapshot.runtimeState)
        assertTrue(snapshot.health.healthy)
        assertFalse(snapshot.health.degraded)
        assertEquals(4, snapshot.engines.size)
    }

    @Test
    fun `failed canonical engine degrades runtime health`() {
        val snapshot = assemble(
            listOf(
                engine("engine.interpretation.core", EngineState.Running, EngineHealth.Healthy),
                engine("engine.reasoning.core", EngineState.Failed("boom"), EngineHealth.Failed)
            )
        )

        assertEquals(RuntimeState.Ready, snapshot.runtimeState)
        assertFalse(snapshot.health.healthy)
        assertTrue(snapshot.health.degraded)
        assertEquals(now, snapshot.health.lastFailure)
    }

    @Test
    fun `stopped canonical engines make runtime offline`() {
        val snapshot = assemble(
            listOf(
                engine("engine.interpretation.core", EngineState.Stopped, EngineHealth.Healthy),
                engine("engine.reasoning.core", EngineState.Stopped, EngineHealth.Healthy)
            )
        )

        assertEquals(RuntimeState.Offline, snapshot.runtimeState)
        assertFalse(snapshot.health.healthy)
    }

    private fun assemble(engines: List<EngineSnapshot>) = assembler.assemble(
        speechState = SpeechSessionState.Idle,
        engines = engines,
        facts = emptyList(),
        timeline = emptyList(),
        traces = emptyList(),
        providers = emptyList(),
        startedAtTime = Instant.ofEpochMilli(1_000L)
    )

    private fun engine(
        id: String,
        state: EngineState,
        health: EngineHealth
    ) = EngineSnapshot(
        id = id,
        state = state,
        health = health,
        metrics = EngineMetrics(
            latencyMs = null,
            uptime = Duration.ZERO,
            eventsProcessed = 0,
            queueSize = 0,
            cpuUsage = 0.0
        ),
        lastActivity = now
    )
}
