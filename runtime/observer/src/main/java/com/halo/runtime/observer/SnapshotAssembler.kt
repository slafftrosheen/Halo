package com.halo.runtime.observer

import com.halo.runtime.cognition.blackboard.Knowledge
import com.halo.sdk.runtime.BlackboardSnapshot
import com.halo.sdk.runtime.EngineSnapshot
import com.halo.sdk.runtime.EngineState
import com.halo.sdk.runtime.FactSummary
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.sdk.runtime.ObservationTrace
import com.halo.sdk.runtime.ProviderSnapshot
import com.halo.sdk.runtime.RuntimeSnapshot
import com.halo.sdk.runtime.RuntimeState
import com.halo.sdk.runtime.SpeechSessionState
import com.halo.sdk.runtime.TimelineEvent
import com.halo.sdk.runtime.resource.ResourceLeaseManager
import java.time.Instant
import kotlinx.datetime.toJavaInstant
import java.time.Clock

interface SnapshotAssembler {
    fun assemble(
        speechState: SpeechSessionState,
        engines: List<EngineSnapshot>,
        facts: List<Knowledge>,
        timeline: List<TimelineEvent>,
        traces: List<ObservationTrace>,
        providers: List<ProviderSnapshot>,
        startedAtTime: Instant
    ): RuntimeSnapshot
}

class DefaultSnapshotAssembler(
    private val healthEvaluator: RuntimeHealthEvaluator,
    private val clock: Clock,
    private val nativeResourceCoordinator: NativeResourceCoordinator? = null,
    private val resourceLeaseManager: ResourceLeaseManager? = null,
) : SnapshotAssembler {
    override fun assemble(
        speechState: SpeechSessionState,
        engines: List<EngineSnapshot>,
        facts: List<Knowledge>,
        timeline: List<TimelineEvent>,
        traces: List<ObservationTrace>,
        providers: List<ProviderSnapshot>,
        startedAtTime: Instant
    ): RuntimeSnapshot {
        val isBooting = engines.isEmpty() || engines.any { engine ->
            engine.state == EngineState.Created || engine.state == EngineState.Initializing
        }

        val runtimeState = when {
            isBooting -> RuntimeState.Booting
            engines.all { it.state == EngineState.Stopped || it.state == EngineState.Disposed } -> RuntimeState.Offline
            engines.all { it.state == EngineState.Running } -> RuntimeState.Running
            else -> RuntimeState.Ready
        }

        val health = healthEvaluator.evaluate(
            isBooting = isBooting,
            startedAtTime = startedAtTime,
            speechState = speechState,
            engines = engines
        )

        return RuntimeSnapshot(
            runtimeVersion = "1.0.0-alpha",
            buildNumber = 44,
            startedAt = startedAtTime,
            health = health,
            runtimeState = runtimeState,
            speech = speechState,
            engines = engines,
            timeline = timeline,
            providers = providers,
            blackboard = BlackboardSnapshot(
                facts.size,
                facts.takeLast(10).map {
                    FactSummary(
                        it.id.value,
                        it.payload::class.simpleName ?: "Unknown",
                        it.createdAt.toJavaInstant()
                    )
                }
            ),
            traces = traces,
            nativeResources = nativeResourceCoordinator?.snapshot?.value ?: emptyList(),
            resourceLeases = resourceLeaseManager?.snapshot()
        )
    }
}
