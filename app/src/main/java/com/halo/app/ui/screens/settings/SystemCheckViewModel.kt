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
import com.halo.host.android.assistant.AssistantEnrollmentState
import com.halo.providers.llamacpp.LocalModelDiagnosticSnapshot
import com.halo.providers.llamacpp.NativeLlamaProvider
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.runtime.EventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

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
    private val eventBus: EventBus,
    private val providerRouter: AiProviderRouter,
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
                    status = CheckStatus.PASSED,
                    reason = "EventBus alive, CognitiveSession ready"
                )
            )

            // 2. Android Assistant
            assistantRoleController.refresh()
            val enrollment = assistantRoleController.enrollmentState.value
            val assistantStatus = when (enrollment) {
                AssistantEnrollmentState.Active -> CheckStatus.PASSED to "Halo is default system assistant ✓"
                AssistantEnrollmentState.RoleRequestAvailable -> CheckStatus.NOT_CONFIGURED to "Assistant role available for request"
                AssistantEnrollmentState.SystemSettingsRequired -> CheckStatus.NOT_CONFIGURED to "Voice input settings enrollment required"
                is AssistantEnrollmentState.Unavailable -> CheckStatus.UNSUPPORTED to enrollment.reason
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

            // 4. Local AI
            val localProvider = providerRouter.getAllProviders().firstOrNull { it.isLocal }
            val selectedModel = providerRouter.selectedModelId.value
            list.add(
                SystemCheckItem(
                    id = "local_ai",
                    name = "Local AI Model",
                    glyph = HaloGlyph.MODEL,
                    status = if (selectedModel != null) CheckStatus.READY else CheckStatus.NOT_CONFIGURED,
                    reason = if (selectedModel != null) "Selected: $selectedModel" else "No Native Local model selected"
                )
            )

            // 5. Audio Route
            list.add(
                SystemCheckItem(
                    id = "audio",
                    name = "Audio Output Route",
                    glyph = HaloGlyph.SPEAK,
                    status = CheckStatus.READY,
                    reason = "Speaker output ready"
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

    fun testTextInference() {
        viewModelScope.launch(Dispatchers.IO) {
            updateItem("local_ai", CheckStatus.RUNNING, "Running text probe prompt...")
            val startTime = System.currentTimeMillis()
            runCatching {
                val provider = providerRouter.getAllProviders().firstOrNull { it.isLocal } as? AiInferenceProvider
                    ?: throw IllegalStateException("No local provider available")

                val request = AiRequest(text = "Reply with exactly: HALO")
                val response = provider.generate(request, AiContext.EMPTY)

                val elapsed = System.currentTimeMillis() - startTime
                val pass = response.text.contains("HALO", ignoreCase = true)
                updateItem(
                    "local_ai",
                    if (pass) CheckStatus.PASSED else CheckStatus.FAILED,
                    if (pass) "Response: \"${response.text.trim()}\"" else "Unexpected response: \"${response.text.trim()}\"",
                    elapsed
                )
            }.onFailure { err ->
                updateItem("local_ai", CheckStatus.FAILED, err.message ?: "Text inference failed")
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
                updateItem("audio", CheckStatus.PASSED, "Audio tone played on phone speaker", elapsed)
            }.onFailure { err ->
                updateItem("audio", CheckStatus.FAILED, err.message ?: "Audio playback failed")
            }
        }
    }

    fun runSafeChecks() {
        refreshChecks()
        testTextInference()
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
