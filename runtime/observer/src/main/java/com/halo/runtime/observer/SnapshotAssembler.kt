package com.halo.runtime.observer

import com.halo.sdk.runtime.*
import java.time.Instant
import kotlinx.datetime.toJavaInstant

interface SnapshotAssembler {
    fun assemble(
        speechState: SpeechSessionState,
        processes: List<com.halo.runtime.kernel.Process>,
        facts: List<com.halo.runtime.cognition.blackboard.Knowledge>,
        timeline: List<TimelineEvent>,
        traces: List<ObservationTrace>,
        providers: List<ProviderSnapshot>,
        startedAtTime: Instant
    ): RuntimeSnapshot
}

class DefaultSnapshotAssembler(
    private val healthEvaluator: RuntimeHealthEvaluator,
    private val clock: java.time.Clock,
    private val nativeResourceCoordinator: NativeResourceCoordinator? = null
) : SnapshotAssembler {
    override fun assemble(
        speechState: SpeechSessionState,
        processes: List<com.halo.runtime.kernel.Process>,
        facts: List<com.halo.runtime.cognition.blackboard.Knowledge>,
        timeline: List<TimelineEvent>,
        traces: List<ObservationTrace>,
        providers: List<ProviderSnapshot>,
        startedAtTime: Instant
    ): RuntimeSnapshot {
        val engines = processes.map { p -> p.engineSnapshot() }

        val actualEngines = if (engines.isEmpty()) {
            listOf(
                EngineSnapshot("Speech", EngineState.Unknown, EngineHealth.Unknown, EngineMetrics(null, java.time.Duration.ZERO, 0, 0, 0.0), clock.instant()),
                EngineSnapshot("Reasoning", EngineState.Unknown, EngineHealth.Unknown, EngineMetrics(null, java.time.Duration.ZERO, 0, 0, 0.0), clock.instant()),
                EngineSnapshot("Planning", EngineState.Unknown, EngineHealth.Unknown, EngineMetrics(null, java.time.Duration.ZERO, 0, 0, 0.0), clock.instant()),
                EngineSnapshot("Execution", EngineState.Unknown, EngineHealth.Unknown, EngineMetrics(null, java.time.Duration.ZERO, 0, 0, 0.0), clock.instant())
            )
        } else engines

        val isBooting = processes.isEmpty()
        val health = healthEvaluator.evaluate(isBooting, startedAtTime, speechState, actualEngines)

        return RuntimeSnapshot(
            runtimeVersion = "1.0.0-alpha",
            buildNumber = 44,
            startedAt = startedAtTime,
            health = health,
            runtimeState = if (isBooting) RuntimeState.Booting else RuntimeState.Running,
            speech = speechState,
            engines = actualEngines,
            timeline = timeline,
            providers = providers,
            blackboard = BlackboardSnapshot(facts.size, facts.takeLast(10).map {
                FactSummary(it.id.value, it.payload::class.simpleName ?: "Unknown", it.createdAt.toJavaInstant())
            }),
            traces = traces,
            nativeResources = nativeResourceCoordinator?.snapshot?.value ?: emptyList()
        )
    }
}
