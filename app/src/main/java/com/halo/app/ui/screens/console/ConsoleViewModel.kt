package com.halo.app.ui.screens.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.runtime.kernel.ProcessManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

class ConsoleViewModel(
    private val processManager: ProcessManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConsoleUiState())
    val uiState: StateFlow<ConsoleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // For Alpha MVP, mock some console data
            _uiState.update { it.copy(
                engines = listOf(
                    EngineStatus("Speech", true, 42, 0),
                    EngineStatus("Reasoning", true, 120, 0),
                    EngineStatus("Planning", true, 15, 0),
                    EngineStatus("Execution", true, 10, 0)
                )
            ) }
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }
}
