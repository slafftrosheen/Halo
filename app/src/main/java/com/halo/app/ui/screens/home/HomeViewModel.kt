package com.halo.app.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.capture.LocalCaptureStore
import com.halo.app.ai.readRuntimeAiContextPolicy
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.app.ui.state.presenceFromSpeech
import com.halo.cognition.language.InteractionId
import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.runtime.cognition.context.CognitiveOperatingMode
import com.halo.runtime.cognition.conversation.ConversationTurn
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import com.halo.runtime.cognition.session.CognitiveSession
import com.halo.sdk.runtime.multimodal.MediaAcquisitionException
import com.halo.sdk.runtime.multimodal.MediaFailure
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TimelinePreviewEvent(val command: String, val status: String, val timestamp: Long)

data class SpeechRejectionNotice(
    val reasonText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val transcript: String? = null,
    val conversationId: String? = null,
    val outputFailure: Boolean = false
)

data class HomeUiState(
    val contextualText: String = "Ready when you are",
    val runtimeState: HaloRuntimeState = HaloRuntimeState.Idle,
    val recentEvents: List<TimelinePreviewEvent> = emptyList(),
    val activeProvider: String = "No provider",
    val providerLocality: String = "Unavailable",
    val activeProviderStatus: String = "Unavailable",
    val activeProviderState: ProviderStatus.State? = null,
    val activeProviderMessage: String? = null,
    val rejectionNotice: SpeechRejectionNotice? = null,
    val sessionState: CognitiveSessionState = CognitiveSessionState.Idle,
    val speechActive: Boolean = false,
    val speechProcessing: Boolean = false,
    val speechReady: Boolean = true,
    val microphonePermissionGranted: Boolean = true,
    val presentedResponse: String? = null,
    val presentedIdentity: PresentedAssistantResponse? = null,
    val conversationId: String? = null,
    /**
     * MINING-P2-001: committed structured presentation for the current answer, when the turn carried
     * one. Display only — [presentedResponse] remains the spoken and persisted text.
     */
    val structuredPresentation: com.halo.runtime.cognition.interaction.StructuredPresentation? = null,
    val orb: com.halo.app.ui.presence.HaloOrbPresentation = com.halo.app.ui.presence.HaloOrbPresentation(),
    val inputStatus: SpeechInputStatus = SpeechInputStatus(),
    val runtimeTaskStatus: String? = null,
    val runtimeWaitingCount: Int = 0,
    val runtimeWaitingState: com.halo.runtime.cognition.agent.AgentRunState? = null,
    val longTaskProgress: com.halo.runtime.cognition.interaction.StructuredPresentation? = null,
    val longTaskStatus: String? = null,
    val researchResult: Boolean = false,
    /** Journal-backed receipt belonging to the current conversation, if any. */
    val toolReceipt: HomeToolResultReceipt? = null,
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
            // A failed cognitive turn is terminal and must not hold the presence in
            // Recovering. Keep the error text visible while returning the orb and
            // microphone affordance to the actual speech runtime state.
            sessionState is CognitiveSessionState.Failed -> speechRuntimeState
            else -> speechRuntimeState
        }

        val contextualText = when (sessionState) {
            is CognitiveSessionState.PreparingContext -> "Preparing context..."
            is CognitiveSessionState.Reasoning -> "Reasoning..."
            is CognitiveSessionState.Streaming -> sessionState.text.ifBlank { "Responding…" }
            is CognitiveSessionState.ExecutingTool -> if (sessionState.toolId.startsWith("halo_ui_") ||
                sessionState.toolId in setOf("halo_read_screen", "halo_find_on_screen")) "Operating the current screen"
                else "Executing ${sessionState.toolId}..."
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

/** Immutable Home projection. Platform and repository reads run in the ViewModel's IO scope. */
data class HomeDashboardState(val assistantActive: Boolean = false,
    val nativeReady: Boolean = false, val quickTools: Set<String> = emptySet())

class HomeSourceDependencies(
    val visual: StateFlow<com.halo.app.capture.VisualAttachment?>,
    val providers: StateFlow<List<com.halo.app.ai.ProviderControlRow>>,
    val refresh: suspend () -> HomeDashboardState,
)

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
    private val runtimeProjection: com.halo.app.ui.state.RuntimeUiProjection? = null,
    private val notebook: com.halo.app.data.notebook.NotebookRepository? = null,
    private val conversations: com.halo.app.data.RoomConversationStore? = null,
    private val database: com.halo.app.data.AppDatabase? = null,
    val workspace: com.halo.app.data.WorkspaceContextStore? = null,
    val captures: com.halo.app.capture.LocalCaptureStore? = null,
    private val agentRuns: com.halo.runtime.cognition.agent.AgentRunStore? = null,
    private val dailyController: com.halo.app.daily.DailyAssistantController? = null,
    private val continuityOwner: com.halo.runtime.cognition.continuity.InteractionContinuityCoordinator? = null,
    private val endpointSource: StateFlow<List<com.halo.runtime.cognition.continuity.InteractionEndpointState>> = MutableStateFlow(emptyList()),
    private val captureProjection: com.halo.app.continuity.RecentCaptureProjection? = null,
    private val homeSources: HomeSourceDependencies? = null,
) : ViewModel() {
    private val _dashboard = MutableStateFlow(HomeDashboardState())
    val dashboard: StateFlow<HomeDashboardState> = _dashboard.asStateFlow()
    private val _visualAttachment = MutableStateFlow<com.halo.app.capture.VisualAttachment?>(null)
    val visualAttachment: StateFlow<com.halo.app.capture.VisualAttachment?> = _visualAttachment.asStateFlow()
    private val _providerRows = MutableStateFlow<List<com.halo.app.ai.ProviderControlRow>>(emptyList())
    val providerRows: StateFlow<List<com.halo.app.ai.ProviderControlRow>> = _providerRows.asStateFlow()
    val chatProviderId: StateFlow<String?> = aiProviderRouter?.selectedProviderId ?: MutableStateFlow(null)

    val dailyAssistant: StateFlow<com.halo.app.daily.DailyAssistantSnapshot> = dailyController?.snapshot
        ?: MutableStateFlow(com.halo.app.daily.DailyAssistantSnapshot())
    val continuity = continuityOwner?.state ?: MutableStateFlow(com.halo.runtime.cognition.continuity.InteractionContinuityState())
    val continuityEndpoints = endpointSource
    val recentCapture = captureProjection?.recent ?: MutableStateFlow<com.halo.app.continuity.RecentCapture?>(null)
    fun dismissRecentCapture() { captureProjection?.dismiss() }
    fun handoff(endpoint: com.halo.runtime.cognition.continuity.InteractionEndpoint) { viewModelScope.launch {
        val current = continuity.value
        current.interactionId?.let { continuityOwner?.handoff(it, endpoint, runId = current.runId) }
    } }

    val blockedRecovery = MutableStateFlow<com.halo.runtime.cognition.agent.recovery.AgentRecoveryBlocked?>(null)
    fun rebindBlockedRecovery() {
        val blocked = blockedRecovery.value ?: return
        blockedRecovery.value = null
        cognitiveSession.resume(com.halo.runtime.cognition.agent.AgentRunId(blocked.runId), continueInCurrentConversation = true)
    }
    fun cancelBlockedRecovery() { viewModelScope.launch {
        val blocked = blockedRecovery.value ?: return@launch
        ManualDependencyGraph.agentRunCoordinator.cancel(com.halo.runtime.cognition.agent.AgentRunId(blocked.runId), "Cancelled by user")
        blockedRecovery.value = null
    } }

    init {
        viewModelScope.launch { eventBus.observe(com.halo.runtime.cognition.agent.recovery.AgentRecoveryBlocked::class.java).collect {
            if (it.reason.startsWith("Conversation unavailable")) blockedRecovery.value = it
        } }
        homeSources?.let { sources ->
            viewModelScope.launch { sources.visual.collect { _visualAttachment.value = it } }
            viewModelScope.launch { sources.providers.collect { _providerRows.value = it } }
            refreshDashboard()
        }
    }
    fun refreshDashboard() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        dailyController?.refresh()
        homeSources?.let { _dashboard.value = it.refresh() }
    }
    fun recordDailyAction(action: com.halo.app.daily.DailyAssistantAction) { dailyController?.recordAction(action) }

    private val mapper = HomeUiMapper()
    private val listeningCommandMutex = Mutex()
    private val _listeningRequested = MutableStateFlow(false)
    val listeningRequested: StateFlow<Boolean> = _listeningRequested.asStateFlow()
    private val _publishedResponse = MutableStateFlow<PresentedAssistantResponse?>(null)
    private val _sessionPresentationEnabled = MutableStateFlow(true)
    private val textSubmissions = TextSubmissionTracker()
    val textSubmission = textSubmissions.state
    val conversationTurns get() = conversations?.turns ?: MutableStateFlow(emptyList<ConversationTurn>())
    private var lastProjectedResponseKey: String? = null
    val selectedMode = MutableStateFlow(CognitiveOperatingMode.AUTO)

    private val _speechRejectionNotice = MutableStateFlow<SpeechRejectionNotice?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        snapshotManager.snapshot,
        cognitiveSession.state,
        aiProviderRouter?.selectedProviderId ?: MutableStateFlow<String?>(null),
        aiProviderRouter?.selectedModelId ?: MutableStateFlow<String?>(null),
        aiProviderRouter?.diagnosticsFlow ?: MutableStateFlow(emptyList<AiProviderDiagnostic>()),
        _publishedResponse,
        _sessionPresentationEnabled,
        conversations?.turns ?: MutableStateFlow(emptyList<ConversationTurn>()),
        speechAvailability,
        microphonePermission,
        speechInput,
        runtimeProjection?.taskSummary ?: MutableStateFlow(
            com.halo.app.ui.state.RuntimeTaskSummary(0, 0)
        ),
        agentRuns?.observeRuns() ?: kotlinx.coroutines.flow.flowOf(emptyList<com.halo.runtime.cognition.agent.AgentRun>()),
        _speechRejectionNotice,
        conversations?.activeConversation ?: MutableStateFlow<String?>(null),
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val snapshot = args[0] as RuntimeSnapshot
        val sessionState = args[1] as CognitiveSessionState
        val selectedProviderId = args[2] as String?
        val selectedModelId = args[3] as String?
        @Suppress("UNCHECKED_CAST") val diagnostics = args[4] as List<AiProviderDiagnostic>
        val publishedResponse = args[5] as PresentedAssistantResponse?
        val sessionPresentationEnabled = args[6] as Boolean
        @Suppress("UNCHECKED_CAST") val turns = args[7] as List<com.halo.runtime.cognition.conversation.ConversationTurn>
        val speechReady = args[8] as Boolean
        val microphonePermissionGranted = args[9] as Boolean
        val input = args[10] as SpeechInputStatus
        val taskSummary = args[11] as com.halo.app.ui.state.RuntimeTaskSummary
        @Suppress("UNCHECKED_CAST") val runs = args[12] as List<com.halo.runtime.cognition.agent.AgentRun>
        val rejectionNotice = args[13] as SpeechRejectionNotice?
        val activeConversationId = conversations?.activeConversationId().orEmpty()
        val root = runs.filter { it.parentRunId == null && activeConversationId.isNotBlank() &&
            com.halo.runtime.cognition.continuity.RunContinuityResolver.conversationId(it) == activeConversationId
        }.maxByOrNull { it.updatedAt }
        val children = runs.filter { it.parentRunId == root?.id }
        val research = children.isNotEmpty() && root?.steps?.any {
            it is com.halo.runtime.cognition.agent.AgentStep.ToolInvocationPrepared && it.toolId in setOf("halo_research", "halo_delegate_task")
        } == true
        val phoneWorkflow = root?.let { com.halo.runtime.cognition.ui.UiWorkflowProjector.from(it) }
        val longTask = root?.takeIf { children.isNotEmpty() || phoneWorkflow != null }
        val presented = HomeResponseProjector.project(
            activeConversationId,
            sessionState,
            publishedResponse,
            turns,
            sessionPresentationEnabled && (continuity.value.conversationId == null || continuity.value.conversationId == activeConversationId),
        )
        presented?.let {
            val key = "${it.conversationId}:${it.turnId}:${it.source}:${it.text.length}"
            if (key != lastProjectedResponseKey) {
                lastProjectedResponseKey = key
                Log.i("HomeViewModel", "HALO_HOME_RESPONSE_PROJECTED conversationId=${it.conversationId} " +
                    "turnId=${it.turnId} interactionId=${it.interactionId} requestId=${it.requestId} " +
                    "source=${it.source} textLength=${it.text.length}")
            }
        }
        val belongsHere = continuity.value.conversationId == null || continuity.value.conversationId == activeConversationId
        val mapped = mapper.map(snapshot, if (belongsHere) sessionState else CognitiveSessionState.Idle, selectedProviderId, selectedModelId, diagnostics)
        val durableStatus = taskSummary.statusText.takeIf {
            sessionState == CognitiveSessionState.Idle &&
                !mapped.speechActive &&
                !mapped.speechProcessing
        }
        val phoneStatus = phoneWorkflow?.takeUnless { it.state in setOf(com.halo.runtime.cognition.ui.UiWorkflowPhase.COMPLETED,
            com.halo.runtime.cognition.ui.UiWorkflowPhase.FAILED) }?.let { workflow ->
            val phase = when (workflow.state) {
                com.halo.runtime.cognition.ui.UiWorkflowPhase.OBSERVING -> "Reading screen"
                com.halo.runtime.cognition.ui.UiWorkflowPhase.READY -> "Ready to act"
                com.halo.runtime.cognition.ui.UiWorkflowPhase.WAITING_FOR_APPROVAL -> "Needs your approval"
                com.halo.runtime.cognition.ui.UiWorkflowPhase.ACTING -> "Acting"
                com.halo.runtime.cognition.ui.UiWorkflowPhase.SETTLING -> "Waiting for screen"
                com.halo.runtime.cognition.ui.UiWorkflowPhase.VERIFYING -> "Verifying effect"
                else -> "Thinking about the next step"
            }
            listOfNotNull(phase, workflow.preview?.description).joinToString(" · ")
        }
        val contextualText = phoneStatus?.takeIf { !mapped.speechActive && !mapped.speechProcessing } ?: durableStatus ?: mapped.contextualText
        val sourceLabel = when (val route = input.turn?.route) {
            SpeechInputRoute.Phone, null -> "Built-in microphone"
            is SpeechInputRoute.Device -> route.displayName
                ?: when (route.kind) {
                    AudioDeviceKind.WEARABLE -> "Wearable microphone"
                    AudioDeviceKind.HEADSET -> "Headset microphone"
                    AudioDeviceKind.EARBUDS -> "Earbuds microphone"
                    AudioDeviceKind.USB_AUDIO -> "USB microphone"
                    AudioDeviceKind.HOST_EXTERNAL -> "External microphone"
                    AudioDeviceKind.UNKNOWN_EXTERNAL -> "External audio"
                }
        }
        mapped.copy(
                contextualText = if (sessionState is CognitiveSessionState.Completed && continuity.value.conversationId != null && continuity.value.conversationId != activeConversationId) "Reply saved to its original conversation" else contextualText,
                inputStatus = input,
                orb = com.halo.app.ui.presence.HaloOrbPresentation(
                    phase = mapped.runtimeState,
                    label = contextualText,
                    source = sourceLabel,
                    inputLevel = if (input.capturing) input.inputLevel else 0f,
                    failed = sessionState is CognitiveSessionState.Failed,
                    transition = (sessionState as? CognitiveSessionState.ExecutingTool)?.hashCode() ?: 0
                ),
                presentedResponse = presented?.text,
                presentedIdentity = presented,
                conversationId = activeConversationId,
                structuredPresentation = presented?.structured,
                speechReady = speechReady,
                microphonePermissionGranted = microphonePermissionGranted,
                runtimeTaskStatus = durableStatus,
                runtimeWaitingCount = taskSummary.waitingCount,
                runtimeWaitingState = taskSummary.primaryWaitingRun?.state.takeIf { durableStatus != null },
                researchResult = research && turns.lastOrNull()?.metadata?.get("runId") == root?.id?.value,
                longTaskProgress = longTask?.let { homeTaskProgress(it.steps) },
                longTaskStatus = phoneWorkflow?.let { "${it.state.name.lowercase().replace('_', ' ')} · ${it.preview?.description.orEmpty()} · ${it.actionCount} actions · ${it.scrollCount} scrolls" } ?: longTask?.let { "${children.count { child -> child.state == com.halo.runtime.cognition.agent.AgentRunState.COMPLETED }} / ${children.size} tasks complete · ${children.count { child -> child.state == com.halo.runtime.cognition.agent.AgentRunState.FAILED }} failed" },
                rejectionNotice = rejectionNotice?.takeIf { it.conversationId == null || it.conversationId == activeConversationId },
                toolReceipt = homeLatestToolReceipt(
                    runs, activeConversationId, continuity.value.runId?.value,
                ),
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), HomeUiState())

    init {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            eventBus.events().collect { event ->
                textSubmissions.onEvent(event)
            }
        }
        viewModelScope.launch {
            var previous: String? = null
            cognitiveSession.activeInteractionIds.collect { active ->
                val ended = previous
                previous = active
                val submission = textSubmission.value
                if (ended != null && ended != active && submission.interactionId == ended && submission.busy) {
                    val committed = submission.conversationId?.let { conversations?.turnsForConversation(it) }
                        .orEmpty().any { it.metadata["interactionId"] == ended }
                    textSubmissions.ownerEnded(ended, committed)
                }
            }
        }
        viewModelScope.launch {
            eventBus.observe(SpeechRejected::class.java).collect { rejected ->
                _listeningRequested.value = false
                val friendlyReason = when (rejected.reason) {
                    "REPETITIVE_HALLUCINATION" -> "repetitive recognition"
                    "INSUFFICIENT_VOICED_AUDIO" -> "audio was too quiet or short"
                    "NO_SPEECH" -> "no speech detected"
                    "LOW_RECOGNIZER_CONFIDENCE" -> "low recognition confidence"
                    "PUNCTUATION_ONLY" -> "punctuation only"
                    "LANGUAGE_MISMATCH" -> "language mismatch"
                    "TRANSCRIPT_REVIEW" -> "Check the recognized words before sending"
                    "INPUT_BUSY" -> "Speech input is finishing its previous transaction. Try again shortly."
                    "CANCELLED" -> "Input interrupted. You can try again or type your question."
                    else -> rejected.reason.lowercase().replace('_', ' ')
                }
                _speechRejectionNotice.value = SpeechRejectionNotice(friendlyReason,
                    transcript = rejected.text.takeIf { it.isNotBlank() }, conversationId = rejected.input?.conversationId)
            }
        }
        viewModelScope.launch {
            eventBus.observe(com.halo.engines.speech.RecognitionFailed::class.java).collect { failed ->
                if (speechInput.value.turn?.captureId?.let { it != failed.id } == true) return@collect
                _listeningRequested.value = false
                if (_speechRejectionNotice.value?.transcript == null) {
                    _speechRejectionNotice.value = SpeechRejectionNotice(failed.reason, conversationId = speechInput.value.turn?.conversationId)
                }
            }
        }
        viewModelScope.launch {
            eventBus.observe(TtsFailed::class.java).collect { failed ->
                if (failed.interactionId == null) return@collect // Settings tests own their own result.
                _speechRejectionNotice.value = SpeechRejectionNotice("Speech output failed: ${failed.message}. Your conversation reply is kept.",
                    conversationId = continuity.value.conversationId, outputFailure = true)
            }
        }
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
                val conversationId = response.conversationId ?: return@collect
                _publishedResponse.value = PresentedAssistantResponse(
                    conversationId = conversationId,
                    requestId = response.transactionId,
                    text = response.text,
                    source = ResponsePresentationSource.SESSION_COMPLETED,
                    continuationPending = response.continuationPending,
                )
            }
        }

        viewModelScope.launch {
            cognitiveSession.state.collect { state ->
                if (state is CognitiveSessionState.PreparingContext ||
                    state is CognitiveSessionState.Reasoning ||
                    state is CognitiveSessionState.Streaming ||
                    state is CognitiveSessionState.ExecutingTool
                ) _sessionPresentationEnabled.value = true
            }
        }

        viewModelScope.launch {
            val store = conversations ?: return@launch
            store.turns.collect { turns ->
                val conversationId = store.activeConversationId().orEmpty()
                turns.lastOrNull { it.conversationId == conversationId && it.assistantText.isNotBlank() }
                    ?.let { turn ->
                        Log.i("HomeViewModel", "HALO_HOME_RESPONSE_OBSERVED conversationId=$conversationId " +
                            "turnId=${turn.id} interactionId=${turn.metadata["interactionId"]} " +
                            "requestId=${turn.metadata["requestId"]} source=COMMITTED_CONVERSATION " +
                            "textLength=${turn.assistantText.length}")
                    }
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

    val contextChips get() = ManualDependencyGraph.interactionContext.chips
    val contextNotice get() = ManualDependencyGraph.interactionContext.message
    val saveReview = MutableStateFlow<com.halo.app.context.ContextResultSaveReview?>(null)
    val composerFocus = MutableStateFlow(0)
    val nextActions: StateFlow<List<com.halo.runtime.cognition.context.ContextualNextAction>> = combine(
        conversations?.turns ?: MutableStateFlow(emptyList<ConversationTurn>()), cognitiveSession.state
    ) { turns,state ->
        val conversation = conversations?.activeConversationId()
        val turn = turns.lastOrNull { it.conversationId == conversation && it.assistantText.isNotBlank() }
        if(turn == null || state is CognitiveSessionState.PreparingContext || state is CognitiveSessionState.Reasoning ||
            state is CognitiveSessionState.Streaming || state is CognitiveSessionState.ExecutingTool || state is CognitiveSessionState.Failed) emptyList()
        else com.halo.runtime.cognition.context.ContextualNextActionProjector.project(
            com.halo.runtime.cognition.context.ContextualResultState(
                com.halo.runtime.cognition.context.ContextReference.ConversationTurn(turn.conversationId,turn.id),
                completed=true,meaningful=turn.assistantText.length >= 120 && turn.assistantText.split(' ').size >= 20,
                selectedSource=turn.metadata["contextWorkspaceObjectId"]?.let {
                    com.halo.runtime.cognition.context.ContextReference.WorkspaceObject(it,turn.metadata["contextWorkspaceVersion"]) },
                sourceAvailable=turn.metadata["contextWorkspaceObjectId"] != null,
                expiresAtMs=turn.timestamp + 120_000),System.currentTimeMillis())
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(0),emptyList())
    fun selectContext(id: String) = viewModelScope.launch {
        try { ManualDependencyGraph.visualAttachments.remove(); ManualDependencyGraph.interactionContext.selectWorkspace(id) }
        catch(error: Exception) { captureMessage.value = error.message ?: "Source unavailable" }
    }
    fun removeContext(category: com.halo.runtime.cognition.context.ContextCategory) = viewModelScope.launch {
        if(category == com.halo.runtime.cognition.context.ContextCategory.VISUAL) ManualDependencyGraph.visualAttachments.remove()
        ManualDependencyGraph.interactionContext.remove(category)
    }
    fun useUpdatedContext(id: String) = selectContext(id)
    fun performNextAction(action: com.halo.runtime.cognition.context.ContextualNextAction) = viewModelScope.launch {
        val target = action.target
        (action.sourceReference as? com.halo.runtime.cognition.context.ContextReference.ConversationTurn)?.let { ref ->
            if (conversations?.turnsForConversation(ref.conversationId).orEmpty().none { it.id == ref.turnId }) {
                captureMessage.value = "That reply is no longer available. Choose a current reply."; return@launch
            }
        }
        if(action.expiresAtMs?.let { System.currentTimeMillis() >= it } == true) { captureMessage.value = "This action expired. Select the source again."; return@launch }
        if(target is com.halo.runtime.cognition.context.ContextualNextActionTarget.ReviewSaveResult) {
            if(conversations?.activeConversationId() != target.conversationId) return@launch
            saveReview.value = ManualDependencyGraph.contextResultSave.review(target.conversationId,target.turnId)
            if(saveReview.value == null) captureMessage.value = "Result unavailable"
        } else {
            val source = action.sourceReference as? com.halo.runtime.cognition.context.ContextReference.WorkspaceObject
            if(source != null && source.version != null && ManualDependencyGraph.workspaceContext.versionFor(source.objectId) != source.version) {
                captureMessage.value = "Original version changed. Select the updated source again."; return@launch
            }
            ManualDependencyGraph.interactionContext.navigate(target)
        }
    }
    fun confirmSaveResult() { val review = saveReview.value ?: return
        viewModelScope.launch { try {
            val id = ManualDependencyGraph.contextResultSave.confirm(review)
            saveReview.value = null
            ManualDependencyGraph.interactionContext.navigate(com.halo.runtime.cognition.context.ContextualNextActionTarget.OpenSource(id))
        } catch(error: Exception) { captureMessage.value = error.message ?: "Could not save this result" } }
    }
    fun submitText(text: String) {
        val normalized = text.trim()
        val interactionId = UUID.randomUUID().toString()
        val conversationAtTap = conversations?.activeConversationId()
        if (!textSubmissions.begin(interactionId, conversationAtTap, normalized)) return

        // Text enters the same interpretation/reasoning pipeline as recognized
        // speech, but it remains a TEXT/USER observation. Response delivery is
        // carried by the transaction, so keyboard turns never need a global TTS mute.
        _publishedResponse.value = null
        viewModelScope.launch {
            runCatching {
                val timestamp = System.currentTimeMillis()
                conversations?.awaitReady()
                val conversationAtSend = conversationAtTap ?: conversations?.activeConversationId()
                    ?: error("Conversation is not ready. Try again.")
                check(conversations?.isConversationAvailable(conversationAtSend) == true) { "Conversation unavailable" }
                textSubmissions.resolveConversation(interactionId, conversationAtSend)
                val imageArg = ManualDependencyGraph.interactionContext.owner.current(conversationAtSend.orEmpty())
                    .any { it.category == com.halo.runtime.cognition.context.ContextCategory.VISUAL }

                // The explicit image stays selected for follow-up until Remove/Clear/expiry.
                viewModelScope.launch {
                    // Admission only, not an inference deadline. An owned runtime turn is never killed here.
                    delay(15_000)
                    val current = textSubmission.value
                    if (current.interactionId == interactionId && current.busy && !cognitiveSession.ownsInteraction(interactionId)) {
                        if (current.accepted) {
                            val committed = conversations?.turnsForConversation(conversationAtSend).orEmpty().any { it.metadata["interactionId"] == interactionId }
                            textSubmissions.ownerEnded(interactionId, committed)
                        } else {
                            textSubmissions.admissionExpired(interactionId)
                            cognitiveSession.cancelInteraction(interactionId)
                        }
                    }
                }

                if (!imageArg) {
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
                                    language = ManualDependencyGraph.appLanguagePreferences.language.value.tag
                                ),
                                interactionId = interactionId,
                                metadata = mapOf("inputModality" to "TEXT", "input" to "keyboard", "mode" to selectedMode.value.name,
                                    "interactionId" to interactionId, "hasAssistantImage" to imageArg.toString(),
                                    "explicitImageReference" to imageArg.toString()) +
                                    listOfNotNull(conversationAtSend?.let { "conversationId" to it }).toMap()
                            )
                        )
                    )
                    // Interpretation owns conversation/action routing. Submitting here too
                    // races that owner and turns ordinary chat into an optional-tool request.
                    return@runCatching
                }

                // An explicitly attached image has one direct, tool-free owner.
                val request = AiRequest(
                    text = normalized,
                    image = null,
                    toolUseMode = ToolUseMode.NONE
                )
                cognitiveSession.submit(request = request, responseDelivery = ResponseDelivery.VISUAL_ONLY,
                    metadata = mapOf("inputModality" to "TEXT", "input" to "keyboard", "mode" to selectedMode.value.name, "interactionId" to interactionId,
                        "hasAssistantImage" to imageArg.toString(), "explicitImageReference" to imageArg.toString()) + listOfNotNull(conversationAtSend?.let { "conversationId" to it }).toMap())
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) {
                    textSubmissions.cancel(interactionId)
                    cognitiveSession.cancelInteraction(interactionId)
                    throw it
                }
                textSubmissions.fail(interactionId, it.message ?: "Could not submit this message. Please try again.")
                captureMessage.value = if (it is com.halo.sdk.runtime.multimodal.MediaAcquisitionException) it.message
                    else if (com.halo.app.di.ManualDependencyGraph.visualAttachments.state.value != null)
                        "Vision unavailable for this image. Configure a Vision model or remove the attachment."
                    else "Could not submit this message. Please try again."
                Log.e("HomeViewModel", "Text submission failed", it)
            }
        }
    }

    suspend fun preparePhoneCapture(): LocalCaptureStore.PendingCapture? {
        val review = ManualDependencyGraph.contextualCapture
        if(review.state.value != null) {
            review.reopen()
            captureMessage.value = "Finish or discard the current capture before taking another photo. Saved images stay in Notebook."
            return null
        }
        return captures?.preparePhoneCapture()
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

    val captureQuestionDraft = MutableStateFlow<String?>(null)
    val captureMessage = MutableStateFlow<String?>(null)

    fun handlePhoneCaptureResult(path: String, received: Boolean) {
        viewModelScope.launch {
            val store = captures ?: return@launch
            val file = java.io.File(path)
            if (!received) {
                store.discardPendingCapture(file)
                return@launch
            }
            receiveCaptureNow(store, file)
        }
    }

    fun discardPhoneCapture(path: String) {
        viewModelScope.launch { captures?.discardPendingCapture(java.io.File(path)) }
    }

    private suspend fun receiveCaptureNow(store: LocalCaptureStore, file: java.io.File) {
        try {
            com.halo.app.di.ManualDependencyGraph.contextualCapture.receivePhone(file, true)
            captureMessage.value = "Image ready for review. No analysis or permanent save has occurred."
        } catch (error: Exception) {
            captureMessage.value = error.message ?: "Capture failed"
        }
    }

    fun removeVisualAttachment() { viewModelScope.launch { com.halo.app.di.ManualDependencyGraph.visualAttachments.removeSelection() } }

    fun saveVisualAttachment() { com.halo.app.di.ManualDependencyGraph.contextualCapture.action(com.halo.app.capture.CaptureAction.SAVE) }

    fun attachSharedImage(uri: android.net.Uri) = reviewSelectedImage(uri, com.halo.app.capture.CaptureSource.SHARED_IMAGE)
    fun attachImportedImage(uri: android.net.Uri) = reviewSelectedImage(uri, com.halo.app.capture.CaptureSource.IMPORTED_IMAGE)
    private fun reviewSelectedImage(uri: android.net.Uri, source: com.halo.app.capture.CaptureSource) = viewModelScope.launch {
        try { ManualDependencyGraph.contextualCapture.receiveUri(uri, source) }
        catch (_: Exception) { captureMessage.value = "Image could not be reviewed. Finish the current capture first." }
    }

    fun createNote() { viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val graph = com.halo.app.di.ManualDependencyGraph
        val id = UUID.randomUUID().toString()
        val run = graph.agentRunCoordinator.start(com.halo.runtime.cognition.agent.AgentRequest(
            com.halo.cognition.language.InteractionId(id), "Create an empty note", kind = com.halo.runtime.cognition.agent.AgentRunKind.NATIVE))
        graph.agentRunCoordinator.transition(run, com.halo.runtime.cognition.agent.AgentRunState.EXECUTING)
        try {
            val result = graph.skillExecutionService.execute(run, com.halo.cognition.language.InteractionId(id), id,
                listOf(ToolCall(id, "halo_create_empty_note", emptyMap())), true, setOf("halo_create_empty_note"))
            if (result is com.halo.runtime.cognition.turn.ToolTurnResult.Completed) {
                val output = result.toolMessages.joinToString("\n") { it.content }
                if ("Created note: " in output) {
                    graph.agentRunCoordinator.complete(run)
                    val noteId = output.substringAfter("Created note: ").substringBefore(" -")
                    _navigationEvents.emit("notebook/note/$noteId")
                } else { graph.agentRunCoordinator.fail(run, "Note creation rejected"); captureMessage.value = "Note creation needs permission or setup" }
            } else captureMessage.value = "Review note creation in the approval panel"
        } catch (cancelled: kotlinx.coroutines.CancellationException) { graph.agentRunCoordinator.cancel(run, "Cancelled"); throw cancelled }
          catch (_: Exception) { graph.agentRunCoordinator.fail(run, "Note creation failed"); captureMessage.value = "Could not create note" }
    } }
    fun saveResponse(response: PresentedAssistantResponse) { viewModelScope.launch {
        val turnId = response.turnId ?: return@launch
        saveReview.value = ManualDependencyGraph.contextResultSave.review(response.conversationId, turnId)
    } }
    private fun cancelVisibleSubmission(stopActiveText: Boolean = false) {
        val conversation = conversations?.activeConversationId()
        textSubmission.value.takeIf { it.busy && (stopActiveText || it.conversationId == conversation) }?.interactionId?.let {
            textSubmissions.cancel(it); cognitiveSession.cancelInteraction(it)
        }
        continuity.value.takeIf { it.conversationId == conversation }?.interactionId?.let(cognitiveSession::cancelInteraction)
    }
    fun newConversation() {
        val previous = conversations?.activeConversationId()
        _sessionPresentationEnabled.value = false
        cancelVisibleSubmission(stopActiveText = true)
        viewModelScope.launch {
            previous?.let { ManualDependencyGraph.interactionContext.clearConversation(it) }
            ManualDependencyGraph.visualAttachments.remove()
            conversations?.newConversation(); _publishedResponse.value = null; saveReview.value = null
            textSubmissions.reset(); ManualDependencyGraph.interactionContext.refresh()
        }
    }

    fun clearConversation() {
        val conversation = conversations?.activeConversationId()
        _sessionPresentationEnabled.value = false
        cancelVisibleSubmission()
        cognitiveSession.clearConversation()
        _publishedResponse.value = null
        saveReview.value = null
        viewModelScope.launch {
            conversation?.let { ManualDependencyGraph.interactionContext.clearConversation(it) }
            ManualDependencyGraph.visualAttachments.remove()
        }
    }

    fun cancelSession() { com.halo.app.ui.fx.HaloMotionEvents.stop(continuity.value.interactionId ?: textSubmission.value.interactionId); com.halo.app.ui.components.VoiceFieldCancellation.stop(); stopSpeechOutput(); cancelVisibleSubmission(stopActiveText = true) }

    fun cancelVoiceInput() {
        val turn = speechInput.value.turn ?: run { stopListening(); return }
        _listeningRequested.value = false
        cognitiveSession.cancelInteraction(turn.captureId)
        _speechRejectionNotice.value = SpeechRejectionNotice("Input interrupted. You can try again or type your question.", conversationId = turn.conversationId)
        viewModelScope.launch {
            listeningCommandMutex.withLock { ManualDependencyGraph.audioPerceptionEngine.cancelExternalInput(turn.captureId) }
        }
    }

    fun stopSpeakingAndListen() {
        val conversationAtTap = conversations?.activeConversationId()
        cognitiveSession.cancel()
        _publishedResponse.value = null
        _listeningRequested.value = true
        viewModelScope.launch {
            listeningCommandMutex.withLock {
                runCatching {
                    stopSpeechOutput()
                    commandBus.dispatch(StartListeningWithActivation(SpeechActivationKind.EXPLICIT_USER, conversationAtTap))
                }.onFailure {
                    _listeningRequested.value = false
                    Log.e("HomeViewModel", "Failed to stop speaking and listen", it)
                }
            }
        }
    }

    fun toggleListening() {
        val conversationAtTap = conversations?.activeConversationId()
        val shouldStart = !_listeningRequested.value
        if (shouldStart) _publishedResponse.value = null
        _listeningRequested.value = shouldStart
        viewModelScope.launch {
            listeningCommandMutex.withLock {
                val command = if (shouldStart) StartListeningWithActivation(SpeechActivationKind.EXPLICIT_USER, conversationAtTap) else StopListening
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
        val conversationAtTap = conversations?.activeConversationId()
        _speechRejectionNotice.value = null
        _publishedResponse.value = null
        _listeningRequested.value = true
        viewModelScope.launch {
            listeningCommandMutex.withLock {
                runCatching {
                    cognitiveSession.cancel()
                    stopSpeechOutput()
                    commandBus.dispatch(StartListeningWithActivation(SpeechActivationKind.EXPLICIT_USER, conversationAtTap))
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

    fun dismissSpeechNotice() { _speechRejectionNotice.value = null }

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    fun navigateToIntelligence() { viewModelScope.launch { _navigationEvents.emit("ai_settings") } }
    fun navigateToVoice() { viewModelScope.launch { _navigationEvents.emit("voice_settings") } }
    fun navigateToDevices() { viewModelScope.launch { _navigationEvents.emit("devices") } }
}
