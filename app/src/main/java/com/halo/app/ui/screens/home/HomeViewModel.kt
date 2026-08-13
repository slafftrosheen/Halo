package com.halo.app.ui.screens.home

import androidx.lifecycle.ViewModel
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.sdk.runtime.*
import com.halo.sdk.provider.*
import com.halo.runtime.cognition.session.CognitiveSession
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

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
    val activeProviderStatus: String = "Unavailable",
    val batteryPercent: Int? = null,
    val isCharging: Boolean = false,
    val capabilities: List<String> = emptyList(),
    val sessionState: CognitiveSessionState = CognitiveSessionState.Idle
)

class HomeUiMapper : RuntimeUiMapper<HomeUiState> {
    fun map(snapshot: RuntimeSnapshot, sessionState: CognitiveSessionState): HomeUiState {
        val isReady = snapshot.runtimeState == RuntimeState.Running
        
        val uiRuntimeState = when (snapshot.speech) {
            is SpeechSessionState.PassiveListening -> HaloRuntimeState.Idle
            is SpeechSessionState.Capturing -> HaloRuntimeState.Capturing
            is SpeechSessionState.Recognizing -> HaloRuntimeState.Recognizing
            is SpeechSessionState.Thinking -> HaloRuntimeState.Reasoning
            is SpeechSessionState.Processing -> HaloRuntimeState.Planning
            is SpeechSessionState.Speaking -> HaloRuntimeState.Speaking
            else -> HaloRuntimeState.Idle
        }
        
        val contextualText = when (sessionState) {
            is CognitiveSessionState.PreparingContext -> "Preparing context..."
            is CognitiveSessionState.Reasoning -> "Reasoning..."
            is CognitiveSessionState.Streaming -> sessionState.text
            is CognitiveSessionState.Completed -> sessionState.response.text ?: "Completed"
            is CognitiveSessionState.Failed -> "Error: ${sessionState.message}"
            else -> when (uiRuntimeState) {
                HaloRuntimeState.Idle -> if (isReady) "Ready when you are" else "Initializing..."
                HaloRuntimeState.Capturing -> "Listening"
                HaloRuntimeState.Recognizing -> "Thinking"
                HaloRuntimeState.Reasoning -> "Reasoning"
                HaloRuntimeState.Speaking -> "Speaking"
                else -> "Processing..."
            }
        }
        
        val isConnected = snapshot.deviceContext.connected
        
        val caps = if (snapshot.deviceContext.capabilities.isEmpty()) {
            listOf("Not reported")
        } else {
            snapshot.deviceContext.capabilities
        }

        val selectedProvider = snapshot.providers.find { it.isSelected } ?: snapshot.providers.firstOrNull()
        val providerStatus = selectedProvider?.status ?: "Unavailable"
        
        val activeProviderText = if (selectedProvider != null) {
            val modelSuffix = selectedProvider.activeModel?.let { " · $it" } ?: ""
            "${selectedProvider.name}$modelSuffix"
        } else {
            "No provider"
        }
        
        return HomeUiState(
            connectionState = if (isConnected) "Connected" else "Disconnected",
            contextualText = if (!isConnected) "Glasses disconnected" else contextualText,
            runtimeState = uiRuntimeState,
            recentEvents = snapshot.timeline.map { 
                TimelinePreviewEvent(it.title, it.status, it.timestamp.toEpochMilli())
            },
            activeProvider = activeProviderText,
            activeProviderStatus = providerStatus,
            batteryPercent = snapshot.deviceContext.batteryPercent,
            isCharging = snapshot.deviceContext.charging,
            capabilities = caps,
            sessionState = sessionState
        )
    }

    override fun map(snapshot: RuntimeSnapshot): HomeUiState = map(snapshot, CognitiveSessionState.Idle)
}

class HomeViewModel(
    private val snapshotManager: RuntimeSnapshotSource,
    private val commandBus: CommandBus,
    private val cognitiveSession: CognitiveSession
) : ViewModel() {

    private val mapper = HomeUiMapper()
    
    val uiState: StateFlow<HomeUiState> = combine(
        snapshotManager.snapshot,
        cognitiveSession.state
    ) { snapshot, sessionState ->
        mapper.map(snapshot, sessionState)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun submitText(text: String) {
        viewModelScope.launch {
            cognitiveSession.submit(AiRequest(text = text))
        }
    }
    
    fun cancelSession() {
        cognitiveSession.cancel()
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

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    fun navigateToIntelligence() {
        viewModelScope.launch {
            _navigationEvents.emit("ai_settings")
        }
    }

    fun navigateToDevices() {
        viewModelScope.launch {
            _navigationEvents.emit("devices")
        }
    }
}
