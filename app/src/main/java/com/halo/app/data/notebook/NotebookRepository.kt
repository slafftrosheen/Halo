package com.halo.app.data.notebook

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface NotebookRepository {
    fun observeNotes(
        query: String = "",
        lifecycle: WorkspaceLifecycle = WorkspaceLifecycle.ACTIVE,
        inboxOnly: Boolean = false
    ): Flow<List<NotebookNote>>

    suspend fun getNote(id: String): NotebookNote?
    suspend fun getDraft(id: String): NoteDraft?
    suspend fun createNote(body: String = "", source: WorkspaceSource = WorkspaceSource.QUICK_NOTE, title: String? = null): NotebookNote
    suspend fun saveDraft(id: String, body: String)
    suspend fun saveNote(id: String, body: String): Boolean
    suspend fun renameNote(id: String, title: String): Boolean
    suspend fun setLifecycle(id: String, lifecycle: WorkspaceLifecycle): Boolean
    suspend fun removeFromInbox(id: String): Boolean
    suspend fun permanentlyDelete(id: String): Boolean
    suspend fun purgeWithReceipt(id: String, version: String? = null, recoveryVersion: String? = null, includeRecovery: Boolean = false, priorReceiptVersion: String? = null): NotebookPurgeResult = NotebookPurgeResult.Refused("PURGE_OWNER_UNAVAILABLE")
    suspend fun purgeStatus(id: String): NotebookPurgeReceipt? = null
    suspend fun retryPurge(id: String, receiptVersion: String): NotebookPurgeResult = NotebookPurgeResult.Refused("PURGE_OWNER_UNAVAILABLE")
}

class RoomNotebookRepository(
    private val dao: NotebookDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /**
     * Optional canonical document writer. When a note owns blocks, the body-only path must not
     * desynchronise the derived projection: it is re-routed through the command path instead.
     */
    private val documents: NotebookDocumentRepository? = null,
    private val purgeOwner: NotebookPurgeOwner? = null,
) : NotebookRepository {
    override fun observeNotes(
        query: String,
        lifecycle: WorkspaceLifecycle,
        inboxOnly: Boolean
    ): Flow<List<NotebookNote>> = dao.observeNotes(
        searchPattern = query.toLikePattern(),
        lifecycle = lifecycle.name,
        inboxOnly = inboxOnly
    ).map { rows -> rows.map(NotebookNoteRow::toDomain) }

    override suspend fun getNote(id: String): NotebookNote? = dao.getNote(id)?.toDomain()

    override suspend fun getDraft(id: String): NoteDraft? = dao.getDraft(id)?.let { draft ->
        NoteDraft(draft.objectId, draft.body, draft.updatedAt)
    }

    override suspend fun createNote(body: String, source: WorkspaceSource, title: String?): NotebookNote {
        val timestamp = now()
        val id = newId()
        val initialTitle = title ?: titleFor(body)
        dao.insertNoteObject(
            objectEntity = WorkspaceObjectEntity(
                id = id,
                type = "NOTE",
                title = initialTitle,
                createdAt = timestamp,
                updatedAt = timestamp,
                source = source.name,
                lifecycle = WorkspaceLifecycle.ACTIVE.name,
                inInbox = true,
                indexingState = "READY",
                contentRef = "note:$id"
            ),
            noteEntity = NoteEntity(objectId = id, body = body, revision = 1)
        )
        return requireNotNull(getNote(id))
    }

    override suspend fun saveNote(id: String, body: String): Boolean {
        val documentWriter = documents
        if (documentWriter != null && dao.countBlocks(id) > 0) {
            // Legacy body callers are routed through the single document command path.
            return when (documentWriter.replaceFromText(id, body)) {
                is CommitOutcome.Committed -> {
                    dao.deleteLegacyDraft(id)
                    true
                }
                else -> false
            }
        }
        val updated = dao.updateNoteBody(id, body) == 1
        if (updated) dao.deleteLegacyDraft(id)
        return updated
    }

    override suspend fun renameNote(id: String, title: String): Boolean {
        return dao.updateTitle(id, title, now()) == 1
    }

    override suspend fun saveDraft(id: String, body: String) {
        // A structural recovery draft (sequence > 0) owns block ids and base revision; a legacy
        // text-only write must not flatten it.
        if ((dao.getDraft(id)?.sequence ?: 0L) > 0L) return
        dao.upsertDraft(NoteDraftEntity(objectId = id, body = body, updatedAt = now()))
    }

    override suspend fun setLifecycle(id: String, lifecycle: WorkspaceLifecycle): Boolean =
        dao.setLifecycle(id, lifecycle.name, now()) == 1

    override suspend fun removeFromInbox(id: String): Boolean =
        dao.setInbox(id, false, now()) == 1

    override suspend fun permanentlyDelete(id: String): Boolean {
        if (purgeOwner == null) return false
        return (purgeWithReceipt(id, includeRecovery = true) as? NotebookPurgeResult.Saved)?.receipt?.sourcePresent == false
    }
    override suspend fun purgeWithReceipt(id: String, version: String?, recoveryVersion: String?, includeRecovery: Boolean, priorReceiptVersion: String?): NotebookPurgeResult {
        val owner = purgeOwner ?: return NotebookPurgeResult.Refused("PURGE_OWNER_UNAVAILABLE")
        // The local UI has just explicitly confirmed this current note. Assistant calls always
        // supply their observed source/receipt identities instead of acquiring authority here.
        val prior = priorReceiptVersion ?: if (version == null) owner.status(id)?.takeIf { it.sourcePresent }?.version else null
        return owner.purge(id, version, recoveryVersion, includeRecovery, prior)
    }
    override suspend fun purgeStatus(id: String): NotebookPurgeReceipt? = purgeOwner?.status(id)
    override suspend fun retryPurge(id: String, receiptVersion: String): NotebookPurgeResult =
        purgeOwner?.retry(id, receiptVersion) ?: NotebookPurgeResult.Refused("PURGE_OWNER_UNAVAILABLE")

    private fun String.toLikePattern(): String {
        if (isBlank()) return ""
        val escaped = trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }

    private fun titleFor(body: String): String = body.lineSequence()
        .map(String::trim)
        .firstOrNull(String::isNotEmpty)
        ?.removePrefix("#")
        ?.trim()
        ?.take(80)
        .orEmpty()
        .ifEmpty { "Untitled note" }
}
