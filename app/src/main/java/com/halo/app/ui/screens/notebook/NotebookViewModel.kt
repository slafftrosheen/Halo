package com.halo.app.ui.screens.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.data.Memory
import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.data.workspace.WorkspaceQuery
import com.halo.app.notebook.NotebookUseCases
import com.halo.app.notebook.WorkspaceUseCases
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NotebookFilter(val label: String) {
    ALL("All"), NOTES("Notes"), FILES("Files"), CHATS("Chats"), MEMORY("Memory"),
    INBOX("Inbox"), ARCHIVED("Archived"), TRASH("Trash")
}

data class NotebookWorkspaceItem(val objectRef: WorkspaceObject, val snippet: String? = null)

data class NotebookUiState(
    val items: List<NotebookWorkspaceItem> = emptyList(),
    val query: String = "",
    val filter: NotebookFilter = NotebookFilter.ALL,
    val workingSet: Set<String> = emptySet(),
    val workingSetItems: List<WorkspaceObject> = emptyList()
)

sealed interface NotebookNavigation {
    data class OpenNote(val id: String) : NotebookNavigation
    data class OpenConversation(val id: String) : NotebookNavigation
    data class OpenFile(val id: String) : NotebookNavigation
    data class OpenMemory(val id: String) : NotebookNavigation
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NotebookViewModel(
    private val workspace: WorkspaceUseCases,
    private val notes: NotebookUseCases
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(NotebookFilter.ALL)
    private val navigationChannel = Channel<NotebookNavigation>(Channel.BUFFERED)
    val navigationEvents = navigationChannel.receiveAsFlow()
    val memories = workspace.memories
    val history = workspace.history
    val useMemories = workspace.useMemories
    val cloudSharing = workspace.cloudSharing

    private val debouncedQuery = query
        .map { it.trim() }
        .debounce(225L)
        .distinctUntilChanged()

    val uiState: StateFlow<NotebookUiState> = combine(query, debouncedQuery, filter, ::Triple)
        .flatMapLatest { (rawText, text, selectedFilter) ->
            val lifecycle = when (selectedFilter) {
                NotebookFilter.ARCHIVED -> WorkspaceObjectLifecycle.ARCHIVED
                NotebookFilter.TRASH -> WorkspaceObjectLifecycle.TRASHED
                else -> WorkspaceObjectLifecycle.ACTIVE
            }
            val types = when (selectedFilter) {
                NotebookFilter.NOTES -> setOf(WorkspaceObjectType.NOTE)
                NotebookFilter.FILES -> setOf(WorkspaceObjectType.FILE)
                NotebookFilter.CHATS -> setOf(WorkspaceObjectType.CONVERSATION)
                NotebookFilter.MEMORY -> setOf(WorkspaceObjectType.MEMORY)
                else -> emptySet()
            }
            val objects = workspace.observe(WorkspaceQuery(types = types, lifecycle = lifecycle,
                inboxOnly = selectedFilter == NotebookFilter.INBOX))
            val hits = flow { emit(if (text.isBlank()) null else workspace.search(text)) }
            combine(objects, hits, workspace.workingSet, workspace.observe(WorkspaceQuery())) {
                rows, result, selected, allObjects ->
                val matches = result?.hits?.associateBy { it.objectId.value }.orEmpty()
                val visible = if (text.isBlank()) rows else rows.filter { row ->
                    row.id.value in matches || row.title.contains(text, ignoreCase = true)
                }
                NotebookUiState(
                    items = visible.sortedByDescending { it.updatedAt }.map { row ->
                        NotebookWorkspaceItem(row, matches[row.id.value]?.snippet)
                    },
                    query = rawText,
                    filter = selectedFilter,
                    workingSet = selected.toSet(),
                    workingSetItems = allObjects.filter { it.id.value in selected }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebookUiState())

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: NotebookFilter) { filter.value = value }
    fun createNote() = viewModelScope.launch {
        navigationChannel.send(NotebookNavigation.OpenNote(notes.createQuickNote().id))
    }
    fun openObject(item: WorkspaceObject) {
        val id = item.id.value
        navigationChannel.trySend(when (item.type) {
            WorkspaceObjectType.NOTE -> NotebookNavigation.OpenNote(id)
            WorkspaceObjectType.CONVERSATION -> NotebookNavigation.OpenConversation(id)
            WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA, WorkspaceObjectType.ARTIFACT -> NotebookNavigation.OpenFile(id)
            WorkspaceObjectType.MEMORY -> NotebookNavigation.OpenMemory(id)
        })
    }
    fun openNote(id: String) { navigationChannel.trySend(NotebookNavigation.OpenNote(id)) }
    fun openConversation(id: String) = viewModelScope.launch { workspace.openConversation(id) }
    fun selectContext(id: String, selected: Boolean) = viewModelScope.launch {
        if (selected) workspace.select(id) else workspace.remove(id)
    }
    fun clearContext() = viewModelScope.launch { uiState.value.workingSet.forEach { workspace.remove(it) } }
    fun remember(id: String) = viewModelScope.launch { workspace.remember(id) }
    fun forget(id: Int) = viewModelScope.launch { workspace.forget(id) }
    fun correct(memory: Memory, text: String) = viewModelScope.launch { workspace.correct(memory, text) }
    fun setUseMemories(value: Boolean) = workspace.setUseMemories(value)
    fun setCloudSharing(value: Boolean) = workspace.setCloudSharing(value)
    fun archive(id: String) = viewModelScope.launch { workspace.archive(id) }
    fun trash(id: String) = viewModelScope.launch { workspace.trash(id) }
    fun restore(id: String) = viewModelScope.launch { workspace.restore(id) }
    fun removeFromInbox(id: String) = viewModelScope.launch { workspace.removeFromInbox(id) }
    fun deletePermanently(id: String) = viewModelScope.launch { notes.permanentlyDelete(id) }
}
