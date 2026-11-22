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
import com.halo.app.data.notebook.LibraryCollectionAddition
import com.halo.app.data.notebook.LibraryCollectionSuggestion
import com.halo.app.data.notebook.NotebookOrganizationRepository
import com.halo.app.data.notebook.NotebookCanvasRepository
import com.halo.app.data.study.StudyPracticeItem
import com.halo.app.data.study.LibraryPracticeCard
import com.halo.app.data.study.StudySessionRecord
import com.halo.app.data.workspace.WorkspaceQuery
import com.halo.app.data.workspace.WorkspaceLibraryNames
import com.halo.app.data.workspace.WorkspaceLibraryTitleAlias
import com.halo.app.data.workspace.ContentOwnership
import com.halo.app.data.workspace.WorkspaceFileAvailability
import com.halo.app.notebook.NotebookUseCases
import com.halo.app.notebook.WorkspaceUseCases
import com.halo.app.study.StudyUseCases
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
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

enum class NotebookSort(val label: String) {
    UPDATED("Recently updated"), CREATED("Newest first"), TITLE("Title A-Z"), TOOL("By tool")
}
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
    ALL("All"), ORGANIZER("Plans"), BRIEFS("Daily briefs"), SOURCES("Saved sources"), CLOCKS("World clocks"), NOTES("Notes"), FILES("Files"), CANVASES("Canvases"), CHATS("Chats"), MEMORY("Memory"),
    PHOTOS("Photos"), GLASSES_PHOTOS("From glasses"), SCANS("Scans"), CARDS("Cards"), PLACES("Places"), RECORDINGS("Recordings"), PEOPLE("People"),
    FAVORITES("Favorites"), INBOX("Inbox"), ARCHIVED("Archived"), TRASH("Trash")
}

data class NotebookWorkspaceItem(val objectRef: WorkspaceObject, val snippet: String? = null, val canvasNodeId: String? = null,
    val libraryKind: HaloLibraryKind? = null, val fromGlasses: Boolean = false, val previousTitle: String? = null,
    val nameFacts: LibraryNameFacts? = null, val renamableFileVersion: String? = null)

data class NotebookUiState(
    val isLoaded: Boolean = false,
    val sort: NotebookSort = NotebookSort.UPDATED,
    val items: List<NotebookWorkspaceItem> = emptyList(),
    val query: String = "",
    val resolvedQuery: String = "",
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
    val loadError: Boolean = false,
    val everyday: List<com.halo.app.everyday.presentation.EverydayLibraryEntry> = emptyList(),
    val everydayError: Boolean = false,
    val libraryCards: List<LibraryPracticeCard> = emptyList(),
    val libraryPeople: List<LibraryPersonReference> = emptyList(),
    val librarySourcesError: Boolean = false,
    val collectionLinks: Map<String, Set<String>> = emptyMap(),
    val collectionSuggestion: LibraryCollectionSuggestion? = null,
    val suggestionUndo: LibraryCollectionAddition? = null,
)

sealed interface NotebookNavigation {
    data class OpenMemoryHub(val section: String = "Memories", val noteId: String? = null) : NotebookNavigation
    data class OpenNote(val id: String) : NotebookNavigation
    data class OpenConversation(val id: String) : NotebookNavigation
    data class OpenFile(val id: String) : NotebookNavigation
    data class OpenMemory(val id: String) : NotebookNavigation
    data class OpenStudio(val id: String) : NotebookNavigation
    data class OpenFileTools(val id: String) : NotebookNavigation
    data class OpenCanvas(val id: String, val nodeId: String? = null) : NotebookNavigation
    data class OpenStudy(val sessionId: String, val review: Boolean = false, val cardId: String? = null) : NotebookNavigation
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
    private val batchOwner: NotebookLibraryBatchOwner? = null,
    private val everyday: ()->kotlinx.coroutines.flow.Flow<List<com.halo.app.everyday.presentation.EverydayLibraryEntry>> = {flowOf(emptyList())},
    private val openEveryday: suspend (com.halo.app.everyday.presentation.EverydayLibraryEntry)->Boolean = {false},
    private val librarySources: () -> kotlinx.coroutines.flow.Flow<HaloLibrarySources> = { flowOf(HaloLibrarySources()) },
    private val libraryNames: WorkspaceLibraryNames? = null,
    private val aliasFlow: () -> kotlinx.coroutines.flow.Flow<Map<String, WorkspaceLibraryTitleAlias>> = { flowOf(emptyMap()) },
    private val libraryAuthority: () -> (() -> Boolean)? = { { true } },
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
    private val mutableBatchNotice = MutableStateFlow<NotebookBatchReceipt?>(null)
    val batchNotice: StateFlow<NotebookBatchReceipt?> = mutableBatchNotice.asStateFlow()
    // Only the last successful Trash batch is undoable here. The existing
    // Workspace lifecycle repository remains the single source of truth.
    private val mutableTrashUndoIds = MutableStateFlow<Set<String>>(emptySet())
    val trashUndoIds: StateFlow<Set<String>> = mutableTrashUndoIds
    private val mutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = mutating
    private val filter = MutableStateFlow(NotebookFilter.ALL)
    private val selectedCollection = MutableStateFlow<String?>(null)
    private val refreshRevision = MutableStateFlow(0L)
    private val dismissedLibrarySuggestions = MutableStateFlow(emptySet<LibraryCollectionSuggestion>())
    private val suggestionUndo = MutableStateFlow<LibraryCollectionAddition?>(null)
    private var suggestionUndoUntil = 0L
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
        val links: Map<String, Set<String>>,
        val aliases: Map<String, WorkspaceLibraryTitleAlias>,
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
        .combine(refreshRevision) { requested, _ -> requested }
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
                NotebookFilter.FILES -> setOf(
                    WorkspaceObjectType.FILE,
                    WorkspaceObjectType.MEDIA,
                    WorkspaceObjectType.CANVAS,
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
            val searchPrompt = parseLibrarySearchPrompt(text)
            val lookup = searchPrompt.terms
            val hits = workspace.observe(WorkspaceQuery()).mapLatest { if (lookup.isBlank()) null else workspace.search(lookup) }
            val organizationState = combine(
                organization.observeCollections(), organization.observeFavorites(), organization.observeCollectionLinks(),
                aliasFlow().catch { emit(emptyMap()) },
            ) { collections, favorites, links, aliases ->
                OrganizationState(collections, favorites, links, aliases)
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
                val collectionMembers = organized.links[queryState.collectionId].orEmpty()
                val sessionRows = aux.sessions
                val matches = result?.hits?.groupBy { it.objectId.value }?.mapValues { (_, hits) ->
                    hits.firstOrNull { it.nodeId != null } ?: hits.first()
                }.orEmpty()
                var visible = if (lookup.isBlank()) rows else rows.filter { row ->
                    searchPrompt.includes(row.createdAt) && (row.id.value in matches || row.title.contains(lookup, ignoreCase = true) ||
                        organized.aliases[row.id.value]?.let { it.newTitle == row.title && matchesLibraryTerms(lookup, it.oldTitle) } == true)
                }
                if(selectedFilter in setOf(NotebookFilter.ORGANIZER,NotebookFilter.BRIEFS,NotebookFilter.SOURCES,NotebookFilter.CLOCKS)) visible=emptyList()
                if (selectedFilter == NotebookFilter.FAVORITES) {
                    visible = visible.filter { it.id.value in organized.favorites }
                }
                if (selectedFilter == NotebookFilter.CANVASES) {
                    visible = visible.filter {
                        it.type == WorkspaceObjectType.CANVAS || it.contentRef.startsWith("canvas:")
                    }
                }
                if (queryState.collectionId != null) {
                    visible = visible.filter { it.id.value in collectionMembers }
                }
                NotebookUiState(
                    isLoaded = true,
                    items = visible.sortedByDescending { it.updatedAt }.map { row ->
                        NotebookWorkspaceItem(row, matches[row.id.value]?.snippet.takeUnless { row.contentRef == "file-tools:${row.id.value}" },
                            matches[row.id.value]?.nodeId, previousTitle = organized.aliases[row.id.value]?.takeIf { it.newTitle == row.title }?.oldTitle)
                    },
                    query = rawText,
                    resolvedQuery = text,
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
                    selectedCollectionMembers = collectionMembers,
                    collectionLinks = organized.links,
                    studySessions = sessionRows,
                    duePractice = aux.duePractice,
                )
            }.catch { emit(libraryFailure(queryState)) }
        }
        .combine(sort) { state, order ->
            val sorted = when (order) {
                NotebookSort.UPDATED -> state.items.sortedByDescending { it.objectRef.updatedAt }
                NotebookSort.CREATED -> state.items.sortedByDescending { it.objectRef.createdAt }
                NotebookSort.TITLE -> state.items.sortedWith(compareBy<NotebookWorkspaceItem> { it.objectRef.title.lowercase() }.thenBy { it.objectRef.id.value })
                NotebookSort.TOOL -> state.items.sortedWith(compareBy<NotebookWorkspaceItem> { it.objectRef.type.name }
                    .thenByDescending { it.objectRef.updatedAt }.thenBy { it.objectRef.id.value })
            }
            state.copy(items = sorted, sort = order)
        }
        .combine(refreshRevision.flatMapLatest {everyday().map {it to false}.catch {emit(emptyList<com.halo.app.everyday.presentation.EverydayLibraryEntry>() to true)}}) {state,domain ->
            val wanted=when(state.filter) {NotebookFilter.ALL -> null;NotebookFilter.ORGANIZER -> "ORGANIZER";NotebookFilter.BRIEFS -> "BRIEF";NotebookFilter.SOURCES -> "SOURCE";NotebookFilter.CLOCKS -> "CLOCK";else -> "NONE"}
            val rows=if(wanted=="NONE") emptyList() else domain.first.filter {entry ->
                (wanted==null || entry.kind==wanted) &&
                    (state.selectedCollectionId == null || entry.linkedObjects.any(state.selectedCollectionMembers::contains)) &&
                    matchesLibraryTerms(state.resolvedQuery, "${entry.title} ${entry.detail} ${entry.code}")
            }
            state.copy(everyday = when (state.sort) {
                NotebookSort.TITLE -> rows.sortedBy { it.title.lowercase() }
                NotebookSort.TOOL -> rows.sortedWith(compareBy<com.halo.app.everyday.presentation.EverydayLibraryEntry> { it.kind }
                    .thenByDescending { it.updatedAt })
                else -> rows.sortedByDescending { it.updatedAt }
            }, everydayError = domain.second)
        }
        .combine(refreshRevision.flatMapLatest {
            librarySources().map { it to false }.catch { emit(HaloLibrarySources() to true) }
        }) { state, projection ->
            val sources = projection.first
            val kind = state.filter.libraryKind()
            val items = state.items.map { item ->
                val objectRef = item.objectRef
                val file = sources.files[objectRef.id.value]
                val renamable = file?.takeIf { it.ownership == ContentOwnership.HALO_OWNED && it.availability == WorkspaceFileAvailability.OWNED_LOCAL }
                item.copy(libraryKind = sources.kind(objectRef), fromGlasses = objectRef.id.value in sources.glassesPhotos,
                    nameFacts = sources.nameFacts[objectRef.id.value], renamableFileVersion = renamable?.version)
            }.filter { item ->
                when (state.filter) {
                    NotebookFilter.ALL -> item.objectRef.type != WorkspaceObjectType.MEMORY
                    NotebookFilter.GLASSES_PHOTOS -> item.libraryKind == HaloLibraryKind.PHOTO && item.fromGlasses
                    else -> kind == null || item.libraryKind == kind
                }
            }
            val topLevel = state.selectedCollectionId == null
            val sessionSources = state.studySessions.associate { it.id to it.sourceObjectIds }
            val cards = if (state.filter == NotebookFilter.ALL || state.filter == NotebookFilter.CARDS) {
                val matching = sources.cards.filter {
                    val member = it.sourceObjectId?.let(state.selectedCollectionMembers::contains) == true ||
                        sessionSources[it.sessionId].orEmpty().any(state.selectedCollectionMembers::contains)
                    (topLevel || member) && matchesLibraryTerms(state.resolvedQuery, it.question)
                }
                when (state.sort) {
                    NotebookSort.TITLE -> matching.sortedWith(compareBy<LibraryPracticeCard> { it.question.lowercase() }.thenBy { it.id })
                    NotebookSort.CREATED -> matching
                    NotebookSort.UPDATED, NotebookSort.TOOL -> matching.sortedWith(compareByDescending<LibraryPracticeCard> {
                        it.lastReviewedAtEpochMs ?: it.createdAtEpochMs
                    }.thenBy { it.id })
                }
            } else emptyList()
            val people = if (state.filter == NotebookFilter.PEOPLE || !topLevel && state.filter == NotebookFilter.ALL) {
                sources.people.filter { (topLevel || it.photoId in state.selectedCollectionMembers) &&
                    matchesLibraryTerms(state.resolvedQuery, it.name) }.sortedBy { it.name.lowercase() }
            } else emptyList()
            state.copy(items = items, libraryCards = cards, libraryPeople = people,
                librarySourcesError = projection.second)
        }
        .combine(dismissedLibrarySuggestions) { state, dismissed ->
            state.copy(collectionSuggestion = suggestLibraryCollection(state, dismissed))
        }
        .combine(suggestionUndo) { state, undo -> state.copy(suggestionUndo = undo) }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebookUiState())

    /** Keep a successful prior view available; an error never becomes a loaded-empty result. */
    private fun libraryFailure(requested: QueryState): NotebookUiState {
        val previous = uiState.value
        return if (previous.query == requested.rawText && previous.filter == requested.filter &&
            previous.selectedCollectionId == requested.collectionId) previous.copy(loadError = true)
        else NotebookUiState(query = requested.rawText, resolvedQuery = requested.text,
            filter = requested.filter, selectedCollectionId = requested.collectionId, loadError = true)
    }

    private val mutableViewBinding = MutableStateFlow(0L)
    val viewBinding = mutableViewBinding.asStateFlow()
    private var viewState: androidx.lifecycle.SavedStateHandle? = null
    fun isCurrentView(state: NotebookUiState): Boolean = state.query == query.value && state.resolvedQuery == query.value.trim() &&
        state.sort == sort.value && state.filter == filter.value && state.selectedCollectionId == selectedCollection.value
    fun pendingReturnItem(): String? = viewState?.get<String>("library.lastOpened")
    fun consumedReturnItem(id: String) {
        if (pendingReturnItem() == id) viewState?.set<String?>("library.lastOpened", null)
    }
    /** Reattach the real Library entry after recreation; no command or selection is executed. */
    fun bindSavedView(handle: androidx.lifecycle.SavedStateHandle) {
        if (viewState === handle) return
        viewState = handle
        if (handle.contains("library.query")) {
            query.value = handle.get<String>("library.query").orEmpty().take(500)
            sort.value = NotebookSort.entries.find { it.name == handle.get<String>("library.sort") } ?: NotebookSort.UPDATED
            filter.value = NotebookFilter.entries.find { it.name == handle.get<String>("library.filter") && it != NotebookFilter.MEMORY } ?: NotebookFilter.ALL
            selectedCollection.value = handle.get<String>("library.collection")
            selection.value = handle.get<ArrayList<String>>("library.selection").orEmpty().take(100).toSet()
            libraryRevision++
        }
        keepView()
        mutableViewBinding.value += 1
    }
    private fun keepView() {
        viewState?.let { handle ->
            handle["library.query"] = query.value
            handle["library.sort"] = sort.value.name
            handle["library.filter"] = filter.value.name
            handle["library.collection"] = selectedCollection.value
            handle["library.selection"] = ArrayList(selection.value)
        }
    }

    fun retryLibraryLoad() { libraryRevision++; refreshRevision.value += 1 }

    private var libraryRevision = 0L
    fun setQuery(value: String) { libraryRevision++; query.value = value; selection.value = emptySet(); keepView() }
    fun setFilter(value: NotebookFilter) { libraryRevision++; if (value == NotebookFilter.MEMORY) { openMemoryHub(); return }; filter.value = value; selection.value = emptySet(); keepView() }
    fun selectCollection(id: String?) { libraryRevision++; selectedCollection.value = id; selection.value = emptySet(); keepView() }
    internal suspend fun openLibraryCollection(id: String, authority: () -> Unit = {}): Boolean {
        if (!com.halo.app.data.workspace.WorkspaceActionIds.isValid(id) || !organization.containsCollection(id)) return false
        authority()
        setFilter(NotebookFilter.ALL)
        setQuery("")
        selectCollection(id)
        return true
    }
    internal suspend fun librarySourcesSnapshot(): HaloLibrarySources =
        librarySources().first().copy(people = emptyList(), nameFacts = emptyMap())
    internal suspend fun libraryAliasIds(query: String): Set<String> {
        if (query.isBlank() || query.length > 500) return emptySet()
        val authority = libraryAuthority() ?: return emptySet()
        val aliases = aliasFlow().first()
        if (!authority()) return emptySet()
        return aliases.values.filter { matchesLibraryTerms(query, it.oldTitle) }
            .mapTo(hashSetOf()) { it.objectId }
    }

    internal val libraryNamingEnabled: Boolean get() = libraryNames != null

    internal fun renameLibraryItem(item: NotebookWorkspaceItem, newTitle: String) {
        val state = uiState.value
        if (item in state.items) runLibraryContent(state, LibraryContentChange(LibraryContentOperation.RENAME_ITEM, item.objectRef.id.value, newTitle))
    }

    internal fun dismissLibrarySuggestion(expected: LibraryCollectionSuggestion) {
        val state = uiState.value
        if (expected == state.collectionSuggestion) runLibraryContent(state, LibraryContentChange(LibraryContentOperation.DISMISS_SUGGESTION, expected.collectionId))
    }

    internal fun addLibrarySuggestion(expected: LibraryCollectionSuggestion) {
        val state = uiState.value
        if (expected == state.collectionSuggestion) runLibraryContent(state, LibraryContentChange(LibraryContentOperation.ADD_SUGGESTED, expected.collectionId))
    }

    internal fun undoLibrarySuggestion(expected: LibraryCollectionAddition) {
        val state = uiState.value
        if (expected == state.suggestionUndo) runLibraryContent(state, LibraryContentChange(LibraryContentOperation.UNDO_SUGGESTION, expected.collectionId))
    }

    private fun runLibraryContent(expected: NotebookUiState, change: LibraryContentChange) = viewModelScope.launch {
        val result = try { applyLibraryContent(expected, change) { true } }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        if (result == null) postNotice("This item changed. Open its instrument and review the current information.")
    }

    internal suspend fun applyLibraryContent(expected: NotebookUiState, change: LibraryContentChange,
        admission: suspend () -> Boolean): Int? {
        val authority = libraryAuthority() ?: return null
        if (!authority() || mutating.value || expected != uiState.value || !isCurrentView(expected) || selection.value.isNotEmpty()) return null
        val revision = libraryRevision
        val admit: suspend () -> Boolean = {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                revision == libraryRevision && authority() && admission()
            }
        }
        mutating.value = true
        try {
            val count = when (change.operation) {
                LibraryContentOperation.RENAME_ITEM -> {
                    val owner = libraryNames ?: return null
                    val item = expected.visibleLibraryObjects().firstOrNull { it.objectRef.id.value == change.target } ?: return null
                    if (!canRenameLibraryItem(item) ||
                        !owner.rename(item.objectRef, change.title.orEmpty(), item.renamableFileVersion, admit)) return null
                    1
                }
                LibraryContentOperation.ADD_SUGGESTED, LibraryContentOperation.DISMISS_SUGGESTION -> {
                    val suggestion = expected.collectionSuggestion?.takeIf { it.collectionId == change.target } ?: return null
                    if (change.operation == LibraryContentOperation.DISMISS_SUGGESTION) {
                        if (!admit()) return null
                        dismissedLibrarySuggestions.value = (dismissedLibrarySuggestions.value + suggestion).toList().takeLast(20).toSet()
                        0
                    } else {
                        val addition = organization.applyLibrarySuggestion(suggestion, admit) ?: return null
                        suggestionUndoUntil = android.os.SystemClock.elapsedRealtime() + 10_000L
                        suggestionUndo.value = addition
                        viewModelScope.launch {
                            delay(10_000L)
                            if (suggestionUndo.value == addition) suggestionUndo.value = null
                        }
                        addition.ids.size
                    }
                }
                LibraryContentOperation.UNDO_SUGGESTION -> {
                    val addition = expected.suggestionUndo?.takeIf { it.collectionId == change.target } ?: return null
                    val affected = organization.undoLibrarySuggestion(addition) {
                        admit() && suggestionUndo.value == addition && android.os.SystemClock.elapsedRealtime() < suggestionUndoUntil
                    } ?: return null
                    suggestionUndo.value = null
                    affected
                }
            }
            libraryRevision++
            return count
        } finally { mutating.value = false }
    }
    fun setSort(value: NotebookSort) { libraryRevision++; sort.value = value; keepView() }
    fun clearSelection() { libraryRevision++; selection.value = emptySet(); keepView() }
    internal suspend fun libraryOrganizationSnapshot(objectId: String?, collectionId: String?) =
        organization.captureGuarded(objectId, collectionId)
    internal suspend fun applyLibraryOrganization(expected: com.halo.app.data.notebook.NotebookOrganizationSnapshot,
        change: com.halo.app.data.notebook.NotebookOrganizationChange, isStillAdmitted: suspend () -> Boolean) =
        organization.applyGuarded(expected, change, isStillAdmitted)

    internal fun libraryView() = NotebookLibraryView(uiState.value, query.value, filter.value, sort.value,
        selectedCollection.value, selection.value.toSet(), mutating.value, libraryRevision)

    /** A synchronous view-only change; persistence and destructive confirmations remain with their owners. */
    internal fun applyLibraryChange(change: NotebookLibraryChange): Boolean {
        if (mutating.value) return false
        when (change) {
            is NotebookLibraryChange.Query -> { if (change.text.length > 500) return false; setQuery(change.text) }
            is NotebookLibraryChange.Filter -> { if (change.value == NotebookFilter.MEMORY) return false; setFilter(change.value) }
            is NotebookLibraryChange.Sort -> setSort(change.value)
            is NotebookLibraryChange.Collection -> {
                if (change.id != null && uiState.value.collections.none { it.id == change.id }) return false
                selectCollection(change.id)
            }
            is NotebookLibraryChange.Selection -> {
                val visible = uiState.value.items.map { it.objectRef.id.value }.filterNot { it.startsWith("learning:") }.toSet()
                if (change.ids.size > 100 || !visible.containsAll(change.ids)) return false
                libraryRevision++; selection.value = change.ids.toSet(); keepView()
            }
        }
        return true
    }

    private fun postNotice(value: String?) { mutableBatchNotice.value = null; mutableNotice.value = value }
    fun clearNotice() { postNotice(null) }
    fun dismissTrashUndo() { if (!mutating.value) { libraryRevision++; lastTrashSources = emptyList(); mutableTrashUndoIds.value = emptySet() } }
    fun toggleSelection(id: String) { libraryRevision++;
        if (mutating.value) return
        if (id in selection.value) selection.value = selection.value - id
        else if (selection.value.size < 100) selection.value = selection.value + id
        else postNotice("Select up to 100 items at a time.")
        keepView()
    }
    fun openEverydayItem(entry: com.halo.app.everyday.presentation.EverydayLibraryEntry) {
        if(entry !in uiState.value.everyday) {postNotice("This item changed. Open its instrument and review the current information.");return}
        viewModelScope.launch {
            val opened=try {openEveryday(entry)} catch(cancelled: kotlinx.coroutines.CancellationException) {throw cancelled} catch(_: Exception) {false}
            if(!opened) postNotice("This item changed. Open its instrument and review the current information.")
        }
    }
    fun openLibraryCard(card: LibraryPracticeCard) {
        if (card in uiState.value.libraryCards) navigationChannel.trySend(NotebookNavigation.OpenStudy(card.sessionId, cardId = card.id))
        else postNotice("This item changed. Open its instrument and review the current information.")
    }
    fun openLibraryPerson(person: LibraryPersonReference) = viewModelScope.launch {
        val photo = try {
            workspace.getObject(person.photoId)?.takeIf {
                it.lifecycle == WorkspaceObjectLifecycle.ACTIVE && workspace.getFile(person.photoId)?.version == person.photoVersion
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        if (photo != null && person in uiState.value.libraryPeople) openObject(photo)
        else postNotice("This item changed. Open its instrument and review the current information.")
    }
    fun selectVisible() { libraryRevision++;
        selection.value = uiState.value.items.map { it.objectRef.id.value }.filterNot { it.startsWith("learning:") }.take(100).toSet()
        keepView()
    }
    private var lastTrashSources = emptyList<NotebookBatchSource>()

    internal suspend fun prepareLibraryBatch(action: NotebookBatchAction, collectionId: String? = null,
        undo: Boolean = false): NotebookBatchAdmission? {
        val owner = batchOwner ?: return null
        val before = libraryView()
        if (!before.settled || before.mutating) return null
        val ids = if (undo) lastTrashSources.map { it.objectId } else before.selection.toList()
        if (ids.isEmpty() || ids.size > 100 || (undo && action != NotebookBatchAction.RESTORE)) return null
        if (!undo && !before.rendered.items.map { it.objectRef.id.value }.containsAll(ids)) return null
        if ((action == NotebookBatchAction.COLLECTION) != (collectionId != null)) return null
        try {
            val collectionVersion = collectionId?.let { owner.collectionVersion(it) ?: return null }
            val memberships = if (collectionId == null) emptyMap() else ids.associateWith { owner.membership(collectionId, it) }
            val unavailable = linkedSetOf<String>()
            val sources = if (undo) lastTrashSources.toList() else ids.mapNotNull { id ->
                val source = try { owner.capture(id) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                if (source == null) unavailable += id
                source
            }
            if (libraryView() != before || (undo && sources != lastTrashSources)) return null
            return NotebookBatchAdmission(action, sources, collectionId, collectionVersion, undo, memberships, unavailable)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            postNotice(NOTEBOOK_BATCH_SOURCES_CHANGED)
            return null
        }
    }

    /** Shared by touch and typed tools. No launched work or optimistic all-or-nothing receipt. */
    internal suspend fun applyLibraryBatch(admission: NotebookBatchAdmission,
        isStillAdmitted: suspend () -> Boolean = { true }): NotebookBatchReceipt? {
        val owner = batchOwner ?: return null
        if (mutating.value || !isStillAdmitted()) return null
        if (admission.undo) {
            if (lastTrashSources != admission.sources) return null
        } else if (selection.value != admission.sources.map { it.objectId }.toSet() + admission.unavailableIds) return null
        postNotice(null)
        mutating.value = true
        val successful = linkedSetOf<String>()
        val failed = linkedSetOf<String>().apply { addAll(admission.unavailableIds) }
        val unknown = linkedSetOf<String>()
        val trashed = mutableListOf<NotebookBatchSource>()
        val priorUndo = lastTrashSources
        val selectionAtAdmission = selection.value.toSet()
        if (!admission.undo) { lastTrashSources = emptyList(); mutableTrashUndoIds.value = emptySet() }
        try {
            for (source in admission.sources) {
                try {
                    val after = owner.apply(source, admission, isStillAdmitted)
                    successful += source.objectId
                    if (admission.action == NotebookBatchAction.TRASH) trashed += after
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: NotebookBatchRefused) { failed += source.objectId }
                catch (_: com.halo.app.data.SavedChatAdmissionException) { failed += source.objectId }
                catch (_: Exception) { unknown += source.objectId }
            }
            mutableNotice.value = if (failed.isEmpty() && unknown.isEmpty()) {
                if (admission.action == NotebookBatchAction.CONTEXT)
                    "${successful.size} items added to context; Halo uses up to 20 recent items within its context budget."
                else "${successful.size} items updated"
            } else "${successful.size} items updated; ${failed.size} unchanged; ${unknown.size} outcomes unconfirmed. Review current state before retrying."
            val receipt = NotebookBatchReceipt(admission.action, successful.toSet(), failed.toSet(), unknown.toSet(),
                if (admission.undo) priorUndo.any { it.objectId !in successful } else trashed.isNotEmpty())
            mutableBatchNotice.value = receipt
            return receipt
        } finally {
            // Also retain the exact committed prefix when cancellation interrupts a later item.
            lastTrashSources = if (admission.undo) priorUndo.filterNot { it.objectId in successful } else trashed
            mutableTrashUndoIds.value = lastTrashSources.map { it.objectId }.toSet()
            // Unattempted objects stay selected after cancellation; confirmed successes never do.
            // A newer explicit user selection remains authoritative if the lease was changed.
            if (!admission.undo && selection.value == selectionAtAdmission)
                selection.value = admission.sources.map { it.objectId }.toSet() + admission.unavailableIds - successful
            keepView()
            mutating.value = false
        }
    }

    fun applyToSelection(action: NotebookBatchAction, collectionId: String? = null) = viewModelScope.launch {
        val before = libraryView()
        val admission = prepareLibraryBatch(action, collectionId) ?: return@launch
        applyLibraryBatch(admission) {
            val current = libraryView()
            current.ownerRevision == before.ownerRevision && current.selection == before.selection
        }
    }

    /** Exact post-Trash identities, not the current selection or every object currently in Trash. */
    fun undoLastTrash() = viewModelScope.launch {
        val before = libraryView()
        val admission = prepareLibraryBatch(NotebookBatchAction.RESTORE, undo = true) ?: return@launch
        applyLibraryBatch(admission) { libraryView().ownerRevision == before.ownerRevision }
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
        rememberLibraryItemReturn(id)
        navigationChannel.trySend(when (item.type) {
            WorkspaceObjectType.NOTE -> if (item.contentRef == "file-tools:$id") NotebookNavigation.OpenFileTools(id) else NotebookNavigation.OpenNote(id)
            WorkspaceObjectType.CONVERSATION -> NotebookNavigation.OpenConversation(id)
            WorkspaceObjectType.ARTIFACT -> if (item.contentRef.startsWith("studio:")) {
                NotebookNavigation.OpenStudio(id)
            } else if (item.contentRef.startsWith("canvas:")) {
                NotebookNavigation.OpenCanvas(id)
            } else {
                NotebookNavigation.OpenFile(id)
            }
            WorkspaceObjectType.CANVAS -> NotebookNavigation.OpenCanvas(id)
            WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA -> NotebookNavigation.OpenFile(id)
            WorkspaceObjectType.MEMORY -> NotebookNavigation.OpenMemory(id)
        })
    }
    internal fun rememberLibraryItemReturn(id: String) {
        viewState?.set("library.lastOpened", id)
        keepView()
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
    fun deletePermanently(id: String) = viewModelScope.launch {
        try {
            val result = notes.purgeNote(id, includeRecovery = true)
            postNotice(when {
                result !is com.halo.app.data.notebook.NotebookPurgeResult.Saved || result.receipt.sourcePresent -> "The note could not be deleted. Try again."
                result.receipt.record.state == "COMPLETE" && result.receipt.record.sharedRetained == 0 -> "Note deleted."
                result.receipt.record.state == "COMPLETE" -> "Note deleted. Shared attachment files were kept."
                result.receipt.record.state == "PENDING" -> "Note deleted. Attachment cleanup is still pending."
                else -> "Note deleted. Some attachments could not be removed."
            })
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { postNotice("Deletion could not be confirmed. Check Trash before trying again.") }
    }
}
