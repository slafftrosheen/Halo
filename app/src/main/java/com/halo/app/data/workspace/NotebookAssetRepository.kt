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
