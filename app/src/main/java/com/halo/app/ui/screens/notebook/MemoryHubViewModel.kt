package com.halo.app.ui.screens.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.data.memory.*
import com.halo.runtime.cognition.memory.MemoryConflictKind
import com.halo.runtime.cognition.memory.MemoryRetriever
import com.halo.runtime.cognition.memory.MemorySearchScope
import com.halo.runtime.cognition.memory.MemorySensitivityClassifier
import com.halo.runtime.cognition.memory.MemorySensitivity
import com.halo.runtime.cognition.memory.MemoryProposalOrigin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MemoryScopeChoice(val scope: MemoryScope, val id: String?, val label: String)
data class MemoryReview(val candidate: MemoryCandidate, val conflict: MemoryConflict)
data class MemoryHubState(val items: List<MemoryUiItem> = emptyList(), val count: Int = 0,
    val loading: Boolean = true, val query: String = "", val group: MemoryGroup = MemoryGroup.ALL,
    val scope: MemoryScope? = null, val sort: MemorySort = MemorySort.UPDATED, val error: String? = null)

/** Local presentation only. Retrieval never marks usage and mutations stay behind the repository. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MemoryHubViewModel(
    private val memories: MemoryRepository,
    private val retriever: MemoryRetriever,
    val learning: PersonalLearningRepository,
    val memoryUse: StateFlow<Boolean>,
    private val setMemoryUse: (Boolean) -> Unit,
    private val scopeName: suspend (MemoryScope, String) -> String?,
    private val eligibleScopes: suspend () -> List<MemoryScopeChoice>,
) : ViewModel() {
    val pending = learning.pending.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val episodes = learning.episodes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val query = MutableStateFlow("")
    private val group = MutableStateFlow(MemoryGroup.ALL)
    private val scope = MutableStateFlow<MemoryScope?>(null)
    private val sort = MutableStateFlow(MemorySort.UPDATED)
    private val error = MutableStateFlow<String?>(null)
    private val mutableReview = MutableStateFlow<MemoryReview?>(null)
    val review = mutableReview.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableScopes = MutableStateFlow(listOf(MemoryScopeChoice(MemoryScope.GLOBAL, null, "Everywhere")))
    val scopeChoices = mutableScopes.asStateFlow()
    private data class Filters(val query: String, val group: MemoryGroup, val scope: MemoryScope?, val sort: MemorySort)
    private val filters = combine(query.debounce(250), group, scope, sort, ::Filters)
    val state = combine(memories.observeMemories(), filters) { records, filters -> records to filters }
        .mapLatest { (records, filters) ->
            val safe = records.filter { it.scope != MemoryScope.CONVERSATION &&
                MemorySensitivityClassifier.classify(it.content) != MemorySensitivity.SECRET }
            val selected = if (filters.query.isBlank()) safe else {
                val hits = retriever.search(filters.query, filters.scope?.let { MemorySearchScope.valueOf(it.name) } ?: MemorySearchScope.ALL, 20)
                hits.mapNotNull { hit -> safe.firstOrNull { it.id == hit.memoryId } }
            }
            val filtered = selected.filter { filters.group.includes(it.kind) && (filters.scope == null || it.scope == filters.scope) }
            // Preserve shared retrieval ranking when searching; sorting applies only to browsing.
            val sorted = if (filters.query.isNotBlank()) filtered else when (filters.sort) {
                MemorySort.UPDATED -> filtered.sortedByDescending { it.updatedAt }
                MemorySort.USED -> filtered.sortedByDescending { it.usageCount }
                MemorySort.LAST_USED -> filtered.sortedByDescending { it.lastUsedAt ?: Long.MIN_VALUE }
            }
            MemoryHubState(sorted.take(100).map { record -> MemoryPresentation.item(record,
                record.scopeId?.let { scopeName(record.scope, it) }) }, safe.size, false,
                filters.query, filters.group, filters.scope, filters.sort)
        }.catch { emit(MemoryHubState(loading = false, error = "Memory could not be loaded.")) }
        .combine(error) { state, error -> state.copy(error = error ?: state.error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MemoryHubState())

    init { viewModelScope.launch(Dispatchers.IO) { mutableScopes.value = mutableScopes.value + eligibleScopes().distinctBy { it.scope to it.id } } }
    fun search(value: String) { query.value = value.take(MemoryRetriever.MAX_QUERY_CHARS) }
    fun filter(value: MemoryGroup) { group.value = value }
    fun filterScope(value: MemoryScope?) { scope.value = value }
    fun sort(value: MemorySort) { sort.value = value }
    fun useMemory(value: Boolean) = setMemoryUse(value)
    fun discardReview() { mutableReview.value = null }
    fun act(block: suspend () -> Unit) = viewModelScope.launch {
        if (mutableBusy.value) return@launch
        mutableBusy.value = true; error.value = null
        try { withContext(Dispatchers.IO) { block() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error.value = "Could not save. Review the content, scope and available evidence, then try again." }
        finally { mutableBusy.value = false }
    }
    fun prepare(candidate: MemoryCandidate) = act {
        if (MemorySensitivityClassifier.classify(candidate.content) == MemorySensitivity.SECRET) {
            error.value = "Secrets such as passwords or API keys cannot be stored as Halo memory. Use secure configuration instead."
            return@act
        }
        MemorySensitivityClassifier.requireWritable(candidate.content)
        validateScope(candidate)
        mutableReview.value = MemoryReview(candidate.copy(origin = MemoryProposalOrigin.USER_EXPLICIT, acceptedByUser = true), memories.conflict(candidate))
    }
    private suspend fun validateScope(candidate: MemoryCandidate) {
        require(candidate.scope == MemoryScope.GLOBAL || eligibleScopes().any { it.scope == candidate.scope && it.id == candidate.scopeId })
    }
    fun saveReviewed() = act {
        val review = mutableReview.value ?: return@act
        validateScope(review.candidate)
        val current = memories.conflict(review.candidate)
        if (current.kind != review.conflict.kind || current.records.map { it.id to it.content } != review.conflict.records.map { it.id to it.content }) {
            mutableReview.value = review.copy(conflict = current); return@act
        }
        if (current.kind == MemoryConflictKind.CONFLICTS) {
            require(current.records.size == 1)
            check(memories.correctMemory(current.records.single().id, review.candidate.content))
        } else memories.promoteCandidate(review.candidate)
        mutableReview.value = null
    }
}
