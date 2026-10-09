package com.halo.app.capture

import android.content.Context
import android.net.Uri
import com.halo.sdk.runtime.multimodal.*
import com.halo.sdk.provider.*
import com.halo.app.ai.readRuntimeAiContextPolicy
import com.halo.app.data.workspace.NotebookAssetRepository
import com.halo.app.data.memory.MemoryCandidate
import com.halo.app.data.notebook.WorkspaceObjectId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Workflow only: bytes remain owned by the existing ephemeral store or Workspace. */
class ContextualCaptureController(private val context: Context, private val media: MediaAssetStore,
    private val captures: LocalCaptureStore, private val attachments: VisualAttachmentStore,
    private val assets: NotebookAssetRepository, private val scope: CoroutineScope,
    private val mediaPolicy: com.halo.app.data.workspace.WorkspaceMediaAiPolicy,
    private val workflowStorage: com.halo.sdk.runtime.Storage? = null) {
    private val mutable = MutableStateFlow<ContextualCaptureState?>(null)
    val state = mutable.asStateFlow()
    val visible = MutableStateFlow(false)
    private val pendingKey = "contextual_capture_unsaved"
    val notice = MutableStateFlow<String?>(if(workflowStorage?.getBoolean(pendingKey,false) == true)
        "This temporary image is no longer available. Previously saved images are still in Notebook." else null)
    private val lock = Mutex()
    private val seen = linkedSetOf<String>()
    private val sourceAcquisition = Mutex()
    private val attemptedSources = linkedSetOf<String>()
    val saveTitle = MutableStateFlow("")
    val saveDescription = MutableStateFlow("")
    private val events = Channel<ContextualCaptureNavigation>(Channel.BUFFERED)
    val navigation = events.receiveAsFlow()
    var memoryCandidate: MemoryCandidate? = null
        private set

    suspend fun receive(content: MediaAssetContent, source: CaptureSource, label: String,
        operationId: String = UUID.randomUUID().toString(), deviceId: String? = null,
        quality: CaptureQuality = CaptureQuality.ORIGINAL, sourceAssetId: String? = null,
        captureTimestampMs: Long? = null, trigger: String? = null) = lock.withLock {
        if (operationId in seen) return@withLock
        check(mutable.value == null) { "Finish or discard the current capture first" }
        require(content.bytes.size in 1..ImageInputLimits().maxSourceBytes)
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(content.bytes, 0, content.bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "No valid image received" }
        val provenance = when(source) {
            CaptureSource.PHONE_CAMERA -> MediaProvenance.PHONE_CAMERA
            CaptureSource.MANAGED_WEARABLE_CAMERA -> MediaProvenance.UNKNOWN
            CaptureSource.IMPORTED_IMAGE -> MediaProvenance.IMPORTED
            CaptureSource.SHARED_IMAGE -> MediaProvenance.SHARED_FILE
            CaptureSource.ASSISTANT_SCREENSHOT -> MediaProvenance.SCREEN_CAPTURE
        }
        val ref = media.ingest(MediaAssetContent(content.bytes, bounds.outMimeType ?: content.mimeType), provenance,
            "capture:$operationId", deviceId)
        mutable.value = ContextualCaptureState(operationId, source, label, ref, bounds.outWidth, bounds.outHeight, quality, sourceAssetId=sourceAssetId, captureTimestampMs=captureTimestampMs, trigger=trigger)
        seen.add(operationId); while(seen.size > 256) seen.remove(seen.first())
        workflowStorage?.putBoolean(pendingKey,true); notice.value = null
        saveTitle.value = ""; saveDescription.value = ""
        visible.value = true
    }
    /** Future cameras supply receipt through the existing host-owned still-image seam. */
    suspend fun captureStill(source: com.halo.sdk.driver.StillImageSource, request: com.halo.sdk.driver.StillCaptureRequest,
        label: String, quality: CaptureQuality = CaptureQuality.UNKNOWN) = sourceAcquisition.withLock {
        val id = request.captureId.value
        if(id in attemptedSources) {
            check(state.value?.captureId == id) { "Capture already requested. Start a new explicit capture; do not retry a possible dispatch." }
            return@withLock
        }
        check(state.value == null) { "Finish the current capture first" }
        attemptedSources.add(id); while(attemptedSources.size > 256) attemptedSources.remove(attemptedSources.first())
        val asset = source.captureStill(request).getOrThrow()
        val bytes = asset.bytes ?: asset.uri?.let { value -> withContext(Dispatchers.IO) {
            val uri = Uri.parse(value)
            require(uri.scheme in setOf("content", "file")) { "Unsupported camera media reference" }
            context.contentResolver.openInputStream(uri)?.use { it.readBytesBounded() }
        } } ?: error("No image bytes received")
        receive(MediaAssetContent(bytes,asset.mimeType),CaptureSource.MANAGED_WEARABLE_CAMERA,label,
            id,asset.originDeviceId,quality,asset.id,asset.capturedAtMs,request.origin.name)
    }
    suspend fun receiveUri(uri: Uri, source: CaptureSource = CaptureSource.IMPORTED_IMAGE) {
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytesBounded() } ?: error("Image unavailable")
        }
        receive(MediaAssetContent(bytes, context.contentResolver.getType(uri) ?: "image/jpeg"), source,
            if(source == CaptureSource.SHARED_IMAGE) "Shared image" else "Imported image")
    }
    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while(true) { val n = read(buffer); if(n < 0) break
            require(out.size() + n <= ImageInputLimits().maxSourceBytes) { "Image exceeds input budget" }; out.write(buffer,0,n) }
        return out.toByteArray()
    }
    suspend fun receivePhone(file: java.io.File, success: Boolean) {
        if (!success) notice.value = "Photo cancelled. No image was received."
        try { if(success) withContext(Dispatchers.IO) {
            require(file.length() in 1..ImageInputLimits().maxSourceBytes.toLong())
            receive(MediaAssetContent(file.readBytes(), "image/jpeg"), CaptureSource.PHONE_CAMERA, "Phone camera", file.nameWithoutExtension)
        } } finally { captures.discardPendingCapture(file) }
    }
    suspend fun save(): String = lock.withLock { saveLocked() }
    private suspend fun saveLocked(): String {
        val capture = requireNotNull(mutable.value)
        capture.workspaceObjectId?.let { return it }
        mutable.value = capture.copy(status = CaptureStatus.SAVING, error = null)
        val pending = try { captures.preparePhoneCapture() }
        catch(error: Exception) {
            mutable.value = capture.copy(status = if(error is CancellationException) CaptureStatus.CANCELLED else CaptureStatus.FAILED,
                error = if(error is CancellationException) "Save cancelled. Your image is still available." else "Save failed. Your image is still available.")
            throw error
        }
        try {
            val content = media.open(capture.mediaRef)
            withContext(Dispatchers.IO) { pending.file.writeBytes(content.bytes) }
            val id = captures.receive(pending.file, capture.mediaRef.sourceDeviceId, capture.captureId, publishForInference = false, sourceLabel = capture.label, sourceKind = capture.source.name, captureQuality = capture.quality.name, captureTimestampMs = capture.captureTimestampMs, trigger = capture.trigger, title = saveTitle.value, body = saveDescription.value)
            mediaPolicy.associateSaved(capture.mediaRef.id, id)
            workflowStorage?.putBoolean(pendingKey,false)
            mutable.value = capture.copy(workspaceObjectId = id, status = CaptureStatus.SAVED)
            return id
        } catch(error: Exception) {
            captures.discardPendingCapture(pending.file)
            mutable.value = capture.copy(status = if(error is CancellationException) CaptureStatus.CANCELLED else CaptureStatus.FAILED,
                error = if(error is CancellationException) "Save cancelled. Your image is still available." else "Save failed. Your image is still available.")
            throw error
        }
    }
    fun action(action: CaptureAction, mode: CaptureExtractionMode? = null) = scope.launch {
        try { lock.withLock {
            val capture = requireNotNull(mutable.value)
            if(action in setOf(CaptureAction.ASK, CaptureAction.EXTRACT, CaptureAction.STUDY)) {
                val graph = com.halo.app.di.ManualDependencyGraph
                val policy = graph.storage.readRuntimeAiContextPolicy()
                check(media.metadata(capture.mediaRef) != null) { "The image source is unavailable. Reattach it; your question is kept." }
                check(policy.includeImages) { "Image context is disabled. Enable it in Context privacy; the capture is kept." }
                check(graph.workspaceMediaAiPolicy.permits(capture.mediaRef, true)) { "This Workspace image is excluded from AI or its source is unavailable. Review the source's privacy settings; the capture is kept." }
                val source = media.open(capture.mediaRef)
                graph.imageInputPreparer.prepare(source, capture.mediaRef)
                val remoteAllowed = policy.allowCloudImages && graph.workspaceMediaAiPolicy.permits(capture.mediaRef, false)
                val target = requireNotNull(graph.aiProviderRouter.resolveExecutionTarget(AiRequest(text="Capture analysis setup"),
                    AiWorkloadSpec(kind=AiWorkloadKind.VISUAL_UNDERSTANDING, requiredCapabilities=setOf(AiCapability.TEXT,AiCapability.IMAGE),
                        allowRemote=remoteAllowed, preferredRole=AiModelRole.VISION))) {
                    if (!remoteAllowed) "Cloud image analysis is not authorized and no eligible local vision model is configured. Review Context privacy and this image's sharing permission, or choose local vision."
                    else "The configured Vision role has no eligible image model. Choose a vision model; the capture is kept."
                }
                val local = target.model.executionLocation == AiExecutionLocation.ON_DEVICE
                check(CaptureWorkflowPolicy.permitsModel(target.model, policy, graph.workspaceMediaAiPolicy.permits(capture.mediaRef, local))) {
                    "The image is ready, but the model or privacy settings do not permit image analysis. Choose a vision model or save the image."
                }
                if(local) {
                    val vision = inspectMultimodal(graph.aiProviderRouter)
                    check(vision.target?.providerId == target.providerId && vision.target?.modelId == target.modelId) { "The exact local vision target is not qualified. Configure vision; the capture is kept." }
                    check(vision.packs.any { it.descriptor.id == com.halo.sdk.runtime.capability.RuntimeCapabilityId.LOCAL_VISION && it.state == com.halo.sdk.runtime.capability.RuntimeCapabilityState.READY }) { "Local vision is not qualified. Choose a vision model or save the image." }
                }
            }
            when(action) {
                CaptureAction.SAVE -> saveLocked()
                CaptureAction.STUDY -> { val id = saveLocked(); events.send(ContextualCaptureNavigation.Study(id)); visible.value = false }
                CaptureAction.ASK, CaptureAction.EXTRACT -> {
                    val sourceRef = capture.workspaceObjectId?.let { id ->
                        val image = assets.observe(id).first().single { it.type == "IMAGE" }
                        MediaAssetRef("attachment:$id:${image.assetId}", MediaKind.IMAGE, image.mimeType, image.bytes, assets.captureHash(id),
                            capture.mediaRef.createdAtMs, MediaProvenance.WORKSPACE, MediaLifetime.WORKSPACE_PERSISTED, workspaceObjectId=id)
                    } ?: capture.mediaRef
                    if (capture.selectedAction == action && attachments.state.value?.ref?.id == sourceRef.id) {
                        visible.value = false
                        return@withLock
                    }
                    attachments.attachReference(sourceRef, capture.label)
                    com.halo.app.di.ManualDependencyGraph.homeViewModel.selectedMode.value = com.halo.runtime.cognition.context.CognitiveOperatingMode.ASK
                    mutable.value = capture.copy(selectedAction = action)
                    if(mode != null) com.halo.app.di.ManualDependencyGraph.homeViewModel.captureQuestionDraft.value = mode.prompt
                    events.send(ContextualCaptureNavigation.Ask); visible.value = false
                }
                CaptureAction.REMEMBER -> Unit // editable candidate is required; use reviewMemory.
            }
        } } catch(cancelled: CancellationException) {
            mutable.value = mutable.value?.copy(status = CaptureStatus.CANCELLED, error = "Action cancelled. Your image is still available.")
            throw cancelled
        }
        catch(error: Exception) { mutable.value = mutable.value?.copy(error = error.message ?: "Action unavailable") }
    }
    fun reviewMemory(text: String, modelInferred: Boolean = false) = scope.launch {
        if(text.isBlank()) return@launch
        try { lock.withLock {
            com.halo.runtime.cognition.memory.MemorySensitivityClassifier.requireWritable(text)
            val id = saveLocked()
            memoryCandidate = MemoryCandidate(text.trim(), WorkspaceObjectId(id), origin = if(modelInferred) com.halo.runtime.cognition.memory.MemoryProposalOrigin.MODEL_INFERRED else com.halo.runtime.cognition.memory.MemoryProposalOrigin.USER_EXPLICIT)
            events.send(ContextualCaptureNavigation.Memory); visible.value = false
        } } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: Exception) { mutable.value = mutable.value?.copy(error = "This candidate cannot be stored. Check for secrets.") }
    }
    fun attachToNote(noteId: String) = scope.launch {
        try { lock.withLock {
            val sourceId = saveLocked()
            val image = assets.observe(sourceId).first().single { it.type == "IMAGE" }
            assets.attachExistingImage(noteId, image)
            events.send(ContextualCaptureNavigation.OpenNote(noteId))
            visible.value = false
        } } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { mutable.value = mutable.value?.copy(error="Could not attach to that note. The image is still available.") }
    }
    fun takeMemoryCandidate(): MemoryCandidate? = memoryCandidate.also { memoryCandidate = null }
    suspend fun reviewSaved(objectId: String) = lock.withLock {
        check(mutable.value == null || mutable.value?.workspaceObjectId == objectId) { "Finish the current capture first" }
        val image = assets.observe(objectId).first().filter { it.type == "IMAGE" }.singleOrNull()
            ?: error("Choose one exact image attachment")
        val ref = MediaAssetRef("attachment:$objectId:${image.assetId}", MediaKind.IMAGE, image.mimeType, image.bytes, assets.captureHash(objectId),
            System.currentTimeMillis(), MediaProvenance.WORKSPACE, MediaLifetime.WORKSPACE_PERSISTED, workspaceObjectId=objectId)
        require(media.metadata(ref) != null) { "Saved image unavailable" }
        mutable.value?.mediaRef?.takeIf { it.id != ref.id }?.let { previous ->
            mediaPolicy.releaseTemporary(previous.id)
            previous.interactionId?.let { media.releaseInteraction(it) }
        }
        val provenance = assets.captureProvenance(objectId)
        val kind = runCatching { CaptureSource.valueOf(provenance?.optString("sourceKind").orEmpty()) }.getOrDefault(CaptureSource.IMPORTED_IMAGE)
        val quality = runCatching { CaptureQuality.valueOf(provenance?.optString("quality").orEmpty()) }.getOrDefault(CaptureQuality.UNKNOWN)
        mutable.value = ContextualCaptureState(image.assetId,kind,provenance?.optString("sourceLabel")?.takeIf { it.isNotBlank() } ?: "Notebook image",ref,
            image.width ?: 0,image.height ?: 0,quality,objectId,CaptureStatus.SAVED,
            captureTimestampMs=provenance?.optLong("captureTimestampMs")?.takeIf { it > 0 },trigger=provenance?.optString("trigger"))
        workflowStorage?.putBoolean(pendingKey,false); notice.value = null
        visible.value = true
    }
    fun openSaved() = scope.launch { state.value?.workspaceObjectId?.let { events.send(ContextualCaptureNavigation.OpenNote(it)); visible.value = false } }
    fun discard() = scope.launch { lock.withLock {
        val capture = mutable.value
        val pending = attachments.state.value
        if(pending != null && capture != null && (pending.ref.id == capture.mediaRef.id ||
            capture.workspaceObjectId != null && pending.ref.workspaceObjectId == capture.workspaceObjectId)) attachments.remove()
        mutable.value?.mediaRef?.id?.let { mediaPolicy.releaseTemporary(it) }
        mutable.value?.mediaRef?.interactionId?.let { media.releaseInteraction(it) }
        mutable.value = null; visible.value = false
        workflowStorage?.putBoolean(pendingKey,false); notice.value = null
    } }
    fun reopen() { if(state.value != null) visible.value = true }
}
