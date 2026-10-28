package com.halo.app.study

import com.halo.app.ui.screens.study.StudyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class ActiveStudySource(val objectId: String, val version: String?, val title: String, val stale: Boolean,
    val kind: String = "TEXT", val pageCount: Int? = null)
data class ActiveStudySnapshot(val sessionId: String, val sourceVersion: String, val stage: String,
    val busy: Boolean, val hasDraft: Boolean, val finished: Boolean, val reviewIntensity: String,
    val sources: List<ActiveStudySource>, val candidates: List<ActiveStudySource>,
    val attemptIds: List<String>, val turnIds: List<String>, val practiceIds: List<String>,
    val latestTurn: String?, val notice: String?, val exportedNoteId: String?, val restartedSessionId: String?,
    internal val uiVersion: String = sourceVersion, val knowledgeProbeSkipped: Boolean = false,
    val explanationDepth: String = StudyExplanationDepth.BALANCED.name,
    val journeyStage: String = "DISCOVER", val completedHints: Int = 0,
    val hasPriorKnowledge: Boolean = false, val hasSavedReflection: Boolean = false,
    val activeVisualId: String? = null, val pdfPages: Map<String, Int> = emptyMap(),
    val readerOpen: Boolean = false, val readerPlaying: Boolean = false)
data class ActiveStudyCommand(val action: String, val text: String? = null, val stage: String? = null,
    val intensity: String? = null, val sourceVersions: Map<String, String> = emptyMap(), val depth: String? = null,
    val objectId: String? = null, val page: Int? = null, val turnId: String? = null)
data class ActiveStudyDispatch(val requestId: String, val sessionId: String, val action: String)
interface ActiveStudyControl {
    suspend fun current(): ActiveStudySnapshot
    suspend fun dispatch(sessionId: String, sourceVersion: String, command: ActiveStudyCommand,
        admitted: () -> Boolean): ActiveStudyDispatch
}

/** Ephemeral attached native lesson owner. No tutor, policy, persistence or restored command lives here. */
class ActiveStudyToolsOwner : ActiveStudyControl {
    private data class Binding(val token: String, val viewModel: StudyViewModel)
    private val bindings = linkedMapOf<String, Binding>()
    private val lock = Any()
    fun attach(viewModel: StudyViewModel): String = synchronized(lock) {
        UUID.randomUUID().toString().also { bindings[it] = Binding(it, viewModel) }
    }
    fun detach(token: String) { synchronized(lock) { bindings.remove(token) } }
    private fun binding(): Binding = synchronized(lock) {
        check(bindings.size == 1) { "Open one active Study lesson before using its scoped actions" }
        bindings.values.single()
    }
    override suspend fun current(): ActiveStudySnapshot {
        val attached = binding()
        val result = attached.viewModel.semanticStudySnapshot(attached.token)
        check(binding() === attached) { "Study page changed" }
        return result
    }
    override suspend fun dispatch(sessionId: String, sourceVersion: String, command: ActiveStudyCommand,
        admitted: () -> Boolean): ActiveStudyDispatch {
        val attached = binding()
        // Canonical Room/source reads happen before entering the Main callback boundary.
        val snapshot = attached.viewModel.semanticStudySnapshot(attached.token)
        check(snapshot.sessionId == sessionId && snapshot.sourceVersion == sourceVersion) { "Study changed; read it again" }
        return withContext(Dispatchers.Main.immediate) {
            check(binding() === attached && admitted()) { "Study is no longer active or authorized" }
            attached.viewModel.dispatchSemanticStudy(attached.token, snapshot, command)
        }
    }
}
