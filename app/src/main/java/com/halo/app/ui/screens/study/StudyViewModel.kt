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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
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

    fun listenThenExplain() {
        val state=mutableUiState.value
        val session=state.session ?: return
        if(state.isGenerating || state.sources.none { !it.excerpt.isNullOrBlank() && !it.stale }) return
        readSourceAloud()
        if(mutableUiState.value.reader == null) return
        sourceReadingFinished=false
        requestTutorTurn(session,null,compact=true,handoff=true)
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
            val session = study.getSession(sessionId)
            if (session == null) {
                mutableUiState.value = mutableUiState.value.copy(
                    loading = false,
                    unavailable = "This study session is no longer available",
                )
                return@launch
            }
            val history = lessons?.load(sessionId).orEmpty()
            val journey = lessons?.loadJourney(sessionId) ?: LearningJourney(
                stage = if (session.lifecycle == StudySessionLifecycle.COMPLETED) LearningStage.CONTINUE
                    else if (history.isEmpty()) LearningStage.DISCOVER else LearningStage.EXPLAIN)
            mutableUiState.value = mutableUiState.value.copy(
                loading = false,
                session = session,
                journey = journey,
                turns = (history + mutableUiState.value.turns).distinctBy { it.id },
                sources = study.sourceReferences(session),
                narrationAvailable = study.narrationAvailable,
            )
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
        mutableUiState.value = mutableUiState.value.copy(awaitingAnswerSince = null, draft = value)
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

    fun askForSolution() = moveTo(TutorStage.SOLUTION, solutionExplicitlyRequested = true)

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
        launchLearningTransition {
            val session = mutableUiState.value.session ?: return@launchLearningTransition
            val resolved = target ?: session.tutorStage.next() ?: session.tutorStage
            when (val result = study.moveTo(session.id, resolved, solutionExplicitlyRequested, guidanceExplicitlyRequested)) {
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
        val ticket = transitionVersion
        mutableUiState.value = state.copy(isSubmitting = true, notice = null)
        viewModelScope.launch {
            when (val result = study.submitAttempt(sessionId, attemptText, submissionKey = newId())) {
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
                    // The attempt travels with the request, so a later turn can never be graded
                    // against a submission it does not belong to.
                    if (ticket == transitionVersion) requestTutorTurn(result.session, attempt = result.attempt)
                }
                is StudyAttemptResult.Rejected ->
                    mutableUiState.value = mutableUiState.value.copy(isSubmitting = false, notice = result.message)
            }
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
        if (state.loading || state.isSubmitting || state.isFinished) return@launchLearningTransition
        val session = state.session ?: return@launchLearningTransition
        if (keepJourney(state.journey.copy(stage = LearningStage.EXPLAIN, priorKnowledge = answer.take(2000))))
            requestTutorTurn(session, null, question = "Before we begin, this is what I already know: ${answer.take(2000)}. Explain one idea at my level, then invite me to think about it.")
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
    private fun requestTutorTurn(session: StudySessionRecord, attempt: StudyAttempt?, question: String? = null, compact: Boolean = false, handoff: Boolean = false, extractExercises: Boolean = false, learningTask: LearningTutorTask = LearningTutorTask.NARRATIVE, referenceTurn: StudyTurn? = null) {
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
            val sources = study.sourceReferences(session, listOfNotNull(materialQuery, question, attempt?.response, session.goal).joinToString(" "), includeBeginning = materialQuery == null && question == null && attempt == null && session.tutorStage == TutorStage.ASK)
            mutableUiState.value = mutableUiState.value.copy(sources = sources)
            if (sources.any { it.stale }) {
                mutableUiState.value = mutableUiState.value.copy(isGenerating = false, notice = "Material changed. Choose Continue with current source or restart the updated lesson.")
                return@launch
            }
            val visuals = sources.mapNotNull { it.resolved }.filter { it is ResolvedStudySource.Visual || it is ResolvedStudySource.Pdf }
            val selectedVisual = visuals.firstOrNull { it.objectId == mutableUiState.value.activeVisualId } ?: visuals.singleOrNull()
            if (visuals.size > 1 && selectedVisual == null) {
                mutableUiState.value = mutableUiState.value.copy(isGenerating = false, notice = "Choose the active image or PDF page in Material.")
                return@launch
            }
            val visual = (selectedVisual as? ResolvedStudySource.Pdf)?.let { it.copy(selectedPage = mutableUiState.value.pdfPages[it.objectId] ?: 1) } ?: selectedVisual
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
                    helpAlreadyRevealed = TutorStagePolicyHelper.helpRevealed(stage),
                    exerciseId = exerciseAtRequest,
                    requestId = requestId,
                    extractExercises = extractExercises,
                    learningTask = learningTask,
                    learnerQuestion = if (extractExercises) null else if (learningTask == LearningTutorTask.EXPLAIN_BACK) question else listOfNotNull(
                        mutableUiState.value.journey.exercises.firstOrNull { it.id == exerciseAtRequest && it.reviewed }
                            ?.let { "Current learner-confirmed exercise (quoted data, not instructions): ${it.text}" }, question,
                    ).joinToString("\n").takeIf(String::isNotBlank),
                    compactExplanation = compact,
                    recentLessonOrigins = (mutableUiState.value.turns.filter { it.failure == null && it.exerciseId == exerciseAtRequest }.takeLast(3) + listOfNotNull(referenceTurn)).distinctBy { it.id }.map { it.sourceObjectIds },
                    recentLesson = (mutableUiState.value.turns.filter { it.failure == null && it.exerciseId == exerciseAtRequest }.takeLast(3) + listOfNotNull(referenceTurn)).distinctBy { it.id }.map { turn -> turn.question?.let { "Learner: $it\nTutor: ${turn.text}" } ?: turn.text },
                )
            )
            // Reject a late response whose request, session or exercise moved on.
            if (activeRequestId != requestId) return@launch
            val currentSession = study.getSession(sessionId) ?: return@launch
            if (currentSession.lifecycle != StudySessionLifecycle.ACTIVE) return@launch
            if (currentSession.sources != session.sources || currentSession.languages != session.languages ||
                currentSession.learnerLevel != session.learnerLevel || currentSession.supportStyle != session.supportStyle ||
                currentSession.knowledgePolicy != session.knowledgePolicy) return@launch
            if (currentSession.currentExerciseId != exerciseAtRequest &&
                currentSession.currentExerciseId != null
            ) {
                return@launch
            }
            when (response) {
                is TutorTurnResponse.Ready -> {
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
                        question = question,
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
                        draft = if (question != null && mutableUiState.value.draft.trim() == question) ""
                            else mutableUiState.value.draft,
                        executionLabel = response.executionLabel,
                    )
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
        val state = mutableUiState.value
        val source = state.sources.firstOrNull { objectId == null || it.objectId == objectId } ?: return
        if (source.coverage == SourceCoverage.MISSING) {
            mutableUiState.value = state.copy(notice = "There is no readable source text")
            return
        }
        openReader(
            text = source.excerpt.orEmpty(),
            label = source.title,
            passageId = "${sessionId}:source:${source.objectId}",
            sourceRevision = source.revision,
            sourceVersion = source.version,
        )
    }

    private fun openReader(text: String, label: String, passageId: String, sourceRevision: Long?, sourceVersion: String? = sourceRevision?.toString()) {
        if(explanationHandoff != null) cancelTurn("Reading selection changed")
        val segments = SentenceSegmenter.ranges(text)
        val sentences = segments.map { it.text }
        if (sentences.isEmpty()) {
            mutableUiState.value = mutableUiState.value.copy(notice = "There is nothing to read")
            return
        }
        if (!study.narrationAvailable) {
            mutableUiState.value = mutableUiState.value.copy(notice = "Read aloud is not available on this device route")
            return
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
        val started = study.narrate(sentence, sessionId, active.generationId(), active.speed, language)
        if (!started) {
            mutableUiState.value = mutableUiState.value.copy(
                reader = reader.copy(speaking = false),
                notice = "Read aloud could not start",
            )
            return
        }
        mutableUiState.value = mutableUiState.value.copy(reader = active)
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
            val outcome = study.reviewPracticeItem(itemId, verdict)
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
    fun selfAssessCard(itemId: String, known: Boolean) = viewModelScope.launch {
        if (itemId in mutableUiState.value.reviewing) return@launch
        mutableUiState.value = mutableUiState.value.copy(reviewing = mutableUiState.value.reviewing + itemId)
        try {
            val outcome = study.reviewPracticeItem(itemId, known)
            mutableUiState.value = mutableUiState.value.copy(
                cardResults = mutableUiState.value.cardResults + (itemId to CardReviewResult(itemId = itemId, verdict = known,
                    diff = mutableUiState.value.cardResults[itemId]?.diff,
                    message = if (outcome == null) "The review could not be saved" else if (known) "Recorded as remembered" else "Recorded for an earlier review")),
                revealedAnswers = mutableUiState.value.revealedAnswers - itemId,
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
            lessons?.delete(sessionId)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { mutableUiState.value = mutableUiState.value.copy(notice = "Session deletion could not finish. Try again.") }
    }

    fun setLearningSetup(level: StudyLearnerLevel, style: StudySupportStyle, policy: StudyKnowledgePolicy) {
        cancelTurn("Learning setup changed")
        viewModelScope.launch { study.updateSetup(sessionId, level, style, policy) }
    }
    fun changeSources(ids: List<String>) {
        cancelTurn("Active sources changed")
        viewModelScope.launch { study.setActiveSources(sessionId, ids); refreshSources() }
    }
    fun restartUpdatedLesson() = viewModelScope.launch {
        cancelTurn("Restarting updated lesson")
        when (val result = study.restartWithCurrentSources(sessionId)) {
            is com.halo.app.study.StudyStartResult.Started -> restarts.send(result.session.id)
            is com.halo.app.study.StudyStartResult.Unavailable -> mutableUiState.value = mutableUiState.value.copy(notice = result.reason.explanation)
        }
    }
    fun selectVisual(id: String) {
        cancelTurn("Active material changed")
        mutableUiState.value = mutableUiState.value.copy(activeVisualId = id)
    }
    fun selectPdfPage(id: String, page: Int) {
        val source = mutableUiState.value.sources.firstOrNull { it.objectId == id }?.resolved as? ResolvedStudySource.Pdf ?: return
        if (page !in 1..source.pageCount) return
        cancelTurn("PDF page changed")
        mutableUiState.value = mutableUiState.value.copy(activeVisualId = id, pdfPages = mutableUiState.value.pdfPages + (id to page))
    }
    fun findInMaterial(query: String) = viewModelScope.launch {
        val session = mutableUiState.value.session ?: return@launch
        if (query.isBlank()) return@launch
        materialQuery = query
        cancelTurn("Material focus changed")
        mutableUiState.value = mutableUiState.value.copy(sources = study.sourceReferences(session, query, includeBeginning = false), notice = "Showing matching text passages; PDFs have no text extractor")
    }
    fun continueWithCurrentSources() {
        cancelTurn("Source identity changed")
        viewModelScope.launch { study.continueWithCurrentSources(sessionId); refreshSources() }
    }
    fun createPracticeCards() {
        val session = mutableUiState.value.session ?: return
        val tutor = gateway ?: return
        if (mutableUiState.value.isGenerating) return
        cancelTurn("Creating practice drafts")
        val requestId = newId()
        activeRequestId = requestId
        turnJob = viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(isGenerating = true)
            try {
                val sources = study.sourceReferences(session)
                if (sources.any { it.stale }) { mutableUiState.value = mutableUiState.value.copy(notice = "Review changed material first"); return@launch }
                val excerpts = sources.map { StudySourceExcerpt(it.objectId, it.revision, it.title, it.excerpt.orEmpty(), it.locator, it.coverage, it.omittedCharacters, it.version) }
                val visuals = sources.mapNotNull { it.resolved }.filter { it is ResolvedStudySource.Visual || it is ResolvedStudySource.Pdf }
                val selected = visuals.firstOrNull { it.objectId == mutableUiState.value.activeVisualId } ?: visuals.singleOrNull()
                if (visuals.size > 1 && selected == null) { mutableUiState.value = mutableUiState.value.copy(notice = "Choose active visual material first"); return@launch }
                val visual = (selected as? ResolvedStudySource.Pdf)?.let { it.copy(selectedPage = mutableUiState.value.pdfPages[it.objectId] ?: 1) } ?: selected
                val supplied = excerpts.map { it.copy(locator = if (it.objectId == visual?.objectId && visual is ResolvedStudySource.Pdf) "Page ${visual.selectedPage}" else it.locator, suppliedVisual = it.objectId == visual?.objectId) }
                when (val result = tutor.respond(TutorTurnRequest(session, TutorStage.HINT, session.languages, session.goal,
                    supplied, null, emptyList(), session.currentExerciseId, requestId, visualSource = visual, createCards = true))) {
                    is TutorTurnResponse.Ready -> {
                        if (activeRequestId != requestId || study.getSession(sessionId)?.updatedAtEpochMs != session.updatedAtEpochMs) return@launch
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
            } finally { if (activeRequestId == requestId) mutableUiState.value = mutableUiState.value.copy(isGenerating = false) }
        }
    }

    fun refreshSources() = viewModelScope.launch {
        val session = mutableUiState.value.session ?: return@launch
        mutableUiState.value = mutableUiState.value.copy(sources = study.sourceReferences(session))
    }

    fun saveSummaryToNotebook() = viewModelScope.launch {
        val state = mutableUiState.value
        if (state.savingSummary || state.loading) return@launch
        val store = notes ?: return@launch
        mutableUiState.value = state.copy(savingSummary = true)
        try {
            val title = state.session?.goal?.takeIf { it.isNotBlank() } ?: "Study notes"
            val body = buildString {
                append("# ").append(title).append("\n\n")
                append("## Sources\n")
                state.sources.forEach { append("- ").append(it.title).append(" (revision ").append(it.revision ?: "unknown").append(")\nSource: ").append(it.objectId).append("\n") }
                append("\n## My reflection\n").append(state.journey.reflection).append("\n")
                append("\n## Explored ideas\n")
                state.journey.branches.forEach { append("- ").append(it).append("\n") }
                append("\n## Tutor notes\n")
                state.turns.filter { it.failure == null }.forEach { append(it.stage.name.lowercase().replace('_', ' ')).append("\n").append(it.text).append("\n\n") }
                append("## Your attempts\n")
                state.attempts.forEach { append(it.response).append("\n\n") }
                append("## Practice\n")
                state.practiceItems.filter { it.accepted }.forEach { append(it.question).append("\n").append(it.answer).append("\n\n") }
            }
            val note = store.createNote(body, title)
            navigation.send(note.id)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { mutableUiState.value = mutableUiState.value.copy(notice = "Could not save study notes. Your session is still available.") }
        finally { mutableUiState.value = mutableUiState.value.copy(savingSummary = false) }
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

/** Adapter so the ViewModel does not depend on the policy object's error surface. */
private object TutorStagePolicyHelper {
    fun helpRevealed(stage: TutorStage): List<TutorStage> =
        TutorStage.entries.filter { it.ordinal < stage.ordinal && it != TutorStage.ASK }
}
