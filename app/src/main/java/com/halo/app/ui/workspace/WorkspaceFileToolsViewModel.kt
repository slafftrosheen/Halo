package com.halo.app.ui.workspace

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

internal data class WorkspaceFileToolsState(val selected: WorkspaceFileToolsObject? = null, val file: WorkspaceFile? = null,
    val text: WorkspaceTextDocument? = null, val annotations: WorkspacePdfAnnotations? = null,
    val receipt: WorkspaceBatchReceipt? = null, val recovery: RecoveryDraft? = null,
    val loading: Boolean = false, val busy: Boolean = false, val error: Boolean = false, val cancelled: Boolean = false,
    val output: WorkspaceFile? = null, val textHistory: WorkspaceTextDocuments.HistoryStatus = WorkspaceTextDocuments.HistoryStatus())

/** Screen adapter only. Mutations use the same revision/source contracts as assistant actions. */
internal class WorkspaceFileToolsViewModel(val tools: WorkspaceFileTools, initialId: String? = null,
    private val savedState: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val mutable = MutableStateFlow(WorkspaceFileToolsState())
    val state = mutable.asStateFlow()
    val objects = tools.workspace.observe(WorkspaceQuery(types = setOf(WorkspaceObjectType.FILE, WorkspaceObjectType.NOTE)))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val files = tools.files.observeFiles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var selectedId: String? = null
    val selectedObjectId: String? get() = selectedId
    private var observation: Job? = null
    private var operationJob: Job? = null
    private var activeBatchId: String? = null
    private var recoverySequence = 0L
    private data class DrawingRecovery(val id: String, val revision: Long, val pages: Map<Int, String>, val sequence: Long)
    private val recoveryWrites = Channel<DrawingRecovery>(Channel.CONFLATED)
    init {
        (savedState.get<String>("fileToolsObject") ?: initialId)?.let(::open)
        viewModelScope.launch {
            for (draft in recoveryWrites) {
                try { tools.annotations.saveRecovery(draft.id, draft.revision, draft.pages, draft.sequence) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    // A completed save can legitimately win a queued recovery write. Never recreate its stale draft.
                    if (runCatching { tools.annotations.get(draft.id)?.revision == draft.revision }.getOrDefault(false) && selectedId == draft.id)
                        mutable.value = mutable.value.copy(error = true)
                }
            }
        }
    }
    fun open(id: String) = operation {
        observation?.cancel(); selectedId = id; savedState["fileToolsObject"] = id
        mutable.value = WorkspaceFileToolsState(loading = true, busy = true)
        tools.prepareForEditing(id)
        observe(id, true)
    }
    fun prepareImages(files: List<WorkspaceFile>, onReady: (List<WorkspaceFile>) -> Unit) = operation {
        require(files.size in 1..6)
        val pinned = files.map { tools.versions.pin(reference(it)) }
        onReady(pinned)
    }
    private fun observe(id: String, keepBusy: Boolean) {
        observation?.cancel(); selectedId = id; savedState["fileToolsObject"] = id
        mutable.value = WorkspaceFileToolsState(loading = true, busy = keepBusy)
        observation = viewModelScope.launch {
            try {
                combine(tools.workspace.observe(), tools.files.observeFile(WorkspaceObjectId(id)), tools.text.documents.observe(id)) { _, _, _ -> Unit }
                    .collect { refresh(id) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(selected = null, file = null, text = null, annotations = null,
                receipt = null, recovery = null, loading = false, error = true) }
        }
    }
    private suspend fun refresh(id: String) {
        val snapshot = tools.snapshot(id)
        val selected = snapshot.selected; val file = snapshot.file; val text = snapshot.text
        val annotations = snapshot.annotations; val receipt = snapshot.receipt; val recovery = snapshot.recovery
        recoverySequence = maxOf(recoverySequence, recovery?.sequence ?: 0L)
        if (selectedId == id) mutable.value = mutable.value.copy(selected = selected, file = file, text = text,
            annotations = annotations, receipt = receipt, recovery = recovery, loading = false,
            textHistory = text?.let { tools.text.historyStatus(it.id, it.revision) } ?: WorkspaceTextDocuments.HistoryStatus())
    }
    fun closeSelection() {
        if (mutable.value.busy) return
        observation?.cancel(); selectedId = null; savedState["fileToolsObject"] = null
        mutable.value = WorkspaceFileToolsState()
    }
    fun create(title: String, format: WorkspaceTextFormat) = operation { observe(tools.text.create(title, format).id, true) }
    fun importFile(uri: Uri) = operation { observe(tools.files.importFile(uri).objectId.value, true) }
    fun importText(file: WorkspaceFile) = operation {
        observe(tools.text.import(file.objectId.value, requireNotNull(file.version)).id, true)
    }
    fun editCsv(id: String, revision: Long, command: WorkspaceCsvCommand, onSaved: () -> Unit = {}) = operation {
        tools.text.editCsv(id, revision, command); refresh(id); onSaved()
    }
    fun undoText(document: WorkspaceTextDocument) = operation { tools.text.undo(document.id, document.revision); refresh(document.id) }
    fun redoText(document: WorkspaceTextDocument) = operation { tools.text.redo(document.id, document.revision); refresh(document.id) }
    fun exportText(selected: WorkspaceFileToolsObject, format: WorkspaceTextFormat) = operation {
        mutable.value = mutable.value.copy(output = tools.text.exportNote(selected.id, selected.version.toLong(), format))
    }
    fun transform(file: WorkspaceFile, recipe: CanvasImageRecipe) = operation {
        mutable.value = mutable.value.copy(output = tools.media.transform(reference(file), recipe))
    }
    fun batch(title: String, files: List<WorkspaceFile>, recipe: CanvasImageRecipe) = operation {
        activeBatchId = null
        val receipt = tools.media.batch(title, files.map(::reference), recipe) { started ->
            activeBatchId = started.id; observe(started.id, true)
        }
        mutable.value = mutable.value.copy(receipt = receipt)
    }
    fun collage(files: List<WorkspaceFile>, recipe: CanvasStitchRecipe) = operation {
        mutable.value = mutable.value.copy(output = tools.media.collage(files.map(::reference), recipe))
    }
    fun extractPdfPage(file: WorkspaceFile, page: Int) = operation {
        mutable.value = mutable.value.copy(output = tools.media.extractPdfPageImage(reference(file), page))
    }
    fun createAnnotations(file: WorkspaceFile, title: String) = operation {
        observe(tools.annotations.create(reference(file), title).id, true)
    }
    fun saveAnnotations(note: WorkspacePdfAnnotations, pages: Map<Int, String>) = operation {
        val sequence = recoverySequence
        tools.annotations.setPages(note.id, note.revision, pages, sequence)
        refresh(note.id)
    }
    fun keepDrawingRecovery(note: WorkspacePdfAnnotations, draft: CanvasDrawingDraft) {
        if (mutable.value.busy) return
        if (draft.original != note.block.text || draft.fileVersion != null && draft.fileVersion != note.source.version) { reportError(); return }
        val sequence = ++recoverySequence
        if (recoveryWrites.trySend(DrawingRecovery(note.id, note.revision, draft.pages.toMap(), sequence)).isFailure) reportError()
    }
    fun exportAnnotation(note: WorkspacePdfAnnotations, page: Int) = operation {
        mutable.value = mutable.value.copy(output = tools.annotations.exportPageImage(note.id, note.revision, page))
    }
    fun openOutput(source: WorkspaceFileVersion) = operation {
        mutable.value = mutable.value.copy(output = tools.versions.current(source))
    }
    fun attach(canvasId: String, source: WorkspaceFileToolsObject, onInserted: (String) -> Unit) = operation {
        onInserted(tools.attachToCanvas(canvasId, source.id, source.version))
    }
    fun attachOutput(canvasId: String, output: WorkspaceFile, onInserted: (String) -> Unit) = operation {
        onInserted(tools.attachToCanvas(canvasId, output.objectId.value, requireNotNull(output.version)))
    }
    fun dismissOutput() { mutable.value = mutable.value.copy(output = null) }
    fun cancelOperation() { operationJob?.cancel() }
    fun dismissError() { mutable.value = mutable.value.copy(error = false, cancelled = false) }
    fun reportError() { mutable.value = mutable.value.copy(error = true) }
    private fun reference(file: WorkspaceFile) = WorkspaceFileVersion(file.objectId.value, requireNotNull(file.version))
    private fun operation(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = false, cancelled = false)
        operationJob = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) {
                withContext(NonCancellable) { activeBatchId?.let { id -> runCatching { refresh(id) } } }
                mutable.value = mutable.value.copy(cancelled = true, loading = false)
                throw cancelled
            }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = true, loading = false) }
            finally { activeBatchId = null; mutable.value = mutable.value.copy(busy = false) }
        }
    }
    class Factory(private val tools: WorkspaceFileTools, private val initialId: String?, private val savedState: SavedStateHandle) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WorkspaceFileToolsViewModel::class.java))
            return WorkspaceFileToolsViewModel(tools, initialId, savedState) as T
        }
    }
}
