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

data class EngineUiState(
    val id: String,
    val state: String,
    val isHealthy: Boolean
)

data class HomeUiState(
    val greeting: String = "System Online",
    val systemStatus: String = "Initializing...",
    val runtimeState: HaloRuntimeState = HaloRuntimeState.Idle,
    val recentEvents: List<TimelinePreviewEvent> = emptyList(),
    val engines: List<EngineUiState> = emptyList(),
    val activeProvider: String = "None",
    val batteryPercent: Int? = null,
    val isDeviceConnected: Boolean = false,
    val isCharging: Boolean = false
)

interface GreetingProvider {
    fun getGreeting(): String
}

class DefaultGreetingProvider : GreetingProvider {
    override fun getGreeting(): String = "System Online"
}

class HomeUiMapper(private val greetingProvider: GreetingProvider) : RuntimeUiMapper<HomeUiState> {
    override fun map(snapshot: RuntimeSnapshot): HomeUiState {
        val isReady = snapshot.runtimeState == com.halo.sdk.runtime.RuntimeState.Running
        
        val uiRuntimeState = when (snapshot.speech) {
            is SpeechSessionState.PassiveListening -> HaloRuntimeState.Idle
            is SpeechSessionState.Capturing, is SpeechSessionState.Recognizing -> HaloRuntimeState.Capturing
            is SpeechSessionState.Processing -> HaloRuntimeState.Recognizing
            is SpeechSessionState.Speaking -> HaloRuntimeState.Speaking
            else -> HaloRuntimeState.Idle
        }
        
        return HomeUiState(
            greeting = greetingProvider.getGreeting(),
            systemStatus = if (isReady) "Halo Runtime Ready." else "Initializing...",
            runtimeState = uiRuntimeState,
            recentEvents = snapshot.timeline.map { 
                TimelinePreviewEvent(it.title, it.status, System.currentTimeMillis())
            },
            engines = snapshot.engines.map { 
                EngineUiState(it.id, it.state.javaClass.simpleName, it.health == com.halo.sdk.runtime.EngineHealth.Healthy) 
            },
            activeProvider = snapshot.providers.firstOrNull()?.name ?: "Native Engine",
            batteryPercent = snapshot.deviceContext.batteryPercent,
            isDeviceConnected = snapshot.deviceContext.connected,
            isCharging = snapshot.deviceContext.charging
        )
    }
}

class HomeViewModel(
    private val snapshotManager: RuntimeSnapshotSource,
    private val commandBus: CommandBus,
    private val greetingProvider: GreetingProvider = DefaultGreetingProvider()
) : ViewModel() {

    private val mapper = HomeUiMapper(greetingProvider)
    
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
