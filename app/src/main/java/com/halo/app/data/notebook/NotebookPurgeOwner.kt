package com.halo.app.data.notebook

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File

/** One durable Notebook deletion/cleanup owner, shared by UI, assistant and background retry. */
class NotebookPurgeOwner(
    private val database: AppDatabase,
    private val managedRoot: File,
    private val schedule: () -> Unit = {},
    private val removeFile: (File) -> Unit = { check(it.delete()) },
    private val now: () -> Long = System::currentTimeMillis,
    private val ownFileAuthority: String = "${com.halo.app.BuildConfig.APPLICATION_ID}.captures",
) {
    private val dao get() = database.notebookPurgeDao()
    private val notes get() = database.notebookDao()
    suspend fun status(id: String): NotebookPurgeReceipt? = database.withTransaction {
        dao.get(id)?.let { NotebookPurgeReceipt(it, notes.getWorkspaceObject(id) != null) }
    }

    suspend fun purge(id: String, expectedVersion: String? = null, expectedRecovery: String? = null, includeRecovery: Boolean = false, priorReceiptVersion: String? = null): NotebookPurgeResult {
        currentCoroutineContext().ensureActive()
        if (database.inTransaction()) return NotebookPurgeResult.Refused("OUTER_TRANSACTION_NOT_SUPPORTED")
        val result = withContext(NonCancellable + Dispatchers.IO) {
            var needsSchedule = false
            try {
                val claimed = database.withTransaction {
                    val previous = dao.get(id)
                    previous?.let { old ->
                        val present = notes.getWorkspaceObject(id) != null
                        if (present) {
                            if (priorReceiptVersion != NotebookPurgeReceipt(old, true).version) return@withTransaction NotebookPurgeResult.Refused("RESTORED_SOURCE_NEEDS_FRESH_RECEIPT")
                        } else {
                            if (expectedVersion != null && old.sourceVersion != expectedVersion) return@withTransaction NotebookPurgeResult.Refused("SOURCE_CHANGED")
                            if (expectedVersion != null && (old.includeRecovery != includeRecovery || old.recoveryVersion != (expectedRecovery ?: "note-draft:none")))
                                return@withTransaction NotebookPurgeResult.Refused("DELETION_CHOICE_CHANGED")
                            return@withTransaction NotebookPurgeResult.Saved(NotebookPurgeReceipt(old, notes.getWorkspaceObject(id) != null), true)
                        }
                    }
                    val header = notes.getWorkspaceObject(id)?.takeIf { it.type == "NOTE" && it.lifecycle == "TRASHED" }
                        ?: return@withTransaction NotebookPurgeResult.Refused("TRASHED_NOTE_REQUIRED")
                    val document = NotebookDocumentRepository(database.notebookDocumentDao(), database).load(id)
                        ?: return@withTransaction NotebookPurgeResult.Refused("NOTE_UNAVAILABLE")
                    val rawNote = notes.getNote(id) ?: return@withTransaction NotebookPurgeResult.Refused("NOTE_UNAVAILABLE")
                    val attachments = database.workspaceAssetDao().observeAttachments(id).first()
                    val assets = notes.getAssetsForObject(id)
                    val version = NotebookNoteIdentity.content(document, header.inInbox, attachments, assets,
                        rawNote.body.takeIf { document.blocks.isEmpty() })
                    if (expectedVersion != null && expectedVersion != version) return@withTransaction NotebookPurgeResult.Refused("SOURCE_CHANGED")
                    val draft = notes.getDraft(id)
                    if (expectedRecovery != null && expectedRecovery != NotebookNoteIdentity.recovery(draft)) return@withTransaction NotebookPurgeResult.Refused("RECOVERY_CHANGED")
                    if (draft != null && (!includeRecovery || (expectedVersion != null && expectedRecovery != NotebookNoteIdentity.recovery(draft))))
                        return@withTransaction NotebookPurgeResult.Refused("RECOVERY_CHOICE_REQUIRED")
                    val managed = assets.filter { it.storageType == "MANAGED_FILE" }
                    val record = NotebookPurgeEntity(id, version, NotebookNoteIdentity.recovery(draft), includeRecovery, (previous?.revision ?: 0) + 1, if (managed.isEmpty()) "COMPLETE" else "PENDING", now(), managed.size,
                        0, 0, assets.size - managed.size, null)
                    if (previous == null) dao.insert(record) else { dao.removeFilesForNote(id); dao.update(record) }
                    dao.insertFiles(managed.map { NotebookPurgeFileEntity(it.id, id, NotebookNoteIdentity.asset(it)) })
                    check(notes.permanentlyDeleteTrashed(id) == 1)
                    assets.filter { it.storageType != "MANAGED_FILE" }.forEach { check(dao.removeAsset(it.id) == 1) }
                    NotebookPurgeResult.Saved(NotebookPurgeReceipt(record, false))
                }
                if (claimed is NotebookPurgeResult.Saved && !claimed.alreadyRecorded) {
                    needsSchedule = claimed.receipt.record.pending > 0
                    if (!needsSchedule) claimed else cleanup(id).let { cleaned ->
                        if (cleaned is NotebookPurgeResult.Saved) {
                            needsSchedule = cleaned.receipt.record.state == "PENDING"
                            cleaned.copy(alreadyRecorded = false)
                        } else cleaned
                    }
                } else claimed
            } finally { if (needsSchedule) runCatching(schedule) }
        }
        return result
    }

    suspend fun retry(id: String, expectedReceipt: String): NotebookPurgeResult {
        currentCoroutineContext().ensureActive()
        if (database.inTransaction()) return NotebookPurgeResult.Refused("OUTER_TRANSACTION_NOT_SUPPORTED")
        val result = withContext(NonCancellable + Dispatchers.IO) {
            var needsSchedule = true
            try { cleanup(id, expectedReceipt).also { needsSchedule = it is NotebookPurgeResult.Saved && it.receipt.record.state == "PENDING" } }
            finally { if (needsSchedule) runCatching(schedule) }
        }
        return result
    }

    /** Reconciliation only consumes committed deletion receipts, never arbitrary orphan files. */
    suspend fun reconcile(limit: Int = 20): Boolean {
        check(!database.inTransaction()) { "Cleanup requires its own transaction boundary" }
        return withContext(Dispatchers.IO) {
            for (id in dao.pending(limit.coerceIn(1, 20))) {
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) { cleanup(id) }
            }
            dao.pendingCount() > 0
        }
    }

    private suspend fun cleanup(id: String, expectedReceipt: String? = null): NotebookPurgeResult = database.withTransaction {
        var record = dao.get(id) ?: return@withTransaction NotebookPurgeResult.Refused("RECEIPT_NOT_FOUND")
        val present = notes.getWorkspaceObject(id) != null
        if (expectedReceipt != null && expectedReceipt != NotebookPurgeReceipt(record, present).version)
            return@withTransaction NotebookPurgeResult.Refused("RECEIPT_CHANGED")
        if (record.state == "COMPLETE") return@withTransaction NotebookPurgeResult.Saved(NotebookPurgeReceipt(record, present), true)
        record = record.copy(lastAttemptOrder = dao.nextAttemptOrder())
        fun blocked(reason: String, retryable: Boolean = false): NotebookPurgeEntity = record.copy(revision = record.revision + 1,
            state = if (retryable) "PENDING" else "REVIEW_REQUIRED", reason = reason)
        if (present) {
            record = blocked("SOURCE_PRESENT"); dao.update(record)
            return@withTransaction NotebookPurgeResult.Saved(NotebookPurgeReceipt(record, true))
        }
        for (index in 0 until 32) {
            val entry = dao.nextFile(id)
            if (entry == null) {
                if (record.pending != 0) { record = blocked("QUEUE_INCONSISTENT"); dao.update(record) }
                break
            }
            val asset = dao.asset(entry.assetId)
            if (asset == null || asset.objectId != id || NotebookNoteIdentity.asset(asset) != entry.identity) {
                record = blocked("ASSET_CHANGED"); dao.update(record)
                return@withTransaction NotebookPurgeResult.Saved(NotebookPurgeReceipt(record, false))
            }
            val candidate = File(asset.pathOrUri)
            val path = runCatching { candidate.canonicalFile }.getOrNull()
            val root = runCatching { managedRoot.canonicalFile }.getOrNull()
            // Trust the configured root alias, but reject traversal and symlinks underneath it.
            val relative = runCatching { managedRoot.absoluteFile.toPath().relativize(candidate.absoluteFile.toPath()) }.getOrNull()
            if (path == null || root == null || relative == null || candidate.toPath().any { it.toString() in setOf(".", "..") } || relative.any { it.toString() in setOf(".", "..") } ||
                path == root || !path.toPath().startsWith(root.toPath()) || root.toPath().resolve(relative).toFile().absolutePath != path.path) {
                record = blocked("UNSAFE_PATH"); dao.update(record)
                return@withTransaction NotebookPurgeResult.Saved(NotebookPurgeReceipt(record, false))
            }
            val ownership = runCatching {
                // Other claims in this same committed deletion are cleanup entries, not live readers.
                (dao.otherAssetPaths(id) + dao.filePaths()).map { referencePath(it)?.canonicalFile }.any { it == path }
            }
            if (ownership.isFailure) {
                record = blocked("OWNERSHIP_UNVERIFIED"); dao.update(record)
                return@withTransaction NotebookPurgeResult.Saved(NotebookPurgeReceipt(record, false))
            }
            val shared = ownership.getOrThrow()
            if (!shared) {
                val removed = runCatching {
                    if (java.nio.file.Files.exists(path.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                        check(path.isFile); runCatching { removeFile(path) }
                    }
                    // notExists distinguishes observed absence from an inaccessible/unknown path.
                    java.nio.file.Files.notExists(path.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
                }.getOrDefault(false)
                if (!removed) {
                    record = blocked("FILE_UNAVAILABLE", true); dao.update(record)
                    return@withTransaction NotebookPurgeResult.Saved(NotebookPurgeReceipt(record, false))
                }
            }
            check(dao.removeAsset(asset.id) == 1)
            check(dao.removeFile(asset.id) == 1)
            record = record.copy(revision = record.revision + 1, state = "PENDING", pending = record.pending - 1,
                exclusiveCleared = record.exclusiveCleared + if (shared) 0 else 1,
                sharedRetained = record.sharedRetained + if (shared) 1 else 0, reason = null)
            dao.update(record)
        }
        if (record.pending == 0) { record = record.copy(state = "COMPLETE", reason = null); dao.update(record) }
        NotebookPurgeResult.Saved(NotebookPurgeReceipt(record, false))
    }

    /** Maps only the public roots declared in capture_paths.xml; never opens external providers. */
    private fun referencePath(reference: String): File? {
        if (reference.startsWith("content:")) {
            val uri = android.net.Uri.parse(reference)
            if (uri.authority != ownFileAuthority) return null
            val segments = uri.pathSegments
            require(segments.size >= 2 && segments.drop(1).none { it in setOf(".", "..") || '/' in it || '\\' in it })
            return when (segments.first()) {
                "workspace-owned" -> segments.drop(1).fold(managedRoot) { parent, name -> File(parent, name) }
                "images" -> null // A separate fixed files-path, outside workspace/owned.
                else -> error("Unrecognized owned URI root")
            }
        }
        if (reference.startsWith("file:")) return File(java.net.URI(reference))
        if (reference.contains("://")) return null
        return File(reference)
    }
}
