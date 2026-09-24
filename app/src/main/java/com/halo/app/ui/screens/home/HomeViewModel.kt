package com.halo.app.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.app.ui.state.presenceFromSpeech
import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import com.halo.runtime.cognition.session.CognitiveSession
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TimelinePreviewEvent(val command: String, val status: String, val timestamp: Long)

data class HomeUiState(
    val contextualText: String = "Ready when you are",
    val runtimeState: HaloRuntimeState = HaloRuntimeState.Idle,
    val recentEvents: List<TimelinePreviewEvent> = emptyList(),
    val activeProvider: String = "No provider",
    val providerLocality: String = "Unavailable",
    val activeProviderStatus: String = "Unavailable",
    val activeProviderState: ProviderStatus.State? = null,
    val activeProviderMessage: String? = null,
    val sessionState: CognitiveSessionState = CognitiveSessionState.Idle,
    val speechActive: Boolean = false,
    val speechProcessing: Boolean = false,
    val speechReady: Boolean = true,
    val microphonePermissionGranted: Boolean = true,
    val presentedResponse: String? = null,
    val orb: com.halo.app.ui.presence.HaloOrbPresentation = com.halo.app.ui.presence.HaloOrbPresentation(),
    val inputStatus: SpeechInputStatus = SpeechInputStatus()
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
        // Thinking + no cognitive transaction yet is the buffered STT decode
        // boundary. Keep it separate from microphone ownership and LLM reasoning.
        val speechIsProcessing =
            snapshot.speech == SpeechSessionState.Thinking &&
                sessionState == CognitiveSessionState.Idle

        val uiRuntimeState = when {
            snapshot.speech == SpeechSessionState.Speaking -> HaloRuntimeState.Speaking
            speechIsActive -> speechRuntimeState
            sessionState is CognitiveSessionState.PreparingContext -> HaloRuntimeState.Reasoning
            sessionState is CognitiveSessionState.Reasoning -> HaloRuntimeState.Reasoning
            sessionState is CognitiveSessionState.Streaming -> HaloRuntimeState.Reasoning
            sessionState is CognitiveSessionState.ExecutingTool -> HaloRuntimeState.Executing
            sessionState is CognitiveSessionState.Failed -> HaloRuntimeState.Recovering
            else -> speechRuntimeState
        }

        val contextualText = when (sessionState) {
            is CognitiveSessionState.PreparingContext -> "Preparing context..."
            is CognitiveSessionState.Reasoning -> "Reasoning..."
            is CognitiveSessionState.Streaming -> sessionState.text.ifBlank { "Responding…" }
            is CognitiveSessionState.ExecutingTool -> "Executing ${sessionState.toolId}..."
            is CognitiveSessionState.Completed -> sessionState.response.text.ifBlank { "Completed" }
            is CognitiveSessionState.Failed -> "Error: ${sessionState.message}"
            CognitiveSessionState.Idle -> when {
                speechIsProcessing -> "Transcribing speech…"
                else -> when (uiRuntimeState) {
                    HaloRuntimeState.Idle -> "Ready when you are"
                    HaloRuntimeState.Capturing -> "Listening"
                    HaloRuntimeState.Recognizing -> "Understanding"
                    HaloRuntimeState.Reasoning -> "Reasoning"
                    HaloRuntimeState.Speaking -> "Speaking"
                    else -> "Processing..."
                }
            }
        }
        val selectedDiagnostic = diagnostics.find { it.providerId == selectedProviderId }
        val snapshotProvider = snapshot.providers.find { it.isSelected }
        val activeProviderText = selectedDiagnostic?.let { diagnostic ->
            val modelSuffix = selectedModelId?.let { " · $it" }
                ?: diagnostic.selectedModelId?.let { " · $it" }
                ?: ""
            "${diagnostic.displayName}$modelSuffix"
        } ?: snapshotProvider?.let { provider ->
            "${provider.name}${provider.activeModel?.let { " · $it" } ?: ""}"
        } ?: "No provider"
        val providerStatus = selectedDiagnostic?.state?.name
            ?: snapshotProvider?.status
            ?: "Unavailable"
        val providerState = selectedDiagnostic?.state ?: snapshotProvider?.status?.let { raw ->
            ProviderStatus.State.values().firstOrNull { state ->
                state.name.equals(raw.trim(), ignoreCase = true)
            }
        }
        return HomeUiState(
            contextualText = contextualText,
            runtimeState = uiRuntimeState,
            recentEvents = snapshot.timeline.map { TimelinePreviewEvent(it.title, it.status, it.timestamp.toEpochMilli()) },
            activeProvider = activeProviderText,
            providerLocality = selectedDiagnostic?.let { if (it.local) "Local" else "Network" } ?: "Unavailable",
            activeProviderStatus = providerStatus,
            activeProviderState = providerState,
            activeProviderMessage = selectedDiagnostic?.message,
            sessionState = sessionState,
            speechActive = speechIsActive,
            speechProcessing = speechIsProcessing
        )
    }

    override fun map(snapshot: RuntimeSnapshot): HomeUiState = map(snapshot, CognitiveSessionState.Idle)
}

class HomeViewModel(
    private val snapshotManager: RuntimeSnapshotSource,
    private val commandBus: CommandBus,
    private val eventBus: EventBus,
    private val cognitiveSession: CognitiveSession,
    private val stopSpeechOutput: () -> Unit,
    private val aiProviderRouter: AiProviderRouter? = null,
    private val speechAvailability: StateFlow<Boolean> = MutableStateFlow(true),
    private val microphonePermission: StateFlow<Boolean> = MutableStateFlow(true),
    private val networkRevision: StateFlow<Long> = MutableStateFlow(0L),
    private val speechInput: StateFlow<SpeechInputStatus> = MutableStateFlow(SpeechInputStatus()),
    private val notebook: com.halo.app.data.notebook.NotebookRepository? = null,
    private val conversations: com.halo.app.data.RoomConversationStore? = null,
    private val database: com.halo.app.data.AppDatabase? = null,
    val workspace: com.halo.app.data.WorkspaceContextStore? = null,
    val captures: com.halo.app.capture.LocalCaptureStore? = null
) : ViewModel() {
    private val mapper = HomeUiMapper()
    private val listeningCommandMutex = Mutex()
    private val _listeningRequested = MutableStateFlow(false)
    val listeningRequested: StateFlow<Boolean> = _listeningRequested.asStateFlow()
    private val _presentedResponse = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        snapshotManager.snapshot,
        cognitiveSession.state,
        aiProviderRouter?.selectedProviderId ?: MutableStateFlow<String?>(null),
        aiProviderRouter?.selectedModelId ?: MutableStateFlow<String?>(null),
        aiProviderRouter?.diagnosticsFlow ?: MutableStateFlow(emptyList<AiProviderDiagnostic>()),
        _presentedResponse,
        speechAvailability,
        microphonePermission,
        speechInput
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val snapshot = args[0] as RuntimeSnapshot
        val sessionState = args[1] as CognitiveSessionState
        val selectedProviderId = args[2] as String?
        val selectedModelId = args[3] as String?
        @Suppress("UNCHECKED_CAST") val diagnostics = args[4] as List<AiProviderDiagnostic>
        val presentedResponse = args[5] as String?
        val speechReady = args[6] as Boolean
        val microphonePermissionGranted = args[7] as Boolean
        val input = args[8] as SpeechInputStatus
        val mapped = mapper.map(snapshot, sessionState, selectedProviderId, selectedModelId, diagnostics)
        mapped.copy(
                inputStatus = input,
                orb = com.halo.app.ui.presence.HaloOrbPresentation(
                    phase = mapped.runtimeState,
                    label = mapped.contextualText,
                    source = (input.turn?.route as? SpeechInputRoute.Wearable)?.displayName ?: "Phone",
                    inputLevel = if (input.capturing) input.inputLevel else 0f,
                    failed = sessionState is CognitiveSessionState.Failed,
                    transition = (sessionState as? CognitiveSessionState.ExecutingTool)?.hashCode() ?: 0
                ),
                presentedResponse = presentedResponse,
                speechReady = speechReady,
                microphonePermissionGranted = microphonePermissionGranted
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), HomeUiState())

    init {
        // The UI command state is deliberately separate from the runtime
        // snapshot. A second tap can arrive before the asynchronous snapshot
        // has rendered the first tap; without this local intent state both taps
        // become StartListening commands.
        viewModelScope.launch {
            snapshotManager.snapshot.collect { snapshot ->
                when (snapshot.speech) {
                    SpeechSessionState.Thinking,
                    SpeechSessionState.Speaking,
                    SpeechSessionState.Recovering -> _listeningRequested.value = false
                    else -> Unit
                }
            }
        }

        viewModelScope.launch {
            eventBus.observe(AssistantResponsePublished::class.java).collect { response ->
                _presentedResponse.value = response.text
            }
        }

        viewModelScope.launch {
            networkRevision
                .drop(1)
                .collectLatest {
                    // Connectivity callbacks can arrive in bursts while Wi-Fi/cellular
                    // hand over. Coalesce them before doing provider network I/O.
                    delay(600L)
                    refreshProviderDiagnosticsNow()
                }
        }
    }

    fun submitText(text: String) {
        val normalized = text.trim()
        if (normalized.isBlank()) return

        // Text enters the same interpretation/reasoning pipeline as recognized
        // speech, but it remains a TEXT/USER observation. Response delivery is
        // carried by the transaction, so keyboard turns never need a global TTS mute.
        _presentedResponse.value = null
        viewModelScope.launch {
            runCatching {
                val timestamp = System.currentTimeMillis()
                eventBus.publish(
                    ObservationCreated(
                        observation = Observation(
                            id = ObservationId(UUID.randomUUID().toString()),
                            timestamp = timestamp,
                            modality = Modality.TEXT,
                            source = Source.USER,
                            confidence = 1.0f,
                            payload = SpeechRecognizedPayload(
                                transcript = normalized,
                                language = Locale.getDefault().toLanguageTag()
                            ),
                            metadata = mapOf("input" to "keyboard")
                        )
                    )
                )
            }.onFailure {
                Log.e("HomeViewModel", "Text submission failed", it)
            }
        }
    }

    fun refreshProviderDiagnostics() {
        viewModelScope.launch {
            refreshProviderDiagnosticsNow()
        }
    }

    private suspend fun refreshProviderDiagnosticsNow() {
        val router = aiProviderRouter ?: return
        runCatching { router.diagnostics() }
            .onFailure { error ->
                Log.w("HomeViewModel", "Provider diagnostics refresh failed", error)
            }
    }

    val captureMessage = MutableStateFlow<String?>(null)
    fun receiveCapture(file: java.io.File) { viewModelScope.launch {
        try {
            captures?.receive(file)
            val target = aiProviderRouter?.resolveExecutionTarget(AiRequest(text = ""))
            captureMessage.value = if (target?.providerCapabilities?.contains(AiCapability.IMAGE) == true)
                "Saved to Inbox. Ask Halo about the image to use it in this turn."
                else "Saved to Inbox. Vision is unavailable for the selected model."
        } catch (error: Exception) { captureMessage.value = error.message ?: "Capture failed" }
    } }

    fun createNote() { viewModelScope.launch {
        val note = notebook?.createNote() ?: return@launch
        _navigationEvents.emit("notebook/note/${note.id}")
    } }
    fun saveResponse(text: String) { viewModelScope.launch {
        val note = notebook?.createNote(text, com.halo.app.data.notebook.WorkspaceSource.IMPORT) ?: return@launch
        val turn = conversations?.turns?.value?.lastOrNull()
        if (turn != null) database?.workspaceContextDao()?.provenance(com.halo.app.data.ObjectProvenance(
            note.id, turn.conversationId, turn.id, org.json.JSONObject(turn.metadata).toString()))
        _navigationEvents.emit("notebook/note/${note.id}")
    } }
    fun newConversation() { viewModelScope.launch { conversations?.newConversation(); _presentedResponse.value = null } }

    fun clearConversation() {
        cognitiveSession.clearConversation()
        _presentedResponse.value = null
    }

    fun cancelSession() { cognitiveSession.cancel() }

    fun stopSpeakingAndListen() {
        cognitiveSession.cancel()
        _presentedResponse.value = null
        _listeningRequested.value = true
        viewModelScope.launch {
            listeningCommandMutex.withLock {
                runCatching {
                    stopSpeechOutput()
                    commandBus.dispatch(StartListening)
                }.onFailure {
                    _listeningRequested.value = false
                    Log.e("HomeViewModel", "Failed to stop speaking and listen", it)
                }
            }
        }
    }

    fun toggleListening() {
        val shouldStart = !_listeningRequested.value
        if (shouldStart) _presentedResponse.value = null
        _listeningRequested.value = shouldStart
        viewModelScope.launch {
            listeningCommandMutex.withLock {
                val command = if (shouldStart) StartListening else StopListening
                runCatching {
                    if (shouldStart) { cognitiveSession.cancel(); stopSpeechOutput() }
                    commandBus.dispatch(command)
                }
                    .onFailure {
                        if (shouldStart) _listeningRequested.value = false
                        Log.e("HomeViewModel", "Failed to dispatch speech command", it)
                    }
            }
        }
    }

    fun startListening() {
        _presentedResponse.value = null
        _listeningRequested.value = true
        viewModelScope.launch {
            listeningCommandMutex.withLock {
                runCatching {
                    cognitiveSession.cancel()
                    stopSpeechOutput()
                    commandBus.dispatch(StartListening)
                }
                    .onFailure {
                        _listeningRequested.value = false
                        Log.e("HomeViewModel", "Failed to start listening", it)
                    }
            }
        }
    }

    fun stopListening() {
        _listeningRequested.value = false
        viewModelScope.launch {
            listeningCommandMutex.withLock {
                runCatching { commandBus.dispatch(StopListening) }
                    .onFailure { Log.e("HomeViewModel", "Failed to stop listening", it) }
            }
        }
    }

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    fun navigateToIntelligence() { viewModelScope.launch { _navigationEvents.emit("ai_settings") } }
    fun navigateToVoice() { viewModelScope.launch { _navigationEvents.emit("voice_settings") } }
    fun navigateToDevices() { viewModelScope.launch { _navigationEvents.emit("devices") } }
}
