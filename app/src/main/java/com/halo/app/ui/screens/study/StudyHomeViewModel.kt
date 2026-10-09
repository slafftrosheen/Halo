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

class StudyHomeViewModel(private val study: StudyUseCases, private val workspace: WorkspaceRepository? = null,
    private val files: WorkspaceFileRepository? = null, private val resolver: StudySourceResolver? = null,
    private val lessons: StudyLessonStore? = null) : ViewModel() {
    val candidates = (workspace?.observe(WorkspaceQuery(types = setOf(WorkspaceObjectType.NOTE, WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA, WorkspaceObjectType.ARTIFACT))) ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val selected = MutableStateFlow<List<String>>(emptyList())
    val selectedIds = selected.asStateFlow()
    private val preview = MutableStateFlow<List<ResolvedStudySource>>(emptyList())
    val previews = preview.asStateFlow()
    fun select(id: String) {
        if (working.value || id in selected.value || selected.value.size >= StudySourceLimits.MAX_SOURCES) return
        selected.value = selected.value + id
        refreshPreview()
    }
    fun remove(id: String) { if (!working.value) { selected.value = selected.value - id; refreshPreview() } }
    private fun refreshPreview() = viewModelScope.launch {
        val ids = selected.value
        val sources = ids.mapNotNull { resolver?.resolve(it, "") }
        if (selected.value == ids) preview.value = sources
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
                session.id to "${session.sourceObjectIds.size} sources · ${cards.size} cards · $responses saved replies"
            }
        }) { entries -> entries.toMap() }
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyMap())
    val due = study.observeDuePracticeItems(200).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    private val message = MutableStateFlow<String?>(null)
    val notice = message.asStateFlow()
    private val events = Channel<String>(Channel.BUFFERED)
    val navigation = events.receiveAsFlow()
    fun start(topic: String, language: String, assignment: String = "", strongest: String = "",
        level: StudyLearnerLevel = StudyLearnerLevel.ADULT, style: StudySupportStyle = StudySupportStyle.GUIDED,
        policy: StudyKnowledgePolicy = StudyKnowledgePolicy.SOURCE_ONLY) {
        if (working.value || (topic.isBlank() && selected.value.isEmpty())) return
        working.value = true
        viewModelScope.launch {
            try {
                val languages = StudyLanguagePreferences(assignment.takeIf { it.isNotBlank() }, language.takeIf { it.isNotBlank() }, strongest.takeIf { it.isNotBlank() })
                val result = if (selected.value.isEmpty()) study.startTopic(topic, language.trim().takeIf { it.isNotBlank() })
                    else study.startMaterial(selected.value, languages, topic.takeIf { it.isNotBlank() }, level, style, policy)
                when (result) {
                    is StudyStartResult.Started -> {
                        if (selected.value.isEmpty()) { study.setPreferences(result.session.id, languages, topic); study.updateSetup(result.session.id, level, style, StudyKnowledgePolicy.SOURCE_PLUS_GENERAL) }
                        events.send(result.session.id)
                    }
                    is StudyStartResult.Unavailable -> message.value = result.reason.explanation
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message.value = "Couldn't start yet. Your topic is still here." }
            finally { working.value = false }
        }
    }
    class Factory(private val study: StudyUseCases, private val workspace: WorkspaceRepository? = null,
        private val files: WorkspaceFileRepository? = null, private val resolver: StudySourceResolver? = null,
        private val lessons: StudyLessonStore? = null) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StudyHomeViewModel(study, workspace, files, resolver,lessons) as T
    }
}
