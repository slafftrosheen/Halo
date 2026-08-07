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
                p.state.map { state -> Pair(p, state) }
            }) { it.toList() }
        }
    }

    override val snapshot: StateFlow<RuntimeSnapshot> = combine(
        speechObserver.sessionState,
        processesStates,
        blackboard.flow,
        timelineRecorder.timelineFlow
    ) { speechState, processes, facts, timeline ->
        
        val engines = processes.map { (p, state) ->
            EngineSnapshot(
                id = p.name,
                state = when (state) {
                    com.halo.runtime.kernel.ProcessState.Created -> EngineState.Unknown
                    com.halo.runtime.kernel.ProcessState.Running -> EngineState.Running
                    com.halo.runtime.kernel.ProcessState.Paused -> EngineState.Unknown
                    com.halo.runtime.kernel.ProcessState.Stopped -> EngineState.Unknown
                    com.halo.runtime.kernel.ProcessState.Crashed -> EngineState.Failed("Crashed")
                },
                health = EngineHealth.Unknown,
                metrics = EngineMetrics(null, Duration.ZERO, 0, 0, 0.0),
                lastActivity = clock.instant()
            )
        }

        val actualEngines = if (engines.isEmpty()) {
            listOf(
                EngineSnapshot("Speech", EngineState.Unknown, EngineHealth.Unknown, EngineMetrics(null, Duration.ZERO, 0, 0, 0.0), clock.instant()),
                EngineSnapshot("Reasoning", EngineState.Unknown, EngineHealth.Unknown, EngineMetrics(null, Duration.ZERO, 0, 0, 0.0), clock.instant()),
                EngineSnapshot("Planning", EngineState.Unknown, EngineHealth.Unknown, EngineMetrics(null, Duration.ZERO, 0, 0, 0.0), clock.instant()),
                EngineSnapshot("Execution", EngineState.Unknown, EngineHealth.Unknown, EngineMetrics(null, Duration.ZERO, 0, 0, 0.0), clock.instant())
            )
        } else engines
        
        val isBooting = processes.isEmpty()
        
        val speechHealthy = speechState != SpeechSessionState.Idle && speechState !is SpeechSessionState.Error
        val allRequiredEnginesHealthy = actualEngines.any { it.id == "Planning" && it.health == EngineHealth.Healthy }
        // For now, if booting we're not healthy, else if missing engines we rely on speech & planning logic eventually
        val isHealthy = if (isBooting) false else true // Placeholder, to be refined when actual metrics flow

        RuntimeSnapshot(
            runtimeVersion = "1.0.0-alpha",
            buildNumber = 42,
            startedAt = startedAtTime,
            health = RuntimeHealth(
                healthy = isHealthy,
                uptime = Duration.between(startedAtTime, clock.instant()),
                degraded = false,
                lastFailure = null
            ),
            runtimeState = if (isBooting) RuntimeState.Booting else RuntimeState.Running,
            speech = speechState,
            engines = actualEngines,
            timeline = timeline,
            providers = emptyList(),
            blackboard = BlackboardSnapshot(facts.size, facts.takeLast(10).map { FactSummary(it.id.value, it.payload::class.simpleName ?: "Unknown", it.createdAt.toJavaInstant()) })
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = RuntimeSnapshot(
            runtimeVersion = "1.0.0-alpha",
            buildNumber = 42,
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
