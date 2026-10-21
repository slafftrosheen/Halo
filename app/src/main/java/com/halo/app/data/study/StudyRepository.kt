package com.halo.app.data.study

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.study.ReviewDecision
import com.halo.sdk.runtime.study.StudyLanguagePreferences
import com.halo.sdk.runtime.study.StudySessionLifecycle
import com.halo.sdk.runtime.study.StudySession
import com.halo.sdk.runtime.study.TutorStage
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay

/**
 * Persistence for guided Study. The repository owns durable state only; teaching policy lives in the
 * use cases and execution truth stays in the runtime journal.
 */
class StudyRepository(
    private val database: AppDatabase,
    private val dao: StudyDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clockTicks: Flow<Long> = minuteClock(now),
) {
    fun observeActiveSessions(): Flow<List<StudySessionRecord>> =
        dao.observeActiveSessions().map { rows -> rows.map(StudySessionEntity::toRecord) }

    fun observeSessionsForSource(objectId: String): Flow<List<StudySessionRecord>> =
        dao.observeSessionsForSource(objectId).map { rows -> rows.map(StudySessionEntity::toRecord) }

    fun observeSession(sessionId: String): Flow<StudySessionRecord?> =
        dao.observeSession(sessionId).map { it?.toRecord() }

    suspend fun getSession(sessionId: String): StudySessionRecord? = dao.getSession(sessionId)?.toRecord()

    suspend fun createSession(
        sourceObjectIds: List<String>,
        sourceRevision: Long?,
        languages: StudyLanguagePreferences,
        goal: String?,
        conversationId: String? = null,
        runId: String? = null,
        interactionId: String? = null,
    ): StudySessionRecord {
        val timestamp = now()
        val entity = StudySessionEntity(
            id = newId(),
            goal = goal?.trim()?.take(400)?.ifEmpty { null },
            assignmentLanguage = languages.assignmentLanguage?.trim()?.ifEmpty { null },
            explanationLanguage = languages.explanationLanguage?.trim()?.ifEmpty { null },
            strongestLanguage = languages.strongestLanguage?.trim()?.ifEmpty { null },
            sourceObjectIdsJson = StudyJson.encodeIds(sourceObjectIds),
            sourceRevision = sourceRevision,
            conversationId = conversationId,
            runId = runId,
            interactionId = interactionId,
            tutorStage = TutorStage.ASK.name,
            lifecycle = StudySessionLifecycle.ACTIVE.name,
            currentExerciseId = null,
            solutionRevealed = false,
            completedAtEpochMs = null,
            schemaVersion = StudySession.SCHEMA_VERSION,
            knowledgePolicy = if (sourceObjectIds.isEmpty()) "SOURCE_PLUS_GENERAL" else "SOURCE_ONLY",
            createdAtEpochMs = timestamp,
            updatedAtEpochMs = timestamp,
        )
        dao.insertSession(entity)
        return requireNotNull(dao.getSession(entity.id)).toRecord()
    }

    suspend fun createMaterialSession(sources: List<com.halo.sdk.runtime.study.StudySessionSource>, languages: StudyLanguagePreferences,
        goal: String?, level: com.halo.sdk.runtime.study.StudyLearnerLevel, style: com.halo.sdk.runtime.study.StudySupportStyle,
        policy: com.halo.sdk.runtime.study.StudyKnowledgePolicy, conversationId: String?, runId: String?, interactionId: String?): StudySessionRecord = database.withTransaction {
        val session = createSession(sources.map { it.objectId }, sources.firstOrNull()?.revision?.toLongOrNull(), languages, goal, conversationId, runId, interactionId)
        check(updateSetup(session.id, sources, level, style, policy))
        requireNotNull(getSession(session.id))
    }

    suspend fun updateSetup(id: String, sources: List<com.halo.sdk.runtime.study.StudySessionSource>, level: com.halo.sdk.runtime.study.StudyLearnerLevel, style: com.halo.sdk.runtime.study.StudySupportStyle, policy: com.halo.sdk.runtime.study.StudyKnowledgePolicy): Boolean =
        dao.updateSetup(id, StudyJson.encodeSources(sources), StudyJson.encodeIds(sources.map { it.objectId }), level.name, style.name, policy.name, now()) == 1

    suspend fun updateProgress(
        sessionId: String,
        stage: TutorStage,
        exerciseId: String?,
        solutionRevealed: Boolean,
        lifecycle: StudySessionLifecycle,
        completedAtEpochMs: Long?,
    ): Boolean = dao.updateProgress(
        sessionId = sessionId,
        stage = stage.name,
        exerciseId = exerciseId,
        solutionRevealed = solutionRevealed,
        lifecycle = lifecycle.name,
        completedAt = completedAtEpochMs,
        updatedAt = now(),
    ) == 1

    suspend fun updatePreferences(
        sessionId: String,
        assignmentLanguage: String?,
        explanationLanguage: String?,
        strongestLanguage: String?,
        goal: String?,
    ): Boolean = dao.updatePreferences(
        sessionId = sessionId,
        assignmentLanguage = assignmentLanguage,
        explanationLanguage = explanationLanguage,
        strongestLanguage = strongestLanguage,
        goal = goal,
        updatedAt = now(),
    ) == 1

    /**
     * Persist an attempt. The unique [StudyAttempt.submissionKey] makes this idempotent: a retried
     * submission returns the already-stored attempt instead of creating a second one.
     */
    suspend fun recordAttempt(
        sessionId: String,
        exerciseId: String,
        response: String,
        stage: TutorStage,
        helpRevealed: List<TutorStage>,
        submissionKey: String,
    ): StudyAttempt {
        dao.findAttemptBySubmissionKey(submissionKey)?.let { return it.toDomain() }
        val entity = StudyAttemptEntity(
            id = newId(),
            sessionId = sessionId,
            exerciseId = exerciseId,
            response = response,
            stage = stage.name,
            helpRevealedJson = StudyJson.encodeStages(helpRevealed),
            submissionKey = submissionKey,
            submittedAtEpochMs = now(),
        )
        return runCatching {
            dao.insertAttempt(entity)
            entity.toDomain()
        }.getOrElse { error ->
            // Lost a race with an identical submission: the stored attempt is the truth.
            dao.findAttemptBySubmissionKey(submissionKey)?.toDomain()
                ?: throw error
        }
    }

    suspend fun findAttempt(submissionKey: String): StudyAttempt? =
        dao.findAttemptBySubmissionKey(submissionKey)?.toDomain()

    fun observeAttempts(sessionId: String): Flow<List<StudyAttempt>> =
        dao.observeAttempts(sessionId).map { rows -> rows.map(StudyAttemptEntity::toDomain) }

    suspend fun getAttempts(sessionId: String): List<StudyAttempt> =
        dao.getAttempts(sessionId).map(StudyAttemptEntity::toDomain)

    suspend fun recordFeedback(
        sessionId: String,
        attemptId: String,
        generationId: String,
        verdict: StudyVerdict,
        summary: String,
        nextStep: String,
        uncertainty: String?,
        citations: List<StudyCitation>,
    ): StudyFeedback {
        val entity = StudyFeedbackEntity(
            id = newId(),
            sessionId = sessionId,
            attemptId = attemptId,
            generationId = generationId,
            verdict = verdict.name,
            summary = summary,
            nextStep = nextStep,
            uncertainty = uncertainty,
            citationsJson = StudyJson.encodeCitations(citations),
            createdAtEpochMs = now(),
        )
        dao.insertFeedback(entity)
        return entity.toDomain()
    }

    fun observeFeedback(sessionId: String): Flow<List<StudyFeedback>> =
        dao.observeFeedback(sessionId).map { rows -> rows.map(StudyFeedbackEntity::toDomain) }

    suspend fun getFeedback(sessionId: String): List<StudyFeedback> =
        dao.getFeedback(sessionId).map(StudyFeedbackEntity::toDomain)

    suspend fun addGroundedPracticeDrafts(sessionId: String, drafts: List<com.halo.app.study.StudyCardDraft>) = database.withTransaction {
        require(drafts.size in 1..5)
        drafts.forEach { draft -> addPracticeDraft(sessionId, draft.question, draft.answer, draft.source.objectId,
            draft.source.revision, sourceVersion = draft.source.version, sourceLocator = draft.source.locator) }
    }

    suspend fun addPracticeDraft(
        sessionId: String,
        question: String,
        answer: String,
        sourceObjectId: String?,
        sourceRevision: Long?,
        accepted: Boolean = false,
        sourceVersion: String? = sourceRevision?.toString(),
        sourceLocator: String? = null,
        dueAtEpochMs: Long? = null,
    ): StudyPracticeItem {
        val timestamp = now()
        val entity = StudyPracticeItemEntity(
            id = newId(),
            sessionId = sessionId,
            sourceVersion = sourceVersion, sourceLocator = sourceLocator,
            question = question.trim().take(600),
            answer = answer.trim().take(2_000),
            sourceObjectId = sourceObjectId,
            sourceRevision = sourceRevision,
            accepted = accepted,
            intervalIndex = 0,
            dueAtEpochMs = dueAtEpochMs ?: timestamp,
            reviewCount = 0,
            knownStreak = 0,
            wrongStreak = 0,
            mastered = false,
            userEdited = false,
            createdAtEpochMs = timestamp,
            lastReviewedAtEpochMs = null,
        )
        dao.insertPracticeItem(entity)
        return entity.toDomain()
    }

    fun observePracticeItems(sessionId: String): Flow<List<StudyPracticeItem>> =
        dao.observePracticeItems(sessionId).map { rows -> rows.map(StudyPracticeItemEntity::toDomain) }

    suspend fun getPracticeItems(sessionId: String): List<StudyPracticeItem> =
        dao.getPracticeItems(sessionId).map(StudyPracticeItemEntity::toDomain)

    fun observeDuePracticeItems(limit: Int = 50): Flow<List<StudyPracticeItem>> =
        flushDueQuery(limit)

    fun observeSessionDuePractice(sessionId: String): Flow<List<StudyPracticeItem>> =
        clockTicks.flatMapLatest { at -> dao.observeSessionDuePractice(sessionId, at) }
            .map { rows -> rows.map(StudyPracticeItemEntity::toDomain) }

    suspend fun getPracticeItem(itemId: String): StudyPracticeItem? =
        dao.getPracticeItem(itemId)?.toDomain()

    suspend fun setPracticeItemAccepted(itemId: String, accepted: Boolean): Boolean =
        dao.setPracticeAccepted(itemId, accepted) == 1

    /**
     * Editing card content marks it as user-owned. Regeneration refuses to overwrite such a card
     * unless the learner explicitly asks for it.
     */
    suspend fun updatePracticeItem(itemId: String, question: String, answer: String, accepted: Boolean): Boolean =
        dao.updatePracticeItemContent(itemId, question.trim().take(600), answer.trim().take(2_000), accepted) == 1

    suspend fun deletePracticeItem(itemId: String): Boolean = dao.deletePracticeItem(itemId) == 1

    /**
     * Store one review pass: the item's schedule fields and the immutable event in one transaction,
     * so the derived mastery curve can never disagree with the item's due date.
     */
    suspend fun recordReview(
        item: StudyPracticeItem,
        decision: ReviewDecision,
        sourceRevision: Long?,
    ): StudyReviewEvent? = database.withTransaction {
        // Compare inside the transaction: rapid submissions or another restored
        // screen cannot apply a decision computed from an older card schedule.
        val current = dao.getPracticeItem(item.id)?.toDomain() ?: return@withTransaction null
        if (current != item) return@withTransaction null
        val reviewedAt = now()
        val updated = dao.recordReview(
            itemId = item.id,
            intervalIndex = decision.ladderStep,
            dueAt = decision.dueAtEpochMs,
            reviewedAt = reviewedAt,
            knownStreak = decision.knownStreak,
            wrongStreak = decision.wrongStreak,
            mastered = decision.mastered,
        )
        if (updated != 1) return@withTransaction null
        val event = StudyReviewEventEntity(
            id = newId(),
            itemId = item.id,
            sessionId = item.sessionId,
            known = decision.knownStreak > 0,
            recallRating = decision.recallRating.name,
            previousIntervalIndex = item.intervalIndex,
            nextIntervalIndex = decision.ladderStep,
            nextDueAtEpochMs = decision.dueAtEpochMs,
            reviewedAtEpochMs = reviewedAt,
        )
        dao.insertReviewEvent(event)
        event.toDomain()
    }

    suspend fun getReviewEvents(itemId: String): List<StudyReviewEvent> =
        dao.getReviewEvents(itemId).map(StudyReviewEventEntity::toDomain)

    fun observeReviewEvents(sessionId: String): Flow<List<StudyReviewEvent>> =
        dao.observeReviewEvents(sessionId).map { rows -> rows.map(StudyReviewEventEntity::toDomain) }

    suspend fun getReviewEventsForSession(sessionId: String): List<StudyReviewEvent> =
        dao.getReviewEventsForSession(sessionId).map(StudyReviewEventEntity::toDomain)

    suspend fun deleteSessionData(sessionId: String): Boolean = dao.deleteSessionData(sessionId) == 1

    private fun flushDueQuery(limit: Int): Flow<List<StudyPracticeItem>> {
        // Time is an input, not a captured constant. While collected by the visible Study UI this
        // rebinds Room's :now parameter at minute granularity even when the DB has not changed.
        return clockTicks.flatMapLatest { at ->
            dao.observeDuePracticeItems(at, limit)
        }.map { rows -> rows.map(StudyPracticeItemEntity::toDomain) }
    }

    companion object {
        private fun minuteClock(now: () -> Long): Flow<Long> = flow {
            while (true) {
                val current = now()
                emit(current)
                delay((60_000L - current.mod(60_000L)).coerceAtLeast(1L))
            }
        }
    }
}
