package com.halo.app.ui.screens.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.halo.sdk.runtime.study.*
import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.*
import com.halo.app.study.*
import com.halo.app.study.StudyUseCases
import com.halo.app.study.StudyStartResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StudyLibrarySummary(val sources: Int, val cards: Int, val replies: Int,
    val learningStage: LearningStage? = null)

data class StudyHomeReadState(val loading: Set<String> = setOf("candidates", "sessions", "summaries", "due"),
    val failed: Set<String> = emptySet(), val selectionRestoreFailed: Boolean = false) {
    val materialReady: Boolean get() = "preview" !in loading && "preview" !in failed && !selectionRestoreFailed
    val libraryReady: Boolean get() = "sessions" !in loading && "sessions" !in failed
}

class StudyHomeViewModel(private val study: StudyUseCases, private val workspace: WorkspaceRepository? = null,
    private val files: WorkspaceFileRepository? = null, private val resolver: StudySourceResolver? = null,
    private val lessons: StudyLessonStore? = null,
    private val executionStorage: com.halo.sdk.runtime.Storage? = null,
    private val versionReader: (suspend (String) -> String?)? = null,
    private val savedState: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val restoredSelection = runCatching { StudySetupSelectionCodec.decode(savedState["study_material_selection"]) }
    private val mutableReads = MutableStateFlow(StudyHomeReadState(selectionRestoreFailed = restoredSelection.isFailure || savedState.get<Boolean>("study_seed_failed") == true))
    val reads = mutableReads.asStateFlow()
    private val readGeneration = MutableStateFlow(0)
    private fun reading(key: String) { mutableReads.update { it.copy(loading = it.loading + key, failed = it.failed - key) } }
    private fun readFinished(key: String, failed: Boolean = false) { mutableReads.update {
        it.copy(loading = it.loading - key, failed = if (failed) it.failed + key else it.failed - key)
    } }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun <T> recoverableRead(key: String, initial: T, source: () -> Flow<T>): StateFlow<T> = readGeneration.flatMapLatest {
        flow { emitAll(source()) }.onStart { reading(key) }.onEach { readFinished(key) }
            .catch { error -> if (error is CancellationException) throw error else readFinished(key, failed = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
    val candidates = recoverableRead("candidates", emptyList<WorkspaceObject>()) {
        workspace?.observe(WorkspaceQuery(types = setOf(WorkspaceObjectType.NOTE, WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA, WorkspaceObjectType.ARTIFACT))) ?: flowOf(emptyList())
    }
    private val selected = MutableStateFlow(restoredSelection.getOrDefault(emptyList()).map { it.id })
    val selectedIds = selected.asStateFlow()
    private val preview = MutableStateFlow<List<ResolvedStudySource>>(emptyList())
    val previews = preview.asStateFlow()
    private val expectedContextVersions = restoredSelection.getOrDefault(emptyList()).mapNotNull { entry -> entry.contextVersion?.let { entry.id to it } }.toMap().toMutableMap()
    private val sourceReferences = restoredSelection.getOrDefault(emptyList()).mapNotNull { entry -> entry.source?.let { entry.id to it } }.toMap().toMutableMap()
    private var previewJob: kotlinx.coroutines.Job? = null
    private var previewGeneration = 0L
    private fun keepSelection() {
        savedState["study_material_selection"] = StudySetupSelectionCodec.encode(selected.value.map {
            StudySetupSelection(it, expectedContextVersions[it], sourceReferences[it])
        })
    }
    fun retryReads() { readGeneration.value++; refreshPreview() }
    /** A route source seeds this setup once; deliberate removals survive recreation. */
    fun selectInitialSource(id: String, expectedVersion: String? = null, sourceReference: StudySessionSource? = null,
        exactReferenceRequired: Boolean = false) {
        if (savedState.get<Boolean>("study_seed_consumed") == true) return
        savedState["study_seed_consumed"] = true
        if (savedState.contains("study_material_selection")) return
        if (exactReferenceRequired && sourceReference == null) {
            savedState["study_seed_failed"] = true
            mutableReads.update { it.copy(selectionRestoreFailed = true) }
            return
        }
        select(id, expectedVersion, sourceReference)
    }
    fun select(id: String, expectedVersion: String? = null, sourceReference: StudySessionSource? = null) {
        if (working.value || id in selected.value || selected.value.size >= StudySourceLimits.MAX_SOURCES) return
        if (sourceReference != null) {
            if (sourceReference.objectId != id || sourceReference.revision == null) {
                message.value = StudyUnavailableReason.SOURCE_CHANGED.explanation
                return
            }
            sourceReferences[id] = sourceReference
        }
        if (expectedVersion != null) expectedContextVersions[id] = expectedVersion
        selected.value = selected.value + id
        keepSelection()
        refreshPreview()
    }
    fun remove(id: String) { if (!working.value) { selected.value = selected.value - id; expectedContextVersions.remove(id); sourceReferences.remove(id); keepSelection(); refreshPreview() } }
    /** Clear an explicitly requested new lesson's material; never clear material
     * preselected by an external Study entry point on first composition.
     */
    fun resetDraft() {
        if (working.value) return
        selected.value = emptyList()
        savedState["study_seed_failed"] = false
        expectedContextVersions.clear()
        sourceReferences.clear()
        previewJob?.cancel(); previewGeneration++
        preview.value = emptyList()
        mutableReads.update { it.copy(selectionRestoreFailed = false, loading = it.loading - "preview", failed = it.failed - "preview") }
        keepSelection()
        message.value = null
    }
    private fun refreshPreview() {
        previewJob?.cancel()
        val generation = ++previewGeneration
        val ids = selected.value
        val expected = expectedContextVersions.toMap()
        val references = sourceReferences.toMap()
        reading("preview")
        previewJob = viewModelScope.launch {
            try {
                val sources = ids.map { id ->
                    val version = expected[id]
                    if (version != null && versionReader?.invoke(id) != version) ResolvedStudySource.Unsupported(id, "Material", "Source changed. Select it again.")
                    else (references[id]?.textAnchor?.let { resolver?.resolveTextAnchor(id, references.getValue(id).revision!!, it) }
                        ?: resolver?.resolve(id, ""))?.let { source ->
                        if ((version != null && versionReader?.invoke(id) != version) ||
                            references[id]?.let { it.revision != source.revision } == true)
                            ResolvedStudySource.Unsupported(id, source.title, "Source changed. Select it again.") else source
                    } ?: ResolvedStudySource.Unsupported(id, "Material", "Source unavailable")
                }
                if (generation == previewGeneration && selected.value == ids) {
                    sources.filterNot { it is ResolvedStudySource.Unsupported }.forEach { source ->
                        source.revision?.let { sourceReferences.putIfAbsent(source.objectId, StudySessionSource(source.objectId, it)) }
                    }
                    keepSelection()
                    preview.value = sources
                    readFinished("preview")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == previewGeneration) readFinished("preview", failed = true) }
        }
    }
    fun importMaterial(uri: android.net.Uri) = viewModelScope.launch {
        if (working.value || selected.value.size >= StudySourceLimits.MAX_SOURCES) return@launch
        try { files?.importFile(uri)?.let { select(it.objectId.value) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message.value = "File import failed. Choose an accessible file." }
    }
    val sessions = recoverableRead("sessions", emptyList<com.halo.app.data.study.StudySessionRecord>()) { study.observeActiveSessions() }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val summaries = recoverableRead("summaries", emptyMap<String, StudyLibrarySummary>()) { sessions.flatMapLatest { list ->
        if(list.isEmpty()) flowOf(emptyMap()) else combine(list.map { session ->
            combine(study.observePracticeItems(session.id),lessons?.observeSavedResponseCount(session.id) ?: flowOf(0)) { cards,responses ->
                session.id to StudyLibrarySummary(session.sourceObjectIds.size, cards.size, responses,
                    lessons?.loadJourney(session.id)?.stage)
            }
        }) { entries -> entries.toMap() }
    } }
    val due = recoverableRead("due", emptyList<com.halo.app.data.study.StudyPracticeItem>()) { study.observeDuePracticeItems(200) }
    private var startJob: kotlinx.coroutines.Job? = null
    private var startVersion = 0L
    fun cancelStart() {
        startVersion++; startJob?.cancel(); startJob = null; working.value = false
        while (events.tryReceive().isSuccess) { /* Cancel queued navigation as well as pending creation. */ }
    }
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    private val message = MutableStateFlow<String?>(null)
    val notice = message.asStateFlow()
    private val events = Channel<String>(Channel.BUFFERED)
    val navigation = events.receiveAsFlow()
    /** Child execution follows the existing parent grant; starting never grants cloud access. */
    fun startChildLesson(topic: String, cloudGranted: Boolean) = start(topic, "",
        level = StudyLearnerLevel.PRIMARY, style = StudySupportStyle.GUIDED,
        policy = StudyKnowledgePolicy.SOURCE_PLUS_GENERAL,
        executionChoice = if (cloudGranted) "cloud" else "phone")

    fun start(topic: String, language: String, assignment: String = "", strongest: String = "",
        level: StudyLearnerLevel = StudyLearnerLevel.ADULT, style: StudySupportStyle = StudySupportStyle.GUIDED,
        policy: StudyKnowledgePolicy = StudyKnowledgePolicy.SOURCE_ONLY, executionChoice: String? = null) {
        if (working.value || (topic.isBlank() && selected.value.isEmpty())) return
        if (!mutableReads.value.materialReady) {
            message.value = if ("preview" in mutableReads.value.loading) MATERIAL_LOADING else MATERIAL_NOT_READY
            return
        }
        if (selected.value.any { id -> preview.value.none {
                it.objectId == id && it !is ResolvedStudySource.Unsupported && it.revision != null
            } }) {
            message.value = StudyUnavailableReason.SOURCE_CHANGED.explanation
            return
        }
        if (executionChoice !in setOf("phone", "cloud")) {
            message.value = "Choose where halo should think before starting."
            return
        }
        val version = ++startVersion
        working.value = true
        startJob = viewModelScope.launch {
            try {
                if (expectedContextVersions.any { (id, expected) -> versionReader?.invoke(id) != expected ||
                    preview.value.none { it.objectId == id && it !is ResolvedStudySource.Unsupported && it.revision != null } }) {
                    message.value = StudyUnavailableReason.SOURCE_CHANGED.explanation
                    return@launch
                }
                val expected = preview.value.mapNotNull { source -> source.revision?.takeIf { source !is ResolvedStudySource.Unsupported }?.let { source.objectId to it } }.toMap()
                val languages = StudyLanguagePreferences(assignment.takeIf { it.isNotBlank() }, language.takeIf { it.isNotBlank() }, strongest.takeIf { it.isNotBlank() })
                val result = if (selected.value.isEmpty()) study.startTopic(topic, language.trim().takeIf { it.isNotBlank() })
                    else study.startMaterial(selected.value, languages, topic.takeIf { it.isNotBlank() }, level, style, policy,
                        expectedVersions = expected, sourceReferences = sourceReferences.toMap())
                when (result) {
                    is StudyStartResult.Started -> {
                        if (executionChoice in setOf("phone", "cloud"))
                            executionStorage?.putString("study_execution_${result.session.id}", executionChoice!!)
                        if (selected.value.isEmpty()) {
                            check(study.setPreferences(result.session.id, languages, topic))
                            check(study.updateSetup(result.session.id, level, style, StudyKnowledgePolicy.SOURCE_PLUS_GENERAL))
                        }
                        if (version == startVersion) events.send(result.session.id)
                    }
                    is StudyStartResult.Unavailable -> message.value = result.reason.explanation
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message.value = "Couldn't start yet. Your topic is still here." }
            finally { if (version == startVersion) { working.value = false; startJob = null } }
        }
    }
    init { if (selected.value.isNotEmpty()) refreshPreview() }

    companion object {
        internal const val MATERIAL_LOADING = "study-material-loading"
        internal const val MATERIAL_NOT_READY = "study-material-not-ready"
    }
    class Factory(private val study: StudyUseCases, private val workspace: WorkspaceRepository? = null,
        private val files: WorkspaceFileRepository? = null, private val resolver: StudySourceResolver? = null,
        private val lessons: StudyLessonStore? = null,
        private val executionStorage: com.halo.sdk.runtime.Storage? = null,
        private val versionReader: (suspend (String) -> String?)? = null) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            StudyHomeViewModel(study, workspace, files, resolver, lessons, executionStorage, versionReader, extras.createSavedStateHandle()) as T
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StudyHomeViewModel(study, workspace, files, resolver,lessons, executionStorage, versionReader) as T
    }
}
