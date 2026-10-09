package com.halo.app.capture

import android.content.Context
import android.net.Uri
import com.halo.sdk.runtime.multimodal.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class VisualAttachment(val ref: MediaAssetRef, val source: String)

/** One explicit pending reference. Nothing may replace it with a newer camera observation. */
class VisualAttachmentStore(private val context: Context, private val media: MediaAssetStore,
    private val onSelected: suspend (VisualAttachment) -> Unit = {}, private val onRemoved: suspend () -> Unit = {}) {
    private val pending = MutableStateFlow<VisualAttachment?>(null)
    val state = pending.asStateFlow()

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

    private suspend fun attachContent(content: MediaAssetContent, source: String, provenance: MediaProvenance, workspaceObjectId: String? = null): VisualAttachment {
        val ref = media.ingest(content, provenance, "attachment:${UUID.randomUUID()}", workspaceObjectId = workspaceObjectId)
        val old = pending.value
        pending.value = VisualAttachment(ref, source)
        onSelected(pending.value!!)
        old?.ref?.interactionId?.let { media.releaseInteraction(it) }
        return pending.value!!
    }

    suspend fun attachReference(ref: MediaAssetRef, source: String): VisualAttachment {
        check(pending.value == null) { "Remove the current image before selecting another" }
        val owned = media.transferOwnership(ref, "attachment:${UUID.randomUUID()}")
        val attachment = VisualAttachment(owned,source)
        onSelected(attachment)
        pending.value = attachment
        return attachment
    }

    /** Sending transfers ownership to the active turn; removing releases the cache copy. */
    fun take(): VisualAttachment? = pending.value.also { pending.value = null }
    suspend fun remove() { take()?.ref?.interactionId?.let { media.releaseInteraction(it) } }
    suspend fun removeSelection() { remove(); onRemoved() }
}
