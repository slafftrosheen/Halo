package com.halo.app.ui.screens.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.state.RuntimeSnapshotManager
import com.halo.sdk.runtime.RuntimeSnapshot
import com.halo.sdk.runtime.RuntimeUiMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class EngineStatus(
    val name: String,
    val isHealthy: Boolean,
    val latencyMs: Long,
    val queueSize: Int
)

data class ConsoleUiState(
    val selectedTab: Int = 0,
    val uptime: String = "00:00:00",
    val memoryUsage: String = "0 MB",
    val threadCount: Int = 0,
    val engines: List<EngineStatus> = emptyList()
)

class ConsoleUiMapper(private val selectedTab: Int) : RuntimeUiMapper<ConsoleUiState> {
    override fun map(snapshot: RuntimeSnapshot): ConsoleUiState {
        return ConsoleUiState(
            selectedTab = selectedTab,
            uptime = "00:00:00", // Would be formatted snapshot.uptime
            memoryUsage = "128 MB", // Mocked for now
            threadCount = 0, // Mocked for now
            engines = snapshot.engines.map {
                EngineStatus(
                    name = it.id,
                    isHealthy = it.health == com.halo.sdk.runtime.EngineHealth.Healthy,
                    latencyMs = 15L, // Mocked until metrics available
                    queueSize = 0
                )
            }
        )
    }
}

class ConsoleViewModel(
    private val snapshotManager: RuntimeSnapshotManager
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
