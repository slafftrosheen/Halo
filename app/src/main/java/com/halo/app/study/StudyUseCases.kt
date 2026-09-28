package com.halo.app.study

import com.halo.app.data.notebook.NotebookDocumentRepository
import com.halo.app.data.study.StudyAttempt
import com.halo.app.data.study.StudyCitation
import com.halo.app.data.study.StudyFeedback
import com.halo.app.data.study.StudyPracticeItem
import com.halo.app.data.study.StudyRepository
import com.halo.app.data.study.StudyReviewEvent
import com.halo.app.data.study.StudySessionRecord
import com.halo.app.data.study.StudyVerdict
import com.halo.sdk.runtime.study.StudyLanguagePreferences
import com.halo.sdk.runtime.study.StudySession
import com.halo.sdk.runtime.study.StudySessionLifecycle
import com.halo.sdk.runtime.study.TutorStage
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Text-to-speech handoff for Study.
 *
 * Study never owns a TTS engine: it publishes through the existing speech routing so output target,
 * leases and route loss stay the host's responsibility.
 */
interface StudyNarrationController {
    val available: Boolean

    /** Playback lifecycle, filtered to the utterance this controller asked for. */
    val events: Flow<NarrationEvent>

    /** [generationId] scopes the request so a late callback can never read an older passage. */
    fun narrate(text: String, sessionId: String, generationId: String)

    fun stop(reason: String)
}

sealed interface NarrationEvent {
    data class Started(val generationId: String) : NarrationEvent

    data class Completed(val generationId: String) : NarrationEvent

    data class Cancelled(val generationId: String) : NarrationEvent

    data class Failed(val generationId: String, val reason: String) : NarrationEvent
}

/**
 * Narrow seam used by host tools: starting a study session must go through the same durable use
 * case as the UI, and a tool may never report success for a session that was not saved.
 */
fun interface StudySessionStarter {
    suspend fun start(sourceObjectId: String, interactionId: String?): StudyStartResult
}

/**
 * How much of a source actually reached the model. Reported to the learner instead of quietly
 * presenting a truncated passage as the whole document.
 */
enum class SourceCoverage { FULL, PARTIAL, MISSING }

/** A source reference that carries locator, excerpt, coverage and extraction provenance. */
data class StudySourceReference(
    val objectId: String,
    val revision: Long?,
    val title: String,
    val excerpt: String?,
    val locator: String? = null,
    val stale: Boolean = false,
    val coverage: SourceCoverage = SourceCoverage.FULL,
    val totalCharacters: Int = 0,
    val omittedCharacters: Int = 0,
)

data class StartStudyRequest(
    val sourceObjectId: String,
    val assignmentLanguage: String? = null,
    val explanationLanguage: String? = null,
    val strongestLanguage: String? = null,
    val goal: String? = null,
    val conversationId: String? = null,
    val runId: String? = null,
    val interactionId: String? = null,
    val actor: StudyStarter = StudyStarter.USER,
)

enum class StudyStarter { USER, TOOL, NOTEBOOK }

enum class StudyUnavailableReason {
    SOURCE_NOT_FOUND,
    SOURCE_IN_TRASH,
    PERSISTENCE_FAILED,
    SOURCE_STORE_UNAVAILABLE,
    ;

    val explanation: String
        get() = when (this) {
            SOURCE_NOT_FOUND -> "The study source no longer exists"
            SOURCE_IN_TRASH -> "The study source is in trash"
            PERSISTENCE_FAILED -> "Halo could not save the study session, so it was not started"
            SOURCE_STORE_UNAVAILABLE -> "Halo cannot read the study source on this device"
        }
}

sealed interface StudyStartResult {
    data class Started(val session: StudySessionRecord) : StudyStartResult

    data class Unavailable(val reason: StudyUnavailableReason) : StudyStartResult
}

sealed interface StudyTransitionResult {
    data class Moved(val session: StudySessionRecord, val move: StageMove) : StudyTransitionResult

    data class Rejected(val message: String) : StudyTransitionResult
}

sealed interface StudyAttemptResult {
    data class Recorded(
        val session: StudySessionRecord,
        val attempt: StudyAttempt,
        /** Deterministic check result, or null when the answer form is not supported. */
        val deterministicVerdict: Boolean?,
    ) : StudyAttemptResult

    data class Rejected(val message: String) : StudyAttemptResult
}

sealed interface StudyFeedbackResult {
    data class Recorded(val feedback: StudyFeedback) : StudyFeedbackResult

    /** The response belongs to an older exercise or attempt and must not be shown or saved. */
    data class Stale(val reason: String) : StudyFeedbackResult
}

/** One reviewed card: what the ladder decided and what that means for measured mastery. */
data class ReviewOutcome(val decision: ReviewDecision, val mastery: StudyMasterySnapshot)

sealed interface PracticeRegeneration {
    data object Updated : PracticeRegeneration

    data object Missing : PracticeRegeneration

    /** The learner owns this card's text; a silent overwrite is refused. */
    data object RefusedUserEdited : PracticeRegeneration

    data class Invalid(val message: String) : PracticeRegeneration
}

/**
 * Guided Study over a durable session.
 *
 * Every write is idempotent or revision-bound: starting persists before reporting success, attempts
 * carry a submission key, and feedback is validated against the exercise that is actually active.
 */
class StudyUseCases(
    private val repository: StudyRepository,
    private val documents: NotebookDocumentRepository? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val reviewPolicy: StudyReviewPolicy = StudyReviewPolicy(),
    private val narration: StudyNarrationController? = null,
    private val reviewIntensity: StudyReviewIntensityController? = null,
) {
    val narrationAvailable: Boolean get() = narration?.available == true

    /**
     * Learner-selected retention target. It is read synchronously inside a review, which is why the
     * controller mirrors the stored preference instead of suspending on a settings read.
     */
    private fun desiredRetention(): Double =
        reviewIntensity?.currentRetention() ?: reviewPolicy.desiredRetention()

    /** Observable review intensity, or a single default value when Study has no settings store. */
    fun observeReviewIntensity(): Flow<StudyReviewIntensity> =
        reviewIntensity?.intensity ?: flowOf(StudyReviewIntensity.DEFAULT)

    /** Persists a new intensity. Existing due dates follow the memory model immediately. */
    suspend fun setReviewIntensity(value: StudyReviewIntensity): Boolean =
        reviewIntensity?.select(value) ?: false

    /** Playback lifecycle from the host speech route; empty when narration is not wired. */
    val narrationEvents: Flow<NarrationEvent> get() = narration?.events ?: emptyFlow()

    fun observeActiveSessions(): Flow<List<StudySessionRecord>> = repository.observeActiveSessions()

    fun observeSessionsForSource(objectId: String): Flow<List<StudySessionRecord>> =
        repository.observeSessionsForSource(objectId)

    fun observeSession(sessionId: String): Flow<StudySessionRecord?> = repository.observeSession(sessionId)

    fun observeAttempts(sessionId: String): Flow<List<StudyAttempt>> = repository.observeAttempts(sessionId)

    fun observeFeedback(sessionId: String): Flow<List<StudyFeedback>> = repository.observeFeedback(sessionId)

    fun observePracticeItems(sessionId: String): Flow<List<StudyPracticeItem>> =
        repository.observePracticeItems(sessionId)

    fun observeDuePracticeItems(limit: Int = 50): Flow<List<StudyPracticeItem>> =
        repository.observeDuePracticeItems(limit)

    suspend fun getSession(sessionId: String): StudySessionRecord? = repository.getSession(sessionId)

    suspend fun sourceRevision(objectId: String): Long? = documents?.load(objectId)?.revision

    /** Start only succeeds after the session is saved and can be reopened. */
    suspend fun start(request: StartStudyRequest): StudyStartResult {
        val sourceId = request.sourceObjectId.trim()
        if (sourceId.isEmpty()) return StudyStartResult.Unavailable(StudyUnavailableReason.SOURCE_NOT_FOUND)
        val documentStore = documents
            ?: return StudyStartResult.Unavailable(StudyUnavailableReason.SOURCE_STORE_UNAVAILABLE)
        val document = documentStore.load(sourceId)
            ?: return StudyStartResult.Unavailable(StudyUnavailableReason.SOURCE_NOT_FOUND)
        if (!document.isWritable) return StudyStartResult.Unavailable(StudyUnavailableReason.SOURCE_IN_TRASH)

        return runCatching {
            repository.createSession(
                sourceObjectIds = listOf(sourceId),
                sourceRevision = document.revision,
                languages = StudyLanguagePreferences(
                    assignmentLanguage = request.assignmentLanguage,
                    explanationLanguage = request.explanationLanguage,
                    strongestLanguage = request.strongestLanguage,
                ),
                goal = request.goal,
                conversationId = request.conversationId,
                runId = request.runId,
                interactionId = request.interactionId,
            )
        }.fold(
            onSuccess = { StudyStartResult.Started(it) },
            onFailure = { StudyStartResult.Unavailable(StudyUnavailableReason.PERSISTENCE_FAILED) },
        )
    }

    /** Resume restores the exact stage and exercise, or reports that there is nothing to resume. */
    suspend fun resume(sessionId: String): StudySessionRecord? = repository.getSession(sessionId)

    suspend fun moveTo(
        sessionId: String,
        target: TutorStage,
        solutionExplicitlyRequested: Boolean = false,
    ): StudyTransitionResult {
        val session = repository.getSession(sessionId)
            ?: return StudyTransitionResult.Rejected("This study session is no longer available")
        if (session.lifecycle != StudySessionLifecycle.ACTIVE) {
            return StudyTransitionResult.Rejected("This study session is already finished")
        }
        val move = TutorStagePolicy.classify(session.tutorStage, target, solutionExplicitlyRequested)
        if (move == StageMove.REJECTED) {
            return StudyTransitionResult.Rejected(
                if (target.isSolution) {
                    "Ask for the full solution explicitly before Study reveals it"
                } else {
                    "Study moves one step at a time; use the guided next step"
                }
            )
        }
        val exerciseId = session.currentExerciseId ?: newExerciseId(sessionId)
        val solutionRevealed = session.solutionRevealed || target.isSolution
        repository.updateProgress(
            sessionId = sessionId,
            stage = target,
            exerciseId = exerciseId,
            solutionRevealed = solutionRevealed,
            lifecycle = session.lifecycle,
            completedAtEpochMs = session.completedAtEpochMs,
        )
        val updated = repository.getSession(sessionId)
            ?: return StudyTransitionResult.Rejected("This study session is no longer available")
        return StudyTransitionResult.Moved(updated, move)
    }

    /** The single guided step; falls back to the current stage when the progression is complete. */
    suspend fun advanceGuided(sessionId: String): StudyTransitionResult {
        val session = repository.getSession(sessionId)
            ?: return StudyTransitionResult.Rejected("This study session is no longer available")
        val next = TutorStagePolicy.next(session.tutorStage) ?: session.tutorStage
        return moveTo(sessionId, next)
    }

    suspend fun setPreferences(
        sessionId: String,
        languages: StudyLanguagePreferences,
        goal: String? = null,
    ): Boolean = repository.updatePreferences(
        sessionId = sessionId,
        assignmentLanguage = languages.assignmentLanguage,
        explanationLanguage = languages.explanationLanguage,
        strongestLanguage = languages.strongestLanguage,
        goal = goal,
    )

    suspend fun ensureExercise(sessionId: String): String? {
        val session = repository.getSession(sessionId) ?: return null
        session.currentExerciseId?.let { return it }
        val exerciseId = newExerciseId(sessionId)
        repository.updateProgress(
            sessionId = sessionId,
            stage = session.tutorStage,
            exerciseId = exerciseId,
            solutionRevealed = session.solutionRevealed,
            lifecycle = session.lifecycle,
            completedAtEpochMs = session.completedAtEpochMs,
        )
        return exerciseId
    }

    /**
     * A learner may submit an attempt at any stage. The attempt is persisted locally before any AI
     * involvement, so an offline attempt survives and is never replayed by the runtime.
     */
    suspend fun submitAttempt(
        sessionId: String,
        response: String,
        submissionKey: String? = null,
        exerciseId: String? = null,
    ): StudyAttemptResult {
        val session = repository.getSession(sessionId)
            ?: return StudyAttemptResult.Rejected("This study session is no longer available")
        if (session.lifecycle != StudySessionLifecycle.ACTIVE) {
            return StudyAttemptResult.Rejected("This study session is already finished")
        }
        val text = response.trim()
        if (text.isEmpty()) return StudyAttemptResult.Rejected("Write your attempt before submitting")
        val activeExercise = exerciseId
            ?: session.currentExerciseId
            ?: newExerciseId(sessionId).also { generated ->
                repository.updateProgress(
                    sessionId = sessionId,
                    stage = session.tutorStage,
                    exerciseId = generated,
                    solutionRevealed = session.solutionRevealed,
                    lifecycle = session.lifecycle,
                    completedAtEpochMs = session.completedAtEpochMs,
                )
            }
        val helpRevealed = TutorStagePolicy.helpRevealed(
            TutorStage.entries.filter { it.ordinal <= session.tutorStage.ordinal }
        )
        val attempt = repository.recordAttempt(
            sessionId = sessionId,
            exerciseId = activeExercise,
            response = text.take(4_000),
            stage = session.tutorStage,
            helpRevealed = helpRevealed,
            submissionKey = submissionKey ?: newId(),
        )
        val updated = repository.getSession(sessionId)
            ?: return StudyAttemptResult.Rejected("This study session is no longer available")
        return StudyAttemptResult.Recorded(
            session = updated,
            attempt = attempt,
            deterministicVerdict = null,
        )
    }

    /**
     * Record model feedback only for the attempt that is still active. A late response for an older
     * exercise is refused rather than displayed against the learner's current work.
     */
    suspend fun recordFeedback(
        sessionId: String,
        attemptId: String,
        generationId: String,
        verdict: StudyVerdict,
        summary: String,
        nextStep: String,
        uncertainty: String? = null,
        citations: List<StudyCitation> = emptyList(),
    ): StudyFeedbackResult {
        val session = repository.getSession(sessionId)
            ?: return StudyFeedbackResult.Stale("This study session is no longer available")
        val attempts = repository.getAttempts(sessionId)
        val attempt = attempts.firstOrNull { it.id == attemptId }
            ?: return StudyFeedbackResult.Stale("This attempt is no longer part of the session")
        val activeExercise = session.currentExerciseId
        if (activeExercise != null && attempt.exerciseId != activeExercise) {
            return StudyFeedbackResult.Stale("The learner moved to a newer exercise")
        }
        if (summary.isBlank() && nextStep.isBlank()) {
            return StudyFeedbackResult.Stale("Feedback was empty")
        }
        return StudyFeedbackResult.Recorded(
            repository.recordFeedback(
                sessionId = sessionId,
                attemptId = attemptId,
                generationId = generationId,
                verdict = verdict,
                summary = summary.take(4_000),
                nextStep = nextStep.take(1_000),
                uncertainty = uncertainty?.take(600),
                citations = citations.take(6),
            )
        )
    }

    /** Deterministic check for supported exact answers; null means "not validated by Halo". */
    fun deterministicVerdict(expected: String, attempt: String): Boolean? =
        StudyAnswerChecker.check(expected, attempt)

    suspend fun addPracticeDraft(
        sessionId: String,
        question: String,
        answer: String,
        sourceObjectId: String?,
    ): StudyPracticeItem? {
        if (question.isBlank() || answer.isBlank()) return null
        val session = repository.getSession(sessionId) ?: return null
        return repository.addPracticeDraft(
            sessionId = sessionId,
            question = question,
            answer = answer,
            sourceObjectId = sourceObjectId ?: session.sourceObjectIds.firstOrNull(),
            sourceRevision = session.sourceRevision,
        )
    }

    suspend fun setPracticeItemAccepted(itemId: String, accepted: Boolean): Boolean =
        repository.setPracticeItemAccepted(itemId, accepted)

    suspend fun updatePracticeItem(itemId: String, question: String, answer: String, accepted: Boolean): Boolean =
        repository.updatePracticeItem(itemId, question, answer, accepted)

    suspend fun deletePracticeItem(itemId: String): Boolean = repository.deletePracticeItem(itemId)

    /**
     * Record one review pass. The ladder decision and the immutable review event are stored together,
     * so what the learner sees as "mastery" can always be recomputed from the event log.
     */
    suspend fun reviewPracticeItem(itemId: String, known: Boolean): ReviewOutcome? {
        val item = repository.getPracticeItem(itemId) ?: return null
        val currentSourceRevision = item.sourceObjectId?.let { documents?.load(it)?.revision }
        val decision = reviewPolicy.schedule(item, known, now())
        repository.recordReview(
            item = item,
            decision = decision,
            sourceRevision = currentSourceRevision ?: item.sourceRevision,
        )
        val updated = repository.getPracticeItem(itemId) ?: item
        val history = repository.getReviewEvents(itemId)
        return ReviewOutcome(
            decision = decision,
            mastery = StudyMasteryModel.snapshot(
                events = history,
                now = now(),
                desiredRetention = desiredRetention(),
                ladderDueAtEpochMs = updated.dueAtEpochMs,
            ),
        )
    }

    /**
     * Replace a generated card's content. A card the learner has edited is never overwritten unless
     * the caller passes [force], mirroring "do not implicitly overwrite user edits".
     */
    suspend fun regeneratePracticeItem(
        itemId: String,
        question: String,
        answer: String,
        force: Boolean = false,
    ): PracticeRegeneration {
        val item = repository.getPracticeItem(itemId) ?: return PracticeRegeneration.Missing
        if (item.userEdited && !force) return PracticeRegeneration.RefusedUserEdited
        if (question.isBlank() || answer.isBlank()) return PracticeRegeneration.Invalid("Question and answer are required")
        repository.updatePracticeItem(itemId, question, answer, accepted = false)
        return PracticeRegeneration.Updated
    }

    suspend fun reviewEvents(itemId: String): List<StudyReviewEvent> = repository.getReviewEvents(itemId)

    /** Review history for a whole session, used to derive mastery for every card. */
    fun observeReviewEvents(sessionId: String): Flow<List<StudyReviewEvent>> =
        repository.observeReviewEvents(sessionId)

    /**
     * Mastery for each card, recomputed from stored review events. Items with no history report
     * "not measured" rather than a default score, so an untouched card never looks learned.
     */
    fun masteryFor(
        items: List<StudyPracticeItem>,
        events: List<StudyReviewEvent>,
        atEpochMs: Long = now(),
    ): Map<String, StudyMasterySnapshot> {
        if (items.isEmpty()) return emptyMap()
        val byItem = events.groupBy(StudyReviewEvent::itemId)
        return items.associate { item ->
            item.id to StudyMasteryModel.snapshot(
                events = byItem[item.id].orEmpty(),
                now = atEpochMs,
                desiredRetention = desiredRetention(),
                ladderDueAtEpochMs = item.dueAtEpochMs,
            )
        }
    }

    suspend fun complete(sessionId: String): StudyTransitionResult {
        val session = repository.getSession(sessionId)
            ?: return StudyTransitionResult.Rejected("This study session is no longer available")
        repository.updateProgress(
            sessionId = sessionId,
            stage = session.tutorStage,
            exerciseId = session.currentExerciseId,
            solutionRevealed = session.solutionRevealed,
            lifecycle = StudySessionLifecycle.COMPLETED,
            completedAtEpochMs = now(),
        )
        narration?.stop("Study session completed")
        val updated = repository.getSession(sessionId)
            ?: return StudyTransitionResult.Rejected("This study session is no longer available")
        return StudyTransitionResult.Moved(updated, StageMove.REPEAT)
    }

    /** Removes attempts, feedback, practice items and review history for one session. */
    suspend fun deleteSessionData(sessionId: String): Boolean {
        narration?.stop("Study data deleted")
        return repository.deleteSessionData(sessionId)
    }

    /**
     * Read-aloud is opt-in and waits for a current response: callers pass the generation id of the
     * text they are reading so a late callback cannot narrate an older passage.
     */
    fun narrate(text: String, sessionId: String, generationId: String): Boolean {
        val controller = narration ?: return false
        if (!controller.available || text.isBlank()) return false
        controller.narrate(text, sessionId, generationId)
        return true
    }

    fun stopNarration(reason: String) {
        narration?.stop(reason)
    }

    /**
     * Sources with their extraction provenance and an honest coverage report: the excerpt shown here
     * is exactly the budget the tutor prompt uses, and the omitted length is surfaced rather than
     * hidden.
     */
    suspend fun sourceReferences(session: StudySessionRecord): List<StudySourceReference> =
        session.sourceObjectIds.map { objectId ->
            val document = documents?.load(objectId)
            val fullText = document?.blocks
                ?.joinToString("\n") { it.text }
                .orEmpty()
                .trim()
            val omitted = (fullText.length - SOURCE_BUDGET_CHARACTERS).coerceAtLeast(0)
            StudySourceReference(
                objectId = objectId,
                revision = document?.revision,
                title = document?.title ?: objectId,
                excerpt = fullText.take(SOURCE_BUDGET_CHARACTERS).takeIf { it.isNotBlank() },
                locator = null,
                stale = document != null && session.sourceRevision != null &&
                    document.revision != session.sourceRevision,
                coverage = when {
                    document == null -> SourceCoverage.MISSING
                    fullText.isBlank() -> SourceCoverage.MISSING
                    omitted > 0 -> SourceCoverage.PARTIAL
                    else -> SourceCoverage.FULL
                },
                totalCharacters = fullText.length,
                omittedCharacters = omitted,
            )
        }

    /**
     * Portable, Android-free view of a session. The runtime sees this contract, never the app's
     * storage record.
     */
    suspend fun portableSession(sessionId: String): StudySession? =
        repository.getSession(sessionId)?.toPortable()

    /** Short, model-readable description of a session for host tools. */
    suspend fun describeSession(sessionId: String): String? {
        val record = repository.getSession(sessionId) ?: return null
        val session: StudySession = record.toPortable()
        val items = repository.getPracticeItems(sessionId)
        val mastery = masteryFor(items, repository.getReviewEventsForSession(sessionId))
        val measured = mastery.values.filter(StudyMasterySnapshot::isMeasured)
        val averageMastery = if (measured.isEmpty()) 0 else {
            (measured.sumOf(StudyMasterySnapshot::masteredProbability) / measured.size * 100).toInt()
        }
        return buildString {
            append("Study session ${session.id} (${session.lifecycle.name.lowercase()})")
            session.goal?.takeIf { it.isNotBlank() }?.let { append("\nGoal: $it") }
            append("\nStage: ${session.tutorStage.name}")
            append("\nSources: ${session.sourceObjectIds.joinToString()}")
            session.sourceRevision?.let { append(" (revision $it)") }
            append("\nLanguages: assignment=${session.assignmentLanguage ?: "unspecified"}, " +
                "explanation=${session.explanationLanguage ?: "unspecified"}, " +
                "strongest=${session.strongestLanguage ?: "unspecified"}")
            val intensity = reviewIntensity?.current() ?: StudyReviewIntensity.DEFAULT
            append("\nReview intensity: ${intensity.label} (target ${intensity.retentionPercent}% retention)")
            append("\nAttempts: ${repository.getAttempts(sessionId).size}")
            append("\nPractice cards: ${items.count { it.accepted }} accepted, ${items.size} total")
            if (measured.isNotEmpty()) append("\nAverage mastery (${measured.size} measured): $averageMastery%")
            append("\nSolution revealed: ${session.solutionRevealed}")
        }
    }

    companion object {
        /**
         * Characters of a source that reach the tutor prompt. Kept in one place so the UI's
         * coverage report and the prompt budget cannot drift apart.
         */
        const val SOURCE_BUDGET_CHARACTERS = 4_000
    }

    private fun newExerciseId(sessionId: String): String = "$sessionId:exercise-${now()}"
}
