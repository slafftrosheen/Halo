package com.halo.app.capture

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.halo.app.data.*
import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.NoteAttachmentEntity
import com.halo.app.data.workspace.WorkspaceAssetEntity
import com.halo.sdk.host.ObservationSource
import com.halo.sdk.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LocalCaptureStore(private val context: Context, private val db: AppDatabase,
    private val notebook: NotebookRepository, private val events: EventBus) {

    data class PendingCapture(val file: File, val uri: Uri)

    suspend fun preparePhoneCapture(): PendingCapture = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "captures/images/${UUID.randomUUID()}.jpg")
        file.parentFile?.mkdirs()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.captures", file)
        PendingCapture(file, uri)
    }

    private fun uri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.captures", file)

    suspend fun discardPendingCapture(file: File) = withContext(Dispatchers.IO) {
        val capturesDir = File(context.filesDir, "captures/images").canonicalFile
        val candidate = file.canonicalFile
        if (candidate.path.startsWith(capturesDir.path + File.separator)) candidate.delete()
    }

    suspend fun receive(file: File, deviceId: String? = null, captureId: String = file.nameWithoutExtension,
        publishForInference: Boolean = false, sourceLabel: String? = null, sourceKind: String? = null, title: String? = null, captureQuality: String? = null, captureTimestampMs: Long? = null, trigger: String? = null, body: String = ""): String = withContext(Dispatchers.IO) {
        require(file.exists() && file.length() in 1..(32L * 1024 * 1024)) { "No complete image received, or image exceeds 32 MB" }
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "Received file is not a decodable image" }
        val mime = options.outMimeType ?: "image/jpeg"
        val capturesDir = File(context.filesDir, "captures/images")
        capturesDir.mkdirs()
        val local = if (file.canonicalPath.startsWith(capturesDir.canonicalPath + File.separator)) file
            else File(capturesDir, "${UUID.randomUUID()}.jpg").also { file.copyTo(it, overwrite = false) }
        val source = sourceLabel ?: if (deviceId == null) "Phone camera" else "Wearable camera"
        val note = db.withTransaction {
            db.workspaceContextDao().captureObject(deviceId ?: "phone", captureId)?.let { id ->
                return@withTransaction requireNotNull(notebook.getNote(id)) { "Saved capture unavailable" }
            }
            val note = notebook.createNote(body.take(8000), WorkspaceSource.DEVICE_CAPTURE, title = title?.take(160)?.takeIf { it.isNotBlank() } ?: "Capture — $source")
            val timestamp = System.currentTimeMillis()
            val assetId = UUID.randomUUID().toString()
            db.workspaceAssetDao().insertAsset(
                WorkspaceAssetEntity(
                    id = assetId,
                    objectId = note.id,
                    type = "IMAGE",
                    mimeType = mime,
                    storageType = "APP_OWNED",
                    pathOrUri = uri(local).toString(),
                    permissionPersisted = true,
                    displayName = local.name,
                    bytes = local.length(),
                    width = options.outWidth,
                    height = options.outHeight,
                    durationMs = null,
                    createdAtEpochMs = timestamp,
                )
            )
            db.workspaceAssetDao().insertAttachment(
                NoteAttachmentEntity(
                    id = UUID.randomUUID().toString(),
                    noteId = note.id,
                    assetId = assetId,
                    position = 0,
                    caption = source,
                    createdAtEpochMs = timestamp,
                )
            )
            db.workspaceContextDao().provenance(ObjectProvenance(note.id, deviceId ?: "phone", captureId,
                org.json.JSONObject(mapOf("mimeType" to mime, "localPath" to local.absolutePath, "captureId" to captureId, "sourceKind" to (sourceKind ?: "DEVICE_CAPTURE"), "sourceLabel" to source, "quality" to (captureQuality ?: "UNKNOWN"), "captureTimestampMs" to captureTimestampMs, "trigger" to trigger, "sha256" to java.security.MessageDigest.getInstance("SHA-256").digest(local.readBytes()).joinToString("") { "%02x".format(it) })).toString()))
            note
        }
        events.publish(ActionReceipt(operationId = captureId, category = "Vision", title = "Image received", stage = "Completed",
            detail = "$source · $mime · saved in Notebook Inbox", sourceDeviceId = deviceId, objectId = note.id))
        // Oversize captures remain local even when they cannot fit model context.
        if (publishForInference && local.length() <= 8L * 1024 * 1024) {
            val ref = com.halo.app.di.ManualDependencyGraph.mediaAssetStore.ingest(
                com.halo.sdk.runtime.multimodal.MediaAssetContent(local.readBytes(), mime),
                if (deviceId == null) com.halo.sdk.runtime.multimodal.MediaProvenance.PHONE_CAMERA else com.halo.sdk.runtime.multimodal.MediaProvenance.HEYCYAN_CAMERA,
                captureId, deviceId,
            )
            events.publish(ObservationCreated(observation = Observation(
            modality = Modality.IMAGE, source = Source.CAMERA,
            payload = ImageObservationPayload(mimeType = mime, mediaRef = ref), sourceDeviceId = deviceId,
            origin = ObservationOrigin(if (deviceId == null) ObservationSource.PhoneCamera else ObservationSource.HeyCyanGlasses),
            metadata = mapOf("captureId" to captureId, "objectId" to note.id, "mimeType" to mime))))
        }
        note.id
    }
}
