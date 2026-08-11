package com.halo.app.ui.screens.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.sdk.runtime.RuntimeSnapshot
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.RuntimeUiMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

class ConsoleUiMapper(private val selectedTab: Int) : RuntimeUiMapper<ConsoleUiState> {
    override fun map(snapshot: RuntimeSnapshot): ConsoleUiState {
        return ConsoleUiState(
            selectedTab = selectedTab,
            uptime = formatDuration(snapshot.health.uptime),
            memoryUsage = "128 MB",
            threadCount = 0,
            engines = snapshot.engines.map {
                EngineStatus(
                    name = it.id,
                    isHealthy = it.health == com.halo.sdk.runtime.EngineHealth.Healthy,
                    latencyMs = it.metrics.latencyMs,
                    queueSize = it.metrics.queueSize
                )
            }
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
    private val snapshotManager: RuntimeSnapshotSource
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)

    val uiState: StateFlow<ConsoleUiState> = combine(
        snapshotManager.snapshot,
        _selectedTab
    ) { snapshot, tab ->
        ConsoleUiMapper(tab).map(snapshot)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConsoleUiState()
    )

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }
}
