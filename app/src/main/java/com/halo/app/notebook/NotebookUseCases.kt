package com.halo.app.notebook

import com.halo.app.data.notebook.NotebookNote
import com.halo.app.data.notebook.NotebookRepository
import com.halo.app.data.notebook.NoteDraft
import com.halo.app.data.notebook.WorkspaceLifecycle
import kotlinx.coroutines.flow.Flow

data class NotebookQuery(
    val text: String = "",
    val lifecycle: WorkspaceLifecycle = WorkspaceLifecycle.ACTIVE,
    val inboxOnly: Boolean = false
)

class NotebookUseCases(private val repository: NotebookRepository) {
    fun observeNotes(query: NotebookQuery): Flow<List<NotebookNote>> =
        repository.observeNotes(query.text, query.lifecycle, query.inboxOnly)

    suspend fun getNote(id: String): NotebookNote? = repository.getNote(id)

    suspend fun getDraft(id: String): NoteDraft? = repository.getDraft(id)

    suspend fun createQuickNote(): NotebookNote = repository.createNote()

    suspend fun createNote(body: String = "", title: String? = null): NotebookNote =
        repository.createNote(body = body, title = title)

    suspend fun saveNote(id: String, body: String): Boolean = repository.saveNote(id, body)

    suspend fun saveDraft(id: String, body: String) = repository.saveDraft(id, body)

    suspend fun archive(id: String): Boolean =
        repository.setLifecycle(id, WorkspaceLifecycle.ARCHIVED)

    suspend fun moveToTrash(id: String): Boolean =
        repository.setLifecycle(id, WorkspaceLifecycle.TRASHED)

    suspend fun restore(id: String): Boolean =
        repository.setLifecycle(id, WorkspaceLifecycle.ACTIVE)

    suspend fun removeFromInbox(id: String): Boolean = repository.removeFromInbox(id)

    suspend fun permanentlyDelete(id: String): Boolean = repository.permanentlyDelete(id)
}
