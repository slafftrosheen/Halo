package com.halo.runtime.observer

import com.halo.sdk.runtime.*
import com.halo.runtime.kernel.ProcessManager
import com.halo.runtime.cognition.blackboard.Blackboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant

class DefaultRuntimeSnapshotManager(
    speechObserver: SpeechStateObserver,
    processManager: ProcessManager,
    blackboard: Blackboard,
    scope: CoroutineScope
) : RuntimeSnapshotSource {
    private val startedAtTime = Instant.now()

    override val snapshot: StateFlow<RuntimeSnapshot> = combine(
        speechObserver.sessionState,
        processManager.processFlow,
        blackboard.flow
    ) { speechState, processes, facts ->
        
        val engines = processes.map { p ->
            EngineSnapshot(
                id = p.name,
                state = EngineState.Unknown,
                health = EngineHealth.Unknown,
                uptime = Duration.ZERO,
                eventsProcessed = 0,
                lastActivity = Instant.now()
            )
        }

        val actualEngines = if (engines.isEmpty()) {
            listOf(
                EngineSnapshot("Speech", EngineState.Unknown, EngineHealth.Unknown, Duration.ZERO, 0, Instant.now()),
                EngineSnapshot("Reasoning", EngineState.Unknown, EngineHealth.Unknown, Duration.ZERO, 0, Instant.now()),
                EngineSnapshot("Planning", EngineState.Unknown, EngineHealth.Unknown, Duration.ZERO, 0, Instant.now()),
                EngineSnapshot("Execution", EngineState.Unknown, EngineHealth.Unknown, Duration.ZERO, 0, Instant.now())
            )
        } else engines
        
        val isBooting = processes.isEmpty()

        RuntimeSnapshot(
            runtimeVersion = "1.0.0-alpha",
            buildNumber = 42,
            startedAt = startedAtTime,
            health = RuntimeHealth(
                healthy = !isBooting,
                uptime = Duration.between(startedAtTime, Instant.now()),
                degraded = false,
                lastFailure = null
            ),
            runtimeState = if (isBooting) RuntimeState.Booting else RuntimeState.Running,
            speech = speechState,
            engines = actualEngines,
            timeline = emptyList(),
            providers = emptyList(),
            blackboard = BlackboardSnapshot(facts.size, facts.map { it.toString() }.takeLast(10))
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
