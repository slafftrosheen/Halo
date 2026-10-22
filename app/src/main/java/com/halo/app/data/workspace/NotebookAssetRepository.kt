package com.halo.app.data.workspace

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.storage.haloStorageLayout
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class NotebookAssetRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val resolver: ContentResolver get() = context.contentResolver

    fun observe(noteId: String): Flow<List<NoteAttachment>> =
        database.workspaceAssetDao().observeAttachments(noteId)

    /** Lists image metadata for explicit local-search selection; it never opens the asset. */
    fun observeImageAttachments(): Flow<List<NoteAttachment>> =
        database.workspaceAssetDao().observeImageAttachments()

    /** Lists audio metadata for explicit local-search selection; it never opens or decodes the asset. */
    fun observeAudioAttachments(): Flow<List<NoteAttachment>> =
        database.workspaceAssetDao().observeAudioAttachments()

    suspend fun migrateLegacyCapture(noteId: String, body: String): Boolean {
        val uri = Regex("content://[^)\\s]+\\.captures/[^)\\s]+")
            .find(body)
            ?.value
            ?.let(Uri::parse)
            ?: return false
        if (database.workspaceAssetDao().attachmentAssetCount(noteId, uri.toString()) > 0) return true
        return runCatching { addFromPicker(noteId, uri) }.isSuccess
    }

    suspend fun addFromPicker(noteId: String, uri: Uri): NoteAttachment = withContext(Dispatchers.IO) {
        val note = database.notebookDao().getNote(noteId)
        requireNotNull(note) { "Note is no longer available" }

        val permissionPersisted = runCatching {
            resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        }.getOrDefault(false)

        val timestamp = now()
        val assetId = newId()
        val attachmentId = newId()

        val (finalStorageType, finalPathOrUri, finalPermission) = if (!permissionPersisted && uri.scheme == "content") {
            val managedDir = haloStorageLayout(context).managedWorkspaceFiles
            if (!managedDir.exists()) managedDir.mkdirs()
            val file = File(managedDir, "asset_${timestamp}_$assetId.bin")
            val copied = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                file.length() > 0L
            }.getOrDefault(false)
            if (copied) {
                Triple("MANAGED_FILE", file.absolutePath, true)
            } else {
                Triple("EXTERNAL_URI", uri.toString(), false)
            }
        } else {
            Triple("EXTERNAL_URI", uri.toString(), permissionPersisted)
        }

        val metadata = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) null else {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    Pair(
                        nameIndex.takeIf { it >= 0 }?.let(cursor::getString),
                        sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong),
                    )
                }
            }
        val declaredMime = resolver.getType(uri) ?: "application/octet-stream"
        val bounds = if (declaredMime.startsWith("image/")) {
            BitmapFactory.Options().also { options ->
                options.inJustDecodeBounds = true
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
        } else null
        val mimeType = bounds?.outMimeType ?: declaredMime

        database.withTransaction {
            database.workspaceAssetDao().insertAsset(
                WorkspaceAssetEntity(
                    id = assetId,
                    objectId = noteId,
                    type = if (mimeType.startsWith("image/")) "IMAGE" else "FILE",
                    mimeType = mimeType,
                    storageType = finalStorageType,
                    pathOrUri = finalPathOrUri,
                    permissionPersisted = finalPermission,
                    displayName = metadata?.first,
                    bytes = metadata?.second,
                    width = bounds?.outWidth?.takeIf { it > 0 },
                    height = bounds?.outHeight?.takeIf { it > 0 },
                    durationMs = null,
                    createdAtEpochMs = timestamp,
                )
            )
            database.workspaceAssetDao().insertAttachment(
                NoteAttachmentEntity(
                    id = attachmentId,
                    noteId = noteId,
                    assetId = assetId,
                    position = database.workspaceAssetDao().nextPosition(noteId),
                    caption = null,
                    createdAtEpochMs = timestamp,
                )
            )
        }
        NoteAttachment(
            id = attachmentId, noteId = noteId, assetId = assetId, position = 0,
            caption = null, type = if (mimeType.startsWith("image/")) "IMAGE" else "FILE",
            mimeType = mimeType, storageType = finalStorageType, pathOrUri = finalPathOrUri,
            permissionPersisted = finalPermission, displayName = metadata?.first,
            bytes = metadata?.second, width = bounds?.outWidth?.takeIf { it > 0 },
            height = bounds?.outHeight?.takeIf { it > 0 }, durationMs = null,
        )
    }

    /** Own generated bytes as a managed asset; no expiring remote URL is persisted. */
    suspend fun addGenerated(noteId: String, image: com.halo.sdk.provider.AiImageResult, prompt: String): NoteAttachment = withContext(Dispatchers.IO) {
        require(image.bytes.size in 1..32_000_000)
        val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, bounds)
        require(bounds.outWidth in 1..16384 && bounds.outHeight in 1..16384) { "Returned image could not be decoded" }
        val timestamp = now(); val assetId = newId(); val attachmentId = newId()
        val mime = bounds.outMimeType ?: error("Unknown image format")
        val directory = haloStorageLayout(context).managedWorkspaceFiles.also { check(it.exists() || it.mkdirs()) }
        val file = File(directory, "generated_$assetId.${if (mime == "image/jpeg") "jpg" else "png"}")
        val caption = "${prompt.take(400)} | ${image.providerId}/${image.modelId}"
        try {
            file.writeBytes(image.bytes)
            val position = database.withTransaction {
                val parent = requireNotNull(database.notebookDao().getWorkspaceObject(noteId))
                require(parent.lifecycle == "ACTIVE") { "Note is not writable" }
                requireNotNull(database.notebookDao().getNote(noteId))
                val next = database.workspaceAssetDao().nextPosition(noteId)
                database.workspaceAssetDao().insertAsset(WorkspaceAssetEntity(assetId, noteId, "IMAGE", mime, "MANAGED_FILE", file.absolutePath,
                    true, "Generated image", image.bytes.size.toLong(), bounds.outWidth, bounds.outHeight, null, timestamp))
                database.workspaceAssetDao().insertAttachment(NoteAttachmentEntity(attachmentId, noteId, assetId, next, caption, timestamp))
                database.notebookDao().updateTitle(noteId, parent.title, timestamp)
                next
            }
            NoteAttachment(attachmentId, noteId, assetId, position, caption, "IMAGE", mime, "MANAGED_FILE", file.absolutePath,
                true, "Generated image", image.bytes.size.toLong(), bounds.outWidth, bounds.outHeight, null)
        } catch (error: Exception) { file.delete(); throw error }
    }

    suspend fun pdfPageCount(file: WorkspaceFile): Int = CanvasPdfPageTools.pageCount(context, file)

    suspend fun previewPdfPage(file: WorkspaceFile, page: Int): android.graphics.Bitmap =
        CanvasPdfPageTools.preview(context, file, page)

    suspend fun exportPdfPage(file: WorkspaceFile, page: Int): ByteArray =
        CanvasPdfPageTools.export(context, file, page)

    /** Recheck the exact attachment identity against the authoritative DAO. */
    suspend fun currentStitchAttachment(noteId: String, attachmentId: String): NoteAttachment? =
        observe(noteId).first().firstOrNull { it.id == attachmentId }

    suspend fun previewLocalStitch(sources: List<CanvasStitchSource>, recipe: CanvasStitchRecipe): android.graphics.Bitmap =
        CanvasImageStitcher.preview(context, sources, recipe)

    suspend fun exportLocalStitch(sources: List<CanvasStitchSource>, recipe: CanvasStitchRecipe): ByteArray =
        CanvasImageStitcher.export(context, sources, recipe)

    suspend fun extractLocalPalette(file: WorkspaceFile): List<String> =
        CanvasImageTools.extractPalette(context, file)

    /** Encode transformed pixels into a separate owned image asset. Never rewrite its source. */
    suspend fun exportLocalEdit(file: WorkspaceFile, recipe: CanvasImageRecipe): ByteArray =
        CanvasImageTools.export(context, file, recipe)

    suspend fun addLocalEdited(noteId: String, bytes: ByteArray, format: CanvasImageFormat,
                               description: String): NoteAttachment = withContext(Dispatchers.IO) {
        require(bytes.size in 1..20_000_000)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096)
        require(bounds.outMimeType == format.mimeType) { "Encoding and image type do not match." }
        val timestamp = now()
        val assetId = newId()
        val attachmentId = newId()
        val directory = haloStorageLayout(context).managedWorkspaceFiles.also { check(it.exists() || it.mkdirs()) }
        val file = File(directory, "edited_" + assetId + "." + format.extension)
        try {
            file.writeBytes(bytes)
            val position = database.withTransaction {
                val parent = requireNotNull(database.notebookDao().getWorkspaceObject(noteId))
                require(parent.lifecycle == "ACTIVE") { "Note is no longer writable." }
                requireNotNull(database.notebookDao().getNote(noteId))
                val next = database.workspaceAssetDao().nextPosition(noteId)
                database.workspaceAssetDao().insertAsset(WorkspaceAssetEntity(
                    assetId, noteId, "IMAGE", format.mimeType, "MANAGED_FILE", file.absolutePath,
                    true, "Edited image." + format.extension, bytes.size.toLong(),
                    bounds.outWidth, bounds.outHeight, null, timestamp))
                database.workspaceAssetDao().insertAttachment(NoteAttachmentEntity(
                    attachmentId, noteId, assetId, next, description.take(400), timestamp))
                database.notebookDao().updateTitle(noteId, parent.title, timestamp)
                next
            }
            NoteAttachment(attachmentId, noteId, assetId, position, description.take(400),
                "IMAGE", format.mimeType, "MANAGED_FILE", file.absolutePath, true,
                "Edited image." + format.extension, bytes.size.toLong(), bounds.outWidth, bounds.outHeight, null)
        } catch (failure: Exception) {
            // A database commit may have won the cancellation race. Never erase a
            // file still referenced by an existing attachment; keep it on uncertainty.
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                val referenced = runCatching {
                    database.workspaceAssetDao().attachmentAssetCount(noteId, file.absolutePath) > 0
                }.getOrDefault(true)
                if (!referenced) file.delete()
            }
            throw failure
        }
    }

    /** Add another note reference to the same owned image bytes; never replace note content. */
    suspend fun attachExistingImage(noteId: String, source: NoteAttachment) = database.withTransaction {
        require(source.type == "IMAGE" && source.storageType in setOf("APP_OWNED", "MANAGED_FILE"))
        require(database.notebookDao().getWorkspaceObject(noteId)?.lifecycle == "ACTIVE")
        requireNotNull(database.notebookDao().getNote(noteId))
        if(database.workspaceAssetDao().attachmentAssetCount(noteId, source.pathOrUri) > 0) return@withTransaction
        val assetId = newId(); val timestamp = now()
        database.workspaceAssetDao().insertAsset(WorkspaceAssetEntity(assetId, noteId, "IMAGE", source.mimeType,
            source.storageType, source.pathOrUri, source.permissionPersisted, source.displayName, source.bytes, source.width, source.height, null, timestamp))
        database.workspaceAssetDao().insertAttachment(NoteAttachmentEntity(newId(),noteId,assetId,
            database.workspaceAssetDao().nextPosition(noteId),source.caption,timestamp))
    }

    suspend fun captureProvenance(noteId: String): org.json.JSONObject? = runCatching {
        database.workspaceContextDao().provenanceMetadata(noteId)?.let(::orgJson)
    }.getOrNull()
    private fun orgJson(value: String) = org.json.JSONObject(value)

    suspend fun captureHash(noteId: String): String? = runCatching {
        database.workspaceContextDao().provenanceMetadata(noteId)?.let { org.json.JSONObject(it).optString("sha256") }
            ?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
    }.getOrNull()

    suspend fun readImage(attachment: NoteAttachment): ByteArray = withContext(Dispatchers.IO) {
        require(attachment.type == "IMAGE")
        val input = if (attachment.storageType == "MANAGED_FILE") File(attachment.pathOrUri).inputStream()
            else requireNotNull(resolver.openInputStream(Uri.parse(attachment.pathOrUri)))
        input.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count = stream.read(buffer)
            while (count >= 0) {
                require(output.size() + count <= 20_000_000) { "Choose a smaller reference image (up to 20 MB)" }
                output.write(buffer, 0, count); count = stream.read(buffer)
            }
            output.toByteArray()
        }
    }

    suspend fun remove(noteId: String, attachmentId: String): Boolean = withContext(Dispatchers.IO) {
        database.withTransaction {
            val assetId = database.workspaceAssetDao().attachmentAssetId(noteId, attachmentId)
                ?: return@withTransaction false
            val removed = database.workspaceAssetDao().deleteAttachment(noteId, attachmentId) == 1
            if (removed) database.workspaceAssetDao().deleteAsset(assetId)
            removed
        }
    }
}
