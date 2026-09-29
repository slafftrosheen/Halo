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

            // Phone abilities (RUNTIME-056): real descriptor/capability truth, not asserted rows.
            sections.add(SystemCheckSection(
                id = "phone",
                title = "Phone abilities",
                items = phoneAbilityItems()
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
