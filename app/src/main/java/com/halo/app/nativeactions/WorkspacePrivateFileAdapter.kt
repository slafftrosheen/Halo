package com.halo.app.nativeactions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.ObjectProvenance
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.workspace.ContentOwnership
import com.halo.app.data.workspace.WorkspaceFileAvailability
import com.halo.app.data.workspace.WorkspaceFileEntity
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Preserves a managed file's canonical identity through the Private envelope. A journal bridges
 * Room and the filesystem. The old FileProvider path goes away before the public row is removed.
 * It can be restored after a rollback, including after process death.
 */
internal class WorkspacePrivateFileAdapter(
    private val context: Context,
    private val database: AppDatabase,
    private val owned: File,
) : PrivateLibraryFileAdapter {
    private val staging = File(context.noBackupFilesDir, "private-library-file-staging")
    private val sealedFiles = File(context.noBackupFilesDir, "private-library-file-payloads")
    private val provider = "${context.packageName}.captures"
    private val backing = PrivateLibraryLocalBacking(context, database, owned)

    override suspend fun capture(header: WorkspaceObjectEntity, protectedIds: Set<String>): JSONObject = withContext(Dispatchers.IO) {
        val record = file(header)
        val provenance = database.workspaceContextDao().provenanceFor(header.id)
        val payload = recordJson(record).put("provenance", provenance?.let(::provenanceJson))
        if (record.ownership == ContentOwnership.EXTERNAL_REFERENCE.name) {
            if (!backing.isLocalUri(record.uri)) return@withContext payload
            val path = backing.exactUri(record.uri)
            val owner = requireNotNull(backing.owner(path)) { "The Halo file reference has no verified owner" }
            check(owner.id != header.id && (owner.independent || owner.id in protectedIds))
            val size = path.length()
            val hash = digest(path, size)
            provenance?.let { rewriteUri(it.metadata, record.uri, "content://private-library-review/attachment") }
            require((record.sizeBytes == null || record.sizeBytes == size) &&
                (record.sha256 == null || record.sha256.equals(hash, ignoreCase = true)))
            return@withContext payload.put("localBacking", owner.encode()).put("backingSize", size).put("backingSha", hash)
        }
        val source = ownedFile(header.id, requireNotNull(record.ownedCopyPath))
        requireCoveredAliases(source, header.id, protectedIds)
        val knownSize = source.length()
        require(knownSize in 0..MAX_BYTES && (record.sizeBytes == null || record.sizeBytes == knownSize))
        val sha = record.sha256?.lowercase()?.also { require(it.matches(SHA256)) } ?: digest(source, knownSize)
        payload.put("storedSha256", sha).put("storedSize", knownSize)
    }

    override suspend fun afterSeal(access: VaultSession, envelopeId: String, header: WorkspaceObjectEntity,
        payload: JSONObject, protectedIds: Set<String>,
    ) = withContext(Dispatchers.IO) {
        val record = file(header)
        val saved = decodeRecord(header.id, payload)
        require(record == saved)
        require(database.workspaceContextDao().provenanceFor(header.id) == decodeProvenance(header.id, payload))
        if (saved.ownership == ContentOwnership.EXTERNAL_REFERENCE.name) return@withContext
        val source = ownedFile(header.id, requireNotNull(record.ownedCopyPath))
        requireCoveredAliases(source, header.id, protectedIds)
        val sha = storedSha(payload)
        val size = storedSize(payload)
        check(source.isFile && source.length() == size)
        val operation = createJournal(JournalKind.LOCK, envelopeId, header.id, source, sha, size)
        check(sealedFiles.isDirectory || sealedFiles.mkdirs())
        val ciphertext = blob(envelopeId, header.id)
        check(!ciphertext.exists())
        source.inputStream().buffered().use { input ->
            operation.encrypted.outputStream().use { output ->
                PrivateLibraryFilePayload.seal(access, aad(envelopeId, header.id), size, sha, input, output.buffered())
                output.fd.sync()
            }
        }
        access.requireOpen()
        Files.move(operation.encrypted.toPath(), ciphertext.toPath(), StandardCopyOption.ATOMIC_MOVE)
        context.revokeUriPermission(Uri.parse(record.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        Files.move(source.toPath(), operation.bytes.toPath(), StandardCopyOption.ATOMIC_MOVE)
        Unit
    }

    override suspend fun restore(access: VaultSession, envelopeId: String, header: WorkspaceObjectEntity,
        payload: JSONObject,
    ) = withContext(Dispatchers.IO) {
        require(header.type in supportedTypes && header.contentRef == "file:${header.id}")
        val record = decodeRecord(header.id, payload)
        if (record.ownership == ContentOwnership.EXTERNAL_REFERENCE.name) {
            if (payload.optJSONObject("localBacking") != null) return@withContext
            // A legacy same-app URI can only stay literal while its recorded owner still exists.
            if (backing.isLocalUri(record.uri)) {
                val path = backing.exactUri(record.uri)
                val owner = requireNotNull(backing.owner(path))
                check(owner.independent && path.isFile && (record.sizeBytes == null || path.length() == record.sizeBytes))
            }
            database.workspaceFileDao().insertFile(record)
            decodeProvenance(header.id, payload)?.let { database.workspaceContextDao().provenance(it) }
            return@withContext
        }
        val size = storedSize(payload)
        val sha = storedSha(payload)
        val root = owned.canonicalFile
        val directory = File(root, ownedDirectoryName(header.id))
        check(directory.isDirectory || directory.mkdirs())
        val suffix = record.displayName.substringAfterLast('.', "").takeIf { it.matches(EXTENSION) }
            ?.let { ".$it" }.orEmpty()
        val target = File(directory, "restored-${UUID.randomUUID()}$suffix")
        check(!target.exists())
        val operation = createJournal(JournalKind.RESTORE, envelopeId, header.id, target, sha, size)
        blob(envelopeId, header.id).inputStream().buffered().use { input ->
            operation.bytes.outputStream().use { output ->
                val staged = output.buffered()
                PrivateLibraryFilePayload.open(access, aad(envelopeId, header.id), size, sha, input) { staged.write(it) }
                staged.flush()
                output.fd.sync()
            }
        }
        access.requireOpen()
        Files.move(operation.bytes.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        database.workspaceFileDao().insertFile(record.copy(
            uri = FileProvider.getUriForFile(context, provider, target).toString(),
            ownedCopyPath = target.absolutePath,
            sizeBytes = size,
            sha256 = sha,
            permissionPersisted = false,
        ))
        decodeProvenance(header.id, payload)?.let { database.workspaceContextDao().provenance(it) }
        Unit
    }

    override suspend fun restoreAliases(items: List<Pair<WorkspaceObjectEntity, JSONObject>>) = withContext(Dispatchers.IO) {
        for ((header, payload) in items) {
            val source = payload.optJSONObject("localBacking") ?: continue
            val record = decodeRecord(header.id, payload)
            require(record.ownership == ContentOwnership.EXTERNAL_REFERENCE.name && backing.isLocalUri(record.uri))
            val owner = PrivateLibraryLocalBacking.Owner.decode(source)
            val path = backing.current(owner)
            val size = payload.getLong("backingSize")
            val hash = payload.getString("backingSha").also { check(it.matches(SHA256)) }
            check(digest(path, size) == hash) { "The original shared file changed" }
            database.workspaceFileDao().insertFile(record.copy(uri = backing.uri(path).toString(), permissionPersisted = false))
            decodeProvenance(header.id, payload)?.let { provenance ->
                database.workspaceContextDao().provenance(provenance.copy(metadata = rewriteUri(provenance.metadata, record.uri, backing.uri(path).toString())))
            }
        }
    }

    private suspend fun requireCoveredAliases(path: File, rootId: String, protectedIds: Set<String>) {
        check(rootId in protectedIds)
        val aliases = database.workspaceFileDao().allFiles().filter { row ->
            row.objectId != rootId && (backing.reference(row.uri) == path || row.ownedCopyPath?.let(backing::reference) == path)
        }
        check(aliases.all { it.objectId in protectedIds && it.ownership == ContentOwnership.EXTERNAL_REFERENCE.name })
        check(database.workspaceAssetDao().privateAssetBackings().filter { backing.reference(it.pathOrUri) == path }
            .all { it.objectId in protectedIds })
    }

    private fun rewriteUri(metadata: String, old: String, current: String): String {
        if (old == current || !metadata.contains(old)) return metadata
        val objectData = JSONObject(metadata)
        if (objectData.optString("uri") == old) objectData.put("uri", current)
        if (objectData.optString("originalUri") == old) objectData.put("originalUri", current)
        return objectData.toString().also { check(!it.contains(old)) { "The linked file has an unknown saved URI field" } }
    }

    override suspend fun preview(access: VaultSession, envelopeId: String, itemId: String, payload: JSONObject,
        maxBytes: Int,
    ): ByteArray? {
        if (payload.getString("ownership") == ContentOwnership.EXTERNAL_REFERENCE.name) return null
        val size = storedSize(payload)
        if (size > maxBytes) return null
        val bytes = ByteArray(size.toInt())
        try {
            var offset = 0
            blob(envelopeId, itemId).inputStream().buffered().use { input ->
                PrivateLibraryFilePayload.open(access, aad(envelopeId, itemId), size, storedSha(payload), input) { part ->
                    part.copyInto(bytes, offset)
                    offset += part.size
                }
            }
            access.requireOpen()
            return bytes
        } catch (error: Throwable) {
            bytes.fill(0)
            throw error
        }
    }

    override fun openPreview(access: VaultSession, envelopeId: String, itemId: String, payload: JSONObject): PrivateLibraryReadSource {
        val ciphertext = blob(envelopeId, itemId)
        check(ciphertext.isFile && !Files.isSymbolicLink(ciphertext.toPath()))
        return PrivateLibraryFilePayload.randomAccess(access, aad(envelopeId, itemId), storedSize(payload), ciphertext)
    }

    override suspend fun settle() = withContext(Dispatchers.IO) {
        // Acquiring the database writer prevents recovery from racing an in-flight Private move.
        if (!staging.isDirectory) return@withContext
        database.withTransaction {
            val files = requireNotNull(staging.listFiles())
            val journalIds = files.mapNotNull { file ->
                val marker = file.name.removeSuffix(".bak")
                marker.removeSuffix(".journal").takeIf { marker.endsWith(".journal") && isUuid(it) }
            }.distinct()
            for (id in journalIds) {
                val operation = pending(id)
                val data = operation.marker.openRead().use { stream ->
                    check(stream.channel.size() in 1..4096)
                    JSONObject(stream.readBytes().toString(Charsets.UTF_8))
                }
                val objectId = data.getString("objectId")
                val path = ownedFile(objectId, data.getString("path"))
                val sha = storedSha(data)
                val size = storedSize(data)
                val ciphertext = blob(data.getString("envelopeId"), objectId)
                val active = database.workspaceFileDao().getFile(objectId)
                val stillPublicHere = active?.ownedCopyPath?.let {
                    runCatching { ownedFile(objectId, it) == path }.getOrDefault(false)
                } == true
                when (JournalKind.valueOf(data.getString("kind"))) {
                    JournalKind.LOCK -> when {
                        active == null -> {
                            check(database.privateLibraryDao().summary(data.getString("envelopeId")) != null && ciphertext.isFile)
                            check(!operation.bytes.exists() || operation.bytes.delete())
                        }
                        stillPublicHere && operation.bytes.isFile -> {
                            check(digest(operation.bytes, size) == sha)
                            if (path.exists()) {
                                check(path.isFile && digest(path, size) == sha && operation.bytes.delete())
                            } else {
                                val parent = requireNotNull(path.parentFile)
                                check(parent.isDirectory || parent.mkdirs())
                                Files.move(operation.bytes.toPath(), path.toPath(), StandardCopyOption.ATOMIC_MOVE)
                            }
                            check(!ciphertext.exists() || ciphertext.delete())
                        }
                        stillPublicHere -> check(!ciphertext.exists() || ciphertext.delete())
                        else -> error("The locked file has a pending recovery")
                    }
                    JournalKind.RESTORE -> {
                        if (!stillPublicHere && path.exists()) {
                            check(path.isFile && digest(path, size) == sha && path.delete())
                        }
                        check(!operation.bytes.exists() || operation.bytes.delete())
                        if (stillPublicHere) {
                            check(path.isFile && path.length() == size)
                            check(!ciphertext.exists() || ciphertext.delete())
                        }
                    }
                }
                check(!operation.encrypted.exists() || operation.encrypted.delete())
                operation.marker.delete()
            }
        }
    }

    private suspend fun file(header: WorkspaceObjectEntity): WorkspaceFileEntity {
        require(header.type in supportedTypes && header.contentRef == "file:${header.id}")
        val record = requireNotNull(database.workspaceFileDao().getFile(header.id))
        if (record.ownership == ContentOwnership.EXTERNAL_REFERENCE.name) {
            require(record.ownedCopyPath == null && Uri.parse(record.uri).scheme == "content")
            return record
        }
        require(record.ownership == ContentOwnership.HALO_OWNED.name && record.availability == WorkspaceFileAvailability.OWNED_LOCAL.name)
        val source = ownedFile(header.id, requireNotNull(record.ownedCopyPath))
        require(source.isFile && FileProvider.getUriForFile(context, provider, source).toString() == record.uri)
        return record
    }

    private fun ownedFile(id: String, path: String): File {
        val root = owned.canonicalFile
        val file = File(path)
        val safeDirectory = File(root, ownedDirectoryName(id))
        val canonical = file.canonicalFile
        require(file.isAbsolute && file.name == canonical.name && !Files.isSymbolicLink(file.toPath()) &&
            canonical.parentFile == safeDirectory &&
            canonical.name.isNotEmpty() && canonical.name.length <= 140)
        return canonical
    }

    private fun ownedDirectoryName(id: String): String {
        require(id.matches(OWNER_ID))
        return id.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)
    }

    private fun recordJson(file: WorkspaceFileEntity) = JSONObject()
        .put("uri", file.uri).put("displayName", file.displayName).put("mimeType", file.mimeType)
        .put("ownership", file.ownership).put("availability", file.availability)
        .put("sizeBytes", file.sizeBytes ?: JSONObject.NULL).put("modifiedAt", file.modifiedAt ?: JSONObject.NULL)
        .put("ownedCopyPath", file.ownedCopyPath).put("permissionPersisted", file.permissionPersisted)
        .put("sha256", file.sha256 ?: JSONObject.NULL)

    private fun provenanceJson(value: ObjectProvenance) = JSONObject().put("source", value.sourceId)
        .put("revision", value.revision).put("metadata", value.metadata)

    private fun decodeProvenance(id: String, payload: JSONObject): ObjectProvenance? =
        payload.optJSONObject("provenance")?.let {
            ObjectProvenance(id, it.getString("source"), it.getString("revision"), it.getString("metadata"))
        }

    private fun decodeRecord(id: String, record: JSONObject): WorkspaceFileEntity {
        val name = record.getString("displayName")
        val mime = record.getString("mimeType")
        val ownership = ContentOwnership.valueOf(record.getString("ownership"))
        val availability = WorkspaceFileAvailability.valueOf(record.getString("availability"))
        val uri = record.getString("uri")
        val path = if (record.isNull("ownedCopyPath")) null else record.getString("ownedCopyPath")
        if (ownership == ContentOwnership.EXTERNAL_REFERENCE) {
            require(path == null && Uri.parse(uri).scheme == "content")
        } else require(path != null && availability == WorkspaceFileAvailability.OWNED_LOCAL)
        require(name.isNotBlank() && name.length <= 240 && mime.isNotBlank() && mime.length <= 120)
        return WorkspaceFileEntity(
            objectId = id, uri = uri, displayName = name, mimeType = mime,
            sizeBytes = record.optLong("sizeBytes").takeUnless { record.isNull("sizeBytes") },
            modifiedAt = record.optLong("modifiedAt").takeUnless { record.isNull("modifiedAt") },
            ownedCopyPath = path, permissionPersisted = record.getBoolean("permissionPersisted"),
            ownership = ownership.name, availability = availability.name,
            sha256 = if (record.isNull("sha256")) null else record.getString("sha256"),
        )
    }

    private enum class JournalKind { LOCK, RESTORE }
    private data class PendingMove(val marker: AtomicFile, val bytes: File, val encrypted: File)

    private fun pending(id: String) = PendingMove(AtomicFile(File(staging, "$id.journal")), File(staging, "$id.bin"),
        File(staging, "$id.sealing"))

    private fun createJournal(kind: JournalKind, envelopeId: String, id: String, path: File, digest: String, size: Long): PendingMove {
        check(staging.isDirectory || staging.mkdirs())
        val entry = pending(UUID.randomUUID().toString())
        val data = JSONObject().put("objectId", id).put("path", ownedFile(id, path.path).absolutePath)
            .put("kind", kind.name).put("envelopeId", envelopeId).put("storedSha256", digest)
            .put("storedSize", size).toString().toByteArray(Charsets.UTF_8)
        val output = entry.marker.startWrite()
        try {
            output.write(data)
            entry.marker.finishWrite(output)
        } catch (error: Exception) {
            entry.marker.failWrite(output)
            throw error
        }
        return entry
    }

    private fun blob(envelopeId: String, itemId: String): File {
        require(isUuid(envelopeId) && itemId.matches(OWNER_ID))
        return File(sealedFiles, "$envelopeId-${sha256(itemId.toByteArray(Charsets.UTF_8))}.sealed")
    }

    private fun aad(envelopeId: String, itemId: String) = "halo-private-library-file-v1:$envelopeId:$itemId".toByteArray(Charsets.UTF_8)

    private fun storedSize(data: JSONObject) = data.getLong("storedSize").also { require(it in 0..MAX_BYTES) }
    private fun storedSha(data: JSONObject) = data.getString("storedSha256").also { require(it.matches(SHA256)) }
    private fun sha256(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).toHex()
    private suspend fun digest(file: File, expectedBytes: Long): String {
        require(file.isFile && file.length() == expectedBytes && expectedBytes in 0..MAX_BYTES)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        try {
            file.inputStream().use { stream ->
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= expectedBytes)
                    digest.update(buffer, 0, count)
                }
                check(total == expectedBytes)
            }
            return digest.digest().toHex()
        } finally {
            buffer.fill(0)
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun isUuid(id: String) = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)

    private companion object {
        val MAX_BYTES = PrivateLibraryFilePayload.maxPlainBytes
        val supportedTypes = setOf("FILE", "MEDIA")
        val OWNER_ID = Regex("[A-Za-z0-9_:-][A-Za-z0-9_.:-]{0,159}")
        val EXTENSION = Regex("[a-zA-Z0-9]{1,12}")
        val SHA256 = Regex("[a-f0-9]{64}")
    }
}
