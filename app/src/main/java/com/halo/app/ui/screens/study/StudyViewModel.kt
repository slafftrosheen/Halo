package com.halo.app.ui.screens.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.halo.app.data.study.StudyAttempt
import com.halo.app.data.study.StudyCitation
import com.halo.app.data.study.StudyFeedback
import com.halo.app.data.study.StudyPracticeItem
import com.halo.app.data.study.StudyReviewEvent
import com.halo.app.data.study.StudySessionRecord
import com.halo.app.data.study.StudyVerdict
import com.halo.app.study.*
import com.halo.sdk.runtime.study.*
import com.halo.app.study.AnswerDiff
import com.halo.app.study.NarrationEvent
import com.halo.app.study.PracticeRegeneration
import com.halo.app.study.SentenceSegmenter
import com.halo.app.study.SourceCoverage
import com.halo.app.study.StudyAnswerDiff
import com.halo.app.study.StudyAttemptResult
import com.halo.app.study.StudyFeedbackResult
import com.halo.app.study.StudyMasterySnapshot
import com.halo.app.study.StudySourceExcerpt
import com.halo.app.study.MasteryCardCurve
import com.halo.app.study.StudyMasteryCurve
import com.halo.app.study.StudyReviewIntensity
import com.halo.app.study.StudySourceReference
import com.halo.app.study.StudyTransitionResult
import com.halo.app.study.StudyTutorGateway
import com.halo.app.study.StudyUseCases
import com.halo.app.study.TutorTurnRequest
import com.halo.app.study.TutorTurnResponse
import com.halo.sdk.runtime.study.StudyLanguagePreferences
import com.halo.sdk.runtime.study.StudySessionLifecycle
import com.halo.sdk.runtime.study.TutorStage
import com.halo.app.notebook.NotebookUseCases
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One tutor turn as presented to the learner, with its provenance. */
data class StudyTurn(
    val id: String,
    val stage: TutorStage,
    val text: String,
    val citations: List<StudyCitation> = emptyList(),
    val verdict: StudyVerdict? = null,
    val nextStep: String? = null,
    val uncertainty: String? = null,
    val failure: String? = null,
    val question: String? = null,
    /** Null means unknown legacy provenance; empty means a topic-only reply. */
    val sourceObjectIds: List<String>? = null,
    val exerciseId: String? = null,
    /** Exact supplied material identities. Null is unknown legacy history and must not be reused. */
    val sourceVersions: Map<String, String>? = null,
    /** Exact image/page focus; null denotes unknown legacy history. */
    val materialFocus: String? = null,
)

/**
 * Read-along state for one passage.
 *
 * Halo's speech contract carries no word timings, so the readable unit is the sentence: the reader
 * highlights what it is speaking and never pretends to track words.
 */
data class ReaderState(
    val sourceLabel: String,
    val sentences: List<String>,
    val index: Int = 0,
    val speaking: Boolean = false,
    val autoAdvance: Boolean = true,
    /** Identifies the passage so a stored reading anchor can be tied to a revision later. */
    val passageId: String,
    val sourceRevision: Long? = null,
    val sourceVersion: String? = sourceRevision?.toString(),
    val offsets: List<Int> = emptyList(),
    val wordRange: IntRange? = null,
    val playbackGeneration: Long = 0,
    val speed: Double = 1.0,
    val preparing: Boolean = false,
) {
    val current: String get() = sentences.getOrNull(index).orEmpty()
    val hasNext: Boolean get() = index < sentences.lastIndex
    val hasPrevious: Boolean get() = index > 0
    val progressLabel: String get() = "${index + 1}/${sentences.size}"

    fun generationId(): String = "$passageId:reader:$index:$playbackGeneration"
}

/** Result of checking one card answer. [verdict] is null when Halo cannot check the answer form. */
data class CardReviewResult(
    val itemId: String,
    val verdict: Boolean?,
    val diff: AnswerDiff?,
    val message: String,
    val meaningComparison: String? = null,
    val meaningExecutionLabel: String? = null,
    /** Transient host formatting metadata; the canonical message and saved schedule stay unchanged. */
    val nextReviewIntervalDays: Int? = null,
)

data class StudyUiState(
    val loading: Boolean = true,
    val unavailable: String? = null,
    val session: StudySessionRecord? = null,
    val sources: List<StudySourceReference> = emptyList(),
    val attempts: List<StudyAttempt> = emptyList(),
    val feedback: List<StudyFeedback> = emptyList(),
    val practiceItems: List<StudyPracticeItem> = emptyList(),
    val dueReviewItems: List<StudyPracticeItem> = emptyList(),
    val reviewing: Set<String> = emptySet(),
    val savingSummary: Boolean = false,
    val turns: List<StudyTurn> = emptyList(),
    val draft: String = "",
    val notice: String? = null,
    val isSubmitting: Boolean = false,
    val isGenerating: Boolean = false,
    val narrationAvailable: Boolean = false,
    val mastery: Map<String, StudyMasterySnapshot> = emptyMap(),
    /** Drawn decay curve per card, derived from the same events as [mastery]. */
    val curves: Map<String, MasteryCardCurve> = emptyMap(),
    val reviewIntensity: StudyReviewIntensity = StudyReviewIntensity.DEFAULT,
    val cardAnswers: Map<String, String> = emptyMap(),
    val cardResults: Map<String, CardReviewResult> = emptyMap(),
    val revealedAnswers: Set<String> = emptySet(),
    val reader: ReaderState? = null,
    val activeVisualId: String? = null,
    val visualSourceVersion: String? = null,
    val pdfPages: Map<String, Int> = emptyMap(),
    val executionLabel: String? = null,
    val journey: LearningJourney = LearningJourney(),
    val awaitingAnswerSince: Long? = null,
    val comparingCards: Set<String> = emptySet(),
    val requestedHomeworkReviewId: String? = null,
) {
    val stage: TutorStage get() = session?.tutorStage ?: TutorStage.ASK
    val isFinished: Boolean get() = session?.lifecycle == StudySessionLifecycle.COMPLETED
    val canSubmitAttempt: Boolean
        get() = !isSubmitting && !isGenerating && !loading && !isFinished && draft.isNotBlank()

    val dueCards: List<StudyPracticeItem>
        get() = dueReviewItems

    val masteredCount: Int get() = practiceItems.count(StudyPracticeItem::mastered)

    val measuredMasteryPercent: Int?
        get() = mastery.values.filter(StudyMasterySnapshot::isMeasured)
            .takeIf { it.isNotEmpty() }
            ?.let { snapshots -> (snapshots.sumOf(StudyMasterySnapshot::masteredProbability) / snapshots.size * 100).toInt() }

    val hasPartialCoverage: Boolean get() = sources.any { it.coverage == SourceCoverage.PARTIAL }

    /**
     * The card whose memory chart is worth showing: the most-reviewed measured card, else the first
     * card with any history. Reviewing is what creates a curve, so an untouched card shows none.
     */
    val curveFocusItemId: String?
        get() = curves.entries
            .filter { entry -> entry.value.points.isNotEmpty() }
            .maxByOrNull { entry -> entry.value.reviewCount }
            ?.key
}

/**
 * Study screen orchestration.
 *
 * Invariants enforced here rather than in the UI:
 * - a late tutor response is dropped unless it belongs to the request that is still active for the
 *   current exercise,
 * - the learner's attempt is stored before any generation starts, so an interrupted turn never loses
 *   work and never claims a finished lesson,
 * - mastery is derived from stored review events, so what is displayed can always be recomputed, and
 * - a card the learner edited is never silently overwritten by regenerated content.
 */
class StudyViewModel(
    private val sessionId: String,
    private val study: StudyUseCases,
    private val gateway: StudyTutorGateway? = null,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** Single clock read per recomputation, so a curve cannot be built from two different "now"s. */
    private val clock: () -> Long = System::currentTimeMillis,
    private val notes: NotebookUseCases? = null,
    private val lessons: StudyLessonStore? = null,
    private val workspace: com.halo.app.data.workspace.WorkspaceRepository? = null,
    private val readerStorage: com.halo.sdk.runtime.Storage? = null,
) : ViewModel() {

    val sourceCandidates = (workspace?.observe(com.halo.app.data.workspace.WorkspaceQuery(types = setOf(
        com.halo.app.data.notebook.WorkspaceObjectType.NOTE, com.halo.app.data.notebook.WorkspaceObjectType.FILE,
        com.halo.app.data.notebook.WorkspaceObjectType.MEDIA, com.halo.app.data.notebook.WorkspaceObjectType.ARTIFACT)))
        ?: kotlinx.coroutines.flow.flowOf(emptyList())).stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())
    private var materialQuery: String? = null
    private var sourceReadGeneration = 0L
    private var readerSelectionGeneration = 0L
    private val mutableUiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = mutableUiState.asStateFlow()

    private val navigation = Channel<String>(Channel.BUFFERED)
    val savedNotes = navigation.receiveAsFlow()
    private val restarts = Channel<String>(Channel.BUFFERED)
    val restartedLessons = restarts.receiveAsFlow()
    private var activeRequestId: String? = null
    private var turnJob: Job? = null
    private var transitionJob: Job? = null
    private var transitionVersion = 0L
    private val cardComparisonJobs = mutableMapOf<String, Job>()
    private val cardComparisonIds = mutableMapOf<String, String>()
    private var generationCounter = 0L
    private var readingGeneration = 0L
    private var explanationHandoff: String? = null
    private var handoffTurn: StudyTurn? = null
    private var sourceReadingFinished = false
    private var semanticEpoch = 0L
    private var semanticPending = false
    private var semanticExportedNoteId: String? = null
    private var semanticRestartedSessionId: String? = null
    private var semanticDispatchSession: StudySessionRecord? = null
    private var semanticDispatchSources: Map<String, String?>? = null
    private var semanticExportState: StudyUiState? = null

    private fun semanticVersion(lease: String, state: StudyUiState): String = "study-view:" +
        java.security.MessageDigest.getInstance("SHA-256").digest(
            "$lease|$semanticEpoch|$semanticPending|$semanticExportedNoteId|$semanticRestartedSessionId|$state".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Snapshot the actual attached editor, then validate its canonical saved session/source identities on IO. */
    internal suspend fun semanticStudySnapshot(lease: String): ActiveStudySnapshot {
        val before = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            val state = mutableUiState.value
            check(!state.loading && state.unavailable == null && state.session != null) { "Study is loading or unavailable" }
            state to semanticVersion(lease, state)
        }
        val state = before.first
        val session = requireNotNull(state.session)
        val (currentSources, candidates) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            check(study.getSession(sessionId) == session) { "Study session changed; wait for the lesson to refresh" }
            val references = study.sourceReferences(session)
            val rows = workspace?.observe(com.halo.app.data.workspace.WorkspaceQuery(types = setOf(
                com.halo.app.data.notebook.WorkspaceObjectType.NOTE, com.halo.app.data.notebook.WorkspaceObjectType.FILE,
                com.halo.app.data.notebook.WorkspaceObjectType.MEDIA, com.halo.app.data.notebook.WorkspaceObjectType.ARTIFACT)))
                ?.first().orEmpty().take(30)
            val candidates = rows.mapNotNull { item ->
                val source = study.sourceReferences(session.copy(sourceObjectIds = listOf(item.id.value),
                    sources = listOf(com.halo.sdk.runtime.study.StudySessionSource(item.id.value)))).singleOrNull()
                source?.takeIf { it.version != null && it.resolved !is ResolvedStudySource.Unsupported }
                    ?.let { ActiveStudySource(it.objectId, it.version, it.title.take(200), it.stale,
                        when (it.resolved) { is ResolvedStudySource.Pdf -> "PDF"; is ResolvedStudySource.Visual -> "IMAGE"; else -> "TEXT" },
                        (it.resolved as? ResolvedStudySource.Pdf)?.pageCount) }
            }
            references.map { ActiveStudySource(it.objectId, it.version, it.title.take(200), it.stale,
                when (it.resolved) { is ResolvedStudySource.Pdf -> "PDF"; is ResolvedStudySource.Visual -> "IMAGE";
                    is ResolvedStudySource.Unsupported -> "UNAVAILABLE"; else -> "TEXT" },
                (it.resolved as? ResolvedStudySource.Pdf)?.pageCount) } to candidates
        }
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            check(semanticVersion(lease, mutableUiState.value) == before.second) { "Study view changed; read it again" }
            val sourceVersion = "study-view:" + java.security.MessageDigest.getInstance("SHA-256")
                .digest("${before.second}|$currentSources|$candidates".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            ActiveStudySnapshot(sessionId, sourceVersion, state.stage.name,
                semanticPending || state.isGenerating || state.isSubmitting || state.savingSummary || state.comparingCards.isNotEmpty(),
                state.draft.isNotBlank(), state.isFinished, state.reviewIntensity.name,
                currentSources, candidates,
                state.attempts.map { it.id }, state.turns.map { it.id },
                state.practiceItems.map { it.id }, state.turns.lastOrNull { it.failure == null }?.text,
                state.notice?.take(500), semanticExportedNoteId, semanticRestartedSessionId, before.second,
                state.journey.probeSkipped, state.journey.explanationDepth.name, state.journey.stage.name,
                completedHints(state), state.journey.priorKnowledge.isNotBlank(), state.journey.reflection.isNotBlank(),
                state.activeVisualId, state.pdfPages, state.reader != null, state.reader?.speaking == true)
        }
    }

    /** Main-only callback projection. Existing native methods keep all tutor/privacy/commit authority. */
    internal fun dispatchSemanticStudy(lease: String, observed: ActiveStudySnapshot, command: ActiveStudyCommand): ActiveStudyDispatch {
        val state = mutableUiState.value
        check(observed.sessionId == sessionId && semanticVersion(lease, state) == observed.uiVersion) { "Study changed" }
        if (command.action != "stop") {
            check(!observed.busy && !state.loading) { "Finish the current Study operation first" }
            if (command.action in setOf("advance", "repeat", "back", "guidance", "solution", "submit_attempt_feedback", "ask_tutor", "skip_probe", "explanation_depth", "knowledge_probe", "explore_topic", "reflect", "reflect_feedback", "suggest_topics"))
                check(!state.isFinished) { "This lesson is already finished" }
            if (command.action !in setOf("skip_probe", "explanation_depth")) check(state.draft.isBlank()) { "Keep or send the existing learner draft first" }
            if (command.action in setOf("advance", "repeat", "back", "guidance", "solution", "submit_attempt_feedback", "ask_tutor", "create_practice_drafts", "knowledge_probe", "explore_topic", "reflect_feedback", "suggest_topics"))
                check(observed.sources.none { it.stale || it.version == null }) { "Review changed or unavailable Study material first" }
        }
        val priorJobs = viewModelScope.coroutineContext[Job]?.children?.toSet().orEmpty()
        if (command.action in setOf("skip_probe", "knowledge_probe")) check(state.journey.stage == LearningStage.DISCOVER) {
            "The introductory knowledge question is no longer current"
        }
        semanticEpoch++ // Consume authority before any native callback, including uncertain dispatch.
        semanticDispatchSession = state.session
        semanticDispatchSources = if (command.action == "select_sources") command.sourceVersions else observed.sources.associate { it.objectId to it.version }
        semanticExportState = state
        try { when (command.action) {
            "review_intensity" -> setReviewIntensity(StudyReviewIntensity.valueOf(requireNotNull(command.intensity)))
            "select_sources" -> {
                check(command.sourceVersions.size <= 4 && command.sourceVersions.all { (id, version) ->
                    observed.candidates.any { it.objectId == id && it.version == version }
                }) { "Choose exact current source candidates and versions" }
                changeSources(command.sourceVersions.keys.toList())
            }
            "refresh_sources" -> refreshSources()
            "continue_sources" -> continueWithCurrentSources()
            "restart_sources" -> restartUpdatedLesson()
            "export_notes" -> saveSummaryToNotebook()
            "advance" -> advance()
            "repeat" -> repeatStage()
            "back" -> goBack()
            "guidance" -> requestGuidance(TutorStage.valueOf(requireNotNull(command.stage)))
            "solution" -> askForSolution()
            "submit_attempt_feedback" -> { setDraft(requireNotNull(command.text)); submitAttempt() }
            "ask_tutor" -> if (command.text == null) askTutor() else { setDraft(command.text); askQuestion() }
            "create_practice_drafts" -> createPracticeCards()
            "skip_probe" -> skipKnowledgeProbe()
            "explanation_depth" -> setExplanationDepth(com.halo.app.study.StudyExplanationDepth.valueOf(requireNotNull(command.depth)))
            "knowledge_probe" -> discoverPriorKnowledge(requireNotNull(command.text))
            "explore_topic" -> exploreBranch(requireNotNull(command.text))
            "reflect" -> reflectOnLearning(requireNotNull(command.text))
            "reflect_feedback" -> reviewExplainBack(requireNotNull(command.text))
            "suggest_topics" -> suggestLearningBranches()
            "select_visual", "select_pdf_page", "read_source" -> {
                val id = requireNotNull(command.objectId)
                val source = observed.sources.single { it.objectId == id }
                check(!source.stale && source.version != null && source.kind != "UNAVAILABLE") { "Material changed" }
                check(state.sources.any { it.objectId == id && it.version == source.version && !it.stale }) { "Refresh lesson material before selecting it" }
                when (command.action) {
                    "select_visual" -> { check(source.kind in setOf("IMAGE", "PDF")); selectVisual(id) }
                    "select_pdf_page" -> {
                        val page = requireNotNull(command.page)
                        check(source.kind == "PDF" && page in 1..requireNotNull(source.pageCount))
                        selectPdfPage(id, page)
                    }
                    else -> { check(source.kind == "TEXT"); readSourceAloud(id) }
                }
            }
            "read_reply" -> readTurnAloud(state.turns.single { it.id == command.turnId && it.failure == null })
            "reader_play_pause" -> { check(state.reader != null); toggleReaderPlayback() }
            "reader_next" -> { check(state.reader?.hasNext == true); readerNextSentence() }
            "reader_previous" -> { check(state.reader?.hasPrevious == true); readerPreviousSentence() }
            "reader_close" -> closeReader()
            "stop" -> cancelTurn("Stopped by learner through Study tools")
            else -> error("Unsupported Study action")
        } } finally { semanticDispatchSession = null; semanticDispatchSources = null; semanticExportState = null }
        val started = viewModelScope.coroutineContext[Job]?.children?.filter { it !in priorJobs }?.toList().orEmpty()
        if (started.isNotEmpty()) {
            semanticPending = true
            viewModelScope.launch {
                try { started.forEach { it.join() } }
                finally { semanticPending = false; semanticEpoch++ }
            }
        }
        return ActiveStudyDispatch(newId(), sessionId, command.action)
    }

    fun listenThenExplain() {
        val state=mutableUiState.value
        if (state.session == null) return
        if(state.isGenerating || state.sources.none { !it.excerpt.isNullOrBlank() && !it.stale }) return
        openValidatedSource(explainAfterReading = true)
    }
    private fun finishHandoff() {
        if(!sourceReadingFinished || explanationHandoff == null) return
        val turn=handoffTurn ?: return
        explanationHandoff=null; handoffTurn=null
        readTurnAloud(turn)
    }

    init {
        viewModelScope.launch {
            study.observeSessionDuePractice(sessionId).collect { due ->
                mutableUiState.value = mutableUiState.value.copy(dueReviewItems = due)
            }
        }
        viewModelScope.launch {
            try {
            val session = study.getSession(sessionId)
            if (session == null) {
                mutableUiState.value = mutableUiState.value.copy(
                    loading = false,
                    unavailable = "This study session is no longer available",
                )
                return@launch
            }
            mutableUiState.value = mutableUiState.value.copy(session = session)
            val restoreGeneration = sourceReadGeneration
            val history = lessons?.load(sessionId).orEmpty()
            val recovery = lessons?.loadRecovery(sessionId)
            val storedJourney = lessons?.loadJourney(sessionId) ?: LearningJourney(
                stage = if (session.lifecycle == StudySessionLifecycle.COMPLETED) LearningStage.CONTINUE
                    else if (history.isEmpty()) LearningStage.DISCOVER else LearningStage.EXPLAIN)
            val summary = study.summaryArtifact(sessionId)
            val journey = summary?.let { storedJourney.copy(summaryNoteId = it.noteId, summaryNoteRevision = it.revision) } ?: storedJourney
            semanticExportedNoteId = journey.summaryNoteId
            var materialUnavailable = false
            val restoredSources = try { study.sourceReferences(session) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { materialUnavailable = true; emptyList() }
            val current = mutableUiState.value
            val sameSession = current.session == session
            // Keep stale identity so dispatch refuses until an explicit current selection.
            val restoredFocus = recovery?.materialFocus
            mutableUiState.value = current.copy(
                loading = false,
                journey = if (sameSession) journey else current.journey,
                turns = (history + current.turns).distinctBy { it.id },
                // A newer read may still be pending. Seed the same-session material until
                // it arrives, but never overwrite an already completed newer projection.
                sources = if (sameSession && (restoreGeneration == sourceReadGeneration || current.sources.isEmpty()))
                    restoredSources else current.sources,
                narrationAvailable = study.narrationAvailable,
                activeVisualId = current.activeVisualId ?: restoredFocus?.objectId,
                visualSourceVersion = if (current.activeVisualId == null) restoredFocus?.version else current.visualSourceVersion,
                pdfPages = if (current.activeVisualId == null && restoredFocus?.page != null)
                    current.pdfPages + (restoredFocus.objectId to restoredFocus.page) else current.pdfPages,
                draft = mutableUiState.value.draft.ifBlank { recovery?.draft.orEmpty() },
                notice = if (recovery?.pendingRequestId != null && history.none { it.id == recovery.pendingRequestId })
                    "The previous reply was interrupted. Your saved work is kept; ask again when you are ready."
                    else if (materialUnavailable) "Some material could not be opened. Your lesson and draft are restored; relink the material and retry."
                    else mutableUiState.value.notice,
            )
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableUiState.value = mutableUiState.value.copy(loading = false,
                    notice = "Could not restore all lesson material. Your saved work is kept; try opening the material again.")
            }
        }
        viewModelScope.launch {
            study.observeSession(sessionId).collect { session ->
                if (session == null) {
                    cancelTurn("Session unavailable")
                    mutableUiState.value = mutableUiState.value.copy(loading = false, unavailable = "This session is no longer available", turns = emptyList())
                    return@collect
                }
                mutableUiState.value = mutableUiState.value.copy(session = session)
                if (session.lifecycle == StudySessionLifecycle.COMPLETED) {
                    stopReading("Session completed")
                }
            }
        }
        viewModelScope.launch {
            study.observeAttempts(sessionId).collect { attempts ->
                mutableUiState.value = mutableUiState.value.copy(attempts = attempts)
            }
        }
        viewModelScope.launch {
            study.observeFeedback(sessionId).collect { feedback ->
                mutableUiState.value = mutableUiState.value.copy(feedback = feedback)
            }
        }
        // Items, history and the retention target are combined so mastery and the drawn decay curve
        // are recomputed exactly when any of them changes: the chart and the schedule cannot drift.
        viewModelScope.launch {
            combine(
                study.observePracticeItems(sessionId),
                study.observeReviewEvents(sessionId),
                study.observeReviewIntensity(),
            ) { items, events, intensity -> PracticeHistory(items, events, intensity) }
                .collect { (items, events, intensity) ->
                    val mastery = study.masteryFor(items, events)
                    val now = clock()
                    mutableUiState.value = mutableUiState.value.copy(
                        practiceItems = items,
                        mastery = mastery,
                        curves = items.mapNotNull { item ->
                            StudyMasteryCurve.cardCurve(
                                history = events.filter { it.itemId == item.id },
                                dueAtEpochMs = mastery[item.id]?.dueAtEpochMs ?: item.dueAtEpochMs,
                                now = now,
                            )?.let { curve -> item.id to curve }
                        }.toMap(),
                        reviewIntensity = intensity,
                    )
                }
        }
        // Playback lifecycle drives the reader: only the current sentence's completion advances it.
        viewModelScope.launch {
            study.narrationEvents.collect(::onNarrationEvent)
        }
    }

    /** One emission of the practice pipeline; a named holder keeps the flow readable. */
    private data class PracticeHistory(
        val items: List<StudyPracticeItem>,
        val events: List<StudyReviewEvent>,
        val intensity: StudyReviewIntensity,
    )

    fun setDraft(value: String) {
        mutableUiState.value = mutableUiState.value.copy(awaitingAnswerSince = null, draft = value.take(4000))
        persistDraft()
    }
    private fun persistDraft() {
        val draft = mutableUiState.value.draft
        viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            try { lessons?.saveDraft(sessionId, draft) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { mutableUiState.value = mutableUiState.value.copy(notice = "Could not save this draft. Keep this lesson open and try again.") }
        }
    }

    /**
     * Change how hard the learner wants to hold on. Stored first; a failed write reports instead of
     * showing a setting that would silently revert.
     */
    fun setReviewIntensity(value: StudyReviewIntensity) {
        if (value == mutableUiState.value.reviewIntensity) return
        viewModelScope.launch {
            if (study.setReviewIntensity(value)) {
                mutableUiState.value = mutableUiState.value.copy(reviewIntensity = value)
            } else {
                mutableUiState.value = mutableUiState.value.copy(
                    notice = "Halo could not save the review intensity",
                )
            }
        }
    }

    fun setStudyLanguages(assignment: String, explanation: String, strongest: String) {
        cancelTurn("Languages changed")
        stopReading("Languages changed")
        viewModelScope.launch {
            val session = study.getSession(sessionId) ?: return@launch
            study.setPreferences(sessionId, StudyLanguagePreferences(assignment.take(80).takeIf { it.isNotBlank() },
                explanation.take(80).takeIf { it.isNotBlank() }, strongest.take(80).takeIf { it.isNotBlank() }), session.goal)
        }
    }
    fun setExplanationLanguage(language: String) = updateLanguages(explanation = language)

    fun setStrongestLanguage(language: String) = updateLanguages(strongest = language)

    fun setGoal(goal: String) = viewModelScope.launch {
        val session = mutableUiState.value.session ?: return@launch
        study.setPreferences(session.id, session.languages, goal)
    }

    private fun updateLanguages(explanation: String? = null, strongest: String? = null) {
        viewModelScope.launch {
            val session = mutableUiState.value.session ?: return@launch
            val languages = StudyLanguagePreferences(
                assignmentLanguage = session.languages.assignmentLanguage,
                explanationLanguage = explanation ?: session.languages.explanationLanguage,
                strongestLanguage = strongest ?: session.languages.strongestLanguage,
            )
            study.setPreferences(session.id, languages, session.goal)
            // A language change invalidates any response generated under the old preference.
            cancelTurn("Explanation language changed")
            stopReading("Explanation language changed")
        }
    }

    // ---- guided progression --------------------------------------------------------------------

    /** Guided next step. */
    fun advance() = moveTo(null, solutionExplicitlyRequested = false)

    fun repeatStage() = moveTo(mutableUiState.value.stage, solutionExplicitlyRequested = false)

    fun goBack() {
        val previous = mutableUiState.value.stage.previous() ?: return
        moveTo(previous, solutionExplicitlyRequested = false)
    }

    fun requestGuidance(stage: TutorStage) {
        val state = mutableUiState.value
        if (state.loading || state.isGenerating || state.isSubmitting || state.isFinished) return
        if (stage in setOf(TutorStage.ASK, TutorStage.HINT, TutorStage.PARTIAL_SOLUTION, TutorStage.ANALOGY))
            moveTo(stage, solutionExplicitlyRequested = false, guidanceExplicitlyRequested = true)
    }

    fun askForSolution() {
        val state = mutableUiState.value
        if (state.session?.learnerLevel != StudyLearnerLevel.ADULT && completedHints(state) < 2) {
            mutableUiState.value = state.copy(notice = "Try two small hints first, then ask for the full solution again.")
            return
        }
        moveTo(TutorStage.SOLUTION, solutionExplicitlyRequested = true)
    }

    /** Only real, accepted replies count; errors and stage navigation cannot unlock a solution. */
    private fun completedHints(state: StudyUiState): Int = state.turns.count {
        it.failure == null && it.text.isNotBlank() && it.stage == TutorStage.HINT &&
            it.materialFocus == materialFocusKey(state) &&
            it.exerciseId == state.session?.currentExerciseId &&
            it.sourceVersions == state.sources.mapNotNull { source -> source.version?.let { version -> source.objectId to version } }.toMap()
    }

    private fun materialFocusKey(state: StudyUiState): String {
        val visuals = state.sources.mapNotNull { it.resolved }.filter { it is ResolvedStudySource.Visual || it is ResolvedStudySource.Pdf }
        val selected = try { selectedVisual(state, visuals) } catch (_: IllegalStateException) { return "unavailable" }
        return org.json.JSONArray(listOf(selected?.objectId, selected?.revision,
            (selected as? ResolvedStudySource.Pdf)?.selectedPage)).toString()
    }

    private fun selectedVisual(state: StudyUiState, visuals: List<ResolvedStudySource>): ResolvedStudySource? {
        val selected = if (state.activeVisualId == null) visuals.singleOrNull() else {
            val focused = visuals.singleOrNull { it.objectId == state.activeVisualId }
            check(focused != null && focused.revision == state.visualSourceVersion) {
                "Selected material changed. Choose the image or PDF page again."
            }
            focused
        }
        if (selected is ResolvedStudySource.Pdf) {
            val page = if (state.activeVisualId == selected.objectId) state.pdfPages[selected.objectId] ?: selected.selectedPage
                else selected.selectedPage
            check(page in 1..selected.pageCount) { "Selected page is unavailable. Choose the PDF page again." }
            return selected.copy(selectedPage = page)
        }
        return selected
    }

    fun finishSession() = viewModelScope.launch {
        stopReading("Session completed")
        when (val result = study.complete(sessionId)) {
            is StudyTransitionResult.Moved -> {
                keepJourney(mutableUiState.value.journey.copy(stage = LearningStage.CONTINUE))
                mutableUiState.value = mutableUiState.value.copy(notice = "Session completed")
            }
            is StudyTransitionResult.Rejected ->
                mutableUiState.value = mutableUiState.value.copy(notice = result.message)
        }
    }

    private fun moveTo(target: TutorStage?, solutionExplicitlyRequested: Boolean, guidanceExplicitlyRequested: Boolean = false) {
        val expected = semanticDispatchSession
        val versions = semanticDispatchSources
        launchLearningTransition {
            val session = mutableUiState.value.session ?: return@launchLearningTransition
            val resolved = target ?: session.tutorStage.next() ?: session.tutorStage
            when (val result = study.moveTo(session.id, resolved, solutionExplicitlyRequested, guidanceExplicitlyRequested,
                expectedSession = expected, expectedVersions = versions)) {
                is StudyTransitionResult.Moved -> {
                    mutableUiState.value = mutableUiState.value.copy(notice = null)
                    requestTutorTurn(result.session, attempt = null)
                }
                is StudyTransitionResult.Rejected ->
                    mutableUiState.value = mutableUiState.value.copy(notice = result.message)
            }
        }
    }

    /** Submits the learner attempt first, then asks the tutor to check it. */
    fun submitAttempt() {
        val state = mutableUiState.value
        if (!state.canSubmitAttempt) return
        val attemptText = state.draft.trim()
        val expected = semanticDispatchSession
        val versions = semanticDispatchSources
        val ticket = transitionVersion
        mutableUiState.value = state.copy(isSubmitting = true, notice = null)
        viewModelScope.launch {
            try {
            val submissionKey = lessons?.prepareAttempt(sessionId, attemptText, newId()) ?: newId()
            when (val result = study.submitAttempt(sessionId, attemptText, submissionKey = submissionKey,
                expectedSession = expected, expectedVersions = versions)) {
                is StudyAttemptResult.Recorded -> {
                    if (ticket != transitionVersion) {
                        mutableUiState.value = mutableUiState.value.copy(isSubmitting = false)
                        return@launch // A saved attempt remains saved; Stop must not launch a late tutor.
                    }
                    keepJourney(mutableUiState.value.journey.copy(stage = LearningStage.PRACTICE))
                    mutableUiState.value = mutableUiState.value.copy(
                        isSubmitting = false,
                        draft = if (mutableUiState.value.draft.trim() == attemptText) "" else mutableUiState.value.draft,
                        notice = if (result.attempt.helpRevealed.isEmpty()) {
                            "Attempt saved (no help used)"
                        } else {
                            "Attempt saved after ${result.attempt.helpRevealed.joinToString { it.name.lowercase() }}"
                        },
                    )
                    persistDraft()
                    // The attempt travels with the request, so a later turn can never be graded
                    // against a submission it does not belong to.
                    if (ticket == transitionVersion) requestTutorTurn(result.session, attempt = result.attempt)
                }
                is StudyAttemptResult.Rejected ->
                    mutableUiState.value = mutableUiState.value.copy(isSubmitting = false, notice = result.message)
            }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { mutableUiState.value = mutableUiState.value.copy(notice = "Could not save your attempt. Your draft is kept; try again.") }
            finally { mutableUiState.value = mutableUiState.value.copy(isSubmitting = false) }
        }
    }

    /** Ask for help at the current stage without submitting an attempt. */
    private suspend fun keepJourney(journey: LearningJourney): Boolean {
        return try {
            lessons?.saveJourney(sessionId, journey)
            mutableUiState.value = mutableUiState.value.copy(journey = journey)
            true
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            mutableUiState.value = mutableUiState.value.copy(notice = "Could not save this learning step. Please try again.")
            false
        }
    }

    fun discoverPriorKnowledge(answer: String) = launchLearningTransition {
        val state = mutableUiState.value
        if (answer.isBlank()) return@launchLearningTransition
        if (state.loading || state.isSubmitting || state.isFinished || state.journey.stage != LearningStage.DISCOVER) return@launchLearningTransition
        val session = state.session ?: return@launchLearningTransition
        if (keepJourney(state.journey.copy(stage = LearningStage.EXPLAIN, priorKnowledge = answer.take(2000), probeSkipped = false)))
            requestTutorTurn(session, null,
                question = "Before we begin, this is what I already know: ${answer.take(2000)}. Explain one idea at my level, then invite me to think about it.",
                learnerText = answer.take(2000))
    }

    /** Local progression only: skipping neither supplies an answer nor starts paid/remote inference. */
    fun skipKnowledgeProbe() = launchLearningTransition {
        val state = mutableUiState.value
        if (state.session == null || state.loading || state.isSubmitting || state.isFinished || state.journey.stage != LearningStage.DISCOVER)
            return@launchLearningTransition
        keepJourney(state.journey.copy(stage = LearningStage.EXPLAIN, probeSkipped = true))
    }

    /** Save an explicit presentation preference without generating, grading or changing learner level. */
    fun setExplanationDepth(depth: com.halo.app.study.StudyExplanationDepth) = launchLearningTransition {
        val state = mutableUiState.value
        if (state.session == null || state.loading || state.isSubmitting || state.isFinished) return@launchLearningTransition
        keepJourney(state.journey.copy(explanationDepth = depth))
    }

    fun exploreBranch(topic: String) = launchLearningTransition {
        val state = mutableUiState.value
        if (state.loading || state.isSubmitting || state.isFinished || topic.isBlank()) return@launchLearningTransition
        val session = state.session ?: return@launchLearningTransition
        val branch = topic.trim().take(300)
        val parent = state.journey.concepts.firstOrNull { it.title == state.journey.currentBranch }?.id
        val concepts = if (state.journey.concepts.any { it.title == branch }) state.journey.concepts
            else (state.journey.concepts + LearningConcept(newId(), branch, parent)).takeLast(80)
        if (keepJourney(state.journey.copy(stage = LearningStage.EXPLORE, concepts = concepts,
                currentBranch = branch, branches = (state.journey.branches + branch).distinct().takeLast(40))))
            requestTutorTurn(session, null, question = branch)
    }

    fun reflectOnLearning(text: String) = launchLearningTransition {
        val state = mutableUiState.value
        if (state.loading || state.isSubmitting) return@launchLearningTransition
        keepJourney(state.journey.copy(stage = LearningStage.REFLECT, reflection = text.take(4000)))
    }

    fun suggestLearningBranches() {
        val state = mutableUiState.value
        if (state.loading || state.isGenerating || state.isSubmitting || state.isFinished) return
        val session = state.session ?: return
        requestTutorTurn(session, null, learningTask = LearningTutorTask.BRANCHES)
    }

    fun reviewExplainBack(transcript: String) = launchLearningTransition {
        val state = mutableUiState.value
        val session = state.session ?: return@launchLearningTransition
        if (transcript.isBlank() || state.turns.none { it.failure == null && it.exerciseId == session.currentExerciseId })
            return@launchLearningTransition
        val text = transcript.trim().take(4000)
        if (keepJourney(state.journey.copy(stage = LearningStage.REFLECT, reflection = text, explainBack = null)))
            requestTutorTurn(session, null, question = text, learningTask = LearningTutorTask.EXPLAIN_BACK)
    }

    private suspend fun acceptLearningReview(task: LearningTutorTask, response: TutorTurnResponse.Ready, transcript: String) {
        try {
            val raw = response.text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            require(raw.length <= 40000)
            val json = org.json.JSONObject(raw)
            val journey = mutableUiState.value.journey
            val next = when (task) {
                LearningTutorTask.BRANCHES -> {
                    val items = json.getJSONArray("branches")
                    val titles = (0 until items.length().coerceAtMost(3)).map { items.getString(it).trim().take(120) }
                        .filter(String::isNotBlank).distinct()
                    require(titles.isNotEmpty())
                    val parent = journey.concepts.firstOrNull { it.title == journey.currentBranch }?.id
                    val additions = titles.filter { title -> journey.concepts.none { it.title == title } }
                        .map { LearningConcept(newId(), it, parent) }
                    journey.copy(concepts = (journey.concepts + additions).takeLast(80))
                }
                LearningTutorTask.EXPLAIN_BACK -> {
                    val observations = json.optJSONArray("observations") ?: org.json.JSONArray()
                    val questions = json.optJSONArray("questions") ?: org.json.JSONArray()
                    val grounded = (0 until observations.length().coerceAtMost(3)).mapNotNull { index ->
                        val item = observations.getJSONObject(index)
                        val quote = item.getString("quote").take(1000)
                        val suggestion = item.getString("suggestion").take(1200)
                        if (quote.isBlank() || !transcript.contains(quote) || suggestion.isBlank()) null
                        else ExplainBackObservation(quote, suggestion)
                    }
                    val followUps = (0 until questions.length().coerceAtMost(3)).map { questions.getString(it).take(500) }
                        .filter(String::isNotBlank)
                    require(grounded.isNotEmpty() || followUps.isNotEmpty())
                    journey.copy(explainBack = ExplainBackReview(transcript, grounded, followUps))
                }
                LearningTutorTask.NARRATIVE, LearningTutorTask.CARD_COMPARE -> return
            }
            if (keepJourney(next)) mutableUiState.value = mutableUiState.value.copy(executionLabel = response.executionLabel)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            mutableUiState.value = mutableUiState.value.copy(notice = "Could not prepare this learning review. Your work is kept; you can continue yourself.")
        }
    }

    fun extractHomework() {
        val state = mutableUiState.value
        if (state.loading || state.isGenerating || state.isSubmitting || state.isFinished) return
        val session = state.session ?: return
        if (session.sourceObjectIds.isEmpty()) {
            mutableUiState.value = state.copy(notice = "Add homework material or type the exercise.")
            return
        }
        requestTutorTurn(session, null, extractExercises = true)
    }

    private suspend fun acceptExtractedExercises(response: TutorTurnResponse.Ready,
        sources: List<StudySourceReference>, visual: ResolvedStudySource?) {
        val parsed = try {
            val raw = response.text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            require(raw.length <= 60000)
            val items = org.json.JSONObject(raw).getJSONArray("exercises")
            (0 until items.length().coerceAtMost(8)).map { index ->
                val item = items.getJSONObject(index)
                val marker = item.getString("source")
                require(marker.matches(Regex("S[1-4]")))
                val source = sources.getOrNull(marker.drop(1).toInt() - 1) ?: error("Unknown source")
                require(source.coverage != SourceCoverage.MISSING || visual?.objectId == source.objectId)
                val text = item.getString("text").trim()
                require(text.isNotBlank() && text.length <= 4000)
                HomeworkExercise(newId(), text, source.objectId,
                    if (visual is ResolvedStudySource.Pdf && visual.objectId == source.objectId)
                        "Page ${visual.selectedPage}" else source.locator,
                    item.optString("uncertainty").take(1000).takeIf(String::isNotBlank))
            }
        } catch (_: Exception) {
            mutableUiState.value = mutableUiState.value.copy(notice = "Could not read exercises reliably. Type or dictate the exercise instead.")
            return
        }
        if (parsed.isEmpty()) {
            mutableUiState.value = mutableUiState.value.copy(notice = "No exercises found in this view. Try another page or type the exercise.")
            return
        }
        val journey = mutableUiState.value.journey
        val retained = journey.exercises.filter { it.reviewed }
        if (keepJourney(journey.copy(exercises = (retained + parsed).take(20))))
            mutableUiState.value = mutableUiState.value.copy(executionLabel = response.executionLabel,
                notice = "Check each exercise against your material before using it.")
    }

    fun finishHomeworkExercise() = launchLearningTransition {
        val state = mutableUiState.value
        val current = state.journey.exercises.firstOrNull { it.id == state.session?.currentExerciseId } ?: return@launchLearningTransition
        if (keepJourney(state.journey.copy(exercises = state.journey.exercises.map {
            if (it.id == current.id) it.copy(finished = true) else it
        }))) mutableUiState.value = mutableUiState.value.copy(notice = "Marked done by you. This is not a verified assessment.")
    }

    fun nextHomeworkExercise() {
        val state = mutableUiState.value
        if (state.loading || state.isGenerating || state.isSubmitting || state.isFinished) return
        val current = state.journey.exercises.indexOfFirst { it.id == state.session?.currentExerciseId }
        val next = state.journey.exercises.drop((current + 1).coerceAtLeast(0)).firstOrNull { !it.finished }
        if (next == null) {
            mutableUiState.value = state.copy(notice = "No more exercises in this set. Add another page or reflect on what you learned.")
        } else if (next.reviewed) confirmHomework(next.id, next.text)
        else mutableUiState.value = state.copy(requestedHomeworkReviewId = next.id,
            notice = "Check the next exercise wording before starting.")
    }

    fun confirmHomework(id: String?, text: String) = launchLearningTransition {
        val clean = text.trim().take(4000)
        if (clean.isEmpty()) return@launchLearningTransition
        val state = mutableUiState.value
        val original = state.journey.exercises.firstOrNull { it.id == id }
        val exercise = original?.copy(text = clean, reviewed = true, finished = original.finished && original.text == clean)
            ?: HomeworkExercise(newId(), clean, reviewed = true)
        val exercises = if (original != null) state.journey.exercises.map { if (it.id == id) exercise else it }
            else (state.journey.exercises + exercise).takeLast(20)
        if (!keepJourney(state.journey.copy(exercises = exercises, stage = LearningStage.PRACTICE))) return@launchLearningTransition
        when (val result = study.selectExercise(sessionId, exercise.id)) {
            is StudyTransitionResult.Moved -> {
                mutableUiState.value = mutableUiState.value.copy(session = result.session, draft = "", notice = null, requestedHomeworkReviewId = null)
                persistDraft()
                requestTutorTurn(result.session, null)
            }
            is StudyTransitionResult.Rejected -> mutableUiState.value = mutableUiState.value.copy(notice = result.message)
        }
    }

    fun askTutor() {
        val state = mutableUiState.value
        if (state.loading || state.isGenerating || state.isSubmitting || state.isFinished) return
        val session = state.session ?: return
        requestTutorTurn(session, attempt = null)
    }

    fun askQuestion() {
        val current = mutableUiState.value
        val session = current.session ?: return
        if (current.loading || current.isGenerating || current.isSubmitting || current.isFinished || current.draft.isBlank()) return
        requestTutorTurn(session, attempt = null, question = current.draft.trim().take(2000))
    }

    fun askAboutSavedTurn(turnId: String, question: String) {
        val state = mutableUiState.value
        val turn = state.turns.firstOrNull { it.id == turnId && it.failure == null } ?: return
        val session = state.session ?: return
        if (state.loading || state.isGenerating || state.isSubmitting || state.isFinished || question.isBlank()) return
        cancelTurn("Question about lesson")
        requestTutorTurn(session, null, question = question.trim().take(2000), referenceTurn = turn)
    }

    /**
     * One tutor turn. Passing the [attempt] (rather than reading "the latest attempt" later) makes
     * the graded submission part of the request, so no shared mutable pointer can drift.
     */
    private fun requestTutorTurn(session: StudySessionRecord, attempt: StudyAttempt?, question: String? = null, compact: Boolean = false, handoff: Boolean = false, extractExercises: Boolean = false, learningTask: LearningTutorTask = LearningTutorTask.NARRATIVE, referenceTurn: StudyTurn? = null,
        learnerText: String? = question) {
        val expected = semanticDispatchSession
        if(!handoff) { explanationHandoff=null; handoffTurn=null; sourceReadingFinished=false }
        val gateway = gateway
        if (gateway == null || !gateway.available) {
            mutableUiState.value = mutableUiState.value.copy(
                notice = "No AI provider is available. Your work is saved and Study will resume here.",
                isGenerating = false,
            )
            return
        }
        generationCounter += 1
        val requestId = "$sessionId:${session.currentExerciseId ?: "no-exercise"}:${newId()}:$generationCounter"
        activeRequestId = requestId
        if(handoff) explanationHandoff=requestId
        val exerciseAtRequest = session.currentExerciseId
        val stage = if(compact) TutorStage.EXPLAIN else session.tutorStage
        turnJob?.cancel()
        // Claim the turn synchronously: two quick taps must not launch duplicate tutors.
        mutableUiState.value = mutableUiState.value.copy(isGenerating = true, notice = null)
        turnJob = viewModelScope.launch {
            try {
            check(expected == null || study.getSession(sessionId) == expected) { "Study changed after review" }
            lessons?.markPendingTurn(sessionId, requestId)
            val sources = study.sourceReferences(session, listOfNotNull(materialQuery, question, attempt?.response, session.goal).joinToString(" "), includeBeginning = materialQuery == null && question == null && attempt == null && session.tutorStage == TutorStage.ASK)
            currentCoroutineContext().ensureActive()
            if (activeRequestId != requestId) return@launch
            mutableUiState.value = mutableUiState.value.copy(sources = sources)
            if (!study.sourcesStillCurrent(session, sources)) {
                mutableUiState.value = mutableUiState.value.copy(isGenerating = false, notice = "Material changed. Choose Continue with current source or restart the updated lesson.")
                return@launch
            }
            val visuals = sources.mapNotNull { it.resolved }.filter { it is ResolvedStudySource.Visual || it is ResolvedStudySource.Pdf }
            val selectedVisual = selectedVisual(mutableUiState.value, visuals)
            if (visuals.size > 1 && selectedVisual == null) {
                mutableUiState.value = mutableUiState.value.copy(isGenerating = false, notice = "Choose the active image or PDF page in Material.")
                return@launch
            }
            val visual = selectedVisual
            val sourceVersions = sources.associate { it.objectId to requireNotNull(it.version) }
            val focusAtRequest = materialFocusKey(mutableUiState.value)
            val history = (mutableUiState.value.turns.filter { it.failure == null && it.exerciseId == exerciseAtRequest }
                .takeLast(3) + listOfNotNull(referenceTurn)).distinctBy { it.id }.filter { turn ->
                    // Explicitly continuing with an edited source does not re-authorize old quoted
                    // material. Unknown legacy replies stay visible but never enter a new prompt.
                    turn.sourceVersions == sourceVersions && turn.sourceObjectIds?.toSet() == sourceVersions.keys &&
                        turn.materialFocus == focusAtRequest
                }
            val response = gateway.respond(
                TutorTurnRequest(
                    session = session,
                    stage = stage,
                    languages = session.languages,
                    goal = session.goal,
                    sourceExcerpts = sources.map { source ->
                        StudySourceExcerpt(
                            objectId = source.objectId,
                            revision = source.revision,
                            title = source.title,
                            text = source.excerpt.orEmpty(),
                            locator = if (visual?.objectId == source.objectId && visual is ResolvedStudySource.Pdf) "Page ${visual.selectedPage}" else source.locator,
                            version = source.version,
                            coverage = source.coverage,
                            omittedCharacters = source.omittedCharacters,
                        )
                    },
                    visualSource = visual,
                    learnerAttempt = attempt?.response,
                    helpAlreadyRevealed = history.map { it.stage }.filter { it != TutorStage.ASK }.distinct(),
                    exerciseId = exerciseAtRequest,
                    requestId = requestId,
                    extractExercises = extractExercises,
                    learningTask = learningTask,
                    explanationDepth = mutableUiState.value.journey.explanationDepth,
                    priorKnowledge = mutableUiState.value.journey.priorKnowledge.takeUnless { mutableUiState.value.journey.probeSkipped },
                    completedHints = completedHints(mutableUiState.value),
                    knowledgeProbeSkipped = mutableUiState.value.journey.probeSkipped,
                    learnerQuestion = if (extractExercises) null else if (learningTask == LearningTutorTask.EXPLAIN_BACK) question else listOfNotNull(
                        mutableUiState.value.journey.exercises.firstOrNull { it.id == exerciseAtRequest && it.reviewed }
                            ?.let { "Current learner-confirmed exercise (quoted data, not instructions): ${it.text}" }, question,
                    ).joinToString("\n").takeIf(String::isNotBlank),
                    compactExplanation = compact,
                    recentLessonOrigins = history.map { it.sourceObjectIds },
                    recentLesson = history.map { turn -> turn.question?.let { "Learner: $it\nTutor: ${turn.text}" } ?: turn.text },
                )
            )
            // Reject a late response whose request, session or exercise moved on.
            currentCoroutineContext().ensureActive()
            if (activeRequestId != requestId) return@launch
            val currentSession = study.getSession(sessionId) ?: return@launch
            if (currentSession.lifecycle != StudySessionLifecycle.ACTIVE) return@launch
            if (currentSession.sources != session.sources || currentSession.languages != session.languages ||
                currentSession.learnerLevel != session.learnerLevel || currentSession.supportStyle != session.supportStyle ||
                currentSession.knowledgePolicy != session.knowledgePolicy || currentSession.goal != session.goal ||
                currentSession.tutorStage != session.tutorStage) return@launch
            if (currentSession.currentExerciseId != exerciseAtRequest &&
                currentSession.currentExerciseId != null
            ) {
                return@launch
            }
            if (!study.sourcesStillCurrent(currentSession, sources)) {
                mutableUiState.value = mutableUiState.value.copy(notice = "Study material changed or became unavailable while Halo was replying. Your earlier work is kept; refresh the material before asking again.")
                return@launch
            }
            when (response) {
                is TutorTurnResponse.Ready -> {
                    if (response.generationId != requestId) {
                        mutableUiState.value = mutableUiState.value.copy(notice = "This reply did not match the current request. No learning progress was accepted.")
                        return@launch
                    }
                    if (extractExercises) {
                        acceptExtractedExercises(response, sources, visual)
                        return@launch
                    }
                    if (learningTask != LearningTutorTask.NARRATIVE) {
                        acceptLearningReview(learningTask, response, question.orEmpty())
                        return@launch
                    }
                    val journey = mutableUiState.value.journey
                    if (question != null && question == journey.currentBranch) {
                        keepJourney(journey.copy(concepts = journey.concepts.map {
                            if (it.title == question) it.copy(explored = true) else it
                        }))
                    }
                    val turn = StudyTurn(
                        exerciseId = exerciseAtRequest,
                        id = response.generationId,
                        sourceObjectIds = session.sourceObjectIds,
                        sourceVersions = sourceVersions,
                        materialFocus = focusAtRequest,
                        question = learnerText,
                        stage = stage,
                        text = response.text,
                        citations = response.citations,
                        verdict = response.verdict,
                        nextStep = response.nextStep,
                        uncertainty = response.uncertainty,
                    )
                    val turns = (mutableUiState.value.turns + turn).takeLast(30)
                    try { lessons?.save(sessionId, turns) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { mutableUiState.value = mutableUiState.value.copy(notice = "This reply couldn't be saved locally. Save study notes to keep a copy.") }
                    mutableUiState.value = mutableUiState.value.copy(
                        isGenerating = false,
                        turns = turns,
                        // A question is an editable draft until the response arrives.
                        // Never erase a newer draft the learner wrote while waiting.
                        draft = if (learnerText != null && mutableUiState.value.draft.trim() == learnerText.trim()) ""
                            else mutableUiState.value.draft,
                        executionLabel = response.executionLabel,
                    )
                    persistDraft()
                    if(explanationHandoff == requestId) { handoffTurn=turn; finishHandoff() }
                    persistFeedback(currentSession, response, exerciseAtRequest, attempt?.id)
                }
                is TutorTurnResponse.Unavailable ->
                    mutableUiState.value = mutableUiState.value.copy(isGenerating = false, notice = response.reason)
                is TutorTurnResponse.Failed ->
                    mutableUiState.value = mutableUiState.value.copy(
                        isGenerating = false,
                        notice = response.message,
                        turns = mutableUiState.value.turns + StudyTurn(
                            id = requestId,
                            stage = stage,
                            text = "",
                            failure = response.message,
                        ),
                    )
            }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (activeRequestId == requestId) mutableUiState.value = mutableUiState.value.copy(notice = "Couldn't read material or save this turn. Your earlier work is kept.")
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    try { lessons?.clearPendingTurn(sessionId, requestId) } catch (_: Exception) { /* Preserve the interrupted marker for recovery. */ }
                }
                if (activeRequestId == requestId) mutableUiState.value = mutableUiState.value.copy(isGenerating = false, awaitingAnswerSince = null)
            }
        }
    }

    private suspend fun persistFeedback(
        session: StudySessionRecord,
        response: TutorTurnResponse.Ready,
        exerciseAtRequest: String?,
        attemptId: String?,
    ) {
        if (attemptId == null) return
        if (session.currentExerciseId != null && session.currentExerciseId != exerciseAtRequest) return
        when (val result = study.recordFeedback(
            sessionId = sessionId,
            attemptId = attemptId,
            generationId = response.generationId,
            verdict = response.verdict,
            summary = response.text,
            nextStep = response.nextStep,
            uncertainty = response.uncertainty,
            citations = response.citations,
        )) {
            is StudyFeedbackResult.Recorded -> Unit
            is StudyFeedbackResult.Stale ->
                mutableUiState.value = mutableUiState.value.copy(notice = result.reason)
        }
    }

    /** Stop cancels the persistence-to-generation handoff as well as the provider request. */
    private fun launchLearningTransition(block: suspend () -> Unit) {
        val state = mutableUiState.value
        if (state.loading || state.isSubmitting || state.isGenerating || state.isFinished) return
        cancelTurn("Learning step changed")
        mutableUiState.value = mutableUiState.value.copy(isGenerating = true)
        val ticket = transitionVersion
        transitionJob = viewModelScope.launch {
            try { block() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableUiState.value = mutableUiState.value.copy(notice = "Could not save this learning step. Please try again.")
            } finally {
                if (ticket == transitionVersion && activeRequestId == null) mutableUiState.value = mutableUiState.value.copy(isGenerating = false)
            }
        }
    }

    /** Cancel an in-flight turn; partial output is never presented as a finished response. */
    fun cancelTurn(reason: String = "Cancelled") {
        transitionVersion += 1
        val comparing = cardComparisonJobs.keys.toSet()
        cardComparisonJobs.values.toList().forEach { it.cancel() }
        cardComparisonJobs.clear()
        cardComparisonIds.clear()
        mutableUiState.value = mutableUiState.value.copy(reviewing = mutableUiState.value.reviewing - comparing, comparingCards = emptySet())
        transitionJob?.cancel()
        transitionJob = null
        explanationHandoff=null; handoffTurn=null; sourceReadingFinished=false
        activeRequestId = null
        turnJob?.cancel()
        turnJob = null
        stopReading(reason)
        mutableUiState.value = mutableUiState.value.copy(isGenerating = false, awaitingAnswerSince = null)
    }

    // ---- reader --------------------------------------------------------------------------------

    fun readLearningPrompt(text: String) {
        if (text.isNotBlank()) openReader(text, text.take(80), "$sessionId:guidance:${mutableUiState.value.journey.stage}", null)
    }

    /** Open the reader on a tutor response. */
    fun readTurnAloud(turn: StudyTurn) {
        if (turn.text.isBlank()) return
        openReader(
            text = turn.text,
            label = turn.stage.label(),
            passageId = "${sessionId}:turn:${turn.id}",
            sourceRevision = mutableUiState.value.session?.sourceRevision,
        )
    }

    /** Open the reader on the session source. */
    fun readSourceAloud(objectId: String? = null) {
        openValidatedSource(objectId)
    }

    private fun openValidatedSource(objectId: String? = null, explainAfterReading: Boolean = false) {
        val state = mutableUiState.value
        val session = state.session ?: return
        val chosen = state.sources.firstOrNull { if (objectId == null) !it.excerpt.isNullOrBlank() && !it.stale else it.objectId == objectId } ?: return
        val generation = ++readerSelectionGeneration
        viewModelScope.launch {
            try {
                check(study.getSession(sessionId) == session && study.sourcesStillCurrent(session, state.sources))
                if (generation != readerSelectionGeneration || mutableUiState.value.session != session) return@launch
                check(chosen.coverage != SourceCoverage.MISSING && !chosen.excerpt.isNullOrBlank())
                val passageId = "$sessionId:source:${chosen.objectId}"
                if (!openReader(chosen.excerpt, chosen.title, passageId, chosen.revision, chosen.version)) return@launch
                val opened = mutableUiState.value.reader
                if (explainAfterReading && opened?.passageId == passageId && opened.sourceVersion == chosen.version &&
                    mutableUiState.value.session == session && !mutableUiState.value.isGenerating) {
                    sourceReadingFinished = false
                    requestTutorTurn(session, null, compact = true, handoff = true)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == readerSelectionGeneration) mutableUiState.value = mutableUiState.value.copy(
                notice = "Review changed or unavailable material first") }
        }
    }

    private fun openReader(text: String, label: String, passageId: String, sourceRevision: Long?, sourceVersion: String? = sourceRevision?.toString()): Boolean {
        readerSelectionGeneration++
        if(explanationHandoff != null) cancelTurn("Reading selection changed")
        val segments = SentenceSegmenter.ranges(text)
        val sentences = segments.map { it.text }
        if (sentences.isEmpty()) {
            mutableUiState.value = mutableUiState.value.copy(notice = "There is nothing to read")
            return false
        }
        if (!study.narrationAvailable) {
            mutableUiState.value = mutableUiState.value.copy(notice = "Read aloud is not available on this device route")
            return false
        }
        val state = mutableUiState.value
        state.reader?.let { study.stopNarration("Reader reopened") }
        val reader = ReaderState(
            sourceLabel = label,
            sentences = sentences,
            passageId = passageId,
            sourceRevision = sourceRevision,
            sourceVersion = sourceVersion,
            offsets = segments.map { it.start },
            speed = readerStorage?.getString("study_reader_speed")?.toDoubleOrNull()?.coerceIn(0.6,1.5) ?: 1.0,
            index = if(sourceVersion != null) readerStorage?.getString("study_reader:$passageId:$sourceVersion")?.toIntOrNull()?.coerceIn(0,sentences.lastIndex) ?: 0 else 0,
        )
        mutableUiState.value = state.copy(reader = reader, notice = if(segments.last().end < text.trimEnd().length) "Reading is limited to the first 400 sentences; the remaining source is not narrated" else null)
        speakCurrentSentence(reader)
        return true
    }

    /** Pause/resume. The speech engine has no pause, so resuming replays the current sentence. */
    fun toggleReaderPlayback() {
        val reader = mutableUiState.value.reader ?: return
        if (reader.speaking || reader.preparing) {
            study.stopNarration("Paused by learner")
            rememberReader(reader)
            mutableUiState.value=mutableUiState.value.copy(reader=reader.copy(speaking=false,preparing=false,wordRange=null,playbackGeneration=++readingGeneration))
        } else speakCurrentSentence(reader)
    }
    private fun rememberReader(reader: ReaderState) {
        reader.sourceVersion?.let { readerStorage?.putString("study_reader:${reader.passageId}:$it",reader.index.toString()) }
    }
    fun setReaderSpeed(speed: Double) {
        val reader=mutableUiState.value.reader ?: return
        val next=reader.copy(speed=speed.coerceIn(0.6,1.5))
        readerStorage?.putString("study_reader_speed",next.speed.toString())
        mutableUiState.value=mutableUiState.value.copy(reader=next)
        if(reader.speaking || reader.preparing) { study.stopNarration("Reading speed changed"); speakCurrentSentence(next) }
    }

    fun readerNextSentence() {
        val reader = mutableUiState.value.reader ?: return
        if (!reader.hasNext) return
        moveReaderTo(reader.index + 1)
    }

    fun readerPreviousSentence() {
        val reader = mutableUiState.value.reader ?: return
        if (!reader.hasPrevious) return
        moveReaderTo(reader.index - 1)
    }

    fun setReaderAutoAdvance(enabled: Boolean) {
        val reader = mutableUiState.value.reader ?: return
        mutableUiState.value = mutableUiState.value.copy(reader = reader.copy(autoAdvance = enabled))
    }

    fun closeReader() = stopReading("Reader closed")

    private fun moveReaderTo(index: Int) {
        val state = mutableUiState.value
        val reader = state.reader ?: return
        val moved = reader.copy(index = index.coerceIn(0, reader.sentences.lastIndex), speaking = false, preparing = false)
        mutableUiState.value = state.copy(reader = moved)
        speakCurrentSentence(moved)
    }

    private fun speakCurrentSentence(reader: ReaderState) {
        val sentence = reader.current
        if (sentence.isBlank()) return
        // A source edit invalidates the anchor: stop instead of reading an outdated passage.
        val source = mutableUiState.value.sources.firstOrNull { reader.passageId == "$sessionId:source:${it.objectId}" }
        if (source != null && (source.stale || (source.version != null && reader.sourceVersion != null && source.version != reader.sourceVersion))) {
            mutableUiState.value = mutableUiState.value.copy(
                reader = null,
                notice = "The source changed since this reading started",
            )
            return
        }
        val active=reader.copy(playbackGeneration=++readingGeneration,wordRange=null, speaking=false, preparing=true)
        rememberReader(active)
        val language = if (active.passageId.startsWith("$sessionId:source:")) mutableUiState.value.session?.languages?.assignmentLanguage else mutableUiState.value.session?.languages?.explanationLanguage
        // Publish ownership before calling the narrator: local playback can report
        // Started/Failed synchronously. Never overwrite those callbacks afterward.
        mutableUiState.value = mutableUiState.value.copy(reader = active)
        val started = study.narrate(sentence, sessionId, active.generationId(), active.speed, language)
        if (!started && mutableUiState.value.reader?.generationId() == active.generationId()) {
            mutableUiState.value = mutableUiState.value.copy(
                reader = active.copy(speaking = false, preparing = false),
                notice = "Read aloud could not start",
            )
            return
        }
    }

    private fun onNarrationEvent(event: NarrationEvent) {
        val state = mutableUiState.value
        val reader = state.reader ?: return
        val generationId = when (event) {
            is NarrationEvent.Range -> event.generationId
            is NarrationEvent.Started -> event.generationId
            is NarrationEvent.Completed -> event.generationId
            is NarrationEvent.Cancelled -> event.generationId
            is NarrationEvent.Failed -> event.generationId
        }
        // Ignore callbacks for a sentence the reader has already moved past.
        if (generationId != reader.generationId()) return
        when (event) {
            is NarrationEvent.Range -> {
                if(reader.speaking && event.start >= 0 && event.end > event.start && event.end <= reader.current.length)
                    mutableUiState.value=state.copy(reader=reader.copy(wordRange=event.start until event.end))
            }
            is NarrationEvent.Started -> mutableUiState.value = state.copy(reader = reader.copy(speaking = true, preparing = false))
            is NarrationEvent.Completed -> {
                if (reader.autoAdvance && reader.hasNext) {
                    moveReaderTo(reader.index + 1)
                } else if (reader.autoAdvance) {
                    sourceReadingFinished=true
                    mutableUiState.value = state.copy(
                        reader = reader.copy(speaking = false, preparing = false, wordRange = null),
                        notice = "Finished reading ${reader.sourceLabel}",
                        awaitingAnswerSince = if (reader.passageId.contains(":guidance:") || state.stage == TutorStage.ASK) clock() else null,
                    )
                    finishHandoff()
                } else {
                    mutableUiState.value = state.copy(reader = reader.copy(speaking = false, preparing = false, wordRange = null))
                }
            }
            is NarrationEvent.Cancelled ->
                mutableUiState.value = state.copy(reader = state.reader?.copy(speaking = false, preparing = false, wordRange = null))
            is NarrationEvent.Failed ->
                mutableUiState.value = state.copy(
                    reader = state.reader?.copy(speaking = false, preparing = false, wordRange = null),
                    notice = event.reason,
                )
        }
    }

    fun stopReading(reason: String = "Stopped by learner") {
        readerSelectionGeneration++
        com.halo.app.ui.components.VoiceFieldCancellation.stop()
        mutableUiState.value.reader?.let(::rememberReader)
        if(explanationHandoff != null) { explanationHandoff=null; handoffTurn=null; cancelTurn(reason) }
        study.stopNarration(reason)
        val state = mutableUiState.value
        mutableUiState.value = state.copy(awaitingAnswerSince = null, reader = null)
    }

    // ---- practice and review -------------------------------------------------------------------

    fun setCardAnswer(itemId: String, answer: String) {
        val state = mutableUiState.value
        mutableUiState.value = state.copy(cardAnswers = state.cardAnswers + (itemId to answer.take(1500)))
    }

    /**
     * Check a typed answer against the stored answer. Halo only grades the forms it can verify; when
     * it cannot, the comparison is shown and the learner marks themselves.
     */
    fun checkCardAnswer(itemId: String) {
        val state = mutableUiState.value
        val item = state.practiceItems.firstOrNull { it.id == itemId } ?: return
        val typed = state.cardAnswers[itemId].orEmpty().trim()
        if (typed.isEmpty()) {
            mutableUiState.value = state.copy(notice = "Write your answer first")
            return
        }
        if (itemId in state.reviewing) return
        mutableUiState.value = state.copy(reviewing = state.reviewing + itemId)
        viewModelScope.launch {
            try {
            val verdict = study.deterministicVerdict(item.answer, typed)
            val diff = StudyAnswerDiff.of(item.answer, typed)
            if (verdict == null) {
                mutableUiState.value = mutableUiState.value.copy(
                    cardResults = mutableUiState.value.cardResults + (itemId to CardReviewResult(
                        itemId = itemId,
                        verdict = null,
                        diff = diff,
                        message = "Halo cannot check this answer automatically. Compare both and mark yourself.",
                    )),
                    revealedAnswers = mutableUiState.value.revealedAnswers + itemId,
                )
                return@launch
            }
            val outcome = study.reviewPracticeItem(itemId, verdict, expectedItem = item)
            mutableUiState.value = mutableUiState.value.copy(
                cardResults = mutableUiState.value.cardResults + (itemId to CardReviewResult(
                    itemId = itemId,
                    verdict = verdict,
                    diff = diff,
                    message = when {
                        outcome == null -> "Answer checked, but the schedule could not be updated"
                        outcome.decision.mastered -> "Correct. This card is now marked mastered."
                        verdict -> "Correct. Next review in ${outcome.decision.intervalDays} day(s)."
                        else -> "Not quite. This card goes back to the start of the ladder."
                    },
                    nextReviewIntervalDays = if (verdict && outcome != null && !outcome.decision.mastered)
                        outcome.decision.intervalDays else null,
                )),
                revealedAnswers = mutableUiState.value.revealedAnswers + itemId,
                cardAnswers = mutableUiState.value.cardAnswers - itemId,
            )
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    notice = "Could not check this answer. Your text is still saved.",
                )
            } finally {
                mutableUiState.value = mutableUiState.value.copy(
                    reviewing = mutableUiState.value.reviewing - itemId,
                )
            }
        }
    }

    /** Explicit AI comparison is separate from the local Check action and never updates scheduling. */
    fun compareCardMeaning(itemId: String) {
        val state = mutableUiState.value
        val session = state.session ?: return
        val item = state.practiceItems.firstOrNull { it.id == itemId } ?: return
        val answer = state.cardAnswers[itemId].orEmpty().trim().take(1500)
        val tutor = gateway ?: return
        val sourceId = item.sourceObjectId ?: run {
            mutableUiState.value = state.copy(notice = "This card has no source identity. Compare its stored answer yourself.")
            return
        }
        if (answer.isBlank() || itemId in state.reviewing || state.isGenerating || state.isSubmitting) return
        val ticket = transitionVersion
        val comparisonId = newId()
        cardComparisonIds[itemId] = comparisonId
        mutableUiState.value = state.copy(reviewing = state.reviewing + itemId, comparingCards = state.comparingCards + itemId)
        val comparisonJob = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                val response = kotlinx.coroutines.withTimeout(90_000) { tutor.respond(TutorTurnRequest(
                    session = session, stage = TutorStage.CHECK_ATTEMPT, languages = session.languages,
                    goal = "Compare the meaning of this answer with the stored practice card.",
                    sourceExcerpts = listOf(StudySourceExcerpt(objectId = sourceId,
                        revision = item.sourceRevision, title = "Stored practice card",
                        text = "Question: ${item.question}\nStored answer: ${item.answer}",
                        version = item.sourceVersion ?: item.sourceRevision?.toString())),
                    learnerAttempt = answer, helpAlreadyRevealed = emptyList(),
                    exerciseId = session.currentExerciseId, requestId = newId(),
                    learningTask = LearningTutorTask.CARD_COMPARE)) }
                if (ticket != transitionVersion || cardComparisonIds[itemId] != comparisonId || mutableUiState.value.cardAnswers[itemId].orEmpty().trim() != answer) return@launch
                val current = mutableUiState.value.practiceItems.firstOrNull { it.id == itemId } ?: return@launch
                if (current.question != item.question || current.answer != item.answer || current.sourceVersion != item.sourceVersion) return@launch
                when (response) {
                    is TutorTurnResponse.Ready -> {
                        val raw = response.text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                        require(raw.length <= 12000)
                        val json = org.json.JSONObject(raw)
                        val quote = json.getString("quote")
                        val feedback = json.getString("comparison").trim().take(2500)
                        require(quote.isNotBlank() && answer.contains(quote) && feedback.isNotBlank())
                        val old = mutableUiState.value.cardResults[itemId]
                        val result = (old ?: CardReviewResult(itemId, null, null,
                            "Compare both answers and rate what you remembered.")).copy(
                            meaningComparison = feedback, meaningExecutionLabel = response.executionLabel)
                        mutableUiState.value = mutableUiState.value.copy(
                            cardResults = mutableUiState.value.cardResults + (itemId to result),
                            revealedAnswers = mutableUiState.value.revealedAnswers + itemId)
                    }
                    is TutorTurnResponse.Unavailable -> mutableUiState.value = mutableUiState.value.copy(notice = response.reason)
                    is TutorTurnResponse.Failed -> mutableUiState.value = mutableUiState.value.copy(notice = response.message)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                if (cancelled is kotlinx.coroutines.TimeoutCancellationException)
                    mutableUiState.value = mutableUiState.value.copy(notice = "Comparison took too long. Your answer is kept; compare it yourself.")
                else throw cancelled
            } catch (_: Exception) {
                mutableUiState.value = mutableUiState.value.copy(notice = "Could not compare meaning reliably. Your answer is kept; compare it yourself.")
            } finally {
                if (cardComparisonIds[itemId] == comparisonId) {
                    cardComparisonIds.remove(itemId)
                    cardComparisonJobs.remove(itemId)
                    mutableUiState.value = mutableUiState.value.copy(reviewing = mutableUiState.value.reviewing - itemId,
                        comparingCards = mutableUiState.value.comparingCards - itemId)
                }
            }
        }
        cardComparisonJobs[itemId] = comparisonJob
        comparisonJob.start()
    }

    /** Self-assessment for answers Halo cannot verify; never presented as a verified grade. */
    fun selfAssessCard(itemId: String, known: Boolean) = selfAssessCard(itemId,
        if (known) com.halo.app.study.StudyRecallRating.KNOWN else com.halo.app.study.StudyRecallRating.AGAIN)

    fun selfAssessCard(itemId: String, rating: com.halo.app.study.StudyRecallRating) = viewModelScope.launch {
        val known = rating == com.halo.app.study.StudyRecallRating.KNOWN
        if (itemId in mutableUiState.value.reviewing) return@launch
        val item = mutableUiState.value.practiceItems.firstOrNull { it.id == itemId } ?: return@launch
        mutableUiState.value = mutableUiState.value.copy(reviewing = mutableUiState.value.reviewing + itemId)
        try {
            val outcome = study.reviewPracticeItem(itemId, rating, expectedItem = item)
            mutableUiState.value = mutableUiState.value.copy(
                cardResults = mutableUiState.value.cardResults + (itemId to CardReviewResult(itemId = itemId, verdict = known.takeIf { outcome != null },
                    diff = mutableUiState.value.cardResults[itemId]?.diff,
                    message = if (outcome == null) "The review could not be saved" else if (known) "Recorded as remembered" else if (rating == com.halo.app.study.StudyRecallRating.ALMOST) "Recorded as almost remembered" else "Recorded for an earlier review")),
                revealedAnswers = if (outcome != null) mutableUiState.value.revealedAnswers - itemId else mutableUiState.value.revealedAnswers,
                notice = outcome?.let { "Next review in ${it.decision.intervalDays} day(s)" } ?: "Could not update this card's schedule",
                dueReviewItems = if (outcome != null) mutableUiState.value.dueReviewItems.filterNot { it.id == itemId } else mutableUiState.value.dueReviewItems,
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { mutableUiState.value = mutableUiState.value.copy(notice = "Could not save this review. Try again.") }
        finally { mutableUiState.value = mutableUiState.value.copy(reviewing = mutableUiState.value.reviewing - itemId) }
    }

    fun revealAnswer(itemId: String) {
        mutableUiState.value = mutableUiState.value.copy(
            revealedAnswers = mutableUiState.value.revealedAnswers + itemId,
        )
    }

    fun saveLatestTurnAsCard(question: String) {
        val turn = mutableUiState.value.turns.lastOrNull { it.text.isNotBlank() } ?: run {
            mutableUiState.value = mutableUiState.value.copy(notice = "There is no tutor response to save")
            return
        }
        viewModelScope.launch {
            study.addPracticeDraft(
                sessionId = sessionId,
                question = question,
                answer = turn.text,
                sourceObjectId = turn.citations.firstOrNull()?.sourceObjectId,
                sourceVersion = turn.citations.firstOrNull()?.sourceVersion,
                sourceLocator = turn.citations.firstOrNull()?.locator,
            )
            mutableUiState.value = mutableUiState.value.copy(
                notice = "Saved as a draft card — review it before it enters the queue",
            )
        }
    }

    fun setCardAccepted(itemId: String, accepted: Boolean) = viewModelScope.launch {
        study.setPracticeItemAccepted(itemId, accepted)
    }

    fun reviewCardSource(itemId: String) {
        val card = mutableUiState.value.practiceItems.firstOrNull { it.id == itemId } ?: return
        val source = mutableUiState.value.sources.firstOrNull { it.objectId == card.sourceObjectId }
        mutableUiState.value = mutableUiState.value.copy(notice = "Review ${source?.title ?: "the original source"} in Material. This card references ${card.sourceLocator ?: "its original passage"}.")
    }
    fun deleteCard(itemId: String) = viewModelScope.launch {
        study.deletePracticeItem(itemId)
    }

    /** Learner edits their own card text; this marks the card as user-owned. */
    fun updateCard(itemId: String, question: String, answer: String, accepted: Boolean = true) = viewModelScope.launch {
        study.updatePracticeItem(itemId, question, answer, accepted = accepted)
        mutableUiState.value = mutableUiState.value.copy(notice = "Card updated — Halo will not overwrite it")
    }

    /** Replace generated content only when the learner owns the replacement. */
    fun regenerateCard(itemId: String, question: String, answer: String, force: Boolean = false) =
        viewModelScope.launch {
            when (val result = study.regeneratePracticeItem(itemId, question, answer, force)) {
                is PracticeRegeneration.Updated ->
                    mutableUiState.value = mutableUiState.value.copy(notice = "Card regenerated as a draft")
                is PracticeRegeneration.RefusedUserEdited ->
                    mutableUiState.value = mutableUiState.value.copy(
                        notice = "You edited this card. Change it here instead of overwriting it.",
                    )
                is PracticeRegeneration.Invalid ->
                    mutableUiState.value = mutableUiState.value.copy(notice = result.message)
                is PracticeRegeneration.Missing ->
                    mutableUiState.value = mutableUiState.value.copy(notice = "This card no longer exists")
            }
        }

    fun deleteAllSessionData() = viewModelScope.launch {
        cancelTurn("Session deleted")
        try {
            check(study.deleteSessionData(sessionId))
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { mutableUiState.value = mutableUiState.value.copy(notice = "Session deletion could not finish. Try again.") }
    }

    fun setLearningSetup(level: StudyLearnerLevel, style: StudySupportStyle, policy: StudyKnowledgePolicy) {
        cancelTurn("Learning setup changed")
        viewModelScope.launch { study.updateSetup(sessionId, level, style, policy) }
    }
    fun changeSources(ids: List<String>) {
        val expected = semanticDispatchSession
        val versions = semanticDispatchSources?.mapValues { requireNotNull(it.value) }
        cancelTurn("Active sources changed")
        viewModelScope.launch {
            try {
                check(study.setActiveSources(sessionId, ids, expected, versions))
                if (mutableUiState.value.activeVisualId !in ids) {
                    mutableUiState.value = mutableUiState.value.copy(activeVisualId = null, visualSourceVersion = null, pdfPages = emptyMap())
                    lessons?.saveMaterialFocus(sessionId, null)
                }
                refreshSources()
            }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { mutableUiState.value = mutableUiState.value.copy(notice = "Study material changed or unavailable. Read it again before a fresh choice.") }
        }
    }
    fun restartUpdatedLesson(): Job {
        val expected = semanticDispatchSession
        val versions = semanticDispatchSources
        return viewModelScope.launch { try {
        cancelTurn("Restarting updated lesson")
        when (val result = study.restartWithCurrentSources(sessionId, expected, versions)) {
            is com.halo.app.study.StudyStartResult.Started -> {
                semanticRestartedSessionId = result.session.id
                restarts.send(result.session.id)
            }
            is com.halo.app.study.StudyStartResult.Unavailable -> mutableUiState.value = mutableUiState.value.copy(notice = result.reason.explanation)
        }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { mutableUiState.value = mutableUiState.value.copy(notice = "Study material changed. Read it again before restarting.") }
        }
    }
    fun selectVisual(id: String) {
        val state = mutableUiState.value
        val source = state.sources.firstOrNull { it.objectId == id && !it.stale && it.version != null } ?: return
        if (source.resolved !is ResolvedStudySource.Visual && source.resolved !is ResolvedStudySource.Pdf) return
        val page = (source.resolved as? ResolvedStudySource.Pdf)?.let { pdf ->
            state.pdfPages[id]?.takeIf { state.activeVisualId == id && state.visualSourceVersion == source.version && it in 1..pdf.pageCount }
                ?: pdf.selectedPage.takeIf { it in 1..pdf.pageCount } ?: return
        }
        cancelTurn("Active material changed")
        mutableUiState.value = mutableUiState.value.copy(activeVisualId = id, visualSourceVersion = source.version,
            pdfPages = if (page != null) mutableUiState.value.pdfPages + (id to page) else mutableUiState.value.pdfPages - id)
        persistMaterialFocus(source, page)
    }
    fun selectPdfPage(id: String, page: Int) {
        val reference = mutableUiState.value.sources.firstOrNull { it.objectId == id && !it.stale && it.version != null } ?: return
        val source = reference.resolved as? ResolvedStudySource.Pdf ?: return
        if (page !in 1..source.pageCount) return
        cancelTurn("PDF page changed")
        mutableUiState.value = mutableUiState.value.copy(activeVisualId = id, visualSourceVersion = reference.version, pdfPages = mutableUiState.value.pdfPages + (id to page))
        persistMaterialFocus(mutableUiState.value.sources.first { it.objectId == id }, page)
    }
    private fun persistMaterialFocus(source: StudySourceReference, page: Int?) {
        val version = source.version ?: return
        viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            try { lessons?.saveMaterialFocus(sessionId, StudyMaterialFocus(source.objectId, version, page)) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { mutableUiState.value = mutableUiState.value.copy(notice = "Could not save the material selection. Choose it again after reopening this lesson.") }
        }
    }
    fun findInMaterial(query: String) = viewModelScope.launch {
        val session = mutableUiState.value.session ?: return@launch
        if (query.isBlank()) return@launch
        materialQuery = query
        cancelTurn("Material focus changed")
        val generation = ++sourceReadGeneration
        val sources = study.sourceReferences(session, query, includeBeginning = false)
        val current = mutableUiState.value
        if (generation != sourceReadGeneration || current.session != session) return@launch
        mutableUiState.value = current.copy(sources = sources, notice = "Showing matching text passages; PDFs have no text extractor")
    }
    fun continueWithCurrentSources() {
        val expected = semanticDispatchSession
        val versions = semanticDispatchSources
        cancelTurn("Source identity changed")
        viewModelScope.launch { try {
            check(study.continueWithCurrentSources(sessionId, expected, versions)); refreshSources()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { mutableUiState.value = mutableUiState.value.copy(notice = "Study material changed. Read it again before continuing.") }
        }
    }
    fun createPracticeCards() {
        val session = mutableUiState.value.session ?: return
        val expected = semanticDispatchSession
        val tutor = gateway ?: return
        if (mutableUiState.value.isGenerating) return
        cancelTurn("Creating practice drafts")
        val requestId = newId()
        activeRequestId = requestId
        turnJob = viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(isGenerating = true)
            try {
                check(expected == null || study.getSession(sessionId) == expected) { "Study changed after review" }
                val sources = study.sourceReferences(session)
                if (!study.sourcesStillCurrent(session, sources)) { mutableUiState.value = mutableUiState.value.copy(notice = "Review changed or unavailable material first"); return@launch }
                val excerpts = sources.map { StudySourceExcerpt(it.objectId, it.revision, it.title, it.excerpt.orEmpty(), it.locator, it.coverage, it.omittedCharacters, it.version) }
                val visuals = sources.mapNotNull { it.resolved }.filter { it is ResolvedStudySource.Visual || it is ResolvedStudySource.Pdf }
                val selected = selectedVisual(mutableUiState.value, visuals)
                if (visuals.size > 1 && selected == null) { mutableUiState.value = mutableUiState.value.copy(notice = "Choose active visual material first"); return@launch }
                val visual = selected
                val supplied = excerpts.map { it.copy(locator = if (it.objectId == visual?.objectId && visual is ResolvedStudySource.Pdf) "Page ${visual.selectedPage}" else it.locator, suppliedVisual = it.objectId == visual?.objectId) }
                when (val result = tutor.respond(TutorTurnRequest(session, TutorStage.HINT, session.languages, session.goal,
                    supplied, null, emptyList(), session.currentExerciseId, requestId, visualSource = visual, createCards = true))) {
                    is TutorTurnResponse.Ready -> {
                        if (activeRequestId != requestId || result.generationId != requestId || study.getSession(sessionId) != session) return@launch
                        if (!study.sourcesStillCurrent(session, sources)) {
                            mutableUiState.value = mutableUiState.value.copy(notice = "Study material changed while creating practice. No cards were saved; refresh the material first.")
                            return@launch
                        }
                        val drafts = StudyCardDraftParser.parse(result.text, supplied)
                        if (drafts == null) mutableUiState.value = mutableUiState.value.copy(notice = "Invalid practice draft; no cards were saved")
                        else {
                            study.addGroundedPracticeDrafts(sessionId, drafts)
                            mutableUiState.value = mutableUiState.value.copy(notice = "${drafts.size} drafts created. Review and Keep each card before scheduling.")
                        }
                    }
                    is TutorTurnResponse.Unavailable -> mutableUiState.value = mutableUiState.value.copy(notice = result.reason)
                    is TutorTurnResponse.Failed -> mutableUiState.value = mutableUiState.value.copy(notice = result.message)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { mutableUiState.value = mutableUiState.value.copy(notice = "Study changed or practice generation was unavailable. Review the lesson before another request.") }
            finally { if (activeRequestId == requestId) mutableUiState.value = mutableUiState.value.copy(isGenerating = false) }
        }
    }

    fun refreshSources() = viewModelScope.launch {
        val session = mutableUiState.value.session ?: return@launch
        val generation = ++sourceReadGeneration
        val sources = study.sourceReferences(session)
        val current = mutableUiState.value
        if (generation != sourceReadGeneration || current.session != session) return@launch
        mutableUiState.value = current.copy(sources = sources)
    }

    fun saveSummaryToNotebook(): Job {
        val reviewed = semanticExportState
        return viewModelScope.launch {
        val state = reviewed ?: mutableUiState.value
        if (state.savingSummary || mutableUiState.value.savingSummary || state.loading) return@launch
        val store = notes ?: return@launch
        mutableUiState.value = mutableUiState.value.copy(savingSummary = true)
        try {
            val session = state.session ?: return@launch
            check(study.getSession(sessionId) == session && study.sourcesStillCurrent(session, state.sources)) {
                "Study material changed before saving"
            }
            val copyLanguage = session.languages.explanationLanguage ?: java.util.Locale.getDefault().language
            fun copy(label: String) = com.halo.app.language.StudyJourneyCopy.translate(label, copyLanguage)
            val title = session.goal?.takeIf { it.isNotBlank() } ?: copy("Study notes")
            val body = buildString {
                append("# ").append(title).append("\n\n")

                append(copy("Knowledge policy")).append(": ").append(copy(if (session.knowledgePolicy == StudyKnowledgePolicy.SOURCE_ONLY) "Selected material only" else "Material and general knowledge")).append("\n")
                append(copy("Explanation language")).append(": ").append(session.languages.explanationLanguage?.let { java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.forLanguageTag(copyLanguage)) } ?: copy("Not specified")).append("\n")
                append(copy("Depth")).append(": ").append(copy(when(state.journey.explanationDepth) { StudyExplanationDepth.SIMPLER -> "Simpler explanation"; StudyExplanationDepth.BALANCED -> "Usual detail"; StudyExplanationDepth.DEEPER -> "Deeper explanation" })).append("\n\n")
                append("## ").append(copy("Sources")).append("\n")
                state.sources.forEach { source ->
                    val label = source.title.replace(Regex("[\\[\\]|\\r\\n]"), " ")
                    append("[[").append(source.objectId).append("|").append(label).append("]]\n")
                    append(copy("Version")).append(": ").append(source.version ?: copy("Not specified")).append("\n")
                    val locator = if (source.objectId == state.activeVisualId && source.resolved is ResolvedStudySource.Pdf &&
                        source.version == state.visualSourceVersion) "${copy("Page")} ${state.pdfPages[source.objectId] ?: source.resolved.selectedPage}"
                        else source.locator
                    locator?.let { append(copy("Passage")).append(": ").append(copy(it)).append("\n") }
                }
                append("\n## ").append(copy("My starting point")).append("\n")
                append(if (state.journey.probeSkipped) copy("Introductory question skipped") else state.journey.priorKnowledge).append("\n")
                append("\n## ").append(copy("My reflection")).append("\n").append(state.journey.reflection).append("\n")
                append("\n## ").append(copy("Explored ideas")).append("\n")
                state.journey.branches.forEach { append("- ").append(it).append("\n") }
                append("\n## ").append(copy("Tutor notes")).append("\n")
                state.turns.filter { it.failure == null }.forEach { turn ->
                    append(turn.text).append("\n")
                    turn.citations.forEach { cite ->
                        append(copy("Source")).append(": ").append(cite.sourceTitle ?: cite.sourceObjectId).append(" · ").append(copy("Version")).append(" ").append(cite.sourceVersion ?: cite.sourceRevision ?: copy("Not specified"))
                            .append(" · ").append(copy(cite.locator.orEmpty())).append("\n")
                        if (cite.excerpt.isNotBlank()) append("> ").append(cite.excerpt).append("\n")
                    }
                    turn.uncertainty?.let { append(it).append("\n") }
                    append("\n")
                }
                append("## ").append(copy("Your attempts")).append("\n")
                state.attempts.forEach { append(it.response).append("\n\n") }
                append("## ").append(copy("Practice")).append("\n")
                state.practiceItems.filter { it.accepted }.forEach { append(it.question).append("\n").append(it.answer).append("\n\n") }
            }.let { text ->
                com.halo.app.data.notebook.NotebookDocumentCodec.serialize(
                    com.halo.app.data.notebook.NotebookDocumentCodec.parse(text, newId).blocks)
            }
            val artifact = study.saveSummaryArtifact(sessionId, body, title, store,
                state.journey.summaryNoteId, state.journey.summaryNoteRevision) ?: run {
                mutableUiState.value = mutableUiState.value.copy(notice = "Your saved study note was edited, moved or has an unsaved draft. Open it to keep your changes; Halo has not overwritten it.")
                return@launch
            }
            val noteId = artifact.noteId
            val revision = artifact.revision
            semanticExportedNoteId = noteId
            val linkedJourney = mutableUiState.value.journey.copy(summaryNoteId = noteId, summaryNoteRevision = revision)
            // The canonical Room provenance receipt owns retry identity across process death;
            // the local journey link is a recoverable projection of that saved artifact.
            mutableUiState.value = mutableUiState.value.copy(journey = linkedJourney)
            if (!keepJourney(linkedJourney)) {
                mutableUiState.value = mutableUiState.value.copy(notice = "The study note was saved, but its lesson link could not be saved. Open the note before exporting again.")
            }
            navigation.send(noteId)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { mutableUiState.value = mutableUiState.value.copy(notice = "Could not save study notes. Your session is still available.") }
        finally { mutableUiState.value = mutableUiState.value.copy(savingSummary = false) }
        }
    }

    fun clearNotice() {
        mutableUiState.value = mutableUiState.value.copy(notice = null)
    }

    class Factory(
        private val sessionId: String,
        private val study: StudyUseCases,
        private val gateway: StudyTutorGateway? = null,
        private val notes: NotebookUseCases? = null,
        private val lessons: StudyLessonStore? = null,
        private val workspace: com.halo.app.data.workspace.WorkspaceRepository? = null,
        private val readerStorage: com.halo.sdk.runtime.Storage? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StudyViewModel(sessionId, study, gateway, notes = notes, lessons = lessons, workspace = workspace, readerStorage=readerStorage) as T
    }
}

private fun TutorStage.label(): String = name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
