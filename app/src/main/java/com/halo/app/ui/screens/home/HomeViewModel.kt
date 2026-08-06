package com.halo.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.components.HaloRuntimeState
import com.halo.app.state.RuntimeSnapshotManager
import com.halo.sdk.runtime.CommandBus
import com.halo.sdk.runtime.RuntimeSnapshot
import com.halo.sdk.runtime.RuntimeUiMapper
import com.halo.sdk.runtime.SpeechSessionState
import com.halo.sdk.runtime.StartListening
import com.halo.sdk.runtime.StopListening
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

class HomeUiMapper : RuntimeUiMapper<HomeUiState> {
    override fun map(snapshot: RuntimeSnapshot): HomeUiState {
        val isReady = snapshot.runtimeState == com.halo.sdk.runtime.RuntimeState.Running
        
        val uiRuntimeState = when (snapshot.speech) {
            is SpeechSessionState.PassiveListening -> HaloRuntimeState.Idle
            is SpeechSessionState.Capturing, is SpeechSessionState.Recognizing -> HaloRuntimeState.Listening
            is SpeechSessionState.Processing -> HaloRuntimeState.Processing
            is SpeechSessionState.Speaking -> HaloRuntimeState.Speaking
            else -> HaloRuntimeState.Idle
        }
        
        return HomeUiState(
            greeting = "System Online",
            systemStatus = if (isReady) "Halo Runtime Ready." else "Initializing...",
            isRuntimeReady = isReady,
            runtimeState = uiRuntimeState,
            recentEvents = snapshot.timeline.map { 
                TimelinePreviewEvent(it.title, it.status, "Just now") 
            },
            engines = snapshot.engines.map { 
                EngineUiState(it.id, it.state.javaClass.simpleName, it.health == com.halo.sdk.runtime.EngineHealth.Healthy) 
            },
            activeProvider = snapshot.providers.firstOrNull()?.name ?: "Native Engine"
        )
    }
}

class HomeViewModel(
    private val snapshotManager: RuntimeSnapshotManager,
    private val commandBus: CommandBus
) : ViewModel() {

    private val mapper = HomeUiMapper()
    
    val uiState: StateFlow<HomeUiState> = snapshotManager.snapshot
        .map { mapper.map(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState()
        )

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
