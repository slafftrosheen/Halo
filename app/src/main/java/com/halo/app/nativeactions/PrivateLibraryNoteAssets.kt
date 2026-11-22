package com.halo.app.nativeactions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.system.Os
import android.util.AtomicFile
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.ObjectProvenance
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.workspace.NoteAttachmentEntity
import com.halo.app.data.workspace.WorkspaceAssetEntity
import com.halo.app.data.workspace.WorkspaceFileEntity
import com.halo.app.data.workspace.privateLibraryCaptureClaim
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Preserves locally owned attachment bytes and the exact asset/link identities of one Note. */
internal class PrivateLibraryNoteAssets(
    private val context: Context,
    private val database: AppDatabase,
    private val managedWorkspaceFiles: File,
) {
    private val provider = "${context.packageName}.captures"
    private val staged = File(context.noBackupFilesDir, "private-library-note-asset-staging")
    private val sealedFiles = File(context.noBackupFilesDir, "private-library-note-asset-payloads")
    private val sources get() = database.workspaceAssetDao()
    private val backing = PrivateLibraryLocalBacking(context, database, managedWorkspaceFiles)

    suspend fun ownedAttachmentIds(noteId: String): Set<String> = sources.privateOwnedAttachmentIds(noteId).toSet()

    /** Includes both sides of a legacy attachment FK and all aliases of a backing that must move. */
    suspend fun linkedWorkspaceIds(id: String): List<String> = withContext(Dispatchers.IO) {
        val header = database.notebookDao().getWorkspaceObject(id)
            ?.takeIf { it.lifecycle in supportedLifecycle } ?: return@withContext emptyList()
        val path = database.workspaceFileDao().getFile(id)?.let { row ->
            row.ownedCopyPath?.let(backing::reference) ?: if (backing.isLocalUri(row.uri)) backing.exactUri(row.uri) else null
        }
        val paths = if (header.type == "NOTE") currentAssets(header).mapNotNull { pathFor(it.asset) } else listOfNotNull(path)
        val references = backingReferences()
        buildSet {
            if (header.type == "NOTE") addAll(sources.privateLinkedNoteOwners(id))
            for (source in paths.distinct()) {
                val original = requireNotNull(backing.owner(source)) { "The local attachment has no verified owner" }
                if (!original.independent || original.id == id) {
                    add(original.id)
                    references.assets[source].orEmpty().forEach { add(it.objectId) }
                    references.workspaceFiles[source].orEmpty().forEach { add(it.objectId) }
                }
            }
            remove(id)
        }.sorted()
    }

    suspend fun capture(header: WorkspaceObjectEntity, protectedNoteIds: Set<String> = setOf(header.id)): JSONObject? = withContext(Dispatchers.IO) {
        check(header.id in protectedNoteIds)
        val current = currentAssets(header)
        val origin = database.workspaceContextDao().provenanceFor(header.id)
        if (current.isEmpty()) {
            check(header.source != captureSource) { "The original camera attachment is unavailable" }
            return@withContext null
        }
        val references = backingReferences()
        val privatePaths = current.map { pathFor(it.asset) }
        check(privatePaths.filterNotNull().all { it.length() in 0..maxBytes })
        val checksums = mutableMapOf<File, String>()
        val rows = current.zip(privatePaths).map { (entry, file) ->
            check(entry.link == null || entry.link.noteId in protectedNoteIds) { "Include the linked Note in this lock" }
            if (file == null) return@map encode(entry)
            val size = file.length()
            check(entry.asset.bytes == null || entry.asset.bytes == size) { "The attachment changed" }
            val hash = checksums[file] ?: digest(file, size).also { checksums[file] = it }
            val physical = checkBacking(entry, file, hash, references, protectedNoteIds)
            verifyInlineAddresses(header.id, file)
            entry.link?.noteId?.takeIf { it != header.id }?.let { verifyInlineAddresses(it, file) }
            encode(entry).put("storedSize", size).put("storedSha256", hash).put("streamed", true)
                .put("workspaceBacking", physical.workspaceOwner?.encode())
                .put("storageRoot", physical.storageRoot)
                .put("originalBacking", physical.original?.encode())
        }
        val capture = if (header.source == captureSource) {
            val saved = requireNotNull(origin)
            val metadata = JSONObject(saved.metadata)
            val capturePath = ownedPath(captureStorage, File(metadata.getString("localPath")))
            val originals = current.zip(privatePaths).zip(rows).filter { (pair, row) ->
                val (entry, file) = pair
                entry.asset.storageType == captureStorage && entry.asset.type == "IMAGE" &&
                    capturePath == file && metadata.getString("sha256") == row.getString("storedSha256") &&
                    metadata.getString("mimeType") == entry.asset.mimeType
            }
            val original = originals.firstOrNull { it.first.first.link != null } ?: originals.firstOrNull()
                ?: error("The original camera attachment could not be verified")
            check(saved.sourceId.isNotBlank() && saved.sourceId.length <= 256 && saved.revision.isNotBlank() &&
                saved.revision.length <= 256 && saved.revision == metadata.getString("captureId"))
            JSONObject().put("source", saved.sourceId).put("revision", saved.revision).put("metadata", saved.metadata)
                .put("assetId", original.first.first.asset.id)
        } else {
            null
        }
        JSONObject().put("version", 2).put("assets", JSONArray(rows)).put("capture", capture)
    }

    suspend fun afterSeal(header: WorkspaceObjectEntity, payload: JSONObject, access: VaultSession, envelopeId: String,
        protectedNoteIds: Set<String> = setOf(header.id),
    ) = withContext(Dispatchers.IO) {
        check(header.id in protectedNoteIds && payload.getInt("version") == 2)
        val originals = currentAssets(header)
        val rows = rows(payload)
        check(rows.map { decode(header.id, it) } == originals) { "The attachments changed during review" }
        val references = backingReferences()
        checkCaptureOrigin(header, payload)
        originals.zip(rows).forEach { (entry, saved) ->
            access.requireOpen()
            if (!saved.optBoolean("streamed", false)) {
                check(sources.deleteAsset(entry.asset.id) == 1)
                return@forEach
            }
            val file = requireNotNull(pathFor(entry.asset))
            val expected = storedDigest(saved)
            val size = storedSize(saved)
            check(file.length() == size)
            val origin = saved.optJSONObject("originalBacking")?.let { PrivateLibraryLocalBacking.Owner.decode(it) }
            val physical = checkBacking(entry, file, expected, references, protectedNoteIds, origin)
            val move = journal(Kind.LOCK, entry.asset, file, expected, size, envelopeId, physical.storageRoot)
            val encrypted = blob(envelopeId, entry.asset.id)
            check(!pathExists(encrypted))
            checkSpace(sealedFiles, size + (size / (256 * 1024) + 1) * 16 + 12)
            file.inputStream().buffered().use { input ->
                move.encrypted.outputStream().use { output ->
                    PrivateLibraryFilePayload.seal(access, aad(envelopeId, header.id, entry.asset.id), size, expected, input, output.buffered())
                    output.fd.sync()
                }
            }
            access.requireOpen()
            moveFile(move.encrypted, encrypted)
            if (!physical.hasOtherUsers && physical.workspaceOwner == null) {
                context.revokeUriPermission(uri(file), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                moveFile(file, move.data)
            }
            check(sources.deleteAsset(entry.asset.id) == 1)
            references.assets[file]?.removeAll { it.id == entry.asset.id }
        }
    }

    suspend fun restore(items: List<Pair<WorkspaceObjectEntity, JSONObject>>, access: VaultSession, envelopeId: String) =
        withContext(Dispatchers.IO) {
            val restoredPaths = linkedMapOf<File, RestoredBacking>()
            val rebindings = linkedMapOf<String, MutableMap<String, String>>()
            val coveredAssets = items.flatMap { (_, payload) -> rows(payload).map { it.getString("id") } }.toSet()
            for ((header, payload) in items) restoreNote(header, payload, access, envelopeId, restoredPaths, rebindings, coveredAssets)
            rebindings.forEach { (noteId, paths) -> rebindText(noteId, paths) }
        }

    private suspend fun restoreNote(header: WorkspaceObjectEntity, payload: JSONObject, access: VaultSession,
        envelopeId: String, restoredPaths: MutableMap<File, RestoredBacking>,
        rebindings: MutableMap<String, MutableMap<String, String>>, coveredAssets: Set<String>,
    ) {
        require(header.type == "NOTE" && header.contentRef == "note:${header.id}")
        val replacementByAsset = linkedMapOf<String, File>()
        for (row in rows(payload)) {
            access.requireOpen()
            val entry = decode(header.id, row)
            if (entry.asset.storageType == externalStorage && !row.has("storedSize")) {
                validateExternal(entry.asset)
                check(!backing.isLocalUri(entry.asset.pathOrUri))
                sources.insertAsset(entry.asset)
                entry.link?.let { sources.insertAttachment(it) }
                continue
            }
            val old = requireNotNull(pathFor(entry.asset))
            val size = storedSize(row)
            val expected = storedDigest(row)
            val previous = restoredPaths[old]
            val fileOwner = row.optJSONObject("workspaceBacking")?.let { PrivateLibraryLocalBacking.Owner.decode(it) }
            val currentOriginal = if (previous == null) {
                if (fileOwner != null) backing.current(fileOwner).also {
                    check(digest(it, size) == expected) { "The original shared file changed" }
                } else old.takeIf {
                    it.isFile && it.length() == size && backing.owner(it)?.independent == true && digest(it, size) == expected
                }
            } else null
            val root = row.optString("storageRoot").ifEmpty { entry.asset.storageType }
            val new = previous?.file ?: currentOriginal ?: freshPath(old, root)
            check(previous == null || previous.size == size && previous.digest == expected)
            if (entry.asset.storageType == externalStorage && previous == null && currentOriginal == null) {
                val origin = row.optJSONObject("originalBacking")?.let { PrivateLibraryLocalBacking.Owner.decode(it) }
                check(origin?.assetId?.let(coveredAssets::contains) == true) { "The original shared attachment changed" }
            }
            val streamed = row.optBoolean("streamed", false)
            val referenceOnly = previous != null || currentOriginal != null
            val move = journal(Kind.RESTORE, entry.asset, new, expected, size, envelopeId.takeIf { streamed },
                physicalRoot(new), referenceOnly)
            if (!referenceOnly) {
                check(!pathExists(new))
                checkSpace(staged, size)
                move.data.outputStream().use { output ->
                    val target = output.buffered()
                    openPayload(access, envelopeId, header.id, row) { target.write(it) }
                    target.flush()
                    output.fd.sync()
                }
                access.requireOpen()
                moveFile(move.data, new)
                restoredPaths[old] = RestoredBacking(new, size, expected)
            }
            if (currentOriginal != null) restoredPaths[old] = RestoredBacking(new, size, expected)
            val stored = if (entry.asset.storageType in setOf(captureStorage, externalStorage)) uri(new).toString() else new.path
            sources.insertAsset(entry.asset.copy(pathOrUri = stored))
            entry.link?.let { sources.insertAttachment(it) }
            replacementByAsset[entry.asset.id] = new
            originalAddresses(old).zip(originalAddresses(new)).forEach { (from, to) ->
                for (noteId in listOfNotNull(header.id, entry.link?.noteId).distinct()) {
                    rebindings.getOrPut(noteId) { linkedMapOf() }[from] = to
                }
            }
        }
        payload.optJSONObject("capture")?.let { saved ->
            val path = requireNotNull(replacementByAsset[saved.getString("assetId")])
            val metadata = JSONObject(saved.getString("metadata")).put("localPath", path.path)
            database.workspaceContextDao().provenance(ObjectProvenance(header.id, saved.getString("source"),
                saved.getString("revision"), metadata.toString()))
        }
    }

    suspend fun readPreview(access: VaultSession, envelopeId: String, noteId: String, row: JSONObject, maxBytes: Int): ByteArray? {
        val size = storedSize(row)
        if (maxBytes < 0 || size > minOf(maxBytes.toLong(), maxPreviewBytes)) return null
        val bytes = ByteArray(size.toInt())
        try {
            var offset = 0
            openPayload(access, envelopeId, noteId, row) { chunk ->
                chunk.copyInto(bytes, offset)
                offset += chunk.size
            }
            check(offset == bytes.size)
            access.requireOpen()
            return bytes
        } catch (error: Throwable) {
            bytes.fill(0)
            throw error
        }
    }

    suspend fun openPreview(access: VaultSession, envelopeId: String, noteId: String, row: JSONObject): PrivateLibraryReadSource {
        if (!row.optBoolean("streamed", false)) {
            val bytes = requireNotNull(readPreview(access, envelopeId, noteId, row, maxLegacyInline.toInt()))
            return PrivateLibraryFilePayload.inMemory(access, bytes)
        }
        val assetId = row.getString("id")
        val ciphertext = blob(envelopeId, assetId)
        check(ciphertext.isFile && !Files.isSymbolicLink(ciphertext.toPath()))
        return PrivateLibraryFilePayload.randomAccess(access, aad(envelopeId, noteId, assetId), storedSize(row), ciphertext)
    }

    private suspend fun openPayload(access: VaultSession, envelopeId: String, noteId: String, row: JSONObject,
        receive: (ByteArray) -> Unit,
    ) {
        val size = storedSize(row)
        val hash = storedDigest(row)
        if (row.optBoolean("streamed", false)) {
            val assetId = row.getString("id")
            val file = blob(envelopeId, assetId)
            check(file.isFile && !Files.isSymbolicLink(file.toPath()))
            file.inputStream().buffered().use { input ->
                PrivateLibraryFilePayload.open(access, aad(envelopeId, noteId, assetId), size, hash, input, receive)
            }
        } else {
            // Earlier installed versions used inline data inside the authenticated Room envelope.
            val encoded = row.getString("payload")
            check(size in 1..maxLegacyInline && encoded.length.toLong() <= ((size + 2) / 3) * 4)
            val data = Base64.decode(encoded, Base64.NO_WRAP)
            try {
                check(data.size.toLong() == size && digest(data) == hash)
                access.requireOpen()
                receive(data)
            } finally { data.fill(0) }
        }
    }

    /** Runs after the outer Room transaction, and again on process startup. */
    suspend fun settle() = withContext(Dispatchers.IO) {
        if (!staged.isDirectory) return@withContext
        database.withTransaction {
            val markers = requireNotNull(staged.listFiles()).mapNotNull { file ->
                val candidate = file.name.removeSuffix(".bak")
                candidate.takeIf { it.endsWith(".journal") }?.removeSuffix(".journal")?.takeIf(::isUuid)
            }.distinct()
            for (id in markers) recover(pending(id))
        }
    }

    private suspend fun currentAssets(header: WorkspaceObjectEntity): List<OwnedAttachment> {
        check(header.type == "NOTE" && header.lifecycle in supportedLifecycle && header.contentRef == "note:${header.id}")
        val assets = database.notebookDao().getAssetsForObject(header.id).sortedBy { it.id }
        val links = sources.privateBackingLinks(header.id).associateBy { it.assetId }
        return assets.map { asset ->
            check(isUuid(asset.id) && asset.storageType in setOf(captureStorage, managedStorage, externalStorage) && asset.mimeType.isNotBlank())
            if (asset.storageType == externalStorage) validateExternal(asset)
            OwnedAttachment(asset, links[asset.id])
        }
    }

    private fun validateExternal(asset: WorkspaceAssetEntity) {
        val uri = Uri.parse(asset.pathOrUri)
        check(uri.scheme == "content" && !uri.authority.isNullOrBlank()) {
            "The attachment needs a verified external provider"
        }
        if (uri.authority == provider) backing.exactUri(asset.pathOrUri)
    }

    private suspend fun backingReferences(): BackingReferences {
        val assets = mutableMapOf<File, MutableList<WorkspaceAssetEntity>>()
        for (record in sources.privateAssetBackings()) {
            val path = backing.reference(record.pathOrUri) ?: continue
            assets.getOrPut(path) { mutableListOf() }.add(record)
        }
        val files = mutableMapOf<File, MutableList<WorkspaceFileEntity>>()
        for (file in database.workspaceFileDao().allFiles()) {
            val paths = listOfNotNull(file.ownedCopyPath?.let(backing::reference), backing.reference(file.uri)).toSet()
            paths.forEach { path -> files.getOrPut(path) { mutableListOf() }.add(file) }
        }
        return BackingReferences(assets, files)
    }

    private suspend fun checkBacking(entry: OwnedAttachment, file: File, hash: String, references: BackingReferences,
        protectedNotes: Set<String>, recorded: PrivateLibraryLocalBacking.Owner? = null,
    ): PhysicalBacking {
        check(file.isFile && !Files.isSymbolicLink(file.toPath()) && Os.lstat(file.path).st_nlink == 1L)
        val otherAssets = references.assets[file].orEmpty().filter { it.id != entry.asset.id }
        val publicOwners = otherAssets.filter { it.objectId !in protectedNotes }
        val fileRecords = references.workspaceFiles[file].orEmpty()
        val publicFiles = fileRecords.filter { it.objectId !in protectedNotes }
        val original = backing.owner(file) ?: recorded?.takeIf { it.id in protectedNotes }
            ?: error("The attachment backing cannot be authenticated")
        val publicOriginal = original.independent && original.id !in protectedNotes &&
            (original.assetId == null || publicOwners.any { ownsPublicBacking(it, file, hash) })
        check(publicOriginal || publicOwners.isEmpty() && publicFiles.isEmpty()) {
            "Include all owners that share this attachment"
        }
        val linkOwners = otherAssets.map { it.id }.chunked(400).flatMap { sources.linkedAttachmentNoteIds(it) }
        val rebinding = (otherAssets.filter { it.objectId in protectedNotes }.map { it.objectId } +
            listOfNotNull(entry.asset.objectId, entry.link?.noteId) + linkOwners.filter(protectedNotes::contains)).distinct()
        check(!sources.privateAttachmentHasPublicReferences(ownerTokens(protectedNotes), ownerTokens(rebinding), entry.asset.id, entry.link?.id, file.name,
            publicBacking = publicOriginal)) {
            "This attachment has another public reference"
        }
        val root = physicalRoot(file)
        val workspace = original.takeIf { it.assetId == null }
        check((root == workspaceStorage) == (workspace != null))
        return PhysicalBacking(root, workspace, original, otherAssets.isNotEmpty() || publicOriginal)
    }

    private fun pathFor(asset: WorkspaceAssetEntity): File? = when (asset.storageType) {
        externalStorage -> if (backing.isLocalUri(asset.pathOrUri)) backing.exactUri(asset.pathOrUri) else null
        managedStorage -> requireNotNull(backing.reference(asset.pathOrUri))
        captureStorage -> backing.exactUri(asset.pathOrUri)
        else -> error("Unknown attachment storage")
    }

    private fun physicalRoot(file: File): String = when (file.parentFile) {
        storageRoot(captureStorage).canonicalFile -> captureStorage
        managedWorkspaceFiles.canonicalFile -> managedStorage
        else -> workspaceStorage
    }

    /** IDs cannot contain the delimiter. This also keeps a large closure below SQLite's bind limit. */
    private fun ownerTokens(ids: Collection<String>): String = ids.joinToString("|", prefix = "|", postfix = "|") {
        check(it.isNotEmpty() && '|' !in it)
        it
    }

    private suspend fun ownsPublicBacking(asset: WorkspaceAssetEntity, file: File, hash: String): Boolean {
        val owner = database.notebookDao().getWorkspaceObject(asset.objectId) ?: return false
        if (owner.type != "NOTE" || owner.lifecycle !in setOf("ACTIVE", "ARCHIVED") || owner.contentRef != "note:${owner.id}") return false
        return when (asset.storageType) {
            // The canonical managed writer names its original after the asset ID.
            managedStorage -> uuidToken.find(file.name)?.value == asset.id && resolve(asset) == file
            captureStorage -> {
                if (owner.source != captureSource || asset.type != "IMAGE" || resolve(asset) != file) return false
                val origin = database.workspaceContextDao().provenanceFor(owner.id) ?: return false
                val provenance = JSONObject(origin.metadata)
                provenance.optString("captureId") == origin.revision && provenance.optString("sha256") == hash &&
                    provenance.optString("mimeType") == asset.mimeType &&
                    provenance.optString("localPath").takeIf(String::isNotBlank)?.let { referencePath(it) } == file
            }
            else -> false
        }
    }

    private suspend fun checkCaptureOrigin(header: WorkspaceObjectEntity, payload: JSONObject) {
        val saved = payload.optJSONObject("capture") ?: return
        check(header.source == captureSource && database.workspaceContextDao().provenanceFor(header.id) ==
            ObjectProvenance(header.id, saved.getString("source"), saved.getString("revision"), saved.getString("metadata")))
    }

    private suspend fun recover(move: Pending) {
        val data = move.marker.openRead().use { input ->
            check(input.channel.size() in 1L..4096L)
            JSONObject(input.readBytes().toString(Charsets.UTF_8))
        }
        val id = data.getString("assetId")
        val noteId = data.getString("noteId")
        val storage = data.getString("storage")
        check(isUuid(id) && isUuid(noteId))
        val file = ownedPath(storage, File(data.getString("path")))
        val expected = storedDigest(data)
        val size = data.optLong("storedSize").takeIf { data.has("storedSize") }?.also { check(it in 0..maxBytes) }
        val envelope = data.optString("envelopeId").takeIf(String::isNotBlank)
        val ciphertext = envelope?.let { blob(it, id) }
        val active = database.notebookPurgeDao().asset(id)
        check(active == null || active.objectId == noteId && pathFor(active) == file)
        when (Kind.valueOf(data.getString("kind"))) {
            Kind.LOCK -> if (active == null) {
                if (envelope != null) check(database.privateLibraryDao().summary(envelope) != null &&
                    ciphertext != null && ciphertext.isFile && !Files.isSymbolicLink(ciphertext.toPath()))
                if (envelope != null && pathExists(move.data)) {
                    check(move.data.length() == size)
                    deleteStagingFile(move.data)
                } else deleteRecordedFile(move.data, expected, size)
            } else {
                if (pathExists(move.data)) {
                    check(digest(move.data, size) == expected)
                    if (pathExists(file)) {
                        check(digest(file, size) == expected)
                        deleteRecordedFile(move.data, expected, size)
                    } else moveFile(move.data, file)
                } else check(file.isFile && (size == null || file.length() == size))
                ciphertext?.let(::deleteStagingFile)
            }
            Kind.RESTORE -> {
                if (active == null) {
                    if (envelope != null) check(database.privateLibraryDao().summary(envelope) != null && ciphertext?.isFile == true)
                    if (!data.optBoolean("referenceOnly")) deleteRecordedFile(file, expected, size)
                } else {
                    check(file.isFile && (size == null || file.length() == size))
                    if (envelope != null) check(database.privateLibraryDao().summary(envelope) == null)
                    ciphertext?.let(::deleteStagingFile)
                }
                // A failed write can leave only a partial staging file with no published path.
                deleteStagingFile(move.data)
            }
        }
        deleteStagingFile(move.encrypted)
        move.marker.delete()
    }

    private suspend fun deleteRecordedFile(file: File, expected: String, size: Long?) {
        if (pathExists(file)) check(file.isFile && !Files.isSymbolicLink(file.toPath()) && digest(file, size) == expected && file.delete())
    }

    private fun deleteStagingFile(file: File) {
        if (pathExists(file)) check(file.isFile && !Files.isSymbolicLink(file.toPath()) && file.delete())
    }

    private fun resolve(asset: WorkspaceAssetEntity): File = when (asset.storageType) {
        captureStorage -> {
            val original = Uri.parse(asset.pathOrUri)
            check(original.scheme == "content" && original.authority == provider)
            val file = ownedPath(captureStorage, requireNotNull(referencePath(asset.pathOrUri)))
            check(uri(file) == original) { "Only a canonical camera URI can be protected" }
            file
        }
        managedStorage -> ownedPath(managedStorage, File(asset.pathOrUri))
        else -> error("External attachments stay with their current owner")
    }

    private fun ownedPath(storage: String, file: File): File {
        if (storage == workspaceStorage) {
            val candidate = requireNotNull(backing.reference(file.path))
            check(physicalRoot(candidate) == workspaceStorage)
            return candidate
        }
        val root = storageRoot(storage).canonicalFile
        val safe = File(root, file.name)
        check(file.isAbsolute && file.canonicalFile == safe && !Files.isSymbolicLink(file.toPath()) &&
            !file.toPath().any { it.toString() in setOf(".", "..") } &&
            (if (storage == captureStorage) captureName else managedName).matches(safe.name)) { "Invalid owned attachment path" }
        return safe
    }

    private fun storageRoot(storage: String): File = when (storage) {
        captureStorage -> File(context.filesDir, "captures/images")
        managedStorage -> managedWorkspaceFiles
        else -> error("Unsupported attachment storage")
    }

    private fun referencePath(value: String): File? {
        val uri = Uri.parse(value)
        return when (uri.scheme) {
            "content" -> {
                if (uri.authority != provider) return null
                val names = uri.pathSegments
                if (names.size < 2) error("Invalid owned file reference")
                val root = when (names.first()) {
                    "images" -> File(context.filesDir, "captures/images")
                    "workspace-owned" -> managedWorkspaceFiles
                    "coach-captures" -> return null
                    else -> error("Unknown owned file root")
                }
                check(names.drop(1).none { it in setOf(".", "..") || '/' in it || '\\' in it })
                names.drop(1).fold(root) { directory, name -> File(directory, name) }.canonicalFile
            }
            "file" -> uri.path?.let { File(it).canonicalFile }
            null -> File(value).takeIf { it.isAbsolute }?.canonicalFile
            else -> null
        }
    }

    private fun freshPath(old: File, storage: String): File {
        val oldToken = uuidToken.find(old.name) ?: error("Attachment path cannot be safely rotated")
        val name = old.name.replaceRange(oldToken.range, UUID.randomUUID().toString())
        return ownedPath(storage, File(old.parentFile, name))
    }

    private suspend fun verifyInlineAddresses(noteId: String, asset: File) {
        val accepted = originalAddresses(asset).toSet()
        val body = requireNotNull(database.privateLibraryDao().note(noteId)).body
        val draft = database.notebookDao().getDraft(noteId)
        val text = buildList {
            add(body)
            for (block in database.notebookDocumentDao().getBlocks(noteId)) {
                add(block.text)
                block.presentation?.let { style ->
                    if (accepted.any { block.text.contains(it) || style.contains(it) }) JSONObject(style)
                }
            }
            draft?.let { recovery ->
                add(recovery.body)
                recovery.structureJson?.let { raw -> rewriteDraft(raw) { value -> add(value); value } }
            }
        }
        for (value in text) for (match in inlineUri.findAll(value)) {
            val link = match.value.trimEnd('.', ',', '!', ';')
            if (referencePath(link) == asset) check(link in accepted) { "The note has a noncanonical attachment link" }
        }
    }

    private suspend fun rebindText(noteId: String, paths: Map<String, String>) {
        val replacements = Regex(paths.keys.sortedByDescending(String::length).joinToString("|") { Regex.escape(it) })
        fun rewrite(text: String): String = replacements.replace(text) { paths.getValue(it.value) }
        fun presentation(text: String, raw: String): String {
            val edits = replacements.findAll(text).map { it.range to paths.getValue(it.value).length }.toList()
            if (edits.isEmpty() && !replacements.containsMatchIn(raw)) return raw
            val data = JSONObject(raw)
            fun mapOffset(value: Int, end: Boolean): Int {
                var shift = 0
                for ((range, count) in edits) {
                    val previousLength = range.last - range.first + 1
                    if (value <= range.first) return value + shift
                    if (value <= range.last && previousLength != count) return range.first + shift + if (end) count else 0
                    shift += count - previousLength
                }
                return value + shift
            }
            for (field in listOf("fonts", "comments")) data.optJSONArray(field)?.let { spans ->
                repeat(spans.length()) { index ->
                    val span = spans.getJSONObject(index)
                    if (span.has("start")) span.put("start", mapOffset(span.getInt("start"), false))
                    if (span.has("end")) span.put("end", mapOffset(span.getInt("end"), true))
                    if (span.has("quote")) span.put("quote", rewrite(span.getString("quote")))
                }
            }
            data.optJSONObject("change")?.let { if (it.has("before")) it.put("before", rewrite(it.getString("before"))) }
            return data.toString()
        }
        val body = requireNotNull(database.privateLibraryDao().note(noteId)).body
        val updated = rewrite(body)
        if (updated != body) check(sources.rebindRestoredNoteBody(noteId, body, updated) == 1)
        for (block in database.notebookDocumentDao().getBlocks(noteId)) {
            val changed = rewrite(block.text)
            val style = block.presentation?.let { presentation(block.text, it) }
            if (changed != block.text || style != block.presentation) {
                check(sources.rebindRestoredBlockText(noteId, block.id, block.text, changed, style) == 1)
            }
        }
        val notebook = database.notebookDao()
        notebook.getDraft(noteId)?.let { draft ->
            val recovery = draft.copy(body = rewrite(draft.body),
                structureJson = draft.structureJson?.let { rewriteDraft(it, ::presentation, ::rewrite) })
            if (recovery != draft) notebook.upsertDraft(recovery)
        }
    }

    private fun rewriteDraft(raw: String, presentation: ((String, String) -> String)? = null, rewrite: (String) -> String): String {
        val isArray = raw.trimStart().startsWith("[")
        val root = if (isArray) null else JSONObject(raw)
        val blocks = root?.getJSONArray("blocks") ?: JSONArray(raw)
        var changed = false
        fun update(row: JSONObject, key: String) {
            if (!row.has(key) || row.isNull(key)) return
            val before = row.getString(key)
            val after = rewrite(before)
            if (after != before) { row.put(key, after); changed = true }
            if (key == "text" && presentation != null && !row.isNull("presentation")) {
                val style = row.getString("presentation")
                val rebound = presentation(before, style)
                if (rebound != style) { row.put("presentation", rebound); changed = true }
            }
        }
        root?.let { update(it, "sourceDraft") }
        repeat(blocks.length()) { update(blocks.getJSONObject(it), "text") }
        return if (changed) root?.toString() ?: blocks.toString() else raw
    }

    private fun originalAddresses(file: File): List<String> {
        val captureRoot = storageRoot(captureStorage)
        val root = if (file.parentFile == captureRoot.canonicalFile) captureRoot else managedWorkspaceFiles
        val relative = root.canonicalFile.toPath().relativize(file.toPath()).toString()
        val original = File(root, relative).absoluteFile
        return listOf(uri(file).toString(), Uri.fromFile(file).toString(), file.path, Uri.fromFile(original).toString(), original.path)
    }
    private fun uri(file: File) = FileProvider.getUriForFile(context, provider, file)

    private fun journal(kind: Kind, asset: WorkspaceAssetEntity, path: File, hash: String, size: Long, envelopeId: String?,
        storage: String, referenceOnly: Boolean = false,
    ): Pending {
        check(staged.isDirectory || staged.mkdirs() || staged.isDirectory)
        val move = pending(UUID.randomUUID().toString())
        val data = JSONObject().put("kind", kind.name).put("assetId", asset.id).put("noteId", asset.objectId)
            .put("storage", storage).put("path", ownedPath(storage, path).path).put("storedSha256", hash)
            .put("storedSize", size).put("envelopeId", envelopeId).put("referenceOnly", referenceOnly)
            .toString().toByteArray(Charsets.UTF_8)
        val stream = move.marker.startWrite()
        try {
            stream.write(data)
            stream.fd.sync()
            move.marker.finishWrite(stream)
        } catch (failure: Exception) { move.marker.failWrite(stream); throw failure }
        return move
    }

    private fun pending(id: String) = Pending(AtomicFile(File(staged, "$id.journal")), File(staged, "$id.bin"), File(staged, "$id.sealed"))

    private fun blob(envelopeId: String, assetId: String): File {
        check(isUuid(envelopeId) && isUuid(assetId))
        return File(sealedFiles, "$envelopeId-$assetId.bin")
    }

    private fun aad(envelopeId: String, noteId: String, assetId: String): ByteArray {
        check(isUuid(envelopeId) && isUuid(noteId) && isUuid(assetId))
        return "halo-private-note-asset-v2:$envelopeId:$noteId:$assetId".toByteArray(Charsets.UTF_8)
    }

    private fun checkSpace(directory: File, bytes: Long) {
        check(directory.isDirectory || directory.mkdirs() || directory.isDirectory)
        check(directory.usableSpace >= bytes) { "Free more storage before moving the attachment" }
    }
    private fun moveFile(from: File, to: File) {
        check(!pathExists(to) && !Files.isSymbolicLink(from.toPath()))
        val targetRoot = requireNotNull(to.parentFile)
        check(targetRoot.isDirectory || targetRoot.mkdirs() || targetRoot.isDirectory)
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    private fun pathExists(file: File): Boolean = Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)

    private suspend fun digest(file: File, expectedSize: Long?): String {
        val size = expectedSize ?: file.length()
        check(size in 0..maxBytes && file.isFile && file.length() == size && !Files.isSymbolicLink(file.toPath()))
        val hash = MessageDigest.getInstance("SHA-256")
        val data = ByteArray(64 * 1024)
        return try {
            file.inputStream().use { input ->
                var seen = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(data)
                    if (count < 0) break
                    seen += count
                    check(seen <= size)
                    hash.update(data, 0, count)
                }
                check(seen == size)
            }
            hash.digest().joinToString("") { "%02x".format(it) }
        } finally { data.fill(0) }
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun storedSize(row: JSONObject) = row.getLong("storedSize").also { check(it in 0..maxBytes) }
    private fun storedDigest(row: JSONObject) = row.getString("storedSha256").also { check(sha.matches(it)) }

    private fun encode(entry: OwnedAttachment) = with(entry.asset) {
        JSONObject().put("id", id).put("type", type).put("mimeType", mimeType).put("storage", storageType).put("path", pathOrUri)
            .put("persisted", permissionPersisted).put("name", displayName).put("bytes", bytes).put("width", width).put("height", height)
            .put("duration", durationMs).put("created", createdAtEpochMs).put("link", entry.link?.let {
                JSONObject().put("id", it.id).put("noteId", it.noteId).put("position", it.position).put("caption", it.caption)
                    .put("created", it.createdAtEpochMs)
            })
    }

    private fun decode(noteId: String, row: JSONObject): OwnedAttachment = with(row) {
        val id = getString("id")
        check(isUuid(id))
        val asset = WorkspaceAssetEntity(id, noteId, getString("type"), getString("mimeType"), getString("storage"),
            getString("path"), getBoolean("persisted"), nullable("name"), number("bytes"), number("width")?.toInt(),
            number("height")?.toInt(), number("duration"), getLong("created"))
        val link = optJSONObject("link")?.let { NoteAttachmentEntity(it.getString("id"), it.optString("noteId").ifEmpty { noteId }, id,
            it.getInt("position"), it.nullable("caption"), it.getLong("created")) }
        OwnedAttachment(asset, link)
    }

    private fun JSONObject.nullable(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.number(key: String): Long? = if (isNull(key)) null else getLong(key)
    private data class OwnedAttachment(val asset: WorkspaceAssetEntity, val link: NoteAttachmentEntity?)
    private enum class Kind { LOCK, RESTORE }
    private data class Pending(val marker: AtomicFile, val data: File, val encrypted: File)
    private data class RestoredBacking(val file: File, val size: Long, val digest: String)
    private data class BackingReferences(val assets: Map<File, MutableList<WorkspaceAssetEntity>>,
        val workspaceFiles: Map<File, List<WorkspaceFileEntity>>)
    private data class PhysicalBacking(val storageRoot: String, val workspaceOwner: PrivateLibraryLocalBacking.Owner?,
        val original: PrivateLibraryLocalBacking.Owner?, val hasOtherUsers: Boolean)

    fun mediaEntries(payload: JSONObject): List<JSONObject> =
        rows(payload).filter { it.optJSONObject("link") != null && it.has("storedSize") }
        .sortedBy { it.getJSONObject("link").getInt("position") }

    fun claimFingerprints(payload: JSONObject): List<String> = payload.optJSONObject("capture")?.let {
        listOf(privateLibraryCaptureClaim(it.getString("source"), it.getString("revision")))
    }.orEmpty()

    private companion object {
        private const val captureSource = "DEVICE_CAPTURE"
        private const val captureStorage = "APP_OWNED"
        private const val managedStorage = "MANAGED_FILE"
        private const val workspaceStorage = "WORKSPACE_FILE"
        private const val externalStorage = "EXTERNAL_URI"
        private val maxBytes = PrivateLibraryFilePayload.maxPlainBytes
        private const val maxLegacyInline = 8L * 1024 * 1024
        private const val maxPreviewBytes = 8L * 1024 * 1024
        private val supportedLifecycle = setOf("ACTIVE", "ARCHIVED", "TRASHED")
        private const val uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        private val uuidToken = Regex(uuid)
        private val captureName = Regex("$uuid\\.jpg")
        private val managedName = Regex("(?:asset_[0-9]+_$uuid\\.bin|(?:edited|generated)_$uuid\\.[a-zA-Z0-9]{1,12})")
        private val sha = Regex("[0-9a-f]{64}")
        private val inlineUri = Regex("(?:content|file)://[^\\s\\[\\]<>\\)\"']+")

        private fun isUuid(id: String) = uuidToken.matches(id)
        private fun rows(payload: JSONObject): List<JSONObject> {
            val version = payload.getInt("version")
            check(version in 1..2)
            return payload.getJSONArray("assets").let { array ->
                check(array.length() > 0)
                List(array.length(), array::getJSONObject).also { rows ->
                    check(rows.all { it.optBoolean("streamed", false) == (version == 2) })
                }
            }
        }

    }
}
