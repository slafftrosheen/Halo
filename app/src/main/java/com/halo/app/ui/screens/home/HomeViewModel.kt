package com.halo.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.components.HaloRuntimeState
import com.halo.engines.reasoning.models.ModelDownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.halo.sdk.runtime.CommandBus
import com.halo.sdk.runtime.StartListening
import com.halo.sdk.runtime.StopListening

data class TimelinePreviewEvent(
    val command: String,
    val status: String,
    val timestamp: String
)

data class EngineUiState(
    val id: String,
    val state: String,
    val isHealthy: Boolean
)

data class HomeUiState(
    val greeting: String = "Good Morning",
    val systemStatus: String = "Initializing...",
    val isRuntimeReady: Boolean = false,
    val runtimeState: HaloRuntimeState = HaloRuntimeState.Idle,
    val recentEvents: List<TimelinePreviewEvent> = emptyList(),
    val engines: List<EngineUiState> = emptyList(),
    val activeProvider: String = "None"
)


class HomeViewModel(
    private val modelDownloadManager: ModelDownloadManager,
    private val commandBus: CommandBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Collect model changes for Runtime Summary (as a temporary measure for Alpha)
        viewModelScope.launch {
            modelDownloadManager.activeModelId.collect { activeId ->
                _uiState.update { it.copy(
                    activeProvider = activeId ?: "None Selected"
                ) }
            }
        }
        
        // Setup initial static state (to be replaced by actual Runtime observers)
        _uiState.update { it.copy(
            isRuntimeReady = true,
            systemStatus = "Halo Runtime Ready.",
            engines = listOf(
                EngineUiState("Speech", "Ready", true),
                EngineUiState("Reasoning", "Waiting", true),
                EngineUiState("Planning", "Idle", true),
                EngineUiState("Execution", "Idle", true)
            )
        ) }
    }

    fun startListening() {
        viewModelScope.launch {
            commandBus.dispatch(StartListening)
        }
    }
    
    fun stopListening() {
        viewModelScope.launch {
            commandBus.dispatch(StopListening)
        }
    }
}
