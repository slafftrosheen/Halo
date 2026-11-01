package com.halo.app.ui.workspace

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.halo.app.data.studio.*
import com.halo.app.data.workspace.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel

data class StudioUiState(val project: StudioProject? = null, val loading: Boolean = false,
    val busy: Boolean = false, val aiReview: Boolean = false, val failure: StudioFailure? = null, val output: WorkspaceFile? = null,
    val versions: List<StudioProjectVersion> = emptyList(), val versionsLoaded: Boolean = false,
    val exportedSource: String? = null, val operation: StudioOperation? = null) { val error: Boolean get() = failure != null }

data class StudioLibraryStatus(val projectsLoading: Boolean = true, val canvasesLoading: Boolean = true,
    val projectsError: Boolean = false, val canvasesError: Boolean = false,
    val imagesLoading: Boolean = true, val imagesError: Boolean = false)

/** Owns presentation/session attachment only. Every edit is delegated to the shared data owner. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StudioViewModel(val service: StudioService, private val files: WorkspaceFileRepository,
    private val imageService: com.halo.app.ai.WorkspaceImageService, private val assets: NotebookAssetRepository,
    initialProjectId: String? = null, private val savedState: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle()) : ViewModel() {
    private val mutable = MutableStateFlow(StudioUiState())
    val state = mutable.asStateFlow()
    private val libraryRevision = MutableStateFlow(0L)
    private val mutableLibraryStatus = MutableStateFlow(StudioLibraryStatus())
    val libraryStatus = mutableLibraryStatus.asStateFlow()
    val projects = libraryRevision.flatMapLatest {
        service.projects.observeProjects()
            .onStart { mutableLibraryStatus.update { it.copy(projectsLoading = true, projectsError = false) } }
            .onEach { mutableLibraryStatus.update { it.copy(projectsLoading = false, projectsError = false) } }
            .catch { mutableLibraryStatus.update { it.copy(projectsLoading = false, projectsError = true) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val canvases = libraryRevision.flatMapLatest {
        service.availableCanvases
            .onStart { mutableLibraryStatus.update { it.copy(canvasesLoading = true, canvasesError = false) } }
            .onEach { mutableLibraryStatus.update { it.copy(canvasesLoading = false, canvasesError = false) } }
            .catch { mutableLibraryStatus.update { it.copy(canvasesLoading = false, canvasesError = true) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun retryLibrary() { libraryRevision.value += 1 }
    private val canvasNavigation = Channel<String>(Channel.BUFFERED)
    val openedCanvases = canvasNavigation.receiveAsFlow()
    fun createCanvas(title: String) = operation(StudioOperation.CREATE) {
        require(title.trim().isNotBlank())
        canvasNavigation.send(service.createCanvas(title.trim().take(160)))
    }
    val images = libraryRevision.flatMapLatest { service.availableImages
        .onStart { mutableLibraryStatus.update { it.copy(imagesLoading = true, imagesError = false) } }
        .onEach { mutableLibraryStatus.update { it.copy(imagesLoading = false, imagesError = false) } }
        .catch { mutableLibraryStatus.update { it.copy(imagesLoading = false, imagesError = true) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var aiSource: StudioProject? = null
    private var aiRequestId: String? = null
    private var aiTitle: String = "AI draft"
    val imageStudio = WorkspaceImageStudio(viewModelScope, imageService, assets) { result, _ ->
        val source = requireNotNull(aiSource)
        val kept = service.keepAiCopy(source, result, aiTitle)
        aiRequestId?.let { service.uiRequests.mark(it, StudioUiRequestPhase.KEPT, kept.id) }
        attachAfterOperation(kept.id)
    }
    private var observation: Job? = null
    private var inkSession: StudioInkSession? = null
    private var openedId: String? = null
    private var retryOperation: (() -> Unit)? = null
    init {
        (savedState.get<String>("studioProject") ?: initialProjectId)?.let(::open)
        savedState.get<String>("studioPendingOperation")?.let { name ->
            StudioOperation.entries.find { it.name == name }?.let { kind ->
                mutable.update { it.copy(failure = StudioFailure(kind, StudioFailureReason.INTERRUPTED)) }
            }
        }
        viewModelScope.launch { imageStudio.state.collect { image ->
            if (image.result != null) aiRequestId?.let { service.uiRequests.mark(it, StudioUiRequestPhase.PREVIEW_READY) }
        } }
    }

    fun open(id: String) {
        if (mutable.value.busy) return
        observeProject(id, false)
    }
    private fun observeProject(id: String, keepBusy: Boolean) {
        retryOperation = null
        finishInk()
        observation?.cancel(); openedId = id; savedState["studioProject"] = id
        mutable.value = StudioUiState(loading = true, busy = keepBusy, exportedSource = savedState.get<String>("studioExportedSource"))
        observation = viewModelScope.launch {
            try {
                service.projects.observe(id).collect { project ->
                    if (project == null) retryOperation = { open(id) }
                    mutable.value = mutable.value.copy(project = project, loading = false,
                        failure = if (project == null) StudioFailure(StudioOperation.OPEN, canRetry = true)
                            else mutable.value.failure?.takeUnless { it.operation == StudioOperation.OPEN })
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                retryOperation = { open(id) }
                mutable.value = mutable.value.copy(loading = false, failure = StudioFailure(StudioOperation.OPEN, canRetry = true))
            }
        }
    }
    fun closeProject() {
        if (mutable.value.busy) return
        retryOperation = null
        finishInk()
        observation?.cancel(); openedId = null; savedState["studioProject"] = null; mutable.value = StudioUiState()
    }
    fun openSource(sourceId: String, attachmentId: String?, title: String, expectedSourceVersion: String? = null, handoffId: String? = null) {
        if (mutable.value.busy) return
        val target = sourceId + ":" + attachmentId.orEmpty() + ":" + expectedSourceVersion.orEmpty()
        val token = handoffId ?: savedState.get<String>("studioSourceToken")?.takeIf { savedState.get<String>("studioSourceTarget") == target }
            ?: java.util.UUID.randomUUID().toString()
        if (savedState.get<String>("studioSourceToken") != token) savedState["studioSourceObservedVersion"] = null
        savedState["studioSourceToken"] = token; savedState["studioSourceTarget"] = target
        val handoff = target + ":" + token
        if (savedState.get<String>("studioSourceImported") == handoff) return
        operation(StudioOperation.IMPORT) {
            val resuming = savedState.get<String>("studioSourcePending") == handoff
            val existing = if (resuming) savedState.get<String>("studioProject")?.let { service.projects.get(it) } else null
            val project = if (attachmentId == null && existing == null) {
                val source = requireNotNull(files.getFile(com.halo.app.data.notebook.WorkspaceObjectId(sourceId)))
                val observed = expectedSourceVersion ?: savedState.get<String>("studioSourceObservedVersion") ?: requireNotNull(source.version)
                savedState["studioSourceObservedVersion"] = observed
                service.createFromImage(title, sourceId, observed, token)
            } else {
                val target = existing ?: service.projects.create(title)
                savedState["studioSourcePending"] = handoff; savedState["studioProject"] = target.id
                if (attachmentId != null) service.importAttachment(target.id, target.revision, sourceId, attachmentId, ensureAbsent = true)
                else service.importImage(target.id, target.revision, sourceId, ensureAbsent = true, expectedSourceVersion = expectedSourceVersion)
            }
            openedId = project.id; savedState["studioProject"] = project.id; savedState["studioSourcePending"] = null
            savedState["studioSourceImported"] = handoff
            attachAfterOperation(project.id)
        }
    }
    fun create(title: String, preset: StudioPreset = StudioPreset.BLANK) = operation(StudioOperation.CREATE) {
        val project = service.projects.create(title, StudioTemplates.document(preset, title))
        attachAfterOperation(project.id)
    }
    private fun attachAfterOperation(id: String) {
        // Preserve the current operation while changing observation; provider-save callbacks have their own owner.
        observeProject(id, mutable.value.busy)
    }
    fun command(expectedRevision: Long, command: StudioCommand) {
        val project = visible(expectedRevision) ?: return
        operation(StudioOperation.EDIT, retryable = true) { service.projects.apply(project.id, project.revision, command) }
    }
    fun edit(expectedRevision: Long, action: String, fields: Map<String, Any?> = emptyMap()) {
        val project = visible(expectedRevision) ?: return
        operation(StudioOperation.EDIT, retryable = true) { service.edit(project.id, project.revision, action, fields) }
    }
    fun save() {
        val project = mutable.value.project ?: return
        operation(StudioOperation.SAVE, retryable = true) { service.projects.save(project.id, project.revision) }
    }
    fun discardDraft(expectedProjectId: String, expectedRevision: Long) {
        val project = mutable.value.project ?: return
        if (mutable.value.busy) return
        if (project.id != expectedProjectId || project.revision != expectedRevision) {
            reportError(StudioOperation.DISCARD, StudioFailureReason.CHANGED); return
        }
        operation(StudioOperation.DISCARD, retryable = true) { service.projects.discardDraft(project.id, project.revision) }
    }
    fun saveAs(title: String) {
        val project = mutable.value.project ?: return
        operation(StudioOperation.COPY) { attachAfterOperation(service.projects.saveAs(project.id, project.revision, title).id) }
    }
    fun rename(title: String) {
        val project = mutable.value.project ?: return
        operation(StudioOperation.RENAME, retryable = true) { service.projects.rename(project.id, project.revision, title) }
    }
    fun versions() {
        val project = mutable.value.project ?: return
        mutable.value = mutable.value.copy(versionsLoaded = false)
        operation(StudioOperation.HISTORY, retryable = true) { mutable.value = mutable.value.copy(versions = service.projects.versions(project.id), versionsLoaded = true) }
    }
    fun restore(version: Long, expectedRevision: Long? = null, expectedProjectId: String? = null): Boolean {
        val project = mutable.value.project ?: return false
        if (mutable.value.busy) return false
        if (expectedProjectId != null && project.id != expectedProjectId || expectedRevision != null && project.revision != expectedRevision) {
            reportError(StudioOperation.RESTORE, StudioFailureReason.CHANGED); return false
        }
        operation(StudioOperation.RESTORE, retryable = true) {
            service.projects.restoreVersion(project.id, project.revision, version)
            mutable.value = mutable.value.copy(versions = emptyList(), versionsLoaded = false)
        }
        return true
    }
    fun dismissVersions() { mutable.value = mutable.value.copy(versions = emptyList(), versionsLoaded = false) }
    fun importExisting(id: String, sourceVersion: String) {
        val project = mutable.value.project ?: return
        operation(StudioOperation.IMPORT) { service.importImage(project.id, project.revision, id, expectedSourceVersion = sourceVersion) }
    }
    fun importAttachment(noteId: String, attachmentId: String) {
        val project = mutable.value.project ?: return
        operation(StudioOperation.IMPORT) { service.importAttachment(project.id, project.revision, noteId, attachmentId) }
    }
    fun importUri(uri: Uri) {
        val project = mutable.value.project ?: return
        operation(StudioOperation.IMPORT) {
            val file = files.importFile(uri)
            service.importImage(project.id, project.revision, file.objectId.value, expectedSourceVersion = requireNotNull(file.version))
        }
    }
    fun export(format: CanvasImageFormat) {
        val project = mutable.value.project ?: return
        operation(StudioOperation.EXPORT) {
            val output = service.export(project.id, project.revision, format)
            val sourceReceipt = "${project.id}:${project.revision}"
            savedState["studioExportedSource"] = sourceReceipt
            mutable.value = mutable.value.copy(output = output, exportedSource = sourceReceipt)
        }
    }
    fun insertOutput(canvasId: String, output: WorkspaceFile, onInserted: (String) -> Unit) = operation(StudioOperation.INSERT) {
        val nodeId = service.insertOutput(canvasId, output.objectId.value, requireNotNull(output.version))
        onInserted(nodeId)
    }
    fun dismissOutput() { mutable.value = mutable.value.copy(output = null) }
    internal fun startInk(expectedRevision: Long, layerId: String): StudioInkSession? {
        val project = visible(expectedRevision) ?: return null
        finishInk()
        return StudioInkSession(project, layerId, service.projects, viewModelScope, { updated ->
            val current = mutable.value.project
            if (current?.id == updated.id && current.revision <= updated.revision)
                mutable.value = mutable.value.copy(project = updated)
        }, { mutable.value = mutable.value.copy(failure = StudioFailure(StudioOperation.EDIT, StudioFailureReason.CHANGED)) }).also { inkSession = it; service.attachInkSelectionOwner(project.id, it.ownerToken) }
    }
    internal fun saveInkCopy(session: StudioInkSession, title: String) = operation(StudioOperation.COPY) {
        attachAfterOperation(service.projects.create(title, session.draft(), session.initial.id, session.initial.revision).id)
    }
    internal fun finishInk() { inkSession?.let { it.finish(); service.detachInkSelectionOwner(it.ownerToken) }; inkSession = null }
    fun retry() { retryOperation?.invoke() ?: openedId?.let(::open) }
    private fun visible(expectedRevision: Long): StudioProject? {
        val project = mutable.value.project
        if (project?.revision == expectedRevision) return project
        mutable.value = mutable.value.copy(failure = StudioFailure(StudioOperation.EDIT, StudioFailureReason.CHANGED))
        return null
    }
    fun prepareAi(project: StudioProject, title: String) {
        if (visible(project.revision)?.id != project.id) return
        operation(StudioOperation.AI_REVIEW) { prepareAiReference(project, title, null, null) }
    }
    fun acceptUiRequest(request: StudioUiRequest, title: String) {
        if (mutable.value.busy) { service.uiRequests.mark(request.id, StudioUiRequestPhase.FAILED); return }
        operation(StudioOperation.AI_REVIEW) {
            try {
                val project = requireNotNull(service.projects.get(request.projectId))
                if (request.kind == StudioUiRequestKind.AI_REVIEW && project.revision != request.revision) {
                    service.uiRequests.mark(request.id, StudioUiRequestPhase.STALE); error("Project changed")
                }
                if (request.kind == StudioUiRequestKind.OPEN) {
                    attachAfterOperation(project.id); service.acknowledgeAttachment(project.id)
                } else {
                    attachAfterOperation(project.id)
                    prepareAiReference(project, title, request.id, request.prompt, request.provenance)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { service.uiRequests.mark(request.id, StudioUiRequestPhase.FAILED); throw failure }
        }
    }
    private suspend fun prepareAiReference(project: StudioProject, title: String, requestId: String?, prompt: String?,
        provenance: com.halo.sdk.provider.RemoteDispatchProvenance? = null) {
        val reference = service.aiReference(project)
        aiSource = project; aiRequestId = requestId
        aiTitle = (project.title.take(80) + " · " + title).take(120)
        imageStudio.prepareReference(reference, "image/png", project.id, project.title, provenance)
        if (prompt != null) imageStudio.prompt(prompt)
        mutable.value = mutable.value.copy(aiReview = true)
        requestId?.let { service.uiRequests.mark(it, StudioUiRequestPhase.REVIEW_READY) }
    }
    fun dismissAi() {
        imageStudio.discardDraft()
        aiRequestId?.let { service.uiRequests.mark(it, StudioUiRequestPhase.DISCARDED) }
        aiRequestId = null; aiSource = null
        mutable.value = mutable.value.copy(aiReview = false)
    }
    fun reportError(operation: StudioOperation = StudioOperation.EDIT,
        reason: StudioFailureReason = StudioFailureReason.FAILED) {
        retryOperation = null
        mutable.update { it.copy(failure = StudioFailure(operation, reason)) }
        val id = mutable.value.project?.id ?: return
        viewModelScope.launch {
            val retained = readProject(id)
            mutable.update { current ->
                if (current.project?.id == id && current.failure?.operation == operation)
                    current.copy(failure = current.failure?.copy(retained = studioRetainedWork(retained)))
                else current
            }
        }
    }
    fun dismissError() { retryOperation = null; mutable.update { it.copy(failure = null) } }
    private suspend fun readProject(id: String): StudioProject? = try { service.projects.get(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }

    private fun operation(kind: StudioOperation, retryable: Boolean = false, expected: StudioProject? = null, block: suspend () -> Unit) {
        if (mutable.value.busy) return
        val original = expected ?: mutable.value.project
        retryOperation = null
        savedState["studioPendingOperation"] = kind.name
        mutable.update { it.copy(busy = true, failure = null, operation = kind) }
        viewModelScope.launch {
            try {
                if (expected != null) {
                    val observed = readProject(expected.id)
                    if (observed == null || observed.revision != expected.revision) {
                        mutable.update { it.copy(failure = StudioFailure(kind, StudioFailureReason.CHANGED, studioRetainedWork(observed))) }
                        savedState["studioPendingOperation"] = null
                        return@launch
                    }
                }
                block()
                savedState["studioPendingOperation"] = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val retained = original?.id?.let { readProject(it) }
                val changed = retained != null && original.revision != retained.revision
                val retry = retryable && retained != null && !changed
                if (retry) retryOperation = {
                    // Use the original callback/revision. A later owner change fails closed.
                    if (mutable.value.project?.id == original?.id)
                        operation(kind, retryable, expected = original, block = block)
                }
                mutable.update { it.copy(failure = StudioFailure(kind,
                    when {
                        changed -> StudioFailureReason.CHANGED
                        failure is SecurityException -> StudioFailureReason.ACCESS
                        else -> StudioFailureReason.FAILED
                    }, studioRetainedWork(retained), retry)) }
                savedState["studioPendingOperation"] = null
            } finally { mutable.update { it.copy(busy = false, operation = null) } }
        }
    }
    class Factory(private val service: StudioService, private val files: WorkspaceFileRepository,
        private val imageService: com.halo.app.ai.WorkspaceImageService, private val assets: NotebookAssetRepository,
        private val projectId: String? = null, private val savedState: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle()) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StudioViewModel::class.java))
            return StudioViewModel(service, files, imageService, assets, projectId, savedState) as T
        }
    }
}
