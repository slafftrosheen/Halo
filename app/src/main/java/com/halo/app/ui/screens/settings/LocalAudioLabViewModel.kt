package com.halo.app.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocalAudioLabState(
    val models: List<AiModel> = emptyList(), val modelId: String? = null,
    val hasAudio: Boolean = false, val busy: Boolean = false,
    val message: String? = null, val response: String? = null,
)

/** Explicit, local, tool-free diagnostic. Selection never changes the conversation's model. */
class LocalAudioLabViewModel : ViewModel() {
    private val mutable = MutableStateFlow(LocalAudioLabState())
    val state = mutable.asStateFlow()
    private var audio: ByteArray? = null
    private val operation = DiagnosticOperation(viewModelScope,
        started = { mutable.value = mutable.value.copy(busy = true, message = null, response = null) },
        cancelled = { mutable.value = mutable.value.copy(message = "Audio test cancelled") },
        failed = { error -> mutable.value = mutable.value.copy(message = error.message?.take(300) ?: "Audio test failed") },
        finished = { mutable.value = mutable.value.copy(busy = false) })
    private fun provider() = Graph.aiProviderRouter.getAllProviders().filterIsInstance<LiteRtLocalProvider>().firstOrNull()
    init { refresh() }

    fun refresh() = start {
        val models = provider()?.listModels().orEmpty().filter { AiCapability.AUDIO in it.inputCapabilities }
        mutable.value = mutable.value.copy(models = models,
            modelId = mutable.value.modelId?.takeIf { id -> models.any { it.id == id } })
    }
    fun select(id: String) {
        if (!mutable.value.busy && mutable.value.models.any { it.id == id })
            mutable.value = mutable.value.copy(modelId = id, response = null)
    }
    fun attach(uri: Uri) = start {
        // Drop the preceding sample before reading; never retain it on a failed replacement.
        releaseAudio()
        audio = Graph.applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_AUDIO_BYTES) { "Choose an audio file smaller than 20 MiB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray().also { com.halo.providers.litert.runtime.LiteRtAudioInput.validateEncoded(it) }
        } ?: error("The audio file could not be opened")
        mutable.value = mutable.value.copy(hasAudio = true, response = null, message = null)
    }
    fun run() = start {
        val bytes = audio ?: error("Choose an audio file first")
        val id = mutable.value.modelId ?: error("Choose an installed audio model")
        val local = provider() ?: error("Local inference is unavailable")
        require(AiCapability.AUDIO in local.capabilitiesForModel(id)) { "This model no longer supports audio input" }
        val response = local.generate(AiRequest(text = "Transcribe the speech in this audio. Return only the transcript.",
            audio = bytes, modelId = id, maxOutputTokens = 256, toolUseMode = ToolUseMode.NONE,
            reasoningMode = AiReasoningMode.DISABLED), AiContext.EMPTY)
        mutable.value = mutable.value.copy(response = response.text, message = "Audio test completed")
    }
    private fun start(block: suspend () -> Unit) = operation.start(block)
    fun cancel() { operation.cancel() }
    fun clearAudio() {
        if (mutable.value.busy) return
        releaseAudio()
    }
    private fun releaseAudio() {
        audio?.fill(0); audio = null
        mutable.value = mutable.value.copy(hasAudio = false, response = null)
    }
    override fun onCleared() { operation.cancel(); audio = null; super.onCleared() }
    companion object { const val MAX_AUDIO_BYTES = com.halo.providers.litert.runtime.LiteRtAudioInput.MAX_BYTES }
}
