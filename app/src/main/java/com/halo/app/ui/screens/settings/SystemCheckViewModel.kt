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
import com.halo.runtime.observer.InferenceLedger
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

data class SystemCheckUiState(
    val items: List<SystemCheckItem> = emptyList(),
    val isRunning: Boolean = false,
    val summaryReport: String = "",
)

class SystemCheckViewModel(
    private val appContext: Context,
    private val providerRouter: AiProviderRouter,
    private val modelManager: LiteRtModelManager? = null,
    private val inferenceLedger: InferenceLedger? = null,
    val assistantRoleController: AndroidAssistantRoleController = AndroidAssistantRoleController(appContext),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemCheckUiState())
    val uiState: StateFlow<SystemCheckUiState> = _uiState.asStateFlow()

    init {
        refreshChecks()
    }

    fun refreshChecks() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<SystemCheckItem>()

            // 1. Core Check
            list.add(
                SystemCheckItem(
                    id = "core",
                    name = "Core Runtime",
                    glyph = HaloGlyph.HALO,
                    status = CheckStatus.READY,
                    reason = "System Check is running; this does not prove an inference path"
                )
            )

            // 2. Android Assistant
            assistantRoleController.refresh()
            val integration = assistantRoleController.integrationState.value
            val runtime = AssistantRuntimeStateStore.state.value
            val assistantStatus = when (integration) {
                is AssistantIntegrationState.Active -> CheckStatus.READY to
                    "Role held; interactor active; service=${runtime.serviceReady}; session=${runtime.sessionCreated}; capture=${runtime.captureState}"
                is AssistantIntegrationState.RoleHeldNotActive -> CheckStatus.BLOCKED to
                    "Role held, but Android interactor is ${integration.diagnostics.activeInteractorStatus} (${integration.diagnostics.activeInteractorComponent ?: "unavailable"})"
                is AssistantIntegrationState.EligibleNotSelected -> CheckStatus.NOT_CONFIGURED to
                    "Candidate=yes; default role=no; interactor=${integration.diagnostics.activeInteractorStatus}"
                is AssistantIntegrationState.NotEligible -> CheckStatus.UNSUPPORTED to integration.diagnostics.problems.joinToString().ifBlank { "Not eligible" }
                is AssistantIntegrationState.Error -> CheckStatus.FAILED to integration.message
                AssistantIntegrationState.Checking -> CheckStatus.RUNNING to "Reading Android assistant state"
            }
            list.add(
                SystemCheckItem(
                    id = "assistant",
                    name = "Android Assistant",
                    glyph = HaloGlyph.PHONE,
                    status = assistantStatus.first,
                    reason = assistantStatus.second
                )
            )

            // 3. Microphone / STT
            val audioPermission = ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            list.add(
                SystemCheckItem(
                    id = "mic",
                    name = "Microphone & STT",
                    glyph = HaloGlyph.LISTEN,
                    status = if (audioPermission) CheckStatus.READY else CheckStatus.NOT_CONFIGURED,
                    reason = if (audioPermission) "RECORD_AUDIO granted" else "RECORD_AUDIO permission missing"
                )
            )

            // 4. Selected AI execution target. Refreshing this row never runs generation.
            val selectedProvider = providerRouter.selectedProviderId.value
            val selectedModel = providerRouter.selectedModelId.value
            val provider = providerRouter.getAllProviders().firstOrNull { it.id == selectedProvider }
            val providerStatus = provider?.let { runCatching { it.getStatus() }.getOrNull() }
            val localProvider = provider as? LiteRtLocalProvider
            val localDiagnostics = localProvider?.loadDiagnostics?.value
            val acquisitionDiagnostics = modelManager?.acquisitionDiagnostics?.value
            val execution = providerRouter.executionDiagnostics.value
            val observedExecution = inferenceLedger?.lastExecution?.value
            val useObserved = observedExecution != null &&
                observedExecution.recordedAtMs >= (execution.recordedAtMs ?: Long.MIN_VALUE)
            val lastProviderId = if (useObserved) observedExecution?.providerId else execution.providerId
            val lastModelId = if (useObserved) observedExecution?.modelId else execution.modelId
            val executionMatchesSelection = lastProviderId == selectedProvider && lastModelId == selectedModel
            list.add(
                SystemCheckItem(
                    id = "ai_selection",
                    name = "AI Selection",
                    glyph = HaloGlyph.MODEL,
                    status = if (provider != null && selectedModel != null) CheckStatus.PASSED else CheckStatus.NOT_CONFIGURED,
                    reason = "provider=${selectedProvider ?: "none"}; model=${selectedModel ?: "none"}",
                )
            )
            list.add(
                SystemCheckItem(
                    id = "ai_configuration",
                    name = "AI Configuration",
                    glyph = HaloGlyph.MODEL,
                    status = when (providerStatus?.state) {
                        com.halo.sdk.provider.ProviderStatus.State.UNCONFIGURED,
                        com.halo.sdk.provider.ProviderStatus.State.MODEL_MISSING -> CheckStatus.NOT_CONFIGURED
                        com.halo.sdk.provider.ProviderStatus.State.ERROR,
                        com.halo.sdk.provider.ProviderStatus.State.OFFLINE -> CheckStatus.FAILED
                        null -> CheckStatus.NOT_CONFIGURED
                        else -> CheckStatus.READY
                    },
                    reason = "state=${providerStatus?.state ?: "unknown"}; ${providerStatus?.message.orEmpty()}",
                )
            )
            if (localProvider != null) {
                val resident = localProvider.modelSession.isLoaded() &&
                    localProvider.modelSession.loadedModelId == selectedModel
                list.add(
                    SystemCheckItem(
                        id = "ai_artifact",
                        name = "Local Artifact",
                        glyph = HaloGlyph.MODEL,
                        status = when (providerStatus?.state) {
                            com.halo.sdk.provider.ProviderStatus.State.UNCONFIGURED,
                            com.halo.sdk.provider.ProviderStatus.State.MODEL_MISSING -> CheckStatus.NOT_CONFIGURED
                            com.halo.sdk.provider.ProviderStatus.State.ERROR -> CheckStatus.FAILED
                            else -> CheckStatus.READY
                        },
                        reason = localDiagnostics?.technicalSummary()
                            ?: acquisitionDiagnostics?.technicalSummary()
                            ?: when (providerStatus?.state) {
                                com.halo.sdk.provider.ProviderStatus.State.UNCONFIGURED -> "artifact=unknown; no selected model"
                                com.halo.sdk.provider.ProviderStatus.State.MODEL_MISSING -> "artifact=missing"
                                else -> "artifact=valid"
                            },
                    )
                )
                list.add(
                    SystemCheckItem(
                        id = "ai_residency",
                        name = "Model Residency",
                        glyph = HaloGlyph.MODEL,
                        status = if (resident) CheckStatus.PASSED else CheckStatus.NOT_CONFIGURED,
                        reason = if (resident) {
                            "resident=true; backend=${localProvider.modelSession.activeBackend ?: "unknown"}"
                        } else {
                            "resident=false; selected model has not been loaded in this process"
                        },
                    )
                )
            } else {
                list.add(
                    SystemCheckItem(
                        id = "ai_reachability",
                        name = "Provider Reachability",
                        glyph = HaloGlyph.MODEL,
                        status = when (providerStatus?.state) {
                            com.halo.sdk.provider.ProviderStatus.State.READY -> CheckStatus.PASSED
                            com.halo.sdk.provider.ProviderStatus.State.OFFLINE -> CheckStatus.FAILED
                            else -> CheckStatus.NOT_CONFIGURED
                        },
                        reason = providerStatus?.message ?: "No reachability evidence",
                    )
                )
            }
            list.add(
                SystemCheckItem(
                    id = "ai_execution",
                    name = "Last AI Generation",
                    glyph = HaloGlyph.MODEL,
                    status = when {
                        !useObserved && execution.inFlight && executionMatchesSelection -> CheckStatus.RUNNING
                        useObserved && observedExecution?.success == true && executionMatchesSelection -> CheckStatus.PASSED
                        useObserved && observedExecution?.success == false && executionMatchesSelection -> CheckStatus.FAILED
                        !useObserved && execution.lastSuccessAtMs != null && executionMatchesSelection -> CheckStatus.PASSED
                        !useObserved && execution.lastFailureStage != null && executionMatchesSelection -> CheckStatus.FAILED
                        else -> CheckStatus.NOT_CONFIGURED
                    },
                    reason = buildString {
                        if (!executionMatchesSelection) {
                            append("No generation recorded for the selected provider/model")
                        } else {
                            append("successAt=${if (useObserved && observedExecution?.success == true) observedExecution?.recordedAtMs else execution.lastSuccessAtMs ?: "none"}")
                            append("; latencyMs=${if (useObserved) observedExecution?.latencyMs ?: "unknown" else execution.lastLatencyMs ?: "unknown"}")
                            append("; backend=${if (useObserved) observedExecution?.backend ?: localProvider?.modelSession?.activeBackend ?: "unknown" else execution.lastBackend ?: localProvider?.modelSession?.activeBackend ?: "unknown"}")
                            val failureStage = localDiagnostics?.stage?.name
                                ?: acquisitionDiagnostics?.stage?.name
                                ?: if (useObserved) observedExecution?.failureStage else execution.lastFailureStage
                            failureStage?.let { append("; failureStage=$it") }
                            (if (useObserved) observedExecution?.failureType else execution.lastFailureType)
                                ?.let { append("; failureType=$it") }
                            val diagnosticsId = localDiagnostics?.diagnosticsId?.take(8)
                                ?: acquisitionDiagnostics?.diagnosticsId?.take(8)
                                ?: if (useObserved) observedExecution?.requestId?.take(8) else execution.diagnosticsId?.take(8)
                            append("; diagnosticsId=${diagnosticsId ?: "none"}")
                        }
                    }
                )
            )

            // 5. Audio Route
            list.add(
                SystemCheckItem(
                    id = "audio",
                    name = "Audio Output Route",
                    glyph = HaloGlyph.SPEAK,
                    status = if (runtime.outputRoute != null) CheckStatus.READY else CheckStatus.NOT_CONFIGURED,
                    reason = "input=${runtime.inputRoute ?: "not observed"}; output=${runtime.outputRoute ?: "not observed"}"
                )
            )

            _uiState.value = _uiState.value.copy(items = list)
            generateSummaryReport()
        }
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

    fun runAiProbe() {
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

    fun testAudioOutput() {
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

    private fun updateItem(id: String, status: CheckStatus, reason: String, durationMs: Long? = null) {
        val list = _uiState.value.items.map {
            if (it.id == id) it.copy(status = status, reason = reason, durationMs = durationMs) else it
        }
        _uiState.value = _uiState.value.copy(items = list)
        generateSummaryReport()
    }

    private fun generateSummaryReport() {
        val builder = StringBuilder()
        builder.append("Halo System Check Diagnostic Report\n")
        builder.append("===================================\n")
        for (item in _uiState.value.items) {
            builder.append("[${item.status.name}] ${item.name}: ${item.reason}")
            if (item.durationMs != null) builder.append(" (${item.durationMs}ms)")
            builder.append("\n")
        }
        _uiState.value = _uiState.value.copy(summaryReport = builder.toString())
    }
}
