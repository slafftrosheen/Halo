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
}

class RoomNotebookRepository(
    private val dao: NotebookDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
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
        val updated = dao.updateNoteBody(id, body) == 1
        if (updated) dao.deleteDraft(id)
        return updated
    }

    override suspend fun renameNote(id: String, title: String): Boolean {
        return dao.updateTitle(id, title, now()) == 1
    }

    override suspend fun saveDraft(id: String, body: String) {
        dao.upsertDraft(NoteDraftEntity(objectId = id, body = body, updatedAt = now()))
    }

    override suspend fun setLifecycle(id: String, lifecycle: WorkspaceLifecycle): Boolean =
        dao.setLifecycle(id, lifecycle.name, now()) == 1

    override suspend fun removeFromInbox(id: String): Boolean =
        dao.setInbox(id, false, now()) == 1

    override suspend fun permanentlyDelete(id: String): Boolean =
        dao.purgePermanently(id)

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
