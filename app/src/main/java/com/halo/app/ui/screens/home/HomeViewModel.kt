package com.halo.app.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.app.ui.state.presenceFromSpeech
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import com.halo.runtime.cognition.session.CognitiveSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class TimelinePreviewEvent(val command: String, val status: String, val timestamp: Long)

data class HomeUiState(
    val contextualText: String = "Ready when you are",
    val runtimeState: HaloRuntimeState = HaloRuntimeState.Idle,
    val recentEvents: List<TimelinePreviewEvent> = emptyList(),
    val activeProvider: String = "No provider",
    val activeProviderStatus: String = "Unavailable",
    val sessionState: CognitiveSessionState = CognitiveSessionState.Idle,
    val speechActive: Boolean = false
)

class HomeUiMapper : RuntimeUiMapper<HomeUiState> {
    fun map(
        snapshot: RuntimeSnapshot,
        sessionState: CognitiveSessionState,
        selectedProviderId: String? = null,
        selectedModelId: String? = null,
        diagnostics: List<AiProviderDiagnostic> = emptyList()
    ): HomeUiState {
        val speechRuntimeState = presenceFromSpeech(snapshot.speech)

        val speechIsActive = when (snapshot.speech) {
            SpeechSessionState.Capturing,
            SpeechSessionState.Recognizing -> true
            else -> false
        }

        val uiRuntimeState = when {
            // Actual TTS/speech presence always wins over the cognitive transaction.
            snapshot.speech == SpeechSessionState.Speaking ->
                HaloRuntimeState.Speaking

            // Actual microphone recognition always wins while it is active.
            speechIsActive ->
                speechRuntimeState

            sessionState is CognitiveSessionState.PreparingContext ->
                HaloRuntimeState.Reasoning

            sessionState is CognitiveSessionState.Reasoning ->
                HaloRuntimeState.Reasoning

            sessionState is CognitiveSessionState.Streaming ->
                HaloRuntimeState.Speaking

            sessionState is CognitiveSessionState.ExecutingTool ->
                HaloRuntimeState.Executing

            sessionState is CognitiveSessionState.Failed ->
                HaloRuntimeState.Recovering

            // Completed means the cognitive transaction is finished,
            // but speech state may still be Speaking. That case was handled above.
            else ->
                speechRuntimeState
        }

        val contextualText = when (sessionState) {
            is CognitiveSessionState.PreparingContext -> "Preparing context..."
            is CognitiveSessionState.Reasoning -> "Reasoning..."
            is CognitiveSessionState.Streaming -> sessionState.text.ifBlank { "Responding…" }
            is CognitiveSessionState.ExecutingTool -> "Executing ${sessionState.toolId}..."
            is CognitiveSessionState.Completed -> sessionState.response.text.ifBlank { "Completed" }
            is CognitiveSessionState.Failed -> "Error: ${sessionState.message}"
            CognitiveSessionState.Idle -> when (uiRuntimeState) {
                HaloRuntimeState.Idle -> "Ready when you are"
                HaloRuntimeState.Capturing -> "Listening"
                HaloRuntimeState.Recognizing -> "Understanding"
                HaloRuntimeState.Reasoning -> "Reasoning"
                HaloRuntimeState.Speaking -> "Speaking"
                else -> "Processing..."
            }
        }
        val activeProviderText = diagnostics.find { it.providerId == selectedProviderId }?.let { diagnostic ->
            val modelSuffix = selectedModelId?.let { " · $it" }
                ?: diagnostic.selectedModelId?.let { " · $it" }
                ?: ""
            "${diagnostic.displayName}$modelSuffix"
        } ?: snapshot.providers.find { it.isSelected }?.let { provider ->
            "${provider.name}${provider.activeModel?.let { " · $it" } ?: ""}"
        } ?: "No provider"
        val providerStatus = diagnostics.find { it.providerId == selectedProviderId }?.state?.name
            ?: snapshot.providers.find { it.isSelected }?.status
            ?: "Unavailable"
        return HomeUiState(
            contextualText = contextualText,
            runtimeState = uiRuntimeState,
            recentEvents = snapshot.timeline.map { TimelinePreviewEvent(it.title, it.status, it.timestamp.toEpochMilli()) },
            activeProvider = activeProviderText,
            activeProviderStatus = providerStatus,
            sessionState = sessionState,
            speechActive = speechIsActive
        )
    }

    override fun map(snapshot: RuntimeSnapshot): HomeUiState = map(snapshot, CognitiveSessionState.Idle)
}

class HomeViewModel(
    private val snapshotManager: RuntimeSnapshotSource,
    private val commandBus: CommandBus,
    private val cognitiveSession: CognitiveSession,
    private val aiProviderRouter: AiProviderRouter? = null
) : ViewModel() {
    private val mapper = HomeUiMapper()
    val uiState: StateFlow<HomeUiState> = combine(
        snapshotManager.snapshot,
        cognitiveSession.state,
        aiProviderRouter?.selectedProviderId ?: MutableStateFlow<String?>(null),
        aiProviderRouter?.selectedModelId ?: MutableStateFlow<String?>(null),
        aiProviderRouter?.diagnosticsFlow ?: MutableStateFlow(emptyList<AiProviderDiagnostic>())
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val snapshot = args[0] as RuntimeSnapshot
        val sessionState = args[1] as CognitiveSessionState
        val selectedProviderId = args[2] as String?
        val selectedModelId = args[3] as String?
        @Suppress("UNCHECKED_CAST") val diagnostics = args[4] as List<AiProviderDiagnostic>
        mapper.map(snapshot, sessionState, selectedProviderId, selectedModelId, diagnostics)
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
        viewModelScope.launch {
            runCatching { commandBus.dispatch(StartListening) }
                .onFailure { Log.e("HomeViewModel", "Failed to start listening", it) }
        }
    }

    fun stopListening() {
        viewModelScope.launch {
            runCatching { commandBus.dispatch(StopListening) }
                .onFailure { Log.e("HomeViewModel", "Failed to stop listening", it) }
        }
    }

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    fun navigateToIntelligence() { viewModelScope.launch { _navigationEvents.emit("ai_settings") } }
    fun navigateToDevices() { viewModelScope.launch { _navigationEvents.emit("devices") } }
}
