package com.halo.app.state

import com.halo.sdk.runtime.*
import com.halo.runtime.speech.SpeechSessionManager
import com.halo.runtime.kernel.ProcessManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

class RuntimeSnapshotManager(
    private val speechSessionManager: SpeechSessionManager,
    private val processManager: ProcessManager,
    private val scope: CoroutineScope
) {
    private val _snapshot = MutableStateFlow(
        RuntimeSnapshot(
            runtimeState = RuntimeState.Booting,
            speech = SpeechSessionState.Idle,
            engines = emptyList(),
            timeline = emptyList(),
            providers = emptyList(),
            blackboard = BlackboardSnapshot(0, emptyList())
        )
    )
    val snapshot: StateFlow<RuntimeSnapshot> = _snapshot.asStateFlow()

    init {
        // Collect Speech Session State
        speechSessionManager.sessionState.onEach { speechState ->
            _snapshot.update { it.copy(speech = speechState) }
        }.launchIn(scope)

        // Alpha MVP: Poll ProcessManager and map processes to EngineSnapshots
        scope.launch {
            while (true) {
                val engines = processManager.processes.map { p ->
                    EngineSnapshot(
                        id = p.name,
                        state = EngineState.Running, // Mock mapping for Alpha
                        health = EngineHealth.Healthy,
                        uptime = Duration.ZERO,
                        eventsProcessed = 0,
                        lastActivity = Instant.now()
                    )
                }
                
                // Add default engines if processManager is empty during boot
                val defaultEngines = if (engines.isEmpty()) {
                    listOf(
                        EngineSnapshot("Speech", EngineState.Running, EngineHealth.Healthy, Duration.ZERO, 0, Instant.now()),
                        EngineSnapshot("Reasoning", EngineState.Running, EngineHealth.Healthy, Duration.ZERO, 0, Instant.now()),
                        EngineSnapshot("Planning", EngineState.Running, EngineHealth.Healthy, Duration.ZERO, 0, Instant.now()),
                        EngineSnapshot("Execution", EngineState.Running, EngineHealth.Healthy, Duration.ZERO, 0, Instant.now())
                    )
                } else engines

                _snapshot.update { 
                    it.copy(
                        engines = defaultEngines, 
                        runtimeState = RuntimeState.Running 
                    ) 
                }
                delay(1000)
            }
        }
    }
}
