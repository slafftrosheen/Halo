package com.halo.app.capture

import android.content.Context
import android.net.Uri
import com.halo.sdk.runtime.multimodal.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

data class VisualAttachment(val ref: MediaAssetRef, val source: String, val contextText: String? = null,
    /** Ephemeral host guard; never serialized or sent to a provider. */
    internal val commitSource: suspend (() -> Unit) -> Unit = { commit -> commit() })

/** One explicit pending reference. Nothing may replace it with a newer camera observation. */
class VisualAttachmentStore(private val context: Context, private val media: MediaAssetStore,
    private val onSelected: suspend (VisualAttachment) -> Unit = {}, private val onRemoved: suspend () -> Unit = {}) {
    private val pending = MutableStateFlow<VisualAttachment?>(null)
    val state = pending.asStateFlow()
    private val lock = Mutex()

    suspend fun attach(uri: Uri, source: String, provenance: MediaProvenance, workspaceObjectId: String? = null): VisualAttachment = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        if (!mime.startsWith("image/")) throw MediaAcquisitionException(MediaFailure.UNSUPPORTED_FORMAT)
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > ImageInputLimits().maxSourceBytes) throw MediaAcquisitionException(MediaFailure.TOO_LARGE)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw MediaAcquisitionException(MediaFailure.SOURCE_UNAVAILABLE)
        attachContent(MediaAssetContent(bytes, mime), source, provenance, workspaceObjectId)
    }

    suspend fun attachFile(file: File): VisualAttachment = withContext(Dispatchers.IO) {
        if (file.length() > ImageInputLimits().maxSourceBytes) throw MediaAcquisitionException(MediaFailure.TOO_LARGE)
        attachContent(MediaAssetContent(file.readBytes(), "image/jpeg"), "Camera", MediaProvenance.PHONE_CAMERA)
    }

    /** Attach a bounded derived image through the same explicit context and privacy path as user-selected media. */
    suspend fun attachGeneratedImage(bytes: ByteArray, source: String, workspaceObjectId: String,
        contextText: String? = null, validateSource: suspend () -> Unit = {},
        commitSource: suspend (() -> Unit) -> Unit = { commit -> commit() }): VisualAttachment =
        withContext(Dispatchers.IO) {
            require(bytes.size in 1..ImageInputLimits().maxBytes)
            attachContent(MediaAssetContent(bytes, "image/png"), source, MediaProvenance.WORKSPACE,
                workspaceObjectId, contextText, validateSource, commitSource)
        }

    private suspend fun attachContent(content: MediaAssetContent, source: String, provenance: MediaProvenance,
        workspaceObjectId: String? = null, contextText: String? = null,
        validateSource: suspend () -> Unit = {},
        commitSource: suspend (() -> Unit) -> Unit = { commit -> commit() }): VisualAttachment = lock.withLock {
        check(pending.value == null) { "Remove the attached image before selecting another" }
        validateSource()
        val interactionId = "attachment:${UUID.randomUUID()}"
        try {
            val ref = media.ingest(content, provenance, interactionId, workspaceObjectId = workspaceObjectId)
            val attachment = VisualAttachment(ref, source, contextText?.takeIf { it.isNotBlank() }?.take(2000), commitSource)
            validateSource()
            onSelected(attachment)
            pending.value = attachment
            attachment
        } catch (error: Throwable) {
            withContext(kotlinx.coroutines.NonCancellable) { media.releaseInteraction(interactionId) }
            throw error
        }
    }

    suspend fun attachReference(ref: MediaAssetRef, source: String): VisualAttachment = lock.withLock {
        pending.value?.takeIf { it.ref.id == ref.id }?.let { return@withLock it }
        check(pending.value == null) { "Remove the current image before selecting another" }
        val owned = media.transferOwnership(ref, "attachment:${UUID.randomUUID()}")
        val attachment = VisualAttachment(owned,source)
        try { onSelected(attachment) }
        catch (error: Throwable) { withContext(kotlinx.coroutines.NonCancellable) { owned.interactionId?.let { media.releaseInteraction(it) } }; throw error }
        pending.value = attachment
        attachment
    }

    /** Sending transfers ownership to the active turn; removing releases the cache copy. */
    fun take(): VisualAttachment? = pending.value.also { pending.value = null }
    suspend fun remove() = lock.withLock { take()?.ref?.interactionId?.let { media.releaseInteraction(it) }; Unit }
    suspend fun removeSelection() = lock.withLock {
        take()?.ref?.interactionId?.let { media.releaseInteraction(it) }
        onRemoved()
    }
}
