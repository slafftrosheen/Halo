package com.halo.app.data.workspace

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.notebook.WorkspaceObjectSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WorkspaceFile(
    val objectId: WorkspaceObjectId,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val permissionMode: FilePermissionMode,
    val ownership: ContentOwnership,
    val availability: WorkspaceFileAvailability,
    val sha256: String?,
    /** Stable existing Workspace row version when no content hash is available. */
    val version: String? = sha256,
)

interface WorkspaceFileRepository {
    suspend fun importFile(
        uri: Uri,
        displayName: String? = null,
        mimeType: String? = null,
        sizeBytes: Long? = null,
        source: WorkspaceObjectSource = WorkspaceObjectSource.IMPORT
    ): WorkspaceFile

    suspend fun getFile(objectId: WorkspaceObjectId): WorkspaceFile?
    fun observeFile(id: WorkspaceObjectId): Flow<WorkspaceFile?> = observeFiles().map { list -> list.firstOrNull { it.objectId == id } }
    fun observeFiles(): Flow<List<WorkspaceFile>>
    suspend fun keepLocalCopy(objectId: WorkspaceObjectId): WorkspaceFile
    suspend fun keepLocalCopyBounded(objectId: WorkspaceObjectId, maxBytes: Long): WorkspaceFile {
        require(getFile(objectId)?.sizeBytes?.let { it <= maxBytes } == true) { "Cannot establish a bounded local copy" }
        return keepLocalCopy(objectId)
    }
    suspend fun relink(objectId: WorkspaceObjectId, uri: Uri): WorkspaceFile
    suspend fun reconcileAvailability()
}

class RoomWorkspaceFileRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val ownedRoot: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val ownedUri: (File) -> Uri = { file -> FileProvider.getUriForFile(context, "${context.packageName}.captures", file) },
) : WorkspaceFileRepository {
    private val copyMutex = Mutex()

    override suspend fun importFile(
        uri: Uri,
        displayName: String?,
        mimeType: String?,
        sizeBytes: Long?,
        source: WorkspaceObjectSource
    ): WorkspaceFile {
        val resolver = context.contentResolver
        var permissionPersisted = false

        try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            permissionPersisted = true
        } catch (_: SecurityException) {
            // Permission couldn't be persisted, URI can still be used for transient reading
        }

        var resolvedName = displayName
        var resolvedSize = sizeBytes
        var resolvedMime = mimeType ?: resolver.getType(uri) ?: "application/octet-stream"

        if (resolvedName == null || resolvedSize == null) {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (resolvedName == null && nameIdx != -1) {
                        resolvedName = cursor.getString(nameIdx)
                    }
                    if (resolvedSize == null && sizeIdx != -1 && !cursor.isNull(sizeIdx)) {
                        resolvedSize = cursor.getLong(sizeIdx)
                    }
                }
            }
        }

        val finalName = resolvedName?.ifBlank { null } ?: uri.lastPathSegment ?: "Imported File"
        val id = newId()
        val timestamp = now()

        val objectEntity = WorkspaceObjectEntity(
            id = id,
            type = "FILE",
            title = finalName,
            createdAt = timestamp,
            updatedAt = timestamp,
            source = source.name,
            lifecycle = WorkspaceObjectLifecycle.ACTIVE.name,
            inInbox = true,
            indexingState = "READY",
            contentRef = "file:$id"
        )

        val fileEntity = WorkspaceFileEntity(
            objectId = id,
            uri = uri.toString(),
            displayName = finalName,
            mimeType = resolvedMime,
            sizeBytes = resolvedSize,
            modifiedAt = timestamp,
            ownedCopyPath = null,
            permissionPersisted = permissionPersisted,
            ownership = ContentOwnership.EXTERNAL_REFERENCE.name,
            availability = WorkspaceFileAvailability.AVAILABLE.name,
        )

        db.withTransaction {
            db.notebookDao().insertWorkspaceObject(objectEntity)
            db.workspaceFileDao().insertFile(fileEntity)
            db.workspaceContextDao().provenance(com.halo.app.data.ObjectProvenance(id, uri.toString(), timestamp.toString(),
                org.json.JSONObject().put("originalUri", uri.toString()).put("source", source.name)
                    .put("mimeType", resolvedMime).put("permissionPersisted", permissionPersisted).toString()))
        }

        return WorkspaceFile(
            objectId = WorkspaceObjectId(id),
            uri = uri,
            displayName = finalName,
            mimeType = resolvedMime,
            sizeBytes = resolvedSize,
            permissionMode = if (permissionPersisted) FilePermissionMode.PERSISTABLE_URI else FilePermissionMode.READ_ONLY,
            ownership = ContentOwnership.EXTERNAL_REFERENCE,
            availability = WorkspaceFileAvailability.AVAILABLE,
            sha256 = null,
            version = "workspace-file:$timestamp",
        )
    }

    override suspend fun getFile(objectId: WorkspaceObjectId): WorkspaceFile? {
        val entity = db.workspaceFileDao().getFile(objectId.value) ?: return null
        return entity.toDomain()
    }

    override fun observeFile(id: WorkspaceObjectId): Flow<WorkspaceFile?> = db.workspaceFileDao().observeFile(id.value).map { it?.toDomain() }

    override fun observeFiles(): Flow<List<WorkspaceFile>> {
        return db.workspaceFileDao().observeFiles().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun keepLocalCopy(objectId: WorkspaceObjectId): WorkspaceFile = copyLocal(objectId,Long.MAX_VALUE)
    override suspend fun keepLocalCopyBounded(objectId: WorkspaceObjectId, maxBytes: Long): WorkspaceFile = copyLocal(objectId,maxBytes)
    private suspend fun copyLocal(objectId: WorkspaceObjectId, maxBytes: Long): WorkspaceFile = copyMutex.withLock { withContext(Dispatchers.IO) {
        require(maxBytes > 0)
        val entity = requireNotNull(db.workspaceFileDao().getFile(objectId.value))
        if (entity.ownership == ContentOwnership.HALO_OWNED.name) {
            val existing = entity.ownedCopyPath?.let(::File)
            check(existing?.isFile == true && existing.length() in 1..maxBytes) { "Local copy unavailable" }
            return@withContext entity.toDomain()
        }
        val destination = File(ownedRoot, "${safe(entity.objectId)}/${safe(entity.displayName)}")
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, ".${destination.name}.copying")
        val digest = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(Uri.parse(entity.uri)) ?: error("Unable to read selected file")
        try { input.use { sourceStream ->
            temporary.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copiedBytes = 0L
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val read = sourceStream.read(buffer)
                    if (read < 0) break
                    copiedBytes += read
                    require(copiedBytes <= maxBytes) { "File exceeds the local-copy budget" }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        } catch (error: Throwable) { temporary.delete(); throw error }
        if (temporary.length() <= 0 || destination.exists() || !temporary.renameTo(destination)) {
            temporary.delete(); error("Unable to keep a non-empty local copy")
        }
        try {
        val updated = entity.copy(
            uri = ownedUri(destination).toString(),
            sizeBytes = destination.length(),
            ownedCopyPath = destination.absolutePath,
            permissionPersisted = false,
            ownership = ContentOwnership.HALO_OWNED.name,
            availability = WorkspaceFileAvailability.OWNED_LOCAL.name,
            sha256 = digest.digest().joinToString("") { "%02x".format(it) },
        )
        db.workspaceFileDao().insertFile(updated)
        requireNotNull(getFile(objectId))
        } catch (failure: Throwable) { destination.delete(); throw failure }
    } }

    override suspend fun relink(objectId: WorkspaceObjectId, uri: Uri): WorkspaceFile = withContext(Dispatchers.IO) {
        val entity = requireNotNull(db.workspaceFileDao().getFile(objectId.value))
        require(entity.ownership == ContentOwnership.EXTERNAL_REFERENCE.name) { "Owned files cannot be relinked" }
        val metadata = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0) to if (!cursor.isNull(1)) cursor.getLong(1) else null
            } else null
        }
        val name = metadata?.first
        val size = metadata?.second
        if (name != null) require(name == entity.displayName) { "Selected file name does not match" }
        if (entity.sizeBytes != null && size != null) require(entity.sizeBytes == size) { "Selected file size does not match" }
        if (entity.sha256 != null) {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = context.contentResolver.openInputStream(uri) ?: error("Unable to read selected file")
            input.use { source ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) { val read = source.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
            }
            require(digest.digest().joinToString("") { "%02x".format(it) } == entity.sha256) { "Selected file does not match the saved hash" }
        }
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        db.workspaceFileDao().insertFile(entity.copy(uri = uri.toString(), modifiedAt = if (uri.toString() == entity.uri) entity.modifiedAt else maxOf(now(), (entity.modifiedAt ?: 0) + 1), permissionPersisted = true, availability = WorkspaceFileAvailability.AVAILABLE.name))
        requireNotNull(getFile(objectId))
    }

    override suspend fun reconcileAvailability() = withContext(Dispatchers.IO) {
        val grants = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }.toSet()
        db.workspaceFileDao().allFiles().forEach { entity ->
            val availability = if (entity.ownership == ContentOwnership.HALO_OWNED.name) {
                if (entity.ownedCopyPath?.let(::File)?.isFile == true) WorkspaceFileAvailability.OWNED_LOCAL else WorkspaceFileAvailability.MISSING
            } else if (entity.permissionPersisted && Uri.parse(entity.uri) !in grants) {
                WorkspaceFileAvailability.NEEDS_RELINK
            } else {
                val readable = runCatching { context.contentResolver.openFileDescriptor(Uri.parse(entity.uri), "r")?.use { true } ?: false }.getOrDefault(false)
                if (readable) WorkspaceFileAvailability.AVAILABLE else WorkspaceFileAvailability.NEEDS_RELINK
            }
            if (availability.name != entity.availability) db.workspaceFileDao().insertFile(entity.copy(availability = availability.name))
        }
    }

    private fun safe(value: String) = value.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100).ifBlank { "file" }

    private fun WorkspaceFileEntity.toDomain() = WorkspaceFile(
        objectId = WorkspaceObjectId(objectId),
        uri = Uri.parse(uri),
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        permissionMode = when (ownership) {
            ContentOwnership.HALO_OWNED.name -> FilePermissionMode.OWNED_COPY
            else -> if (permissionPersisted) FilePermissionMode.PERSISTABLE_URI else FilePermissionMode.READ_ONLY
        },
        ownership = ContentOwnership.valueOf(ownership),
        availability = WorkspaceFileAvailability.valueOf(availability),
        sha256 = sha256,
        version = sha256 ?: modifiedAt?.let { "workspace-file:$it" },
    )
}
