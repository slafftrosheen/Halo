package com.halo.runtime.observer

import com.halo.libraries.common.core.Identifier
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.sdk.runtime.BlackboardSnapshot
import com.halo.sdk.runtime.Engine
import com.halo.sdk.runtime.EngineMetrics
import com.halo.sdk.runtime.EngineSnapshot
import com.halo.sdk.runtime.ProviderSnapshot
import com.halo.sdk.runtime.RuntimeHealth
import com.halo.sdk.runtime.RuntimeSnapshot
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.RuntimeState
import com.halo.sdk.runtime.SpeechSessionState
import com.halo.sdk.runtime.SpeechStateObserver
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Projects the canonical phone runtime into one UI-facing snapshot.
 *
 * Runtime health comes from the actual Engine instances that Halo starts, not
 * from the legacy ProcessManager/microkernel compatibility layer.
 */
class DefaultRuntimeObserver(
    speechObserver: SpeechStateObserver,
    engines: List<Engine>,
    blackboard: Blackboard,
    timelineRecorder: TimelineRecorder,
    private val traceRecorder: TraceRecorder,
    private val assembler: SnapshotAssembler,
    private val providerSource: () -> List<ProviderSnapshot>,
    private val clock: Clock,
    scope: CoroutineScope
) : RuntimeSnapshotSource {
    private val startedAtTime = clock.instant()

    private val engineSnapshots = if (engines.isEmpty()) {
        flowOf(emptyList())
    } else {
        combine(
            engines.map { engine ->
                combine(engine.state, engine.health) { state, health ->
                    EngineSnapshot(
                        id = engine.metadata.id.value,
                        state = state,
                        health = health,
                        metrics = EngineMetrics(
                            latencyMs = null,
                            uptime = Duration.ZERO,
                            eventsProcessed = 0,
                            queueSize = 0,
                            cpuUsage = 0.0
                        ),
                        lastActivity = clock.instant()
                    )
                }
            }
        ) { snapshots -> snapshots.toList() }
    }

    override val snapshot: StateFlow<RuntimeSnapshot> = combine(
        speechObserver.sessionState,
        engineSnapshots,
        blackboard.flow,
        timelineRecorder.timelineFlow,
        traceRecorder.tracesFlow
    ) { speechState, currentEngines, facts, timeline, traces ->
        assembler.assemble(
            speechState = speechState,
            engines = currentEngines,
            facts = facts,
            timeline = timeline,
            traces = traces,
            providers = providerSource(),
            startedAtTime = startedAtTime
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Lazily,
        initialValue = RuntimeSnapshot(
            runtimeVersion = "1.0.0-alpha",
            buildNumber = 44,
            startedAt = startedAtTime,
            health = RuntimeHealth(false, Duration.ZERO, false, null),
            runtimeState = RuntimeState.Booting,
            speech = SpeechSessionState.Idle,
            engines = emptyList(),
            timeline = emptyList(),
            providers = emptyList(),
            blackboard = BlackboardSnapshot(0, emptyList())
        )
    )
}
