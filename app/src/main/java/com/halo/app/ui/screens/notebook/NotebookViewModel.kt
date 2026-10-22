package com.halo.app.ui.screens.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.data.Memory
import com.halo.app.data.memory.PersonalLearningRepository
import com.halo.app.data.memory.PersonalSuggestionEntity
import com.halo.app.data.memory.PersonalMemoryCodec
import com.halo.runtime.cognition.memory.MemorySensitivity
import com.halo.runtime.cognition.memory.MemorySensitivityClassifier
import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.data.notebook.NotebookCollection
import com.halo.app.data.notebook.NotebookOrganizationRepository
import com.halo.app.data.notebook.NotebookCanvasRepository
import com.halo.app.data.study.StudyPracticeItem
import com.halo.app.data.study.StudySessionRecord
import com.halo.app.data.workspace.WorkspaceQuery
import com.halo.app.notebook.NotebookUseCases
import com.halo.app.notebook.WorkspaceUseCases
import com.halo.app.study.StudyUseCases
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class NotebookSort(val label: String) { UPDATED("Recently updated"), CREATED("Newest first"), TITLE("Title A-Z") }
enum class NotebookBatchAction { CONTEXT, COLLECTION, ARCHIVE, TRASH, RESTORE }

/** Only decodable, currently pending, non-secret MEMORY proposals count.
 * Other personal-learning suggestions belong in the Inbox, not a memory fact.
 */
internal fun notebookReviewableMemoryCount(rows: List<PersonalSuggestionEntity>): Int =
    rows.count { row ->
        row.state == "PENDING" && row.kind == "MEMORY" &&
            runCatching {
                val proposal = PersonalMemoryCodec.decodeProposal(row.json)
                proposal.sensitivity != MemorySensitivity.SECRET &&
                    MemorySensitivityClassifier.classify(proposal.content) != MemorySensitivity.SECRET
            }.getOrDefault(false)
    }

enum class NotebookFilter(val label: String) {
    ALL("All"), NOTES("Notes"), FILES("Files"), CANVASES("Canvases"), CHATS("Chats"), MEMORY("Memory"),
    FAVORITES("Favorites"), INBOX("Inbox"), ARCHIVED("Archived"), TRASH("Trash")
}

data class NotebookWorkspaceItem(val objectRef: WorkspaceObject, val snippet: String? = null, val canvasNodeId: String? = null)

data class NotebookUiState(
    val isLoaded: Boolean = false,
    val sort: NotebookSort = NotebookSort.UPDATED,
    val items: List<NotebookWorkspaceItem> = emptyList(),
    val query: String = "",
    val filter: NotebookFilter = NotebookFilter.ALL,
    val workingSet: Set<String> = emptySet(),
    val workingSetItems: List<WorkspaceObject> = emptyList(),
    val workingSetTokens: Map<String, Int> = emptyMap(),
    val collections: List<NotebookCollection> = emptyList(),
    val selectedCollectionId: String? = null,
    val favorites: Set<String> = emptySet(),
    val selectedCollectionMembers: Set<String> = emptySet(),
    /** Active guided-study sessions, resumable from Notebook. */
    val studySessions: List<StudySessionRecord> = emptyList(),
    /** Accepted practice cards whose review is due now, across every session. */
    val duePractice: List<StudyPracticeItem> = emptyList(),
)

sealed interface NotebookNavigation {
    data class OpenMemoryHub(val section: String = "Memories", val noteId: String? = null) : NotebookNavigation
    data class OpenNote(val id: String) : NotebookNavigation
    data class OpenConversation(val id: String) : NotebookNavigation
    data class OpenFile(val id: String) : NotebookNavigation
    data class OpenMemory(val id: String) : NotebookNavigation
    data class OpenCanvas(val id: String, val nodeId: String? = null) : NotebookNavigation
    data class OpenStudy(val sessionId: String, val review: Boolean = false) : NotebookNavigation
    data class StartStudyFromSource(val objectId: String) : NotebookNavigation
    data class ReviewAutomation(val routineId: String,val suggestionId: String) : NotebookNavigation
    data object ReviewCapabilities : NotebookNavigation
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NotebookViewModel(
    private val workspace: WorkspaceUseCases,
    private val notes: NotebookUseCases,
    private val organization: NotebookOrganizationRepository,
    private val canvases: NotebookCanvasRepository,
    private val study: StudyUseCases? = null,
    private val personalLearning: PersonalLearningRepository? = null,
    private val conversationStore: com.halo.app.data.RoomConversationStore? = null,
) : ViewModel() {
    private val studySessionsFlow: kotlinx.coroutines.flow.Flow<List<StudySessionRecord>> =
        study?.observeActiveSessions() ?: flowOf(emptyList())
    private val duePracticeFlow: kotlinx.coroutines.flow.Flow<List<StudyPracticeItem>> =
        study?.observeDuePracticeItems(limit = 50) ?: flowOf(emptyList())
    private val query = MutableStateFlow("")
    val queryText: StateFlow<String> = query.asStateFlow()
    private val sort = MutableStateFlow(NotebookSort.UPDATED)
    private val selection = MutableStateFlow<Set<String>>(emptySet())
    /** One read-only projection of the existing durable Suggestions queue.
     * No automatic memory approval, alternate store, or loading side effect.
     */
    val pendingMemoryReviewCount: StateFlow<Int> =
        (personalLearning?.pending ?: flowOf(emptyList<PersonalSuggestionEntity>()))
            .map(::notebookReviewableMemoryCount)
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val selectedIds: StateFlow<Set<String>> = selection
    private val mutableNotice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = mutableNotice
    // Only the last successful Trash batch is undoable here. The existing
    // Workspace lifecycle repository remains the single source of truth.
    private val mutableTrashUndoIds = MutableStateFlow<Set<String>>(emptySet())
    val trashUndoIds: StateFlow<Set<String>> = mutableTrashUndoIds
    private val mutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = mutating
    private val filter = MutableStateFlow(NotebookFilter.ALL)
    private val selectedCollection = MutableStateFlow<String?>(null)
    private val navigationChannel = Channel<NotebookNavigation>(Channel.BUFFERED)
    private val creatingNote = AtomicBoolean(false)
    private val creatingCanvas = AtomicBoolean(false)
    private val startingStudy = AtomicBoolean(false)
    val navigationEvents = navigationChannel.receiveAsFlow()
    val memories = workspace.memories
    val history = workspace.history
    val useMemories = workspace.useMemories
    fun refreshContextPolicy() = workspace.refreshContextPolicy()
    val cloudSharing = workspace.cloudSharing
    fun remoteAllowed(item: WorkspaceObject): Boolean = workspace.remoteAllowed(item.type.name)

    private val debouncedQuery = query
        .map { it.trim() }
        .debounce(225L)
        .distinctUntilChanged()

    private data class QueryState(
        val rawText: String,
        val text: String,
        val filter: NotebookFilter,
        val collectionId: String?,
    )

    private data class OrganizationState(
        val collections: List<NotebookCollection>,
        val favorites: Set<String>,
        val members: Set<String>,
    )

    private data class AuxState(
        val organization: OrganizationState,
        val sessions: List<StudySessionRecord>,
        val duePractice: List<StudyPracticeItem>,
    )

    val uiState: StateFlow<NotebookUiState> = combine(query, debouncedQuery, filter, selectedCollection) {
            rawText, text, selectedFilter, collectionId ->
        QueryState(rawText, text, selectedFilter, collectionId)
    }
        .flatMapLatest { queryState ->
            val rawText = queryState.rawText
            val text = queryState.text
            val selectedFilter = queryState.filter
            val lifecycle = when (selectedFilter) {
                NotebookFilter.ARCHIVED -> WorkspaceObjectLifecycle.ARCHIVED
                NotebookFilter.TRASH -> WorkspaceObjectLifecycle.TRASHED
                else -> WorkspaceObjectLifecycle.ACTIVE
            }
            val types = when (selectedFilter) {
                NotebookFilter.NOTES -> setOf(WorkspaceObjectType.NOTE)
                NotebookFilter.FILES -> setOf(
                    WorkspaceObjectType.FILE,
                    WorkspaceObjectType.MEDIA,
                    WorkspaceObjectType.ARTIFACT,
                )
                NotebookFilter.CANVASES -> setOf(WorkspaceObjectType.CANVAS, WorkspaceObjectType.ARTIFACT)
                NotebookFilter.CHATS -> setOf(WorkspaceObjectType.CONVERSATION)
                NotebookFilter.MEMORY -> setOf(WorkspaceObjectType.MEMORY)
                else -> emptySet()
            }
            val objects = workspace.observe(WorkspaceQuery(types = types, lifecycle = lifecycle,
                inboxOnly = selectedFilter == NotebookFilter.INBOX))
            val sessions = studySessionsFlow
            val due = duePracticeFlow
            val hits = workspace.observe(WorkspaceQuery()).mapLatest { if (text.isBlank()) null else workspace.search(text) }
            val members = queryState.collectionId?.let(organization::observeMembers) ?: flowOf(emptySet<String>())
            val organizationState = combine(
                organization.observeCollections(), organization.observeFavorites(), members,
            ) { collections, favorites, collectionMembers ->
                OrganizationState(collections, favorites, collectionMembers)
            }
            val auxState = combine(organizationState, sessions, due) { organizationState, sessionRows, dueRows ->
                AuxState(organizationState, sessionRows, dueRows)
            }
            combine(
                objects,
                hits,
                workspace.workingSet,
                workspace.observe(WorkspaceQuery()),
                auxState,
            ) { rows, result, selected, allObjects, aux ->
                val organized = aux.organization
                val sessionRows = aux.sessions
                val matches = result?.hits?.groupBy { it.objectId.value }?.mapValues { (_, hits) ->
                    hits.firstOrNull { it.nodeId != null } ?: hits.first()
                }.orEmpty()
                var visible = if (text.isBlank()) rows else rows.filter { row ->
                    row.id.value in matches || row.title.contains(text, ignoreCase = true)
                }
                if (selectedFilter == NotebookFilter.FAVORITES) {
                    visible = visible.filter { it.id.value in organized.favorites }
                }
                if (selectedFilter == NotebookFilter.FILES) {
                    visible = visible.filterNot {
                        it.type == WorkspaceObjectType.ARTIFACT && it.contentRef.startsWith("canvas:")
                    }
                }
                if (selectedFilter == NotebookFilter.CANVASES) {
                    visible = visible.filter {
                        it.type == WorkspaceObjectType.CANVAS || it.contentRef.startsWith("canvas:")
                    }
                }
                if (queryState.collectionId != null) {
                    visible = visible.filter { it.id.value in organized.members }
                }
                NotebookUiState(
                    isLoaded = true,
                    items = visible.sortedByDescending { it.updatedAt }.map { row ->
                        NotebookWorkspaceItem(row, matches[row.id.value]?.snippet, matches[row.id.value]?.nodeId)
                    },
                    query = rawText,
                    filter = selectedFilter,
                    workingSet = selected.toSet(),
                    workingSetItems = allObjects.filter { it.id.value in selected },
                    workingSetTokens = allObjects.filter { it.id.value in selected }.associate { item ->
                        item.id.value to com.halo.runtime.cognition.context.ConservativeContextTokenEstimator().estimate(
                            workspace.contextText(item.id.value).orEmpty())
                    },
                    collections = organized.collections,
                    selectedCollectionId = queryState.collectionId,
                    favorites = organized.favorites,
                    selectedCollectionMembers = organized.members,
                    studySessions = sessionRows,
                    duePractice = aux.duePractice,
                )
            }
        }
        .combine(sort) { state, order ->
            val sorted = when (order) {
                NotebookSort.UPDATED -> state.items.sortedByDescending { it.objectRef.updatedAt }
                NotebookSort.CREATED -> state.items.sortedByDescending { it.objectRef.createdAt }
                NotebookSort.TITLE -> state.items.sortedWith(compareBy<NotebookWorkspaceItem> { it.objectRef.title.lowercase() }.thenBy { it.objectRef.id.value })
            }
            state.copy(items = sorted, sort = order)
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebookUiState())

    fun setQuery(value: String) { query.value = value; selection.value = emptySet() }
    fun setFilter(value: NotebookFilter) { if (value == NotebookFilter.MEMORY) { openMemoryHub(); return }; filter.value = value; selection.value = emptySet() }
    fun selectCollection(id: String?) { selectedCollection.value = id; selection.value = emptySet() }
    fun setSort(value: NotebookSort) { sort.value = value }
    fun clearSelection() { selection.value = emptySet() }
    fun clearNotice() { mutableNotice.value = null }
    fun dismissTrashUndo() { if (!mutating.value) mutableTrashUndoIds.value = emptySet() }
    fun toggleSelection(id: String) {
        if (mutating.value) return
        if (id in selection.value) selection.value = selection.value - id
        else if (selection.value.size < 100) selection.value = selection.value + id
        else mutableNotice.value = "Select up to 100 items at a time."
    }
    fun selectVisible() {
        selection.value = uiState.value.items.map { it.objectRef.id.value }.filterNot { it.startsWith("learning:") }.take(100).toSet()
    }
    fun applyToSelection(action: NotebookBatchAction, collectionId: String? = null) = viewModelScope.launch {
        if (mutating.value) return@launch
        val ids = selection.value.toList()
        if (ids.isEmpty() || (action == NotebookBatchAction.COLLECTION && collectionId == null)) return@launch
        mutating.value = true
        // An intervening batch invalidates the previous one-click Undo target.
        mutableTrashUndoIds.value = emptySet()
        var completed = 0
        val failed = mutableSetOf<String>()
        val movedToTrash = mutableSetOf<String>()
        try {
            for (id in ids) {
                try {
                    val item = workspace.getObject(id) ?: error("Item unavailable")
                    when (action) {
                        NotebookBatchAction.CONTEXT -> {
                            require(item.lifecycle == WorkspaceObjectLifecycle.ACTIVE)
                            workspace.select(id)
                        }
                        NotebookBatchAction.COLLECTION -> check(organization.setMembership(collectionId!!, id, true))
                        NotebookBatchAction.ARCHIVE -> workspace.archive(id)
                        NotebookBatchAction.TRASH -> {
                            check(item.lifecycle != WorkspaceObjectLifecycle.TRASHED) {
                                "Already in Trash"
                            }
                            if (item.type == WorkspaceObjectType.CONVERSATION && conversationStore != null) {
                                check(conversationStore.trashSavedConversation(id))
                            } else workspace.trash(id)
                            movedToTrash += id
                        }
                        NotebookBatchAction.RESTORE -> {
                            if (item.type == WorkspaceObjectType.CONVERSATION &&
                                conversationStore != null && item.lifecycle == WorkspaceObjectLifecycle.TRASHED)
                                check(conversationStore.restoreSavedConversation(id))
                            else workspace.restore(id)
                        }
                    }
                    completed++
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { failed += id }
            }
            selection.value = failed
            if (action == NotebookBatchAction.TRASH) mutableTrashUndoIds.value = movedToTrash
            mutableNotice.value = if (failed.isEmpty()) { if (action == NotebookBatchAction.CONTEXT) "$completed items added to context; Halo uses up to 20 recent items within its context budget." else "$completed items updated" } else "$completed items updated; ${failed.size} could not be changed and remain selected."
        } finally { mutating.value = false }
    }
    /** Undo only items actually moved in the last completed Trash batch.
     * Already-removed or otherwise changed objects are never silently recreated.
     * Failed restores remain individually retryable until another batch begins.
     */
    fun undoLastTrash() = viewModelScope.launch {
        if (mutating.value) return@launch
        val ids = mutableTrashUndoIds.value
        if (ids.isEmpty()) return@launch
        mutating.value = true
        val failed = mutableSetOf<String>()
        var restored = 0
        try {
            for (id in ids) {
                try {
                    val item = workspace.getObject(id)
                    check(item?.lifecycle == WorkspaceObjectLifecycle.TRASHED) {
                        "Item can no longer be restored from Trash"
                    }
                    if (item.type == WorkspaceObjectType.CONVERSATION && conversationStore != null)
                        check(conversationStore.restoreSavedConversation(id))
                    else workspace.restore(id)
                    restored++
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { failed += id }
            }
            mutableTrashUndoIds.value = failed
            mutableNotice.value = if (failed.isEmpty()) {
                "$restored items restored"
            } else {
                "$restored items restored; ${failed.size} could not be restored."
            }
        } finally {
            mutating.value = false
        }
    }

    fun startStudy(objectId: String) {
        // NB01/S02: opening Study is not permission to start inference.
        // The source is selected in the four-step lesson wizard; local/cloud
        // execution stays unselected until the learner chooses it.
        navigationChannel.trySend(NotebookNavigation.StartStudyFromSource(objectId))
    }

    fun createCollection(name: String) = viewModelScope.launch {
        organization.createCollection(name)?.let { selectedCollection.value = it.id }
    }
    fun deleteSelectedCollection() {
        val selectedId = selectedCollection.value ?: return
        viewModelScope.launch {
            if (organization.deleteCollection(selectedId) && selectedCollection.value == selectedId) {
                selectedCollection.value = null
            }
        }
    }
    fun addToCollection(objectId: String, collectionId: String) = viewModelScope.launch {
        organization.setMembership(collectionId, objectId, true)
    }
    fun removeFromSelectedCollection(objectId: String) {
        val collectionId = selectedCollection.value ?: return
        viewModelScope.launch {
            organization.setMembership(collectionId, objectId, false)
        }
    }
    fun toggleFavorite(objectId: String) = viewModelScope.launch {
        organization.toggleFavorite(objectId)
    }
    fun createNote() {
        if (!creatingNote.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                navigationChannel.send(NotebookNavigation.OpenNote(notes.createQuickNote().id))
            } finally {
                creatingNote.set(false)
            }
        }
    }
    fun openStudy(sessionId: String, review: Boolean = false) {
        navigationChannel.trySend(NotebookNavigation.OpenStudy(sessionId, review))
    }
    fun createCanvas() {
        if (!creatingCanvas.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                navigationChannel.send(NotebookNavigation.OpenCanvas(canvases.create()))
            } finally {
                creatingCanvas.set(false)
            }
        }
    }
    fun openObject(item: WorkspaceObject) {
        val id = item.id.value
        navigationChannel.trySend(when (item.type) {
            WorkspaceObjectType.NOTE -> NotebookNavigation.OpenNote(id)
            WorkspaceObjectType.CONVERSATION -> NotebookNavigation.OpenConversation(id)
            WorkspaceObjectType.ARTIFACT -> if (item.contentRef.startsWith("canvas:")) {
                NotebookNavigation.OpenCanvas(id)
            } else {
                NotebookNavigation.OpenFile(id)
            }
            WorkspaceObjectType.CANVAS -> NotebookNavigation.OpenCanvas(id)
            WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA -> NotebookNavigation.OpenFile(id)
            WorkspaceObjectType.MEMORY -> NotebookNavigation.OpenMemory(id)
        })
    }
    fun openSearchItem(item: NotebookWorkspaceItem) {
        if (item.canvasNodeId != null) navigationChannel.trySend(NotebookNavigation.OpenCanvas(item.objectRef.id.value, item.canvasNodeId))
        else openObject(item.objectRef)
    }
    fun openCanvasNode(canvasId: String, nodeId: String) {
        if (canvasId.isNotBlank() && nodeId.isNotBlank())
            navigationChannel.trySend(NotebookNavigation.OpenCanvas(canvasId, nodeId))
    }
    fun openMemoryHub(section: String = "Memories", noteId: String? = null) { navigationChannel.trySend(NotebookNavigation.OpenMemoryHub(section, noteId)) }
    fun openMemory(id: String) { navigationChannel.trySend(NotebookNavigation.OpenMemory(id)) }
    fun reviewAutomation(id: String,suggestionId: String) { navigationChannel.trySend(NotebookNavigation.ReviewAutomation(id,suggestionId)) }
    fun reviewCapabilities() { navigationChannel.trySend(NotebookNavigation.ReviewCapabilities) }
    fun openReferencedObject(id: String) = viewModelScope.launch { workspace.getObject(id)?.let(::openObject) }
    fun openNote(id: String) { navigationChannel.trySend(NotebookNavigation.OpenNote(id)) }
    fun openConversation(id: String) = viewModelScope.launch { workspace.openConversation(id) }
    fun selectContext(id: String, selected: Boolean) = viewModelScope.launch {
        if (selected) workspace.select(id) else workspace.remove(id)
    }
    fun clearContext() = viewModelScope.launch { uiState.value.workingSet.forEach { workspace.remove(it) } }
    fun remember(id: String) { openMemoryHub(noteId = id) }
    fun forget(id: Int) = viewModelScope.launch { workspace.forget(id) }
    fun correct(memory: Memory, text: String) = viewModelScope.launch { workspace.correct(memory, text) }
    fun setUseMemories(value: Boolean) = workspace.setUseMemories(value)
    fun setCloudSharing(value: Boolean) = workspace.setCloudSharing(value)
    fun archive(id: String) = viewModelScope.launch { workspace.archive(id) }
    fun trash(id: String) = viewModelScope.launch {
        val item = workspace.getObject(id) ?: return@launch
        if (item.type == WorkspaceObjectType.CONVERSATION && conversationStore != null)
            conversationStore.trashSavedConversation(id)
        else workspace.trash(id)
    }
    fun restore(id: String) = viewModelScope.launch {
        val item = workspace.getObject(id) ?: return@launch
        if (item.type == WorkspaceObjectType.CONVERSATION &&
            item.lifecycle == WorkspaceObjectLifecycle.TRASHED && conversationStore != null)
            conversationStore.restoreSavedConversation(id)
        else workspace.restore(id)
    }
    fun removeFromInbox(id: String) = viewModelScope.launch { workspace.removeFromInbox(id) }
    fun deletePermanently(id: String) = viewModelScope.launch { notes.permanentlyDelete(id) }
}
