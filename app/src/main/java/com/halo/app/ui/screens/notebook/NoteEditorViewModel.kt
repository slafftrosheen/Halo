package com.halo.app.ui.screens.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.halo.app.data.notebook.WorkspaceLifecycle
import com.halo.app.notebook.NotebookUseCases
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SaveStatus { LOADING, SAVED, SAVING, ERROR, MISSING }

data class NoteEditorUiState(
    val body: String = "",
    val lifecycle: WorkspaceLifecycle = WorkspaceLifecycle.ACTIVE,
    val status: SaveStatus = SaveStatus.LOADING
)

class NoteEditorViewModel(
    private val noteId: String,
    private val useCases: NotebookUseCases,
    private val autosaveDelayMs: Long = 400
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = mutableUiState.asStateFlow()
    private var saveJob: Job? = null
    private var draftJob: Job? = null

    init {
        viewModelScope.launch {
            val note = useCases.getNote(noteId)
            mutableUiState.value = if (note == null) {
                NoteEditorUiState(status = SaveStatus.MISSING)
            } else {
                val draft = useCases.getDraft(noteId)
                val recoveredBody = draft
                    ?.takeIf { it.updatedAt >= note.updatedAt && it.body != note.body }
                    ?.body
                if (recoveredBody == null) {
                    NoteEditorUiState(note.body, note.lifecycle, SaveStatus.SAVED)
                } else {
                    scheduleCommit(recoveredBody)
                    NoteEditorUiState(recoveredBody, note.lifecycle, SaveStatus.SAVING)
                }
            }
        }
    }

    fun updateBody(body: String) {
        val current = mutableUiState.value
        if (current.status == SaveStatus.MISSING || body == current.body) return
        mutableUiState.value = current.copy(body = body, status = SaveStatus.SAVING)
        draftJob?.cancel()
        draftJob = viewModelScope.launch { useCases.saveDraft(noteId, body) }
        scheduleCommit(body)
    }

    private fun scheduleCommit(body: String) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(autosaveDelayMs)
            persist(body)
        }
    }

    fun flush() {
        val current = mutableUiState.value
        if (current.status != SaveStatus.SAVING) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch { persist(current.body) }
    }

    fun archive() = updateLifecycle(WorkspaceLifecycle.ARCHIVED)

    fun moveToTrash() = updateLifecycle(WorkspaceLifecycle.TRASHED)

    fun restore() = updateLifecycle(WorkspaceLifecycle.ACTIVE)

    private suspend fun persist(body: String) {
        draftJob?.join()
        val saved = useCases.saveNote(noteId, body)
        val current = mutableUiState.value
        if (current.body == body) {
            mutableUiState.value = current.copy(status = if (saved) SaveStatus.SAVED else SaveStatus.ERROR)
        }
    }

    private fun updateLifecycle(lifecycle: WorkspaceLifecycle) {
        val beforeChange = mutableUiState.value
        saveJob?.cancel()
        viewModelScope.launch {
            draftJob?.join()
            if (beforeChange.status == SaveStatus.SAVING && !useCases.saveNote(noteId, beforeChange.body)) {
                mutableUiState.value = mutableUiState.value.copy(status = SaveStatus.ERROR)
                return@launch
            }
            val changed = when (lifecycle) {
                WorkspaceLifecycle.ACTIVE -> useCases.restore(noteId)
                WorkspaceLifecycle.ARCHIVED -> useCases.archive(noteId)
                WorkspaceLifecycle.TRASHED -> useCases.moveToTrash(noteId)
            }
            if (changed) {
                mutableUiState.value = mutableUiState.value.copy(
                    lifecycle = lifecycle,
                    status = SaveStatus.SAVED
                )
            }
        }
    }

    class Factory(
        private val noteId: String,
        private val useCases: NotebookUseCases
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NoteEditorViewModel(noteId, useCases) as T
    }
}
