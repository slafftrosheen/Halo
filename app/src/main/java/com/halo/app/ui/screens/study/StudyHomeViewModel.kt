package com.halo.app.ui.screens.study

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

class StudyHomeViewModel(private val study: StudyUseCases, private val workspace: WorkspaceRepository? = null,
    private val files: WorkspaceFileRepository? = null, private val resolver: StudySourceResolver? = null,
    private val lessons: StudyLessonStore? = null,
    private val executionStorage: com.halo.sdk.runtime.Storage? = null,
    private val versionReader: (suspend (String) -> String?)? = null) : ViewModel() {
    val candidates = (workspace?.observe(WorkspaceQuery(types = setOf(WorkspaceObjectType.NOTE, WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA, WorkspaceObjectType.ARTIFACT))) ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val selected = MutableStateFlow<List<String>>(emptyList())
    val selectedIds = selected.asStateFlow()
    private val preview = MutableStateFlow<List<ResolvedStudySource>>(emptyList())
    val previews = preview.asStateFlow()
    private val expectedContextVersions = mutableMapOf<String, String>()
    private val sourceReferences = mutableMapOf<String, StudySessionSource>()
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
        refreshPreview()
    }
    fun remove(id: String) { if (!working.value) { selected.value = selected.value - id; expectedContextVersions.remove(id); sourceReferences.remove(id); refreshPreview() } }
    /** Clear an explicitly requested new lesson's material; never clear material
     * preselected by an external Study entry point on first composition.
     */
    fun resetDraft() {
        if (working.value) return
        selected.value = emptyList()
        expectedContextVersions.clear()
        sourceReferences.clear()
        preview.value = emptyList()
        message.value = null
    }
    private fun refreshPreview() = viewModelScope.launch {
        val ids = selected.value
        val expected = expectedContextVersions.toMap()
        val references = sourceReferences.toMap()
        val sources = ids.mapNotNull { id ->
            val version = expected[id]
            if (version != null && versionReader?.invoke(id) != version) ResolvedStudySource.Unsupported(id, "Material", "Source changed. Select it again.")
            else (references[id]?.textAnchor?.let { resolver?.resolveTextAnchor(id, references.getValue(id).revision!!, it) }
                ?: resolver?.resolve(id, ""))?.let { source ->
                if ((version != null && versionReader?.invoke(id) != version) ||
                    references[id]?.let { it.revision != source.revision } == true)
                    ResolvedStudySource.Unsupported(id, source.title, "Source changed. Select it again.") else source
            }
        }
        if (selected.value == ids && sourceReferences == references) preview.value = sources
    }
    fun importMaterial(uri: android.net.Uri) = viewModelScope.launch {
        if (working.value || selected.value.size >= StudySourceLimits.MAX_SOURCES) return@launch
        try { files?.importFile(uri)?.let { select(it.objectId.value) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message.value = "File import failed. Choose an accessible file." }
    }
    val sessions = study.observeActiveSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val summaries = sessions.flatMapLatest { list ->
        if(list.isEmpty()) flowOf(emptyMap()) else combine(list.map { session ->
            combine(study.observePracticeItems(session.id),lessons?.observeSavedResponseCount(session.id) ?: flowOf(0)) { cards,responses ->
                session.id to StudyLibrarySummary(session.sourceObjectIds.size, cards.size, responses,
                    lessons?.loadJourney(session.id)?.stage)
            }
        }) { entries -> entries.toMap() }
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyMap())
    val due = study.observeDuePracticeItems(200).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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
    class Factory(private val study: StudyUseCases, private val workspace: WorkspaceRepository? = null,
        private val files: WorkspaceFileRepository? = null, private val resolver: StudySourceResolver? = null,
        private val lessons: StudyLessonStore? = null,
        private val executionStorage: com.halo.sdk.runtime.Storage? = null,
        private val versionReader: (suspend (String) -> String?)? = null) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StudyHomeViewModel(study, workspace, files, resolver,lessons, executionStorage, versionReader) as T
    }
}
