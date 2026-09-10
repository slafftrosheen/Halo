package com.halo.app.ui.screens.home

import android.util.Log
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
    fun map(
        snapshot: RuntimeSnapshot,
        sessionState: CognitiveSessionState,
        selectedProviderId: String? = null,
        selectedModelId: String? = null,
        diagnostics: List<AiProviderDiagnostic> = emptyList(),
        activeSessions: List<com.halo.sdk.runtime.embodiment.EmbodimentSession> = emptyList()
    ): HomeUiState {
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
            is CognitiveSessionState.Completed -> sessionState.response.text.ifBlank { "Completed" }
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
        
        val activeSession = activeSessions.firstOrNull()
        val isConnected = if (activeSession != null) {
            val sState = activeSession.state.value
            sState == com.halo.sdk.runtime.embodiment.SessionState.CONNECTED ||
                sState == com.halo.sdk.runtime.embodiment.SessionState.HANDSHAKE ||
                sState == com.halo.sdk.runtime.embodiment.SessionState.CAPABILITY_SYNC ||
                sState == com.halo.sdk.runtime.embodiment.SessionState.STATE_SYNC ||
                sState == com.halo.sdk.runtime.embodiment.SessionState.READY
        } else {
            snapshot.deviceContext.connected
        }

        val connectionText = if (activeSession != null) {
            when (activeSession.state.value) {
                com.halo.sdk.runtime.embodiment.SessionState.READY -> "Ready"
                com.halo.sdk.runtime.embodiment.SessionState.CONNECTED -> "Connected"
                com.halo.sdk.runtime.embodiment.SessionState.CONNECTING -> "Connecting"
                com.halo.sdk.runtime.embodiment.SessionState.DISCONNECTING -> "Disconnecting"
                com.halo.sdk.runtime.embodiment.SessionState.ERROR -> "Error"
                else -> "Disconnected"
            }
        } else {
            if (isConnected) "Connected" else "Disconnected"
        }

        val caps = if (snapshot.deviceContext.capabilities.isEmpty()) {
            listOf("Not reported")
        } else {
            snapshot.deviceContext.capabilities
        }

        val selectedDiag = diagnostics.find { it.providerId == selectedProviderId }
            ?: diagnostics.find { it.selected }
        
        val activeProviderText = if (selectedDiag != null) {
            val modelSuffix = selectedModelId?.let { " · $it" }
                ?: selectedDiag.selectedModelId?.let { " · $it" }
                ?: ""
            "${selectedDiag.displayName}$modelSuffix"
        } else {
            val snapProvider = snapshot.providers.find { it.isSelected } ?: snapshot.providers.firstOrNull()
            if (snapProvider != null) {
                val modelSuffix = snapProvider.activeModel?.let { " · $it" } ?: ""
                "${snapProvider.name}$modelSuffix"
            } else {
                "No provider"
            }
        }

        val providerStatus = selectedDiag?.state?.name
            ?: snapshot.providers.find { it.isSelected }?.status
            ?: "Unavailable"

        val finalContextualText = if (sessionState != CognitiveSessionState.Idle) {
            contextualText
        } else if (!isConnected) {
            "Glasses disconnected"
        } else {
            contextualText
        }
        
        return HomeUiState(
            connectionState = connectionText,
            contextualText = finalContextualText,
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
    private val cognitiveSession: CognitiveSession,
    private val aiProviderRouter: AiProviderRouter? = null,
    private val deviceSessionManager: com.halo.sdk.runtime.embodiment.DeviceSessionManager? = null
) : ViewModel() {

    private val mapper = HomeUiMapper()
    
    val uiState: StateFlow<HomeUiState> = combine(
        snapshotManager.snapshot,
        cognitiveSession.state,
        aiProviderRouter?.selectedProviderId ?: MutableStateFlow(null),
        aiProviderRouter?.selectedModelId ?: MutableStateFlow(null),
        aiProviderRouter?.diagnosticsFlow ?: MutableStateFlow(emptyList<AiProviderDiagnostic>()),
        deviceSessionManager?.activeSessions ?: MutableStateFlow(emptyList<com.halo.sdk.runtime.embodiment.EmbodimentSession>())
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val snapshot = args[0] as RuntimeSnapshot
        val sessionState = args[1] as CognitiveSessionState
        val selectedProviderId = args[2] as String?
        val selectedModelId = args[3] as String?
        @Suppress("UNCHECKED_CAST")
        val diagnostics = args[4] as List<AiProviderDiagnostic>
        @Suppress("UNCHECKED_CAST")
        val activeSessions = args[5] as List<com.halo.sdk.runtime.embodiment.EmbodimentSession>

        mapper.map(
            snapshot = snapshot,
            sessionState = sessionState,
            selectedProviderId = selectedProviderId,
            selectedModelId = selectedModelId,
            diagnostics = diagnostics,
            activeSessions = activeSessions
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun submitText(text: String) {
        Log.d("HomeViewModel", "submitText invoked")
        viewModelScope.launch {
            cognitiveSession.submit(AiRequest(text = text))
        }
    }
    
    fun clearConversation() {
        Log.d("HomeViewModel", "clearConversation")
        cognitiveSession.clearConversation()
    }
    
    fun cancelSession() {
        Log.d("HomeViewModel", "cancelSession")
        cognitiveSession.cancel()
    }

    fun startListening() {
        Log.d("HomeViewModel", "startListening")
        viewModelScope.launch {
            commandBus.dispatch(StartListening)
        }
    }
    
    fun stopListening() {
        Log.d("HomeViewModel", "stopListening")
        viewModelScope.launch {
            commandBus.dispatch(StopListening)
        }
    }

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    fun navigateToIntelligence() {
        Log.d("HomeViewModel", "navigateToIntelligence")
        viewModelScope.launch {
            _navigationEvents.emit("ai_settings")
        }
    }

    fun navigateToDevices() {
        Log.d("HomeViewModel", "navigateToDevices")
        viewModelScope.launch {
            _navigationEvents.emit("devices")
        }
    }
}
