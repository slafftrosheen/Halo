package com.halo.app.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import com.halo.runtime.cognition.session.CognitiveSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class TimelinePreviewEvent(val command: String, val status: String, val timestamp: Long)

data class HomeUiState(
    val connectionState: String = "Disconnected",
    val contextualText: String = "Ready when you are",
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
        val speechRuntimeState = when (snapshot.speech) {
            is SpeechSessionState.PassiveListening -> HaloRuntimeState.Idle
            is SpeechSessionState.Capturing -> HaloRuntimeState.Capturing
            is SpeechSessionState.Recognizing -> HaloRuntimeState.Recognizing
            is SpeechSessionState.Thinking -> HaloRuntimeState.Reasoning
            is SpeechSessionState.Processing -> HaloRuntimeState.Planning
            is SpeechSessionState.Speaking -> HaloRuntimeState.Speaking
            else -> HaloRuntimeState.Idle
        }
        val uiRuntimeState = when (sessionState) {
            is CognitiveSessionState.PreparingContext -> HaloRuntimeState.Recognizing
            is CognitiveSessionState.Reasoning -> HaloRuntimeState.Reasoning
            is CognitiveSessionState.Streaming -> HaloRuntimeState.Reasoning
            is CognitiveSessionState.ExecutingTool -> HaloRuntimeState.Executing
            is CognitiveSessionState.Completed -> HaloRuntimeState.Idle
            is CognitiveSessionState.Failed -> HaloRuntimeState.Recovering
            CognitiveSessionState.Idle -> speechRuntimeState
        }
        val contextualText = when (sessionState) {
            is CognitiveSessionState.PreparingContext -> "Preparing context..."
            is CognitiveSessionState.Reasoning -> "Reasoning..."
            is CognitiveSessionState.Streaming -> sessionState.text.ifBlank { "Generating..." }
            is CognitiveSessionState.ExecutingTool -> "Executing ${sessionState.toolId}..."
            is CognitiveSessionState.Completed -> sessionState.response.text.ifBlank { "Completed" }
            is CognitiveSessionState.Failed -> "Error: ${sessionState.message}"
            CognitiveSessionState.Idle -> when (uiRuntimeState) {
                HaloRuntimeState.Idle -> if (isReady) "Ready when you are" else "Starting Halo..."
                HaloRuntimeState.Capturing -> "Listening"
                HaloRuntimeState.Recognizing -> "Understanding"
                HaloRuntimeState.Reasoning -> "Reasoning"
                HaloRuntimeState.Speaking -> "Speaking"
                else -> "Processing..."
            }
        }
        val activeSessionState = activeSessions.firstOrNull()?.state?.value
        val connectionText = when (activeSessionState) {
            com.halo.sdk.runtime.embodiment.SessionState.SAVED -> "Saved"
            com.halo.sdk.runtime.embodiment.SessionState.DISCOVERED -> "Discovered"
            com.halo.sdk.runtime.embodiment.SessionState.CONNECTING -> "Connecting"
            com.halo.sdk.runtime.embodiment.SessionState.CONNECTED -> "Connected"
            com.halo.sdk.runtime.embodiment.SessionState.HANDSHAKE -> "Handshaking"
            com.halo.sdk.runtime.embodiment.SessionState.CAPABILITY_SYNC -> "Syncing capabilities"
            com.halo.sdk.runtime.embodiment.SessionState.STATE_SYNC -> "Syncing state"
            com.halo.sdk.runtime.embodiment.SessionState.READY -> "Ready"
            com.halo.sdk.runtime.embodiment.SessionState.DISCONNECTING -> "Disconnecting"
            com.halo.sdk.runtime.embodiment.SessionState.DISCONNECTED -> "Disconnected"
            com.halo.sdk.runtime.embodiment.SessionState.ERROR -> "Error"
            null -> if (snapshot.deviceContext.connected) "Connected" else "Disconnected"
        }
        val caps = if (snapshot.deviceContext.capabilities.isEmpty()) listOf("Phone runtime") else snapshot.deviceContext.capabilities
        val selectedDiag = diagnostics.find { it.providerId == selectedProviderId } ?: diagnostics.find { it.selected }
        val activeProviderText = if (selectedDiag != null) {
            val modelSuffix = selectedModelId?.let { " · $it" } ?: selectedDiag.selectedModelId?.let { " · $it" } ?: ""
            "${selectedDiag.displayName}$modelSuffix"
        } else {
            val snapProvider = snapshot.providers.find { it.isSelected } ?: snapshot.providers.firstOrNull()
            if (snapProvider != null) "${snapProvider.name}${snapProvider.activeModel?.let { " · $it" } ?: ""}" else "No provider"
        }
        val providerStatus = selectedDiag?.state?.name ?: snapshot.providers.find { it.isSelected }?.status ?: "Unavailable"
        return HomeUiState(
            connectionState = connectionText,
            contextualText = contextualText,
            runtimeState = uiRuntimeState,
            recentEvents = snapshot.timeline.map { TimelinePreviewEvent(it.title, it.status, it.timestamp.toEpochMilli()) },
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
        aiProviderRouter?.selectedProviderId ?: MutableStateFlow<String?>(null),
        aiProviderRouter?.selectedModelId ?: MutableStateFlow<String?>(null),
        aiProviderRouter?.diagnosticsFlow ?: MutableStateFlow(emptyList<AiProviderDiagnostic>()),
        deviceSessionManager?.activeSessions ?: MutableStateFlow(emptyList<com.halo.sdk.runtime.embodiment.EmbodimentSession>())
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val snapshot = args[0] as RuntimeSnapshot
        val sessionState = args[1] as CognitiveSessionState
        val selectedProviderId = args[2] as String?
        val selectedModelId = args[3] as String?
        @Suppress("UNCHECKED_CAST") val diagnostics = args[4] as List<AiProviderDiagnostic>
        @Suppress("UNCHECKED_CAST") val activeSessions = args[5] as List<com.halo.sdk.runtime.embodiment.EmbodimentSession>
        mapper.map(snapshot, sessionState, selectedProviderId, selectedModelId, diagnostics, activeSessions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun submitText(text: String) {
        Log.d("HomeViewModel", "submitText invoked")
        val normalized = text.trim()
        if (normalized.isNotBlank()) viewModelScope.launch {
            runCatching { cognitiveSession.submit(AiRequest(text = normalized)) }
                .onFailure { Log.e("HomeViewModel", "Text submission failed", it) }
        }
    }
    fun clearConversation() { cognitiveSession.clearConversation() }
    fun cancelSession() { cognitiveSession.cancel() }
    fun startListening() {
        viewModelScope.launch { runCatching { commandBus.dispatch(StartListening) }.onFailure { Log.e("HomeViewModel", "Failed to start listening", it) } }
    }
    fun stopListening() {
        viewModelScope.launch { runCatching { commandBus.dispatch(StopListening) }.onFailure { Log.e("HomeViewModel", "Failed to stop listening", it) } }
    }
    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()
    fun navigateToIntelligence() { viewModelScope.launch { _navigationEvents.emit("ai_settings") } }
    fun navigateToDevices() { viewModelScope.launch { _navigationEvents.emit("devices") } }
    fun navigateToConsole() { viewModelScope.launch { _navigationEvents.emit("console") } }
}
