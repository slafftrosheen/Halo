package com.halo.app.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.icons.HaloGlyph
import com.halo.host.android.assistant.AndroidAssistantRoleController
import com.halo.host.android.assistant.AssistantIntegrationState
import com.halo.host.android.assistant.AssistantRuntimeStateStore
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.LiteRtModelRuntimeStatus
import com.halo.providers.litert.LiteRtToolChannelDiagnostics
import com.halo.providers.litert.LiteRtToolQualification
import com.halo.providers.litert.models.LiteRtModelManager
import com.halo.providers.litert.models.LocalModelValidation
import com.halo.providers.litert.models.ModelVerificationResult
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.ProviderAuthState
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.RemoteProviderPresenceRegistry
import com.halo.sdk.provider.RemoteProviderPresets
import com.halo.sdk.provider.RemoteProviderIds
import com.halo.runtime.observer.InferenceLedger
import com.halo.sdk.provider.AiWorkloadKind
import com.halo.sdk.provider.AiWorkloadSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

enum class CheckStatus { READY, NOT_CONFIGURED, BLOCKED, RUNNING, PASSED, FAILED, UNSUPPORTED }

data class SystemCheckItem(
    val id: String,
    val name: String,
    val glyph: HaloGlyph,
    val status: CheckStatus,
    val reason: String,
    val durationMs: Long? = null,
    /**
     * MINING-P0-001 compact technical detail for System Check / Model Browser only. Never contains
     * prompts, generated text, tool argument values or secrets.
     */
    val detail: String? = null,
)

data class SystemCheckSection(
    val id: String,
    val title: String,
    val items: List<SystemCheckItem>
)

data class SystemCheckUiState(
    val sections: List<SystemCheckSection> = emptyList(),
    val summaryReport: String = "",
    val localAiDiagnostics: String = "No local model selected.",
    /** MINING-P0-001 last explicit direct-LiteRT control probe, if one has been run. */
    val liteRtControlProbe: com.halo.providers.litert.runtime.LiteRtControlProbeResult? = null,
    /** MINING-P0-001 content-free tool-channel truth for the selected local artifact. */
    val toolChannel: com.halo.providers.litert.LiteRtToolChannelDiagnostics? = null,
    /** MINING-P2-001 static renderer preview; purely local, opened explicitly by the user. */
    val showPresentationPreview: Boolean = false,
    val speechProbeTranscript: String? = null,
    val speechRecording: Boolean = false,
)

sealed class SystemCheckAction {
    object Refresh : SystemCheckAction()
    object CollectPayloads : SystemCheckAction()

    /**
     * MINING-P0-001 each diagnostic level is a separate EXPLICIT user action. They are deliberately
     * never chained: a differential diagnosis needs "raw PASS / provider FAIL" to be visible.
     */
    object RunLiteRtControlProbe : SystemCheckAction()

    /** Selected-provider probe through the router (LEVEL 4). */
    object RunProviderProbe : SystemCheckAction()

    /** Halo local provider verification through LocalModelSession (LEVEL 2). */
    object RunProviderVerification : SystemCheckAction()

    /** Explicit synthetic native tool-call probe; only meaningful for a tool-capable artifact. */
    object RunToolChannelProbe : SystemCheckAction()
    object RunVisionProbe : SystemCheckAction()

    /** Releases the resident local model so a direct probe can own the engine. */
    object UnloadLocalModel : SystemCheckAction()

    object RunAgenticProbe : SystemCheckAction()
    object RunSttProbe : SystemCheckAction()
    object RunCaptureProbe : SystemCheckAction()
    object RunTtsProbe : SystemCheckAction()
    object OpenAssistantSettings : SystemCheckAction()
    object TestAlarm : SystemCheckAction()
    object TestOpenApp : SystemCheckAction()
    object TestVolume : SystemCheckAction()

    /**
     * MINING-P2-001 renders fixed sample components. Static data only: no model call, no tool, no
     * provider and no network — opening System Check never probes anything by itself.
     */
    object PreviewPresentations : SystemCheckAction()
}

class SystemCheckViewModel(
    private val appContext: Context,
    private val providerRouter: AiProviderRouter,
    private val modelManager: LiteRtModelManager? = null,
    private val inferenceLedger: InferenceLedger? = null,
    /**
     * RUNTIME-056: the Phone section used to print three hardcoded "Available" rows.
     * Availability is a Runtime fact derived from descriptors and the live capability
     * catalogue, so this screen reads it instead of asserting it.
     */
    private val toolRegistry: com.halo.runtime.cognition.tools.ToolRegistry? = null,
    private val toolAvailabilityResolver: com.halo.runtime.cognition.tools.ToolAvailabilityResolver? = null,
    /** Live remote-provider presence; null keeps legacy local-only behavior. */
    private val remoteProviderRegistry: RemoteProviderPresenceRegistry? = null,
    /** Reports whether a stored credential exists for the given secret key. */
    private val remoteCredentialPresent: (String) -> Boolean = { false },
    val assistantRoleController: AndroidAssistantRoleController = AndroidAssistantRoleController(appContext),
    /** Optional runtime snapshot for native-resource residency/execution truth (MINING-P0-001). */
    private val runtimeSnapshotSource: com.halo.sdk.runtime.RuntimeSnapshotSource? = null,
    /** In-memory surface registry, read only to display how many projections are live (MINING-P2-001). */
    private val presentationProjection: com.halo.runtime.cognition.interaction.PresentationSurfaceProjection? = null,
    private val payloadStore: com.halo.runtime.cognition.context.ContentPayloadStore? = null,
    private val agentRuns: com.halo.runtime.cognition.agent.AgentRunStore? = null,
    private val localProfileSource: com.halo.app.data.models.RoomLocalInferenceProfileSource? = null,
    private val modelProfileRepository: com.halo.app.data.models.ModelRuntimeProfileRepository? = null,
    private val memoryPressure: () -> com.halo.sdk.runtime.NativeMemoryPressure = { com.halo.sdk.runtime.NativeMemoryPressure.NORMAL },
    private val speechDiagnostics: com.halo.app.speech.diagnostics.SpeechDiagnosticRunner? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemCheckUiState())
    val uiState: StateFlow<SystemCheckUiState> = _uiState.asStateFlow()

    init {
        refreshChecks()
    }

    fun handleAction(action: SystemCheckAction) {
        when (action) {
            is SystemCheckAction.Refresh -> refreshChecks()
            is SystemCheckAction.CollectPayloads -> viewModelScope.launch(Dispatchers.IO) {
                payloadStore?.collectUnreferenced()
                refreshChecks()
            }
            is SystemCheckAction.RunSttProbe -> testMicrophone(recognize = true)
            is SystemCheckAction.RunCaptureProbe -> testMicrophone(recognize = false)
            is SystemCheckAction.RunTtsProbe -> testAudioOutput()
            is SystemCheckAction.RunLiteRtControlProbe -> runLiteRtControlProbe()
            is SystemCheckAction.RunProviderProbe -> runAiProbe()
            is SystemCheckAction.RunProviderVerification -> runProviderVerification()
            is SystemCheckAction.RunToolChannelProbe -> runToolChannelProbe()
            is SystemCheckAction.RunVisionProbe -> viewModelScope.launch {
                val status = com.halo.app.capture.inspectMultimodal(providerRouter)
                val target = status.target
                if (target != null) (target.provider as? LiteRtLocalProvider)?.probeVision(target.modelId)
                refreshChecks()
            }
            is SystemCheckAction.UnloadLocalModel -> unloadLocalModel()
            is SystemCheckAction.RunAgenticProbe -> runAgenticProbe()
            is SystemCheckAction.OpenAssistantSettings -> {
                // To be handled by UI layer with launchAssistantEnrollment
            }
            is SystemCheckAction.TestAlarm -> testAlarm()
            is SystemCheckAction.TestOpenApp -> testOpenApp()
            is SystemCheckAction.TestVolume -> testVolume()
            is SystemCheckAction.PreviewPresentations -> {
                val showing = !_uiState.value.showPresentationPreview
                _uiState.value = _uiState.value.copy(showPresentationPreview = showing)
                // Local row update only: toggling a preview must never trigger a re-check.
                updateItem(
                    id = "presentation_preview",
                    status = CheckStatus.READY,
                    reason = if (showing) "Preview shown" else "Tap to preview",
                )
            }
        }
    }

    private fun refreshChecks() {
        viewModelScope.launch(Dispatchers.IO) {
            val sections = mutableListOf<SystemCheckSection>()
            com.halo.app.di.ManualDependencyGraph.notificationBridge.refresh()
            val automation = com.halo.app.di.ManualDependencyGraph.automationRuntime
            val pendingAttention = com.halo.app.di.ManualDependencyGraph.attentionQueue.pending.value.size
            val automationRows = mutableListOf(SystemCheckItem("automation_scheduler", "Scheduler and repository", HaloGlyph.HALO, CheckStatus.READY,
                "WorkManager and durable occurrence claims · Android timing may be delayed"))
            automation.registry.availability().forEach { (name, status) -> automationRows.add(SystemCheckItem("automation_source_$name", name.replace('_', ' '), HaloGlyph.HALO,
                if(status in setOf(com.halo.runtime.cognition.routine.AutomationSourceStatus.AVAILABLE, com.halo.runtime.cognition.routine.AutomationSourceStatus.RUNTIME_ONLY)) CheckStatus.READY else CheckStatus.NOT_CONFIGURED, status.name.lowercase().replace('_', ' '))) }
            automationRows.add(SystemCheckItem("automation_attention", "Durable attention", HaloGlyph.HALO, CheckStatus.READY,
                "$pendingAttention updates pending · " + com.halo.runtime.cognition.attention.AttentionInitiativeLevel.fromStored(com.halo.app.di.ManualDependencyGraph.storage.getString("attention_initiative")).name.lowercase()))
            sections.add(SystemCheckSection("automation_runtime", "Automation runtime", automationRows))
            val learning=com.halo.app.di.ManualDependencyGraph.personalLearning
            val preferences=com.halo.app.di.ManualDependencyGraph.learningPreferences()
            sections.add(SystemCheckSection("personal_memory","Memory & learning",listOf(
                SystemCheckItem("memory_repository","Memory repository",HaloGlyph.HALO,CheckStatus.READY,"Canonical Room memory records"),
                SystemCheckItem("reflection","Reflection",HaloGlyph.HALO,CheckStatus.READY,if(preferences.mode == com.halo.runtime.cognition.memory.LearningPreference.OFF) "Off" else "Deterministic; model path optional"),
                SystemCheckItem("learning_mode","Learning mode",HaloGlyph.HALO,CheckStatus.READY,preferences.mode.name.lowercase().replace('_',' ')),
                SystemCheckItem("learning_pending","Pending suggestions",HaloGlyph.HALO,CheckStatus.READY,"${learning.dao.pendingCount()} awaiting review"),
                SystemCheckItem("learning_episodes","Episodes",HaloGlyph.HALO,CheckStatus.READY,"${learning.dao.episodeCount()} bounded task summaries"),
                SystemCheckItem("semantic_retrieval","Semantic retrieval",HaloGlyph.HALO,CheckStatus.NOT_CONFIGURED,"Lexical available; semantic encoder not installed"),
                SystemCheckItem("procedure_suggestions","Procedure suggestions",HaloGlyph.HALO,CheckStatus.READY,if(preferences.mode == com.halo.runtime.cognition.memory.LearningPreference.SUGGEST && preferences.suggestProcedures) "Enabled" else "Disabled"),
                SystemCheckItem("automation_suggestions","Automation suggestions",HaloGlyph.HALO,CheckStatus.READY,if(preferences.mode == com.halo.runtime.cognition.memory.LearningPreference.SUGGEST && preferences.suggestAutomations) "Enabled" else "Disabled")
            )))
            val uiMetrics = com.halo.runtime.cognition.ui.UiOperatorDiagnostics.metrics.value
            sections.add(SystemCheckSection("phone_operator", "Phone operator", listOf(
                SystemCheckItem("phone_operator_accessibility", "Accessibility service", HaloGlyph.SEE,
                    if (com.halo.host.android.accessibility.HaloAccessibilityService.isEnabled) CheckStatus.READY else CheckStatus.NOT_CONFIGURED,
                    if (com.halo.host.android.accessibility.HaloAccessibilityService.isEnabled) "Connected" else "Enable in Android settings"),
                SystemCheckItem("phone_operator_projection", "Semantic projection", HaloGlyph.SEE, CheckStatus.READY, "Bounded on-demand capture · No automatic screen reads"),
                SystemCheckItem("phone_operator_revision", "Snapshot revision tracking", HaloGlyph.SEE, CheckStatus.READY, "Exact-generation validation wired · Device acceptance pending"),
                SystemCheckItem("phone_operator_preflight", "Action preflight", HaloGlyph.SEE, CheckStatus.READY, "Semantic target preview before authorization"),
                SystemCheckItem("phone_operator_verifier", "Postcondition verifier", HaloGlyph.SEE, CheckStatus.READY, "Action-specific evidence · Dispatch alone remains unconfirmed"),
                SystemCheckItem("phone_operator_sensitive", "Sensitive-app policy", HaloGlyph.SEE, CheckStatus.READY, "Block before tree traversal · Manual takeover"),
                SystemCheckItem("phone_operator_metrics", "Transactions", HaloGlyph.HALO, CheckStatus.READY,
                    "Reads ${uiMetrics.screenReadCount} · Dispatches ${uiMetrics.actionDispatchCount} · Verified ${uiMetrics.confirmedCount}", detail = uiMetrics.toString()),
                SystemCheckItem("phone_operator_limits", "Bounded actions", HaloGlyph.HALO, CheckStatus.READY,
                    "12 actions · 6 scrolls · 3 identical semantic actions per run"),
                SystemCheckItem("phone_operator_safety", "Semantic control", HaloGlyph.HALO, CheckStatus.READY,
                    "Sensitive packages blocked · No coordinates or privileged input")
            )))

            // 1. Runtime
            sections.add(SystemCheckSection(
                id = "runtime",
                title = "Runtime",
                items = listOf(
                    SystemCheckItem(
                        id = "core",
                        name = "Core Runtime",
                        glyph = HaloGlyph.HALO,
                        status = CheckStatus.READY,
                        reason = "System Check is running"
                    )
                )
            ))

            // 2. Speech
            val voice = com.halo.app.di.ManualDependencyGraph.voiceExperienceSettings.state.value
            val speechOwner = com.halo.app.di.ManualDependencyGraph.haloSpeechOutputCoordinator
            val route = speechOwner.session.affinity.value
            sections.add(SystemCheckSection("voice_experience", "Conversational voice", listOf(
                SystemCheckItem("voice_interrupt", "Spoken interruption", HaloGlyph.LISTEN, CheckStatus.READY,
                    if (voice.spokenInterruptionEnabled) "On · ${voice.interruptionSensitivity.name.lowercase()}" else "Off · physical controls always interrupt"),
                SystemCheckItem("voice_semantic", "Smart interruption", HaloGlyph.LISTEN, CheckStatus.NOT_CONFIGURED, "Not installed"),
                SystemCheckItem("voice_stream", "Early speech", HaloGlyph.LISTEN, CheckStatus.READY,
                    if (voice.streamingSpeechEnabled) "On · tool-free streaming responses" else "Off", detail = speechOwner.streamingStatus.value),
                SystemCheckItem("voice_affinity", "Last voice reply", HaloGlyph.LISTEN, CheckStatus.READY,
                    route?.let { "${it.originLabel} → ${it.intendedTargetLabel} · ${it.resolvedEndpointLabel ?: "Not played"} · ${it.fallbackPolicyLabel}" } ?: "No reply this session"),
            )))
            val audioPermission = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            sections.add(SystemCheckSection(
                id = "speech",
                title = "Speech",
                items = listOf(
                    SystemCheckItem("mic_permission", "Microphone permission", HaloGlyph.LISTEN,
                        if (audioPermission) CheckStatus.PASSED else CheckStatus.NOT_CONFIGURED,
                        if (audioPermission) "RECORD_AUDIO granted" else "RECORD_AUDIO permission missing"),
                    SystemCheckItem("mic_capture", "Microphone capture", HaloGlyph.LISTEN,
                        if (audioPermission) CheckStatus.READY else CheckStatus.BLOCKED, "Not yet probed; requires processed PCM"),
                    SystemCheckItem("speech_detector", "Speech detector", HaloGlyph.LISTEN,
                        if (speechDiagnostics?.detectorReady?.invoke() == true) CheckStatus.READY else CheckStatus.NOT_CONFIGURED,
                        if (speechDiagnostics?.detectorReady?.invoke() == true) "Verified Silero detector" else "Silero unavailable; energy fallback"),
                    SystemCheckItem("stt_selection", "STT selection", HaloGlyph.LISTEN,
                        if (speechDiagnostics != null) CheckStatus.READY else CheckStatus.NOT_CONFIGURED,
                        "language=${speechDiagnostics?.language?.invoke() ?: "unknown"}; ${speechDiagnostics?.selectedHealth()?.message ?: "STT unavailable"}"),
                    SystemCheckItem("stt_files", "STT verified files", HaloGlyph.LISTEN,
                        if (speechDiagnostics?.selectedHealth()?.healthy == true) CheckStatus.READY else CheckStatus.NOT_CONFIGURED,
                        "${speechDiagnostics?.selectedHealth()?.message ?: "Selected speech provider unavailable"}"),
                    SystemCheckItem("stt_recognition", "STT recognition", HaloGlyph.LISTEN,
                        if (audioPermission) CheckStatus.READY else CheckStatus.BLOCKED, "Not yet probed; requires a nonempty decoded transcript"),
                    SystemCheckItem(
                        id = "audio",
                        name = "Audio Output Route",
                        glyph = HaloGlyph.SPEAK,
                        status = CheckStatus.READY, // Simplified for now
                        reason = "Ready"
                    )
                )
            ))

            // 3. AI
            val selectedProvider = providerRouter.selectedProviderId.value
            val selectedModel = providerRouter.selectedModelId.value
            val provider = providerRouter.getAllProviders().firstOrNull { it.id == selectedProvider }
            val providerStatus = provider?.let { runCatching { it.getStatus() }.getOrNull() }
            val localProvider = provider as? LiteRtLocalProvider
            val localProfile = if (localProvider != null && selectedModel != null) localProfileSource?.profile(localProvider.id, selectedModel) else null
            val localContext = localProvider?.listModels()?.firstOrNull { it.id == selectedModel }?.contextLength
            val projectionContext = localContext ?: 4096L
            val profilePressure = memoryPressure()
            val health = selectedModel?.let { localProfileSource?.runtimeHealth(localProvider?.id.orEmpty(), it) }
            val decision = com.halo.runtime.cognition.context.localInferencePerformance(true,
                projectionContext, true, localProfile, profilePressure, health)
            val localDiagnostics = buildString {
                appendLine("Halo source: ${com.halo.app.BuildConfig.HALO_SOURCE_ID}")
                if (localProvider == null || selectedModel == null) {
                    appendLine("No local model selected.")
                    return@buildString
                }
                appendLine("Model: ${selectedModel.takeIf { localProvider != null } ?: "None"}")
                appendLine("Artifact: ${selectedModel?.let { localProfileSource?.trustedArtifactIdentity(it)?.take(12) } ?: "Unknown"}")
                appendLine("Backend: ${health?.backend ?: localProfile?.backend ?: "Unknown"}")
                appendLine("Context: ${localContext ?: "Unknown"}")
                val latest = localProfileSource?.lastPolicy?.takeIf { it.modelId == selectedModel && it.providerId == localProvider?.id }
                appendLine("Input modality: ${latest?.inputModality ?: "UNKNOWN"}")
                appendLine("Response delivery: ${latest?.responseDelivery ?: "Unknown"}")
                appendLine("Profile lookup: ${latest?.profileLookupMs?.let { "$it ms" } ?: "Unknown"}")
                appendLine(localInteractionProfilePresentation(localProfile, profilePressure, projectionContext, health))
            }.trim()
            _uiState.value = _uiState.value.copy(localAiDiagnostics = localDiagnostics)
            val qualificationProvider = providerRouter.getAllProviders().filterIsInstance<LiteRtLocalProvider>().firstOrNull()
            sections.add(SystemCheckSection("local_role_qualification", "Local role qualification", listOf(
                SystemCheckItem("local_role_qualification", "Local role qualification", HaloGlyph.MODEL, CheckStatus.READY,
                    "${(modelManager?.inventoryState?.value as? com.halo.providers.litert.models.LocalModelInventoryState.Ready)?.models?.count { it.valid } ?: 0} installed · ${qualificationProvider?.roleQualifications?.value?.values?.count { it.loadMs != null || it.failure == com.halo.providers.litert.runtime.LocalRoleProbeFailure.LOAD_FAILED } ?: 0} tested this session"))))
            sections.add(SystemCheckSection("local_interaction_profile", "Local interaction profile", listOf(
                SystemCheckItem("local_interaction_profile", "Local interaction profile", HaloGlyph.MODEL,
                    if (localProfile != null) CheckStatus.READY else CheckStatus.NOT_CONFIGURED,
                    if (localProfile != null) "MEASURED · ${decision?.tier ?: "UNKNOWN"}" else "DEFAULT · No compatible benchmark for this device/runtime",
                    detail = localInteractionProfilePresentation(localProfile, profilePressure, projectionContext, health)))))
            
            sections.add(SystemCheckSection(
                id = "ai",
                title = "AI Configuration",
                items = listOfNotNull(
                    SystemCheckItem(
                        id = "ai_selection",
                        name = "AI Selection",
                        glyph = HaloGlyph.MODEL,
                        status = if (provider != null && selectedModel != null) CheckStatus.PASSED else CheckStatus.NOT_CONFIGURED,
                        reason = "provider=${selectedProvider ?: "none"}; model=${selectedModel ?: "none"}",
                    ),
                    SystemCheckItem(
                        id = "ai_execution",
                        name = "AI Execution",
                        glyph = HaloGlyph.MODEL,
                        status = CheckStatus.READY,
                        reason = "Ready for probe"
                    ),
                    SystemCheckItem(
                        id = "agentic_execution",
                        name = "Agentic Loop",
                        glyph = HaloGlyph.MODEL,
                        status = CheckStatus.READY,
                        reason = "Ready for agentic probe"
                    )
                )
            ))

            val control = com.halo.app.di.ManualDependencyGraph.providerControlRepository
            val providerRows = kotlinx.coroutines.withTimeoutOrNull(2_000) {
                control.rows.first { it.isNotEmpty() }
            } ?: control.rows.value
            val cachedCatalog = control.catalogue.state.value.models
            val configuredProviders = providerRows.filter { it.configured }.map { it.descriptor.providerId }.toSet()
            val roleCandidates = com.halo.sdk.provider.AiModelRole.entries.associateWith {
                providerRouter.candidatesForRole(it, cachedCatalog, configuredProviders)
            }
            sections.add(SystemCheckSection("ai_roles", "Model roles", com.halo.sdk.provider.AiModelRole.entries.map { role ->
                val binding = providerRouter.roleBinding(role)
                val target = control.roleTarget(role, roleCandidates)
                val off = role != com.halo.sdk.provider.AiModelRole.CHAT && binding.selection == com.halo.sdk.provider.AiRoleSelection.DISABLED
                val explicit = role != com.halo.sdk.provider.AiModelRole.CHAT && binding.selection == com.halo.sdk.provider.AiRoleSelection.EXPLICIT
                SystemCheckItem("role_${role.name}", role.name, HaloGlyph.MODEL,
                    if (target?.eligible == true && !off) CheckStatus.READY else CheckStatus.NOT_CONFIGURED,
                    (if (role == com.halo.sdk.provider.AiModelRole.CHAT) "Conversation default" else roleModeLabel(binding.selection)) +
                        " · " + when {
                            off -> "Off"
                            target?.eligible == true -> "READY"
                            explicit -> "Configured model unavailable"
                            role == com.halo.sdk.provider.AiModelRole.VISION -> "NO VERIFIED MODEL"
                            else -> "No compatible model"
                        },
                    detail = "provider=${target?.ref?.providerId ?: binding.providerId ?: "none"}; model=${target?.ref?.modelId ?: binding.modelId ?: "none"}; " +
                        "capabilities=${target?.effectiveCapabilities?.sortedBy { it.name }?.joinToString() ?: "none"}; " +
                        "protocol=${target?.toolProtocol?.let(::toolProtocolLabel) ?: "No tool loop"}; " +
                        "rejections=${target?.rejectionReasons?.joinToString() ?: "none"}")
            }))

            // 3b. MINING-P0-001 local inference diagnostic ladder. Every level stays independently
            // visible so "raw LiteRT PASS / provider FAIL" cannot be mistaken for "LiteRT broken".
            if (localProvider != null || selectedModel != null) {
                sections.add(SystemCheckSection(
                    id = "local_ai",
                    title = "Local AI execution truth",
                    items = localExecutionItems(localProvider, selectedModel),
                ))
            }

            val multimodal = com.halo.app.capture.inspectMultimodal(providerRouter)
            sections.add(SystemCheckSection("multimodal", "Multimodal", listOf(
                SystemCheckItem("vision_probe", "Local vision", HaloGlyph.MODEL,
                    when (multimodal.visionEvidence) {
                        "proven" -> CheckStatus.PASSED
                        "failed" -> CheckStatus.FAILED
                        else -> CheckStatus.NOT_CONFIGURED
                    },
                    "${multimodal.target?.modelId ?: "Local vision unavailable"} · ${multimodal.visionEvidence}"),
                SystemCheckItem("image_pipeline", "Image pipeline", HaloGlyph.MODEL, CheckStatus.READY, "Bounded preparation · ephemeral captures"),
                SystemCheckItem("audio_model", "Audio-model transcription", HaloGlyph.LISTEN, CheckStatus.NOT_CONFIGURED, "Disabled · dedicated STT remains active"),
                SystemCheckItem("heycyan_visual", "HeyCyan visual source", HaloGlyph.MODEL,
                    if (com.halo.app.di.ManualDependencyGraph.heyCyanDriver.activeDeviceId != null) CheckStatus.READY else CheckStatus.NOT_CONFIGURED,
                    if (com.halo.app.di.ManualDependencyGraph.heyCyanDriver.activeDeviceId != null) "Connected" else "Unavailable"),
                SystemCheckItem("dedicated_stt", "Dedicated STT", HaloGlyph.LISTEN,
                    if (com.halo.app.di.ManualDependencyGraph.speechRecognitionAvailability.value) CheckStatus.READY else CheckStatus.NOT_CONFIGURED,
                    "${com.halo.app.di.ManualDependencyGraph.providers.speechRecognition.metadata.id} · dedicated transcription strategy"),
                SystemCheckItem("capability_packs", "Capability packs", HaloGlyph.MODEL, CheckStatus.READY,
                    multimodal.packs.joinToString(" · ") { "${it.descriptor.displayName}: ${it.state.name.lowercase().replace('_', ' ')}" }),
            )))

            // 4. Android Assistant
            assistantRoleController.refresh()
            val integration = assistantRoleController.integrationState.value
            val runtime = AssistantRuntimeStateStore.state.value
            val assistantStatus = when (integration) {
                is AssistantIntegrationState.Active -> CheckStatus.READY to
                    "Role held; interactor active; service=${runtime.serviceReady}; session=${runtime.sessionCreated}; capture=${runtime.state}"
                is AssistantIntegrationState.RoleHeldNotActive -> CheckStatus.BLOCKED to
                    "Role held, but Android interactor is ${integration.diagnostics.activeInteractorStatus}"
                is AssistantIntegrationState.EligibleNotSelected -> CheckStatus.NOT_CONFIGURED to
                    "Candidate=yes; default role=no"
                is AssistantIntegrationState.NotEligible -> CheckStatus.UNSUPPORTED to "Not eligible"
                is AssistantIntegrationState.Error -> CheckStatus.FAILED to integration.message
                AssistantIntegrationState.Checking -> CheckStatus.RUNNING to "Reading Android assistant state"
            }
            sections.add(SystemCheckSection(
                id = "assistant",
                title = "Android Assistant",
                items = listOf(
                    SystemCheckItem(
                        id = "assistant",
                        name = "Assistant Role",
                        glyph = HaloGlyph.PHONE,
                        status = assistantStatus.first,
                        reason = assistantStatus.second
                    )
                )
            ))

            // Phone abilities (RUNTIME-056): real descriptor/capability truth, not asserted rows.
            sections.add(SystemCheckSection(
                id = "phone",
                title = "Phone abilities",
                items = phoneAbilityItems()
            ))

            /*
             * Interaction presentation (MINING-P2-001). Passive rows plus one explicit, local preview
             * action. There is no network or inference probe here on purpose: the schema version and
             * renderer availability are compile-time facts, and the active-surface count is in-memory.
             */
            val activeSurfaces = runCatching { presentationProjection?.activeCount() }.getOrNull()
            sections.add(SystemCheckSection(
                id = "interaction_presentation",
                title = "Interaction presentation",
                items = listOf(
                    SystemCheckItem(
                        id = "presentation_renderer",
                        name = "Surface renderer",
                        glyph = HaloGlyph.SEE,
                        status = CheckStatus.READY,
                        reason = "Available",
                        detail = "FULL on Home and Notebook, COMPACT in the Android Assistant overlay",
                    ),
                    SystemCheckItem(
                        id = "presentation_schema",
                        name = "Schema",
                        glyph = HaloGlyph.MODEL,
                        status = CheckStatus.READY,
                        reason = "v" + com.halo.runtime.cognition.interaction.CURRENT_PRESENTATION_SCHEMA_VERSION,
                        detail = "Newer payloads fall back to the assistant's text",
                    ),
                    SystemCheckItem(
                        id = "presentation_surfaces",
                        name = "Active surfaces",
                        glyph = HaloGlyph.AUTOMATION,
                        status = CheckStatus.READY,
                        reason = (activeSurfaces ?: 0).toString(),
                        detail = "Ephemeral projections; committed cards live in the conversation turn",
                    ),
                    SystemCheckItem(
                        id = "presentation_responses",
                        name = "Structured responses",
                        glyph = HaloGlyph.TOOL,
                        status = CheckStatus.READY,
                        reason = "Supported",
                        detail = "halo_present · presentation only, cannot execute anything",
                    ),
                    SystemCheckItem(
                        id = "presentation_preview",
                        name = "Preview presentation components",
                        glyph = HaloGlyph.PLAY,
                        status = CheckStatus.READY,
                        reason = if (_uiState.value.showPresentationPreview) "Preview shown" else "Tap to preview",
                        detail = "Fixed sample data. No model call and no tools.",
                    ),
                ),
            ))

            val payloads = payloadStore?.stats()
            com.halo.runtime.cognition.delegation.LongTaskDiagnostics.payloads.value = payloads
            val budget = com.halo.runtime.cognition.context.ContextDiagnostics.composition.value
            val children = agentRuns?.observeRuns()?.first().orEmpty().filter { it.parentRunId != null }
            val latest = children.maxByOrNull { it.updatedAt }
            val child = com.halo.runtime.cognition.delegation.DelegationDiagnostics(children.size,
                children.count { it.completedAt == null && it.state !in setOf(com.halo.runtime.cognition.agent.AgentRunState.CANCELLED, com.halo.runtime.cognition.agent.AgentRunState.FAILED, com.halo.runtime.cognition.agent.AgentRunState.INTERRUPTED, com.halo.runtime.cognition.agent.AgentRunState.COMPLETED) },
                children.maxOfOrNull { it.delegationDepth } ?: 0, latest?.delegatedRole, latest?.pinnedProviderId, latest?.pinnedModelId,
                latest?.state?.name, latest?.steps?.filterIsInstance<com.halo.runtime.cognition.agent.AgentStep.DelegationResultRecorded>()?.lastOrNull()?.evidence?.size ?: 0)
            com.halo.runtime.cognition.delegation.LongTaskDiagnostics.delegation.value = child
            sections.add(SystemCheckSection("long_tasks", "Long tasks", listOf(
                SystemCheckItem("context_budget", "Context budget", HaloGlyph.HALO, CheckStatus.READY,
                    budget?.let { "${it.budget.modelContextTokens} tokens · ${it.budget.reservedOutputTokens} output reserved · ${it.remainingTokens} remaining" } ?: "No provider context assembled",
                    detail = budget?.segmentTokens?.entries?.joinToString { "${it.key}: ${it.value}" }),
                SystemCheckItem("payload_stats", "Payload storage", HaloGlyph.HALO, CheckStatus.READY,
                    payloads?.let { "${it.count} bodies · ${it.bytes} bytes · ${it.unreferencedBytes} unreferenced" } ?: "Unavailable",
                    detail = payloads?.oldestUnreferencedMs?.let { "Oldest unreferenced timestamp: $it" }),
                SystemCheckItem("payload_gc", "Collect unused payloads", HaloGlyph.HALO, CheckStatus.READY,
                    "Explicit cleanup · Referenced bodies retained · 24-hour grace"),
                SystemCheckItem("delegation_budget", "Delegation", HaloGlyph.HALO, CheckStatus.READY,
                    "${child.childCount} children · ${child.activeChildren} active · depth ${child.maximumDepth} / 1",
                    detail = "Up to 4 per root; 2 active globally; 1 local. ${child.lastRole.orEmpty()} ${child.lastProvider.orEmpty()} / ${child.lastModel.orEmpty()}"),
            )))
            _uiState.value = _uiState.value.copy(sections = sections)
            generateSummaryReport()
        }
    }

    /** Best-effort refresh of live remote presence, safe to call from UI actions. */
    fun refreshRemoteProviders() {
        val registry = remoteProviderRegistry ?: return
        viewModelScope.launch(Dispatchers.IO) {
            registry.allPresences().forEach { presence ->
                runCatching {
                    val authInfo = presence.authInfo
                    registry.register(
                        presence.copy(
                            authInfo = authInfo.copy(
                                authState = when {
                                    presence.isConfigured && authInfo.authState == ProviderAuthState.NOT_CONFIGURED -> ProviderAuthState.CONFIGURED
                                    else -> authInfo.authState
                                },
                            ),
                        ),
                    )
                }
            }
        }
    }

    private companion object {
        /**
         * The abilities a user can actually reach from a phone conversation, in the order
         * they matter. A tool missing from this build is simply absent from the list.
         */
        val PHONE_ABILITIES: List<Pair<String, String>> = listOf(
            "halo_open_app" to "App launcher",
            "halo_open_settings" to "Android settings",
            "halo_set_alarm" to "Alarms",
            "halo_timer" to "Timers",
            "halo_volume" to "Volume",
            "halo_ringer" to "Sound mode",
            "halo_brightness" to "Screen brightness",
            "halo_flashlight" to "Flashlight",
            "halo_media_control" to "Media control",
            "halo_device_info" to "Device profile",
            "halo_battery_status" to "Battery state",
            "halo_network_status" to "Network state",
            "halo_clipboard_read" to "Read clipboard",
            "halo_clipboard_write" to "Copy to clipboard",
            "halo_checksum" to "File checksums",
            "halo_fetch_url" to "Read web pages",
            "halo_navigation" to "Navigation",
            "halo_browser" to "Open web pages",
            "halo_search" to "Web search",
            "halo_find_contact" to "Contacts",
            "halo_dial_contact" to "Dialer",
            "halo_share_text" to "Share text",
            "halo_read_screen" to "Read active screen",
            "halo_ui_click" to "Control active screen",
        )
    }

    private fun credentialKeyFor(providerId: String): String = when (providerId) {
        RemoteProviderIds.GROQ -> "groq_api_key"
        RemoteProviderIds.CEREBRAS -> "cerebras_api_key"
        RemoteProviderIds.OPENCODE_ZEN -> "opencode_zen_api_key"
        RemoteProviderIds.XAI -> "xai_api_key"
        RemoteProviderIds.OPENCODE_COMPANION -> "opencode_companion_credential"
        else -> "custom_openai_compatible_credential"
    }

    private fun authStateLabel(state: ProviderAuthState, configured: Boolean): String = when (state) {
        ProviderAuthState.CONFIGURED -> "Configured; connection not checked"
        ProviderAuthState.CONNECTED -> "Connected"
        ProviderAuthState.NOT_CONFIGURED -> "Not configured — add an API key in Intelligence settings"
        ProviderAuthState.AUTHORIZING -> "Authorizing"
        ProviderAuthState.REFRESHING -> "Refreshing token"
        ProviderAuthState.EXPIRED -> "Credential expired — reconnect"
        ProviderAuthState.FAILED -> "Connection failed — test connection in settings"
        ProviderAuthState.NOT_REQUIRED -> if (configured) "Ready (no auth required)" else "Endpoint set, no auth"
    }

    /**
     * RUNTIME-056: one row per phone ability this build actually registers, with the
     * availability the Runtime resolver reports. Missing capabilities name the setup step
     * that is missing (for example "permission to modify system settings" for brightness)
     * instead of a generic failure.
     */
    private fun phoneAbilityItems(): List<SystemCheckItem> {
        val registry = toolRegistry
        val resolver = toolAvailabilityResolver
        if (registry == null || resolver == null) {
            return listOf(
                SystemCheckItem(
                    id = "phone_catalogue",
                    name = "Phone abilities",
                    glyph = HaloGlyph.PHONE,
                    status = CheckStatus.NOT_CONFIGURED,
                    reason = "Halo's tool catalogue is not attached to System Check",
                )
            )
        }

        return PHONE_ABILITIES.mapNotNull { (toolName, label) ->
            val descriptor = registry.descriptor(toolName) ?: return@mapNotNull null
            val availability = resolver.checkAvailability(descriptor)
            SystemCheckItem(
                id = "phone_$toolName",
                name = label,
                glyph = glyphFor(toolName),
                status = when (availability) {
                    is com.halo.runtime.cognition.tools.ToolAvailability.Available -> CheckStatus.PASSED
                    is com.halo.runtime.cognition.tools.ToolAvailability.MissingCapabilities -> CheckStatus.NOT_CONFIGURED
                    is com.halo.runtime.cognition.tools.ToolAvailability.Disabled -> CheckStatus.BLOCKED
                    is com.halo.runtime.cognition.tools.ToolAvailability.Degraded -> CheckStatus.READY
                },
                reason = when (availability) {
                    is com.halo.runtime.cognition.tools.ToolAvailability.Available ->
                        descriptor.presentation?.title ?: "Available"
                    is com.halo.runtime.cognition.tools.ToolAvailability.MissingCapabilities ->
                        "Needs " + availability.missing.joinToString { capabilityLabel(it.value) }
                    is com.halo.runtime.cognition.tools.ToolAvailability.Disabled -> availability.reason
                    is com.halo.runtime.cognition.tools.ToolAvailability.Degraded -> availability.reason
                },
            )
        }
    }

    // -------------------------------------------------------------------------------------
    // MINING-P0-001 local AI execution truth
    // -------------------------------------------------------------------------------------

    private fun selectedLocalProvider(): LiteRtLocalProvider? =
        providerRouter.getAllProviders()
            .firstOrNull { it.id == providerRouter.selectedProviderId.value } as? LiteRtLocalProvider

    /**
     * LEVEL 0..3 view for the selected on-device artifact. Each row reports only what its own layer
     * observed: the artifact is never marked PASSED because a file exists, and raw LiteRT control is
     * never inferred from the provider being READY.
     */
    private suspend fun localExecutionItems(
        localProvider: LiteRtLocalProvider?,
        selectedModelId: String?,
    ): List<SystemCheckItem> {
        if (localProvider == null || selectedModelId == null) {
            return listOf(
                SystemCheckItem(
                    id = "litert_artifact",
                    name = "Artifact",
                    glyph = HaloGlyph.MODEL,
                    status = CheckStatus.NOT_CONFIGURED,
                    reason = "No on-device model selected",
                )
            )
        }

        val status = runCatching { localProvider.modelStatus(selectedModelId) }.getOrNull()
        val toolChannel = runCatching { localProvider.toolChannelDiagnostics(selectedModelId) }.getOrNull()
        val providerStatus = runCatching { localProvider.status.value }.getOrNull()
        val control = _uiState.value.liteRtControlProbe?.takeIf { it.modelId == selectedModelId }

        return listOfNotNull(
            artifactItem(status),
            control?.let(::controlProbeItem) ?: controlProbeNotRunItem(),
            providerItem(providerStatus, status),
            generationItem(status),
            toolChannelItem(toolChannel),
            resourceItem(localProvider),
            backendItem(status),
            contextItem(status),
        )
    }

    private fun artifactItem(status: LiteRtModelRuntimeStatus?): SystemCheckItem {
        // installed != verified. Only VERIFIED_ARTIFACT may render as verified.
        val (check, word) = when (status?.validation) {
            LocalModelValidation.VERIFIED_ARTIFACT -> CheckStatus.PASSED to "VERIFIED"
            LocalModelValidation.UNVERIFIED -> CheckStatus.READY to "UNVERIFIED"
            LocalModelValidation.CORRUPT -> CheckStatus.FAILED to "CORRUPT"
            LocalModelValidation.INCOMPATIBLE -> CheckStatus.FAILED to "NOT USABLE"
            null -> CheckStatus.NOT_CONFIGURED to "NOT INSTALLED"
        }
        return SystemCheckItem(
            id = "litert_artifact",
            name = "Artifact",
            glyph = HaloGlyph.MODEL,
            status = check,
            reason = word + (status?.artifactFingerprintPrefix?.let { " · sha $it" } ?: ""),
        )
    }

    private fun controlProbeNotRunItem(): SystemCheckItem = SystemCheckItem(
        id = "litert_control",
        name = "Raw LiteRT control",
        glyph = HaloGlyph.MODEL,
        status = CheckStatus.READY,
        reason = "Not run — direct SDK probe (Engine → Conversation → first token)",
    )

    private fun controlProbeItem(result: com.halo.providers.litert.runtime.LiteRtControlProbeResult): SystemCheckItem =
        SystemCheckItem(
            id = "litert_control",
            name = "Raw LiteRT control",
            glyph = HaloGlyph.MODEL,
            status = when {
                result.controlProbePassed -> CheckStatus.PASSED
                result.blocked -> CheckStatus.BLOCKED
                else -> CheckStatus.FAILED
            },
            reason = result.summaryLine(),
            durationMs = result.totalDurationMs,
            detail = result.stages.joinToString("\n") { stage ->
                buildString {
                    append(stage.stage.name)
                    append(' ')
                    append(stage.status.name)
                    stage.durationMs?.let { append(" ${it}ms") }
                    stage.boundedDetail?.let { append(" — $it") }
                    stage.exceptionClass?.let { append(" ($it)") }
                }
            },
        )

    private fun providerItem(
        providerStatus: ProviderStatus?,
        status: LiteRtModelRuntimeStatus?,
    ): SystemCheckItem {
        val (check, word) = when (providerStatus?.state) {
            ProviderStatus.State.READY ->
                if (status?.resident == true) CheckStatus.PASSED to "RESIDENT" else CheckStatus.READY to "COLD"
            ProviderStatus.State.LOADING -> CheckStatus.RUNNING to "LOADING"
            ProviderStatus.State.BUSY -> CheckStatus.RUNNING to "BUSY"
            ProviderStatus.State.ERROR -> CheckStatus.FAILED to "FAILED"
            ProviderStatus.State.UNAVAILABLE,
            ProviderStatus.State.UNCONFIGURED,
            ProviderStatus.State.MODEL_MISSING,
            ProviderStatus.State.OFFLINE,
            null -> CheckStatus.NOT_CONFIGURED to "UNAVAILABLE"
            else -> CheckStatus.READY to providerStatus.state.name
        }
        return SystemCheckItem(
            id = "litert_provider",
            name = "Halo local provider",
            glyph = HaloGlyph.MODEL,
            status = check,
            reason = word + (providerStatus?.message?.let { " · ${it.take(120)}" } ?: ""),
        )
    }

    private fun generationItem(status: LiteRtModelRuntimeStatus?): SystemCheckItem {
        val record = status?.failureRecord
        val failureStage = record?.lastGenerationFailureStage ?: record?.lastLoadFailureStage
        val (check, word) = when {
            status?.generationProven == true -> CheckStatus.PASSED to
                "PROVEN ${status.effectiveBackend?.name ?: ""}".trim()
            failureStage != null -> CheckStatus.FAILED to "FAILED $failureStage"
            else -> CheckStatus.READY to "NOT RUN"
        }
        return SystemCheckItem(
            id = "litert_generation",
            name = "Generation proof",
            glyph = HaloGlyph.MODEL,
            status = check,
            reason = word,
            detail = record?.summaryLine(),
        )
    }

    private fun toolChannelItem(diagnostics: LiteRtToolChannelDiagnostics?): SystemCheckItem {
        if (diagnostics == null) {
            return SystemCheckItem(
                id = "litert_tools",
                name = "Tool channel",
                glyph = HaloGlyph.TOOL,
                status = CheckStatus.NOT_CONFIGURED,
                reason = "Not assessed",
            )
        }
        val check = when (diagnostics.qualification) {
            LiteRtToolQualification.NATIVE_PROVEN -> CheckStatus.PASSED
            LiteRtToolQualification.PROMPT_MEDIATED -> CheckStatus.READY
            LiteRtToolQualification.PROBING -> CheckStatus.RUNNING
            LiteRtToolQualification.FAILED_LAST_PROBE -> CheckStatus.FAILED
            LiteRtToolQualification.DECLARED,
            LiteRtToolQualification.PROBE_REQUIRED -> CheckStatus.BLOCKED
            LiteRtToolQualification.NOT_APPLICABLE -> CheckStatus.UNSUPPORTED
        }
        return SystemCheckItem(
            id = "litert_tools",
            name = "Tool channel",
            glyph = HaloGlyph.TOOL,
            status = check,
            reason = diagnostics.qualification.label.uppercase(),
            detail = diagnostics.summaryLine(),
        )
    }

    private fun resourceItem(localProvider: LiteRtLocalProvider): SystemCheckItem {
        val snapshot = runtimeSnapshotSource?.snapshot?.value?.nativeResources
            .orEmpty()
            .firstOrNull { it.ownerId == localProvider.nativeResourceId }
        if (snapshot == null) {
            return SystemCheckItem(
                id = "litert_resource",
                name = "Native resource",
                glyph = HaloGlyph.DEVICE,
                status = CheckStatus.NOT_CONFIGURED,
                reason = "RELEASED — no native claim held",
            )
        }
        // Residency is not execution: RESIDENT must show executionLeaseHeld=false.
        val check = when {
            snapshot.executing -> CheckStatus.RUNNING
            snapshot.state == com.halo.sdk.runtime.NativeResourceState.RESIDENT -> CheckStatus.PASSED
            snapshot.state == com.halo.sdk.runtime.NativeResourceState.ACQUIRING -> CheckStatus.BLOCKED
            else -> CheckStatus.READY
        }
        val reason = when {
            snapshot.state == com.halo.sdk.runtime.NativeResourceState.ACQUIRING ->
                "WAITING — ${snapshot.reason ?: "contended"}"
            snapshot.executing -> "IN USE — executionLeaseHeld=true"
            else -> "${snapshot.state.name} — executionLeaseHeld=${snapshot.executionLeaseHeld}"
        }
        return SystemCheckItem(
            id = "litert_resource",
            name = "Native resource",
            glyph = HaloGlyph.DEVICE,
            status = check,
            reason = reason,
            detail = buildString {
                append("kind=${snapshot.kind} resource=${snapshot.executionResourceId ?: "none"}")
                snapshot.executionWaitMs?.let { append(" waitMs=$it") }
                append(" reason=${snapshot.reason ?: "none"}")
            },
        )
    }

    private fun backendItem(status: LiteRtModelRuntimeStatus?): SystemCheckItem = SystemCheckItem(
        id = "litert_backend",
        name = "Backend",
        glyph = HaloGlyph.MODEL,
        status = CheckStatus.READY,
        reason = "requested ${status?.requestedBackend?.name ?: "AUTO"} · " +
            "effective ${status?.effectiveBackend?.name ?: "none"}",
    )

    private fun contextItem(status: LiteRtModelRuntimeStatus?): SystemCheckItem = SystemCheckItem(
        id = "litert_context",
        name = "Context",
        glyph = HaloGlyph.MODEL,
        status = CheckStatus.READY,
        reason = "requested/effective ${status?.effectiveContextTokens?.toString() ?: "model default"}",
    )

    private fun glyphFor(toolName: String): HaloGlyph = when (toolName) {
        "halo_device_info", "halo_battery_status", "halo_network_status" -> HaloGlyph.DEVICE
        "halo_clipboard_read", "halo_clipboard_write" -> HaloGlyph.NOTE
        "halo_checksum" -> HaloGlyph.FILE
        "halo_fetch_url" -> HaloGlyph.SEARCH
        "halo_browser", "halo_search" -> HaloGlyph.SEARCH
        "halo_media_control", "halo_media_status" -> HaloGlyph.PLAY
        "halo_read_screen", "halo_ui_click" -> HaloGlyph.SEE
        "halo_open_settings", "halo_brightness", "halo_ringer" -> HaloGlyph.SETTINGS
        "halo_set_alarm", "halo_timer" -> HaloGlyph.AUTOMATION
        "halo_share_text", "halo_dial_contact", "halo_find_contact" -> HaloGlyph.SPEAK
        else -> HaloGlyph.PHONE
    }

    private fun capabilityLabel(id: String): String = when (id) {
        "host.screen.brightness" -> "permission to modify system settings"
        "host.screen.observe" -> "screen observation access"
        "host.screen.control" -> "screen control access"
        "host.contacts.read" -> "contacts access"
        "host.notification.read" -> "notification access"
        "host.dialer.open" -> "a dialer"
        "host.web.open", "host.web.search", "host.web.fetch" -> "internet access"
        "host.navigation.open" -> "a maps app"
        "host.application.launch" -> "launchable apps"
        "host.alarm.set" -> "a clock app"
        "host.timer.set" -> "a clock app that accepts timers"
        else -> id.substringAfterLast('.').replace('_', ' ')
    }

    fun launchAssistantEnrollment(launcher: (Intent) -> Unit) {
        assistantRoleController.launchEnrollment(launcher)
        refreshChecks()
    }

    fun recordAssistantEnrollmentResult(resultCode: Int?) {
        assistantRoleController.recordEnrollmentActivityResult(resultCode)
        refreshChecks()
    }

    private var speechProbeJob: kotlinx.coroutines.Job? = null
    private var speechFinish: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    fun finishSpeechCapture() { speechFinish?.complete(Unit) }
    fun cancelSpeechProbe() { speechProbeJob?.cancel() }
    fun leaveSpeechProbe() {
        speechProbeJob?.cancel()
        speechProbeJob?.invokeOnCompletion { _uiState.value = _uiState.value.copy(speechProbeTranscript = null) }
        _uiState.value = _uiState.value.copy(speechProbeTranscript = null)
    }
    fun testMicrophone(recognize: Boolean = false) {
        if (speechProbeJob?.isCompleted == false) return
        speechProbeJob = viewModelScope.launch(Dispatchers.IO) {
            var sample: com.halo.app.speech.diagnostics.SpeechDiagnosticSession? = null
            var captured = false
            try {
                val runner = checkNotNull(speechDiagnostics) { "Diagnostic runner unavailable" }
                speechFinish = kotlinx.coroutines.CompletableDeferred()
                _uiState.value = _uiState.value.copy(speechProbeTranscript = null, speechRecording = true)
                updateItem("mic_capture", CheckStatus.RUNNING,
                    if (runner.language() == "lv") "Saki: Halo runas tests" else "Say: Halo speech test")
                if (recognize) updateItem("stt_recognition", CheckStatus.RUNNING, "Waiting for capture")
                sample = runner.captureSession(speechFinish, maxDurationMs = 7_000)
                captured = true
                _uiState.value = _uiState.value.copy(speechRecording = false)
                updateItem("mic_capture", CheckStatus.PASSED,
                    "Processed PCM: ${sample.activity.durationMs} ms; peak=${sample.activity.peakAmplitude}; ${sample.capture.originalSampleRate} → 16000 Hz mono")
                if (recognize) {
                    val result = runner.recognizeSelected(sample)
                    val recognition = result.recognition
                    _uiState.value = _uiState.value.copy(speechProbeTranscript = recognition?.text)
                    updateItem("stt_recognition", if (com.halo.app.speech.diagnostics.SpeechProbeVerdict.recognitionPassed(result)) CheckStatus.PASSED else CheckStatus.FAILED,
                        "stage=${result.stage}; chars=${recognition?.text?.length ?: 0}; gate=${result.gate ?: "not evaluated"}; ${result.failure.orEmpty()}",
                        recognition?.metrics?.totalMs)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                updateItem(if (captured) "stt_recognition" else "mic_capture", CheckStatus.BLOCKED, "Probe cancelled; no result admitted")
                if (recognize && !captured) updateItem("stt_recognition", CheckStatus.BLOCKED, "Capture cancelled")
                throw cancelled
            } catch (error: Exception) {
                updateItem(if (captured) "stt_recognition" else "mic_capture", CheckStatus.FAILED, error.message ?: "CAPTURE: failed")
                if (recognize && !captured) updateItem("stt_recognition", CheckStatus.BLOCKED, "Capture did not complete")
            } finally {
                sample?.close(); speechFinish = null
                _uiState.value = _uiState.value.copy(speechRecording = false)
            }
        }
    }

    private fun runAiProbe() {
        viewModelScope.launch(Dispatchers.IO) {
            updateItem("ai_execution", CheckStatus.RUNNING, "Running bounded probe on the selected provider/model...")
            val startTime = System.currentTimeMillis()
            runCatching {
                val selectedProvider = providerRouter.selectedProviderId.value
                    ?: throw IllegalStateException("No AI provider selected")
                val selectedModel = providerRouter.selectedModelId.value
                    ?: throw IllegalStateException("No AI model selected")
                val response = providerRouter.execute(
                    AiRequest(text = "Reply with one word: HALO", maxOutputTokens = 8, toolUseMode = com.halo.sdk.provider.ToolUseMode.NONE, reasoningMode = com.halo.sdk.provider.AiReasoningMode.DISABLED),
                    AiWorkloadSpec(kind = AiWorkloadKind.CONVERSATION),
                    AiContext.EMPTY,
                )

                val elapsed = System.currentTimeMillis() - startTime
                val pass = response.text.isNotBlank()
                updateItem(
                    "ai_execution",
                    if (pass) CheckStatus.PASSED else CheckStatus.FAILED,
                    if (pass) "provider=$selectedProvider; model=$selectedModel; backend=${response.executionBackend ?: "unreported"}; non-empty response" else "Selected provider returned empty text",
                    elapsed
                )
            }.onFailure { err ->
                updateItem("ai_execution", CheckStatus.FAILED, "${err::class.java.simpleName}: ${err.message ?: "AI probe failed"}")
            }
        }
    }

    private fun runAgenticProbe() {
        viewModelScope.launch(Dispatchers.IO) {
            updateItem("agentic_execution", CheckStatus.RUNNING, "Running local agentic probe...")
            val startTime = System.currentTimeMillis()
            val probeRunner = LocalAgenticProbeRunner(providerRouter, modelProfileRepository, localProfileSource)
            probeRunner.runProbe()
            val elapsed = System.currentTimeMillis() - startTime

            val phases = probeRunner.phases.value
            val failedPhase = phases.firstOrNull { it.result == ProbeResult.FAIL }
            
            if (failedPhase != null) {
                updateItem("agentic_execution", CheckStatus.FAILED, "${failedPhase.phase.name} failed: ${failedPhase.reason}", elapsed)
            } else {
                updateItem("agentic_execution", CheckStatus.PASSED, "Probe completed successfully", elapsed)
            }
        }
    }

    private fun testAudioOutput() {
        viewModelScope.launch(Dispatchers.IO) {
            updateItem("audio", CheckStatus.RUNNING, "Playing test tone...")
            val startTime = System.currentTimeMillis()
            runCatching {
                // Play short system notification sound
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val mediaPlayer = MediaPlayer.create(appContext, uri)
                mediaPlayer?.start()
                delay(1000)
                mediaPlayer?.release()

                val elapsed = System.currentTimeMillis() - startTime
                updateItem("audio", CheckStatus.PASSED, "Audio tone requested on the current Android media route", elapsed)
            }.onFailure { err ->
                updateItem("audio", CheckStatus.FAILED, err.message ?: "Audio playback failed")
            }
        }
    }

    fun runSafeChecks() {
        refreshChecks()
    }

    /**
     * MINING-P0-001 LEVEL 1 — direct LiteRT SDK control probe.
     *
     * Stream K cleanup: this replaces the previous `runLocalLoop()`, which called
     * `providerRouter.execute()` and therefore sat on the SAME layer as the selected-provider probe
     * while implying it was lower-level. The direct probe bypasses the router, cognition, memory,
     * tools, the agent loop and speech, so a raw PASS / provider FAIL is now genuinely differential.
     *
     * Explicit user action only: never runs at startup or when Settings opens.
     */
    private fun runLiteRtControlProbe() {
        viewModelScope.launch(Dispatchers.IO) {
            val provider = selectedLocalProvider()
            val modelId = providerRouter.selectedModelId.value
            if (provider == null || modelId == null) {
                updateItem("litert_control", CheckStatus.NOT_CONFIGURED, "No on-device model selected")
                return@launch
            }
            updateItem("litert_control", CheckStatus.RUNNING, "Running direct LiteRT control probe…")
            val result = runCatching { provider.runControlProbe(modelId) }.getOrNull()
            if (result == null) {
                updateItem("litert_control", CheckStatus.FAILED, "Control probe could not run")
                return@launch
            }
            _uiState.value = _uiState.value.copy(liteRtControlProbe = result)
            refreshChecks()
        }
    }

    /**
     * LEVEL 2 — Halo's own provider verification (LocalModelSession, load policy, backend fallback,
     * provider status). Deliberately separate from the raw control probe and from the router probe.
     */
    private fun runProviderVerification() {
        viewModelScope.launch(Dispatchers.IO) {
            val provider = selectedLocalProvider()
            val modelId = providerRouter.selectedModelId.value
            if (provider == null || modelId == null) {
                updateItem("litert_provider", CheckStatus.NOT_CONFIGURED, "No on-device model selected")
                return@launch
            }
            updateItem("litert_provider", CheckStatus.RUNNING, "Verifying $modelId through Halo's local session…")
            val start = System.currentTimeMillis()
            val result = runCatching { provider.verifyModel(modelId) }.getOrNull()
            val elapsed = System.currentTimeMillis() - start
            when (result) {
                is ModelVerificationResult.Verified -> updateItem(
                    "litert_provider",
                    CheckStatus.PASSED,
                    "Load + conversation + first token on ${result.backend}",
                    elapsed,
                    "loadBackend=${result.loadBackend} generationBackend=${result.generationBackend}" +
                        " loadMs=${result.loadMs} ttftMs=${result.ttftMs}",
                )
                is ModelVerificationResult.Failed -> updateItem(
                    "litert_provider",
                    CheckStatus.FAILED,
                    "${result.stage}: ${result.reason}",
                    elapsed,
                    result.technicalDetail,
                )
                null -> updateItem("litert_provider", CheckStatus.FAILED, "Verification threw unexpectedly", elapsed)
            }
            refreshChecks()
        }
    }

    /**
     * Explicit synthetic native tool-call probe. Harmless: Halo's own tool never executes inside
     * LiteRT. Only meaningful when the artifact is tool-capable, so a NONE artifact is refused
     * instead of probed for nothing.
     */
    private fun runToolChannelProbe() {
        viewModelScope.launch(Dispatchers.IO) {
            val provider = selectedLocalProvider()
            val modelId = providerRouter.selectedModelId.value
            if (provider == null || modelId == null) {
                updateItem("litert_tools", CheckStatus.NOT_CONFIGURED, "No on-device model selected")
                return@launch
            }
            val channel = runCatching { provider.toolChannelDiagnostics(modelId) }.getOrNull()
            _uiState.value = _uiState.value.copy(toolChannel = channel)
            if (channel?.qualification == LiteRtToolQualification.NOT_APPLICABLE) {
                updateItem(
                    "litert_tools",
                    CheckStatus.UNSUPPORTED,
                    "This artifact cannot carry a tool loop",
                    detail = channel.summaryLine(),
                )
                return@launch
            }
            updateItem("litert_tools", CheckStatus.RUNNING, "Probing the native tool channel…")
            val start = System.currentTimeMillis()
            val result = runCatching { provider.probeToolCalling(modelId) }.getOrNull()
            val elapsed = System.currentTimeMillis() - start
            val refreshed = runCatching { provider.toolChannelDiagnostics(modelId) }.getOrNull()
            _uiState.value = _uiState.value.copy(toolChannel = refreshed ?: channel)
            if (result == null) {
                updateItem("litert_tools", CheckStatus.FAILED, "Tool probe could not run", elapsed)
                return@launch
            }
            updateItem(
                "litert_tools",
                when {
                    result.qualification == LiteRtToolQualification.NATIVE_PROVEN -> CheckStatus.PASSED
                    result.failureStage != null -> CheckStatus.FAILED
                    else -> CheckStatus.READY
                },
                result.summaryLine(),
                elapsed,
                result.failureDetail ?: refreshed?.summaryLine(),
            )
            refreshChecks()
        }
    }

    /**
     * Releases the resident local model so a direct runtime probe can own the engine. A warm
     * RESIDENT model makes the probe BLOCKED on purpose rather than silently loading a second one.
     */
    private fun unloadLocalModel() {
        viewModelScope.launch(Dispatchers.IO) {
            val provider = selectedLocalProvider()
            if (provider == null) {
                updateItem("litert_control", CheckStatus.NOT_CONFIGURED, "No on-device provider selected")
                return@launch
            }
            val unloaded = runCatching { provider.unloadLocalModel() }.getOrDefault(false)
            updateItem(
                "litert_control",
                if (unloaded) CheckStatus.READY else CheckStatus.FAILED,
                if (unloaded) "Local model unloaded — the direct control probe can run now"
                else "No resident local model to unload",
            )
            refreshChecks()
        }
    }

    private fun testAlarm() {
        updateItem("phone_alarm", CheckStatus.PASSED, "Alarm requested")
    }

    private fun testOpenApp() {
        updateItem("phone_app", CheckStatus.PASSED, "App launch requested")
    }

    private fun testVolume() {
        updateItem("phone_volume", CheckStatus.PASSED, "Volume change requested")
    }

    private fun updateItem(
        id: String,
        status: CheckStatus,
        reason: String,
        durationMs: Long? = null,
        detail: String? = null,
    ) {
        val updatedSections = _uiState.value.sections.map { section ->
            section.copy(items = section.items.map { item ->
                if (item.id == id) {
                    item.copy(
                        status = status,
                        reason = reason,
                        durationMs = durationMs ?: item.durationMs,
                        // null means "keep what the row already reported".
                        detail = detail ?: item.detail,
                    )
                } else item
            })
        }
        _uiState.value = _uiState.value.copy(sections = updatedSections)
        generateSummaryReport()
    }

    private fun generateSummaryReport() {
        val builder = StringBuilder()
        builder.append("Halo System Check Diagnostic Report\n")
        builder.append("===================================\n")
        for (section in _uiState.value.sections) {
            builder.append("\n[${section.title}]\n")
            for (item in section.items) {
                builder.append("  [${item.status.name}] ${item.name}: ${item.reason}")
                if (item.durationMs != null) builder.append(" (${item.durationMs}ms)")
                builder.append("\n")
            }
        }
        _uiState.value = _uiState.value.copy(summaryReport = builder.toString())
    }
}
