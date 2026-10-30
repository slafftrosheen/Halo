package com.halo.app.ui.screens.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.data.memory.*
import com.halo.app.data.notebook.WorkspaceObject
import com.halo.runtime.cognition.memory.MemoryEvidenceRef
import com.halo.runtime.cognition.memory.MemoryEvidenceKind
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MemorySourceUi(val label: String, val available: Boolean?, val objectRef: WorkspaceObject? = null)
data class MemoryDetailUi(val item: MemoryUiItem? = null, val revisions: List<MemoryRevision> = emptyList(),
    val sources: List<MemorySourceUi> = emptyList(), val busy: Boolean = false, val message: String? = null,
    val loading: Boolean = true, val failed: Boolean = false)
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryDetailViewModel(private val id: String, private val repository: MemoryRepository,
    private val scopeName: suspend (MemoryScope, String) -> String?,
    private val exists: suspend (MemoryEvidenceRef) -> Boolean,
    private val sourceObject: suspend (String) -> WorkspaceObject?) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val refresh = MutableStateFlow(0L)
    val state = refresh.flatMapLatest { combine(repository.observeMemories(), repository.observeRevisions(id)) { records, revisions ->
        val record = records.firstOrNull { it.id == id }
        MemoryDetailUi(record?.let { MemoryPresentation.item(it, it.scopeId?.let { id -> scopeName(it.scope, id) }) }, revisions,
            record?.evidence?.map { evidence ->
                val available = if (evidence.kind == MemoryEvidenceKind.IMPORTED) null else exists(evidence)
                MemorySourceUi(MemoryPresentation.evidence(evidence.kind), available,
                    if (available == true && evidence.kind == MemoryEvidenceKind.WORKSPACE_OBJECT) sourceObject(evidence.id) else null)
            }.orEmpty(), loading = false)
    }.onStart { emit(MemoryDetailUi()) }.catch { emit(MemoryDetailUi(loading = false, failed = true)) }
    }.combine(busy) { state, busy -> state.copy(busy = busy) }.combine(message) { state, message -> state.copy(message = message) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MemoryDetailUi())
    fun confirm() = act { repository.confirmMemory(id) }
    fun retry() { refresh.value += 1L }
    private fun act(block: suspend () -> Boolean) = viewModelScope.launch {
        if (busy.value) return@launch
        busy.value = true
        try { message.value = if (withContext(Dispatchers.IO) { block() }) "Confirmed. Existing expiry rules are unchanged." else "Memory is no longer available." }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message.value = "Could not confirm this memory." }
        finally { busy.value = false }
    }
}
