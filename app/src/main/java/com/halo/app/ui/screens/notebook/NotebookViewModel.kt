package com.halo.app.ui.screens.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.data.notebook.NotebookNote
import com.halo.app.data.notebook.WorkspaceLifecycle
import com.halo.app.notebook.NotebookQuery
import com.halo.app.notebook.NotebookUseCases
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NotebookFilter(val label: String) {
    ALL("All"),
    INBOX("Inbox"),
    ARCHIVED("Archived"),
    TRASH("Trash")
}

data class NotebookUiState(
    val notes: List<NotebookNote> = emptyList(),
    val query: String = "",
    val filter: NotebookFilter = NotebookFilter.ALL
)

sealed interface NotebookNavigation {
    data class OpenNote(val id: String) : NotebookNavigation
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NotebookViewModel(private val useCases: NotebookUseCases) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(NotebookFilter.ALL)
    private val navigationChannel = Channel<NotebookNavigation>(Channel.BUFFERED)

    val navigationEvents = navigationChannel.receiveAsFlow()

    val uiState: StateFlow<NotebookUiState> = combine(query, filter, ::Pair)
        .flatMapLatest { (queryText, selectedFilter) ->
            val notebookQuery = when (selectedFilter) {
                NotebookFilter.ALL -> NotebookQuery(text = queryText)
                NotebookFilter.INBOX -> NotebookQuery(text = queryText, inboxOnly = true)
                NotebookFilter.ARCHIVED -> NotebookQuery(
                    text = queryText,
                    lifecycle = WorkspaceLifecycle.ARCHIVED
                )
                NotebookFilter.TRASH -> NotebookQuery(
                    text = queryText,
                    lifecycle = WorkspaceLifecycle.TRASHED
                )
            }
            useCases.observeNotes(notebookQuery).map { notes ->
                NotebookUiState(notes, queryText, selectedFilter)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebookUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilter(value: NotebookFilter) {
        filter.value = value
    }

    fun createNote() {
        viewModelScope.launch {
            val note = useCases.createQuickNote()
            navigationChannel.send(NotebookNavigation.OpenNote(note.id))
        }
    }

    fun openNote(id: String) {
        navigationChannel.trySend(NotebookNavigation.OpenNote(id))
    }

    fun archive(id: String) {
        viewModelScope.launch { useCases.archive(id) }
    }

    fun trash(id: String) {
        viewModelScope.launch { useCases.moveToTrash(id) }
    }

    fun restore(id: String) {
        viewModelScope.launch { useCases.restore(id) }
    }

    fun removeFromInbox(id: String) {
        viewModelScope.launch { useCases.removeFromInbox(id) }
    }

    fun deletePermanently(id: String) {
        viewModelScope.launch { useCases.permanentlyDelete(id) }
    }
}
