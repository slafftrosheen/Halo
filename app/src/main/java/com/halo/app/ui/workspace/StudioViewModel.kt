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

data class StudioUiState(val project: StudioProject? = null, val loading: Boolean = false,
    val busy: Boolean = false, val aiReview: Boolean = false, val error: Boolean = false, val output: WorkspaceFile? = null,
    val versions: List<StudioProjectVersion> = emptyList())

/** Owns presentation/session attachment only. Every edit is delegated to the shared data owner. */
class StudioViewModel(val service: StudioService, private val files: WorkspaceFileRepository,
    private val imageService: com.halo.app.ai.WorkspaceImageService, private val assets: NotebookAssetRepository,
    initialProjectId: String? = null, private val savedState: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle()) : ViewModel() {
    private val mutable = MutableStateFlow(StudioUiState())
    val state = mutable.asStateFlow()
    val projects = service.projects.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val images = service.availableImages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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
    init {
        (savedState.get<String>("studioProject") ?: initialProjectId)?.let(::open)
        viewModelScope.launch { imageStudio.state.collect { image ->
            if (image.result != null) aiRequestId?.let { service.uiRequests.mark(it, StudioUiRequestPhase.PREVIEW_READY) }
        } }
    }

    fun open(id: String) {
        if (mutable.value.busy) return
        observeProject(id, false)
    }
    private fun observeProject(id: String, keepBusy: Boolean) {
        finishInk()
        observation?.cancel(); openedId = id; savedState["studioProject"] = id
        mutable.value = StudioUiState(loading = true, busy = keepBusy)
        observation = viewModelScope.launch {
            try {
                service.projects.observe(id).collect { project ->
                    mutable.value = mutable.value.copy(project = project, loading = false, error = project == null)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(loading = false, error = true) }
        }
    }
    fun closeProject() {
        if (mutable.value.busy) return
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
        operation {
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
    fun create(title: String, preset: StudioPreset = StudioPreset.BLANK) = operation {
        val project = service.projects.create(title, StudioTemplates.document(preset, title))
        attachAfterOperation(project.id)
    }
    private fun attachAfterOperation(id: String) {
        // Preserve the current operation while changing observation; provider-save callbacks have their own owner.
        observeProject(id, mutable.value.busy)
    }
    fun command(expectedRevision: Long, command: StudioCommand) {
        val project = visible(expectedRevision) ?: return
        operation { service.projects.apply(project.id, project.revision, command) }
    }
    fun edit(expectedRevision: Long, action: String, fields: Map<String, Any?> = emptyMap()) {
        val project = visible(expectedRevision) ?: return
        operation { service.edit(project.id, project.revision, action, fields) }
    }
    fun save() {
        val project = mutable.value.project ?: return
        operation { service.projects.save(project.id, project.revision) }
    }
    fun discardDraft() {
        val project = mutable.value.project ?: return
        operation { service.projects.discardDraft(project.id, project.revision) }
    }
    fun saveAs(title: String) {
        val project = mutable.value.project ?: return
        operation { attachAfterOperation(service.projects.saveAs(project.id, project.revision, title).id) }
    }
    fun rename(title: String) {
        val project = mutable.value.project ?: return
        operation { service.projects.rename(project.id, project.revision, title) }
    }
    fun versions() {
        val project = mutable.value.project ?: return
        operation { mutable.value = mutable.value.copy(versions = service.projects.versions(project.id)) }
    }
    fun restore(version: Long) {
        val project = mutable.value.project ?: return
        operation {
            service.projects.restoreVersion(project.id, project.revision, version)
            mutable.value = mutable.value.copy(versions = emptyList())
        }
    }
    fun dismissVersions() { mutable.value = mutable.value.copy(versions = emptyList()) }
    fun importExisting(id: String, sourceVersion: String) {
        val project = mutable.value.project ?: return
        operation { service.importImage(project.id, project.revision, id, expectedSourceVersion = sourceVersion) }
    }
    fun importAttachment(noteId: String, attachmentId: String) {
        val project = mutable.value.project ?: return
        operation { service.importAttachment(project.id, project.revision, noteId, attachmentId) }
    }
    fun importUri(uri: Uri) {
        val project = mutable.value.project ?: return
        operation {
            val file = files.importFile(uri)
            service.importImage(project.id, project.revision, file.objectId.value, expectedSourceVersion = requireNotNull(file.version))
        }
    }
    fun export(format: CanvasImageFormat) {
        val project = mutable.value.project ?: return
        operation {
            val output = service.export(project.id, project.revision, format)
            mutable.value = mutable.value.copy(output = output)
        }
    }
    fun insertOutput(canvasId: String, output: WorkspaceFile, onInserted: (String) -> Unit) = operation {
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
        }, { mutable.value = mutable.value.copy(error = true) }).also { inkSession = it; service.attachInkSelectionOwner(project.id, it.ownerToken) }
    }
    internal fun saveInkCopy(session: StudioInkSession, title: String) = operation {
        attachAfterOperation(service.projects.create(title, session.draft(), session.initial.id, session.initial.revision).id)
    }
    internal fun finishInk() { inkSession?.let { it.finish(); service.detachInkSelectionOwner(it.ownerToken) }; inkSession = null }
    fun retry() { openedId?.let(::open) }
    private fun visible(expectedRevision: Long): StudioProject? {
        val project = mutable.value.project
        if (project?.revision == expectedRevision) return project
        mutable.value = mutable.value.copy(error = true)
        return null
    }
    fun prepareAi(project: StudioProject, title: String) {
        if (visible(project.revision)?.id != project.id) return
        operation { prepareAiReference(project, title, null, null) }
    }
    fun acceptUiRequest(request: StudioUiRequest, title: String) {
        if (mutable.value.busy) { service.uiRequests.mark(request.id, StudioUiRequestPhase.FAILED); return }
        operation {
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
    fun reportError() { mutable.value = mutable.value.copy(error = true) }
    fun dismissError() { mutable.value = mutable.value.copy(error = false) }
    private fun operation(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = false)
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = true) }
            finally { mutable.value = mutable.value.copy(busy = false) }
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
