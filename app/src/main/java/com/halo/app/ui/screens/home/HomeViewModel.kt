package com.halo.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.sdk.runtime.CommandBus
import com.halo.sdk.runtime.RuntimeSnapshot
import com.halo.sdk.runtime.RuntimeSnapshotSource
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
    val timestamp: Long
)

data class HomeUiState(
    val connectionState: String = "Disconnected",
    val contextualText: String = "Initializing...",
    val runtimeState: HaloRuntimeState = HaloRuntimeState.Idle,
    val recentEvents: List<TimelinePreviewEvent> = emptyList(),
    val activeProvider: String = "No provider",
    val batteryPercent: Int? = null,
    val isCharging: Boolean = false,
    val capabilities: List<String> = emptyList()
)

class HomeUiMapper : RuntimeUiMapper<HomeUiState> {
    override fun map(snapshot: RuntimeSnapshot): HomeUiState {
        val isReady = snapshot.runtimeState == com.halo.sdk.runtime.RuntimeState.Running
        
        val uiRuntimeState = when (snapshot.speech) {
            is SpeechSessionState.PassiveListening -> HaloRuntimeState.Idle
            is SpeechSessionState.Capturing, is SpeechSessionState.Recognizing -> HaloRuntimeState.Capturing
            is SpeechSessionState.Processing -> HaloRuntimeState.Recognizing
            is SpeechSessionState.Speaking -> HaloRuntimeState.Speaking
            else -> HaloRuntimeState.Idle
        }
        
        val contextualText = when (uiRuntimeState) {
            HaloRuntimeState.Idle -> if (isReady) "Ready when you are" else "Initializing..."
            HaloRuntimeState.Capturing -> "Listening"
            HaloRuntimeState.Recognizing -> "Thinking"
            HaloRuntimeState.Speaking -> "Speaking"
            else -> "Processing..."
        }
        
        val isConnected = snapshot.deviceContext.connected
        
        val caps = mutableListOf<String>()
        if (isConnected) {
            caps.add("Camera")
            caps.add("Audio")
            caps.add("Glasses")
        }
        
        return HomeUiState(
            connectionState = if (isConnected) "Connected" else "Disconnected",
            contextualText = if (!isConnected) "Glasses disconnected" else contextualText,
            runtimeState = uiRuntimeState,
            recentEvents = snapshot.timeline.map { 
                TimelinePreviewEvent(it.title, it.status, System.currentTimeMillis())
            },
            activeProvider = snapshot.providers.firstOrNull()?.name ?: "No provider",
            batteryPercent = snapshot.deviceContext.batteryPercent,
            isCharging = snapshot.deviceContext.charging,
            capabilities = caps
        )
    }
}

class HomeViewModel(
    private val snapshotManager: RuntimeSnapshotSource,
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
