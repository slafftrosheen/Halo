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
    suspend fun createNote(body: String = "", source: WorkspaceSource = WorkspaceSource.QUICK_NOTE): NotebookNote
    suspend fun saveNote(id: String, body: String): Boolean
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

    override suspend fun createNote(body: String, source: WorkspaceSource): NotebookNote {
        val timestamp = now()
        val id = newId()
        val title = titleFor(body)
        dao.insertNoteObject(
            objectEntity = WorkspaceObjectEntity(
                id = id,
                type = "NOTE",
                title = title,
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

    override suspend fun saveNote(id: String, body: String): Boolean =
        dao.updateNote(id, titleFor(body), body, now())

    override suspend fun setLifecycle(id: String, lifecycle: WorkspaceLifecycle): Boolean =
        dao.setLifecycle(id, lifecycle.name, now()) == 1

    override suspend fun removeFromInbox(id: String): Boolean =
        dao.setInbox(id, false, now()) == 1

    override suspend fun permanentlyDelete(id: String): Boolean =
        dao.permanentlyDeleteTrashed(id) == 1

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
