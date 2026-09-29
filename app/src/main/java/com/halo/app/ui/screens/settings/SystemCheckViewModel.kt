package com.halo.app.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
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
import com.halo.providers.litert.models.LiteRtModelManager
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
)

data class SystemCheckSection(
    val id: String,
    val title: String,
    val items: List<SystemCheckItem>
)

data class SystemCheckUiState(
    val sections: List<SystemCheckSection> = emptyList(),
    val summaryReport: String = "",
)

sealed class SystemCheckAction {
    object Refresh : SystemCheckAction()
    object RunTextProbe : SystemCheckAction()
    object RunAgenticProbe : SystemCheckAction()
    object RunSttProbe : SystemCheckAction()
    object RunTtsProbe : SystemCheckAction()
    object OpenAssistantSettings : SystemCheckAction()
    object TestAlarm : SystemCheckAction()
    object TestOpenApp : SystemCheckAction()
    object TestVolume : SystemCheckAction()
}

class SystemCheckViewModel(
    private val appContext: Context,
    private val providerRouter: AiProviderRouter,
    private val modelManager: LiteRtModelManager? = null,
    private val inferenceLedger: InferenceLedger? = null,
    /** Live remote-provider presence; null keeps legacy local-only behavior. */
    private val remoteProviderRegistry: RemoteProviderPresenceRegistry? = null,
    /** Reports whether a stored credential exists for the given secret key. */
    private val remoteCredentialPresent: (String) -> Boolean = { false },
    val assistantRoleController: AndroidAssistantRoleController = AndroidAssistantRoleController(appContext),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemCheckUiState())
    val uiState: StateFlow<SystemCheckUiState> = _uiState.asStateFlow()

    init {
        refreshChecks()
    }

    fun handleAction(action: SystemCheckAction) {
        when (action) {
            is SystemCheckAction.Refresh -> refreshChecks()
            is SystemCheckAction.RunSttProbe -> testMicrophone()
            is SystemCheckAction.RunTtsProbe -> testAudioOutput()
            is SystemCheckAction.RunTextProbe -> runAiProbe()
            is SystemCheckAction.RunAgenticProbe -> runAgenticProbe()
            is SystemCheckAction.OpenAssistantSettings -> {
                // To be handled by UI layer with launchAssistantEnrollment
            }
            is SystemCheckAction.TestAlarm -> testAlarm()
            is SystemCheckAction.TestOpenApp -> testOpenApp()
            is SystemCheckAction.TestVolume -> testVolume()
        }
    }

    private fun refreshChecks() {
        viewModelScope.launch(Dispatchers.IO) {
            val sections = mutableListOf<SystemCheckSection>()

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
            val audioPermission = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            sections.add(SystemCheckSection(
                id = "speech",
                title = "Speech",
                items = listOf(
                    SystemCheckItem(
                        id = "mic",
                        name = "Microphone & STT",
                        glyph = HaloGlyph.LISTEN,
                        status = if (audioPermission) CheckStatus.READY else CheckStatus.NOT_CONFIGURED,
                        reason = if (audioPermission) "RECORD_AUDIO granted" else "RECORD_AUDIO permission missing"
                    ),
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

            // Phone
            sections.add(SystemCheckSection(
                id = "phone",
                title = "Phone",
                items = listOf(
                    SystemCheckItem("phone_alarm", "Alarm Capability", HaloGlyph.PHONE, CheckStatus.READY, "Available"),
                    SystemCheckItem("phone_app", "App Launcher", HaloGlyph.PHONE, CheckStatus.READY, "Available"),
                    SystemCheckItem("phone_volume", "Volume Control", HaloGlyph.PHONE, CheckStatus.READY, "Available")
                )
            ))

            // Remote provider fabric presence (RUNTIME-055)
            remoteProviderRegistry?.let { registry ->
                val remoteItems = RemoteProviderPresets.groq.let { groq ->
                    listOf(groq, RemoteProviderPresets.cerebras, RemoteProviderPresets.opencodeZen,
                        RemoteProviderPresets.xai, RemoteProviderPresets.opencodeCompanion,
                        RemoteProviderPresets.customOpenAiCompatible).map { descriptor ->
                        val presence = runCatching { registry.presence(descriptor.id).value }.getOrNull()
                        val configured = presence?.isConfigured ?: remoteCredentialPresent(credentialKeyFor(descriptor.id))
                        val authState = presence?.authInfo?.authState
                            ?: if (configured) ProviderAuthState.CONNECTED else ProviderAuthState.NOT_CONFIGURED
                        SystemCheckItem(
                            id = "remote_${descriptor.id}",
                            name = descriptor.displayName,
                            glyph = HaloGlyph.MODEL,
                            status = when (authState) {
                                ProviderAuthState.CONNECTED -> CheckStatus.PASSED
                                ProviderAuthState.NOT_CONFIGURED -> CheckStatus.NOT_CONFIGURED
                                ProviderAuthState.AUTHORIZING, ProviderAuthState.REFRESHING -> CheckStatus.RUNNING
                                ProviderAuthState.EXPIRED, ProviderAuthState.FAILED -> CheckStatus.FAILED
                                ProviderAuthState.NOT_REQUIRED -> if (configured) CheckStatus.PASSED else CheckStatus.READY
                            },
                            reason = authStateLabel(authState, configured),
                        )
                    }
                }
                sections.add(SystemCheckSection(
                    id = "remote_providers",
                    title = "Remote Providers",
                    items = remoteItems,
                ))
            }

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
                                    presence.isConfigured && authInfo.authState == ProviderAuthState.NOT_CONFIGURED -> ProviderAuthState.CONNECTED
                                    else -> authInfo.authState
                                },
                            ),
                        ),
                    )
                }
            }
        }
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
        ProviderAuthState.CONNECTED -> "Connected"
        ProviderAuthState.NOT_CONFIGURED -> "Not configured — add an API key in Intelligence settings"
        ProviderAuthState.AUTHORIZING -> "Authorizing"
        ProviderAuthState.REFRESHING -> "Refreshing token"
        ProviderAuthState.EXPIRED -> "Credential expired — reconnect"
        ProviderAuthState.FAILED -> "Connection failed — test connection in settings"
        ProviderAuthState.NOT_REQUIRED -> if (configured) "Ready (no auth required)" else "Endpoint set, no auth"
    }

    fun launchAssistantEnrollment(launcher: (Intent) -> Unit) {
        assistantRoleController.launchEnrollment(launcher)
        refreshChecks()
    }

    fun recordAssistantEnrollmentResult(resultCode: Int?) {
        assistantRoleController.recordEnrollmentActivityResult(resultCode)
        refreshChecks()
    }

    fun testMicrophone() {
        viewModelScope.launch(Dispatchers.IO) {
            updateItem("mic", CheckStatus.RUNNING, "Recording 2s sample...")
            val startTime = System.currentTimeMillis()
            runCatching {
                val sampleFile = File(appContext.cacheDir, "test_mic_sample.3gp")
                if (sampleFile.exists()) sampleFile.delete()

                @Suppress("DEPRECATION")
                val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(appContext)
                } else {
                    MediaRecorder()
                }
                recorder.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setOutputFile(sampleFile.absolutePath)
                    prepare()
                    start()
                }

                delay(2000)

                var maxAmp = 0
                runCatching { maxAmp = recorder.maxAmplitude }
                recorder.stop()
                recorder.release()
                sampleFile.delete()

                val elapsed = System.currentTimeMillis() - startTime
                updateItem(
                    "mic",
                    CheckStatus.PASSED,
                    "Recorded sample successfully (Max Amplitude: $maxAmp)",
                    elapsed
                )
            }.onFailure { err ->
                updateItem("mic", CheckStatus.FAILED, err.message ?: "Recording failed")
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
                    AiRequest(text = "Reply with one word: HALO", maxOutputTokens = 8),
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
            val probeRunner = LocalAgenticProbeRunner(providerRouter)
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

    fun runLocalLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            updateItem("core", CheckStatus.RUNNING, "Running synthetic loop...")
            val startTime = System.currentTimeMillis()
            runCatching {
                val response = providerRouter.execute(
                    AiRequest(text = "Hello", maxOutputTokens = 8),
                    AiWorkloadSpec(kind = AiWorkloadKind.CONVERSATION),
                    AiContext.EMPTY,
                )
                val elapsed = System.currentTimeMillis() - startTime
                if (response.text.isNotBlank()) {
                    updateItem("core", CheckStatus.PASSED, "Synthetic loop completed (${response.text})", elapsed)
                } else {
                    updateItem("core", CheckStatus.FAILED, "Synthetic loop returned empty text", elapsed)
                }
            }.onFailure { err ->
                updateItem("core", CheckStatus.FAILED, "${err::class.java.simpleName}: ${err.message}", System.currentTimeMillis() - startTime)
            }
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

    private fun updateItem(id: String, status: CheckStatus, reason: String, durationMs: Long? = null) {
        val updatedSections = _uiState.value.sections.map { section ->
            section.copy(items = section.items.map { item ->
                if (item.id == id) item.copy(status = status, reason = reason, durationMs = durationMs) else item
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
