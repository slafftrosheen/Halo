package com.halo.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.components.HaloRuntimeState
import com.halo.engines.reasoning.models.ModelDownloadManager
import com.halo.runtime.kernel.ProcessManager
import com.halo.runtime.kernel.bus.CognitiveBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val runtimeState: HaloRuntimeState = HaloRuntimeState.Idle,
    val isSpeechReady: Boolean = true,
    val isReasoningReady: Boolean = false,
    val isPlanningReady: Boolean = true,
    val isExecutionReady: Boolean = true,
    val activeProviderId: String? = null,
    val lastCommand: String = "None",
    val batteryLevel: String = "98%",
    val networkState: String = "Connected",
    val isRuntimeReady: Boolean = true
)

class HomeViewModel(
    private val processManager: ProcessManager,
    private val cognitiveBus: CognitiveBus,
    private val modelDownloadManager: ModelDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            modelDownloadManager.activeModelId.collect { activeId ->
                _uiState.update { it.copy(
                    activeProviderId = activeId,
                    isReasoningReady = activeId != null
                ) }
            }
        }
        
        // Mocking runtime state for now until we have a real RuntimeState emitter
        viewModelScope.launch {
            // Subscribe to actual intents/events here to change HaloRuntimeState
        }
    }

    fun triggerMicrophone() {
        // Toggle listening state
        val currentState = _uiState.value.runtimeState
        val nextState = if (currentState == HaloRuntimeState.Idle) {
            HaloRuntimeState.Listening
        } else {
            HaloRuntimeState.Idle
        }
        _uiState.update { it.copy(runtimeState = nextState) }
        
        // Actually trigger microphone process or inject event
        if (nextState == HaloRuntimeState.Listening) {
            // In a real implementation, we would send a command to AndroidWakeWordProcess
            // For Alpha, this will act as the "Inject Event" trigger if we want
        }
    }
}
