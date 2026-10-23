package com.halo.app.capture

import android.content.Context
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.workspace.*
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.sdk.runtime.multimodal.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlinx.coroutines.flow.first

/** Existing Workspace ownership/URI permission rules resolve durable assets; the SDK sees no URI. */
class WorkspaceMediaAssetResolver(
    private val context: Context,
    private val workspace: WorkspaceFileRepository,
    private val ephemeral: MediaAssetResolver,
    private val attachments: com.halo.app.data.workspace.NotebookAssetRepository? = null,
    private val objects: WorkspaceRepository? = null,
) : MediaAssetResolver {
    override suspend fun metadata(ref: MediaAssetRef): MediaAssetMetadata? {
        if (ref.lifetime == MediaLifetime.EPHEMERAL_INTERACTION) return ephemeral.metadata(ref)
        val id = ref.workspaceObjectId ?: return null
        if (ref.kind != MediaKind.IMAGE || !active(id)) return null
        if (ref.id.startsWith("attachment:$id:")) {
            val asset = attachments?.observe(id)?.let { it.first() }?.singleOrNull { "attachment:$id:${it.assetId}" == ref.id } ?: return null
            if (asset.type != "IMAGE" || asset.mimeType != ref.mimeType) return null
            return MediaAssetMetadata(ref, asset.width, asset.height)
        }
        if (ref.id != "workspace:$id") return null
        val file = workspace.getFile(WorkspaceObjectId(id)) ?: return null
        if (!readable(file) || file.mimeType != ref.mimeType || ref.sha256 == null || file.sha256 != ref.sha256) return null
        return MediaAssetMetadata(ref)
    }

    override suspend fun open(ref: MediaAssetRef): MediaAssetContent = withContext(Dispatchers.IO) {
        if (ref.lifetime == MediaLifetime.EPHEMERAL_INTERACTION) return@withContext ephemeral.open(ref)
        if (metadata(ref) == null) throw MediaAcquisitionException(MediaFailure.SOURCE_UNAVAILABLE)
        if (ref.id.startsWith("attachment:${ref.workspaceObjectId}:")) {
            val asset = attachments!!.observe(ref.workspaceObjectId!!).first().single { "attachment:${ref.workspaceObjectId}:${it.assetId}" == ref.id }
            val bytes = attachments.readImage(asset)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            if(ref.sha256 != null && hash != ref.sha256) throw MediaAcquisitionException(MediaFailure.TRANSFER_FAILED)
            if (!active(ref.workspaceObjectId!!) || attachments.observe(ref.workspaceObjectId!!).first().singleOrNull { it.assetId == asset.assetId } != asset)
                throw MediaAcquisitionException(MediaFailure.SOURCE_UNAVAILABLE)
            return@withContext MediaAssetContent(bytes, asset.mimeType)
        }
        val file = workspace.getFile(WorkspaceObjectId(ref.workspaceObjectId!!)) ?: throw MediaAcquisitionException(MediaFailure.SOURCE_UNAVAILABLE)
        if (!readable(file) || file.mimeType != ref.mimeType || file.sha256 != ref.sha256) throw MediaAcquisitionException(MediaFailure.SOURCE_UNAVAILABLE)
        val bytes = context.contentResolver.openInputStream(file.uri)?.use { input ->
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
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if (ref.sha256 != null && sha != ref.sha256) throw MediaAcquisitionException(MediaFailure.TRANSFER_FAILED)
        val after = workspace.getFile(file.objectId)
        if (!active(file.objectId.value) || after?.version != file.version || after?.let(::readable) != true)
            throw MediaAcquisitionException(MediaFailure.SOURCE_UNAVAILABLE)
        MediaAssetContent(bytes, file.mimeType)
    }
    private suspend fun active(id: String): Boolean = objects?.get(WorkspaceObjectId(id))?.lifecycle == WorkspaceObjectLifecycle.ACTIVE
    private fun readable(file: WorkspaceFile) = file.availability in setOf(WorkspaceFileAvailability.AVAILABLE, WorkspaceFileAvailability.OWNED_LOCAL)
}
