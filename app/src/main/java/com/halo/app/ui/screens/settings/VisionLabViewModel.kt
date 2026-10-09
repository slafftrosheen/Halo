package com.halo.app.ui.screens.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ai.readRuntimeAiContextPolicy
import com.halo.runtime.cognition.vision.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.multimodal.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID

data class VisionLabState(val candidates: List<VisionCandidate> = emptyList(), val selected: Set<String> = emptySet(),
    val rows: List<VisionLabRow> = emptyList(), val busy: Boolean = false, val source: String? = null,
    val message: String? = null, val localInstalled: Boolean = false)

class VisionLabViewModel : ViewModel() {
    private val _state = MutableStateFlow(VisionLabState())
    val state = _state.asStateFlow()
    private var image: MediaAssetRef? = null
    private val attachments by lazy { com.halo.app.capture.VisualAttachmentStore(Graph.applicationContext, Graph.mediaAssetStore) }
    private var pendingCamera: com.halo.app.capture.LocalCaptureStore.PendingCapture? = null
    private val interaction = "vision_lab:${UUID.randomUUID()}"
    fun refresh() { viewModelScope.launch(Dispatchers.IO) {
        _state.value = _state.value.copy(busy = true)
        val candidates = Graph.aiProviderRouter.getAllProviders().filterIsInstance<AiInferenceProvider>().flatMap { provider ->
            runCatching { provider.listModels() }.getOrDefault(emptyList()).filter { AiCapability.IMAGE in it.inputCapabilities && it.id != "openrouter/free" }
                .map { candidate ->
                    val proof = Graph.storage.getString(VisionLab.probeKey(provider.id, candidate.id))?.toLongOrNull()
                    VisionCandidate(provider, if (proof != null && candidate.executionLocation != AiExecutionLocation.ON_DEVICE) candidate.copy(capabilityEvidence = candidate.capabilityEvidence +
                        (AiCapability.IMAGE to CapabilityEvidence.HALO_PROBE_SUCCESS)) else candidate)
                }
        }
        _state.value = _state.value.copy(candidates = candidates, busy = false,
            localInstalled = candidates.any { it.model.executionLocation == AiExecutionLocation.ON_DEVICE })
    } }
    fun toggle(candidate: VisionCandidate) {
        val key = "${candidate.provider.id}/${candidate.model.id}"
        val selected = _state.value.selected
        _state.value = _state.value.copy(selected = if (key in selected) selected - key else (selected + key).take(8).toSet())
    }
    fun attach(uri: Uri) { viewModelScope.launch(Dispatchers.IO) {
        try {
            val attached = attachments.attach(uri, "Gallery", MediaProvenance.SHARED_FILE)
            image = attached.ref; _state.value = _state.value.copy(source = "Gallery", message = null)
        } catch (_: Exception) { _state.value = _state.value.copy(message = "Image acquisition failed") }
    } }
    fun useAttached() { viewModelScope.launch(Dispatchers.IO) {
        val attached = Graph.visualAttachments.state.value
        image = attached?.ref
        _state.value = _state.value.copy(source = attached?.source, message = if (attached == null) "No attached image" else null)
    } }
    suspend fun prepareCamera(): Uri {
        val pending = Graph.captures.preparePhoneCapture()
        pendingCamera = pending
        return pending.uri
    }
    fun cameraResult(received: Boolean) { viewModelScope.launch(Dispatchers.IO) {
        val pending = pendingCamera ?: return@launch
        pendingCamera = null
        try {
            if (received) {
                val attached = attachments.attachFile(pending.file)
                image = attached.ref; _state.value = _state.value.copy(source = "Phone camera", message = null)
            }
        } catch (_: Exception) { _state.value = _state.value.copy(message = "Phone camera acquisition failed") }
          finally { Graph.captures.discardPendingCapture(pending.file) }
    } }
    fun wearable() { viewModelScope.launch(Dispatchers.IO) {
        if (_state.value.busy) return@launch
        _state.value = _state.value.copy(busy = true)
        try {
            val coordinator = Graph.hostGraph.captureCoordinator ?: error("Wearable disconnected")
            val thumbnail = coordinator.captureAiThumbnail(publishForInference = false, review = false).getOrThrow()
            image = Graph.mediaAssetStore.ingest(MediaAssetContent(thumbnail.bytes, "image/jpeg"), MediaProvenance.HEYCYAN_CAMERA, interaction)
            _state.value = _state.value.copy(source = "HeyCyan camera", busy = false, message = null)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
          catch (_: Exception) { _state.value = _state.value.copy(busy = false, message = "Wearable capture unavailable") }
    } }
    fun assistantScreenshot() { viewModelScope.launch(Dispatchers.IO) {
        val active = com.halo.host.android.assistant.AssistantRuntimeStateStore.state.value
        val ref = active.interactionId?.takeIf { active.sessionVisible }?.let { Graph.assistantContextRepository.imageFor(it)?.mediaRef }
        image = ref
        _state.value = _state.value.copy(source = if (ref == null) null else "Assistant screenshot",
            message = if (ref == null) "No screenshot in the active assistant invocation" else null)
    } }
    override fun onCleared() {
        val pending = pendingCamera; pendingCamera = null
        Graph.infra.scope.launch(Dispatchers.IO) {
            attachments.remove(); Graph.mediaAssetStore.releaseInteraction(interaction)
            pending?.let { Graph.captures.discardPendingCapture(it.file) }
        }
        super.onCleared()
    }
    fun run(probe: Boolean = false) { viewModelScope.launch(Dispatchers.IO) {
        if (_state.value.busy) return@launch
        val chosen = _state.value.candidates.filter { "${it.provider.id}/${it.model.id}" in _state.value.selected }
        if (chosen.isEmpty()) { _state.value = _state.value.copy(message = "Select one to eight concrete models"); return@launch }
        var expected = emptyList<String>()
        val ref = if (probe) {
            val token = java.security.SecureRandom().nextInt(9000).plus(1000).toString()
            expected = listOf("HALO", token)
            val bitmap = Bitmap.createBitmap(300, 160, Bitmap.Config.ARGB_8888)
            val bytes = try {
                val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 40f }
                canvas.drawText("HALO", 25f, 60f, paint); canvas.drawText(token, 25f, 120f, paint)
                ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
            } finally { bitmap.recycle() }
            Graph.mediaAssetStore.ingest(MediaAssetContent(bytes, "image/png"), MediaProvenance.GENERATED, "vision_probe:${UUID.randomUUID()}")
        } else image ?: run { _state.value = _state.value.copy(message = "Choose an image first"); return@launch }
        _state.value = _state.value.copy(busy = true, message = null)
        try {
            val rows = Graph.visionLab.compare(ref, chosen, Graph.storage.readRuntimeAiContextPolicy(),
                if (probe) "Read the word and number printed in this image." else "Describe the important visible content in this image.", probe, expected)
            _state.value = _state.value.copy(rows = rows, busy = false)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
          catch (_: Exception) { _state.value = _state.value.copy(busy = false, message = "Vision diagnostic failed") }
        finally { if (probe) ref.interactionId?.let { Graph.mediaAssetStore.releaseInteraction(it) } }
    } }
}
