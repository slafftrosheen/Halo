package com.halo.app.ui.developer.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.sdk.runtime.RuntimeSnapshot
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.RuntimeUiMapper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

import com.halo.app.devtools.RecordingEventBus
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.app.ui.state.presenceFromSnapshot
import com.halo.sdk.runtime.CommandBus
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.StartListening
import com.halo.sdk.runtime.StopListening
import com.halo.runtime.cognition.blackboard.Blackboard
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class ConsoleUiMapper(
    private val logs: List<String> = emptyList(),
    private val blackboardFacts: List<String> = emptyList()
) : RuntimeUiMapper<ConsoleUiState> {
    override fun map(snapshot: RuntimeSnapshot): ConsoleUiState {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemory = runtime.maxMemory() / (1024 * 1024)

        return ConsoleUiState(
            uptime = formatDuration(snapshot.health.uptime),
            memoryUsage = "$usedMemory / $maxMemory MB",
            threadCount = Thread.activeCount(),
            engines = snapshot.engines.map {
                EngineStatus(
                    name = it.id,
                    isHealthy = it.health == com.halo.sdk.runtime.EngineHealth.Healthy,
                    latencyMs = it.metrics.latencyMs,
                    queueSize = it.metrics.queueSize
                )
            },
            logs = logs,
            blackboardFacts = blackboardFacts,
            providers = snapshot.providers.map {
                ProviderStatusRow(
                    id = it.id,
                    name = it.name,
                    status = it.status,
                    activeModel = it.activeModel
                )
            },
            events = snapshot.timeline.map {
                ConsoleTimelineRow(
                    id = it.id,
                    title = it.title,
                    description = it.description,
                    status = it.status,
                    timestampMs = it.timestamp.toEpochMilli()
                )
            },
            nativeResources = snapshot.nativeResources,
            presence = presenceFromSnapshot(snapshot),
            runtimeState = snapshot.runtimeState.name,
            healthy = snapshot.health.healthy,
            degraded = snapshot.health.degraded
        )
    }

    private fun formatDuration(duration: java.time.Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}

class ConsoleViewModel(
    private val snapshotManager: RuntimeSnapshotSource,
    private val eventBus: EventBus,
    private val blackboard: Blackboard,
    private val commandBus: CommandBus
) : ViewModel() {

    private val logsFlow = if (eventBus is RecordingEventBus) {
        eventBus.history.map { history ->
            history.map { "[${it.createdAt}] ${it.event::class.simpleName}" }
        }
    } else {
        flowOf(emptyList<String>())
    }

    private val blackboardFlow = blackboard.flow.map { facts ->
        facts.map { "[${it.createdAt}] ${it.payload::class.simpleName}" }
    }

    val uiState: StateFlow<ConsoleUiState> = combine(
        snapshotManager.snapshot,
        logsFlow,
        blackboardFlow
    ) { snapshot, logs, facts ->
        ConsoleUiMapper(logs, facts).map(snapshot)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConsoleUiState()
    )

    /**
     * Orb interaction is a command to the runtime, not a change of appearance.
     * The next presence the orb shows comes back through the snapshot.
     */
    fun toggleListening() {
        val presence = presenceFromSnapshot(snapshotManager.snapshot.value)
        val capturing = presence == HaloRuntimeState.Capturing ||
            presence == HaloRuntimeState.Recognizing ||
            presence == HaloRuntimeState.Wakeword
        viewModelScope.launch {
            runCatching { commandBus.dispatch(if (capturing) StopListening else StartListening) }
        }
    }
}
