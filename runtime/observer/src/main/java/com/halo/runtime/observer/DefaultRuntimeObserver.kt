package com.halo.runtime.observer

import com.halo.sdk.runtime.*
import com.halo.runtime.kernel.ProcessManager
import com.halo.runtime.cognition.blackboard.Blackboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Duration
import java.time.Instant

import java.time.Clock
import kotlinx.datetime.toJavaInstant

class DefaultRuntimeObserver(
    speechObserver: SpeechStateObserver,
    processManager: ProcessManager,
    blackboard: Blackboard,
    timelineRecorder: TimelineRecorder,
    private val traceRecorder: TraceRecorder,
    private val assembler: SnapshotAssembler,
    private val clock: Clock,
    scope: CoroutineScope
) : RuntimeSnapshotSource {
    private val startedAtTime = clock.instant()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val processesStates = processManager.processFlow.flatMapLatest { processes ->
        if (processes.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(processes.map { p ->
                combine(p.state, p.metrics) { _, _ -> p }
            }) { it.toList() }
        }
    }

    override val snapshot: StateFlow<RuntimeSnapshot> = combine(
        speechObserver.sessionState,
        processesStates,
        blackboard.flow,
        timelineRecorder.timelineFlow,
        traceRecorder.tracesFlow
    ) { speechState, processes, facts, timeline, traces ->
        assembler.assemble(speechState, processes, facts, timeline, traces, startedAtTime)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
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
