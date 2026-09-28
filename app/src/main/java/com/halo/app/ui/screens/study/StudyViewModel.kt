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
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {
    val current: String get() = sentences.getOrNull(index).orEmpty()
    val hasNext: Boolean get() = index < sentences.lastIndex
    val hasPrevious: Boolean get() = index > 0
    val progressLabel: String get() = "${index + 1}/${sentences.size}"

    fun generationId(): String = "$passageId:reader:$index"
}

/** Result of checking one card answer. [verdict] is null when Halo cannot check the answer form. */
data class CardReviewResult(
    val itemId: String,
    val verdict: Boolean?,
    val diff: AnswerDiff?,
    val message: String,
)

data class StudyUiState(
    val loading: Boolean = true,
    val unavailable: String? = null,
    val session: StudySessionRecord? = null,
    val sources: List<StudySourceReference> = emptyList(),
    val attempts: List<StudyAttempt> = emptyList(),
    val feedback: List<StudyFeedback> = emptyList(),
    val practiceItems: List<StudyPracticeItem> = emptyList(),
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
) {
    val stage: TutorStage get() = session?.tutorStage ?: TutorStage.ASK
    val isFinished: Boolean get() = session?.lifecycle == StudySessionLifecycle.COMPLETED
    val canSubmitAttempt: Boolean
        get() = !isSubmitting && !isFinished && draft.isNotBlank()

    val dueCards: List<StudyPracticeItem>
        get() = practiceItems.filter { it.accepted && !it.mastered }

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
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = mutableUiState.asStateFlow()

    private var activeRequestId: String? = null
    private var turnJob: Job? = null
    private var generationCounter = 0L

    init {
        viewModelScope.launch {
            val session = study.getSession(sessionId)
            if (session == null) {
                mutableUiState.value = mutableUiState.value.copy(
                    loading = false,
                    unavailable = "This study session is no longer available",
                )
                return@launch
            }
            mutableUiState.value = mutableUiState.value.copy(
                loading = false,
                session = session,
                sources = study.sourceReferences(session),
                narrationAvailable = study.narrationAvailable,
            )
        }
        viewModelScope.launch {
            study.observeSession(sessionId).collect { session ->
                if (session == null) return@collect
                mutableUiState.value = mutableUiState.value.copy(session = session, loading = false)
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
        mutableUiState.value = mutableUiState.value.copy(draft = value)
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

    fun askForSolution() = moveTo(TutorStage.SOLUTION, solutionExplicitlyRequested = true)

    fun finishSession() = viewModelScope.launch {
        stopReading("Session completed")
        when (val result = study.complete(sessionId)) {
            is StudyTransitionResult.Moved ->
                mutableUiState.value = mutableUiState.value.copy(notice = "Session completed")
            is StudyTransitionResult.Rejected ->
                mutableUiState.value = mutableUiState.value.copy(notice = result.message)
        }
    }

    private fun moveTo(target: TutorStage?, solutionExplicitlyRequested: Boolean) {
        viewModelScope.launch {
            val session = mutableUiState.value.session ?: return@launch
            val resolved = target ?: session.tutorStage.next() ?: session.tutorStage
            cancelTurn("Stage changed")
            when (val result = study.moveTo(session.id, resolved, solutionExplicitlyRequested)) {
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
        viewModelScope.launch {
            mutableUiState.value = state.copy(isSubmitting = true, notice = null)
            when (val result = study.submitAttempt(sessionId, attemptText, submissionKey = newId())) {
                is StudyAttemptResult.Recorded -> {
                    mutableUiState.value = mutableUiState.value.copy(
                        isSubmitting = false,
                        draft = "",
                        notice = if (result.attempt.helpRevealed.isEmpty()) {
                            "Attempt saved (no help used)"
                        } else {
                            "Attempt saved after ${result.attempt.helpRevealed.joinToString { it.name.lowercase() }}"
                        },
                    )
                    // The attempt travels with the request, so a later turn can never be graded
                    // against a submission it does not belong to.
                    requestTutorTurn(result.session, attempt = result.attempt)
                }
                is StudyAttemptResult.Rejected ->
                    mutableUiState.value = mutableUiState.value.copy(isSubmitting = false, notice = result.message)
            }
        }
    }

    /** Ask for help at the current stage without submitting an attempt. */
    fun askTutor() {
        val session = mutableUiState.value.session ?: return
        requestTutorTurn(session, attempt = null)
    }

    /**
     * One tutor turn. Passing the [attempt] (rather than reading "the latest attempt" later) makes
     * the graded submission part of the request, so no shared mutable pointer can drift.
     */
    private fun requestTutorTurn(session: StudySessionRecord, attempt: StudyAttempt?) {
        val gateway = gateway
        if (gateway == null || !gateway.available) {
            mutableUiState.value = mutableUiState.value.copy(
                notice = "No AI provider is available. Your work is saved and Study will resume here.",
                isGenerating = false,
            )
            return
        }
        generationCounter += 1
        val requestId = "$sessionId:${session.currentExerciseId ?: "no-exercise"}:$generationCounter"
        activeRequestId = requestId
        val exerciseAtRequest = session.currentExerciseId
        val stage = session.tutorStage
        turnJob?.cancel()
        turnJob = viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(isGenerating = true, notice = null)
            val sources = study.sourceReferences(session)
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
                            locator = source.locator,
                            coverage = source.coverage,
                            omittedCharacters = source.omittedCharacters,
                        )
                    },
                    learnerAttempt = attempt?.response,
                    helpAlreadyRevealed = TutorStagePolicyHelper.helpRevealed(stage),
                    exerciseId = exerciseAtRequest,
                    requestId = requestId,
                )
            )
            // Reject a late response whose request, session or exercise moved on.
            if (activeRequestId != requestId) return@launch
            val currentSession = study.getSession(sessionId) ?: return@launch
            if (currentSession.lifecycle != StudySessionLifecycle.ACTIVE) return@launch
            if (currentSession.currentExerciseId != exerciseAtRequest &&
                currentSession.currentExerciseId != null
            ) {
                return@launch
            }
            when (response) {
                is TutorTurnResponse.Ready -> {
                    val turn = StudyTurn(
                        id = response.generationId,
                        stage = stage,
                        text = response.text,
                        citations = response.citations,
                        verdict = response.verdict,
                        nextStep = response.nextStep,
                        uncertainty = response.uncertainty,
                    )
                    mutableUiState.value = mutableUiState.value.copy(
                        isGenerating = false,
                        turns = mutableUiState.value.turns + turn,
                    )
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

    /** Cancel an in-flight turn; partial output is never presented as a finished response. */
    fun cancelTurn(reason: String = "Cancelled") {
        activeRequestId = null
        turnJob?.cancel()
        turnJob = null
        stopReading(reason)
        mutableUiState.value = mutableUiState.value.copy(isGenerating = false)
    }

    // ---- reader --------------------------------------------------------------------------------

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
    fun readSourceAloud() {
        val state = mutableUiState.value
        val source = state.sources.firstOrNull() ?: return
        if (source.coverage == SourceCoverage.MISSING) {
            mutableUiState.value = state.copy(notice = "There is no readable source text")
            return
        }
        openReader(
            text = source.excerpt.orEmpty(),
            label = source.title,
            passageId = "${sessionId}:source:${source.objectId}",
            sourceRevision = source.revision,
        )
    }

    private fun openReader(text: String, label: String, passageId: String, sourceRevision: Long?) {
        val sentences = SentenceSegmenter.split(text)
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
        )
        mutableUiState.value = state.copy(reader = reader, notice = null)
        speakCurrentSentence(reader)
    }

    /** Pause/resume. The speech engine has no pause, so resuming replays the current sentence. */
    fun toggleReaderPlayback() {
        val reader = mutableUiState.value.reader ?: return
        if (reader.speaking) stopReading("Paused by learner") else speakCurrentSentence(reader)
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
        val moved = reader.copy(index = index.coerceIn(0, reader.sentences.lastIndex), speaking = false)
        mutableUiState.value = state.copy(reader = moved)
        speakCurrentSentence(moved)
    }

    private fun speakCurrentSentence(reader: ReaderState) {
        val sentence = reader.current
        if (sentence.isBlank()) return
        // A source edit invalidates the anchor: stop instead of reading an outdated passage.
        val session = mutableUiState.value.session
        if (reader.passageId.startsWith("$sessionId:source") && session?.sourceRevision != null &&
            reader.sourceRevision != null && session.sourceRevision != reader.sourceRevision
        ) {
            mutableUiState.value = mutableUiState.value.copy(
                reader = null,
                notice = "The source changed since this reading started",
            )
            return
        }
        val started = study.narrate(sentence, sessionId, reader.generationId())
        if (!started) {
            mutableUiState.value = mutableUiState.value.copy(
                reader = reader.copy(speaking = false),
                notice = "Read aloud could not start",
            )
            return
        }
        mutableUiState.value = mutableUiState.value.copy(reader = reader.copy(speaking = true))
    }

    private fun onNarrationEvent(event: NarrationEvent) {
        val state = mutableUiState.value
        val reader = state.reader ?: return
        val generationId = when (event) {
            is NarrationEvent.Started -> event.generationId
            is NarrationEvent.Completed -> event.generationId
            is NarrationEvent.Cancelled -> event.generationId
            is NarrationEvent.Failed -> event.generationId
        }
        // Ignore callbacks for a sentence the reader has already moved past.
        if (generationId != reader.generationId()) return
        when (event) {
            is NarrationEvent.Started -> Unit
            is NarrationEvent.Completed -> {
                if (reader.autoAdvance && reader.hasNext) {
                    moveReaderTo(reader.index + 1)
                } else if (reader.autoAdvance) {
                    mutableUiState.value = state.copy(
                        reader = reader.copy(speaking = false),
                        notice = "Finished reading ${reader.sourceLabel}",
                    )
                } else {
                    mutableUiState.value = state.copy(reader = reader.copy(speaking = false))
                }
            }
            is NarrationEvent.Cancelled ->
                mutableUiState.value = state.copy(reader = state.reader?.copy(speaking = false))
            is NarrationEvent.Failed ->
                mutableUiState.value = state.copy(
                    reader = state.reader?.copy(speaking = false),
                    notice = event.reason,
                )
        }
    }

    fun stopReading(reason: String = "Stopped by learner") {
        study.stopNarration(reason)
        val state = mutableUiState.value
        mutableUiState.value = state.copy(reader = null)
    }

    // ---- practice and review -------------------------------------------------------------------

    fun setCardAnswer(itemId: String, answer: String) {
        val state = mutableUiState.value
        mutableUiState.value = state.copy(cardAnswers = state.cardAnswers + (itemId to answer))
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
        viewModelScope.launch {
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
        }
    }

    /** Self-assessment for answers Halo cannot verify; never presented as a verified grade. */
    fun selfAssessCard(itemId: String, known: Boolean) = viewModelScope.launch {
        val outcome = study.reviewPracticeItem(itemId, known)
        mutableUiState.value = mutableUiState.value.copy(
            cardResults = mutableUiState.value.cardResults + (itemId to CardReviewResult(
                itemId = itemId,
                verdict = known,
                diff = mutableUiState.value.cardResults[itemId]?.diff,
                message = if (known) "Recorded as remembered" else "Recorded for an earlier review",
            )),
            revealedAnswers = mutableUiState.value.revealedAnswers - itemId,
            notice = outcome?.let { "Next review in ${it.decision.intervalDays} day(s)" },
        )
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
                sourceObjectId = mutableUiState.value.session?.sourceObjectIds?.firstOrNull(),
            )
            mutableUiState.value = mutableUiState.value.copy(
                notice = "Saved as a draft card — review it before it enters the queue",
            )
        }
    }

    fun setCardAccepted(itemId: String, accepted: Boolean) = viewModelScope.launch {
        study.setPracticeItemAccepted(itemId, accepted)
    }

    fun deleteCard(itemId: String) = viewModelScope.launch {
        study.deletePracticeItem(itemId)
    }

    /** Learner edits their own card text; this marks the card as user-owned. */
    fun updateCard(itemId: String, question: String, answer: String) = viewModelScope.launch {
        study.updatePracticeItem(itemId, question, answer, accepted = true)
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
        cancelTurn("Deleting study data")
        study.deleteSessionData(sessionId)
    }

    fun clearNotice() {
        mutableUiState.value = mutableUiState.value.copy(notice = null)
    }

    class Factory(
        private val sessionId: String,
        private val study: StudyUseCases,
        private val gateway: StudyTutorGateway? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StudyViewModel(sessionId, study, gateway) as T
    }
}

private fun TutorStage.label(): String = name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

/** Adapter so the ViewModel does not depend on the policy object's error surface. */
private object TutorStagePolicyHelper {
    fun helpRevealed(stage: TutorStage): List<TutorStage> =
        TutorStage.entries.filter { it.ordinal < stage.ordinal && it != TutorStage.ASK }
}
