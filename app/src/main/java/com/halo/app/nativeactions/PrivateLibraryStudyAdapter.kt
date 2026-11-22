package com.halo.app.nativeactions

import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.study.StudyPracticeItemEntity
import com.halo.app.data.study.StudyReviewEventEntity
import com.halo.app.data.study.StudySessionEntity
import com.halo.sdk.runtime.study.StudySession
import com.halo.sdk.runtime.study.StudySessionLifecycle
import com.halo.sdk.runtime.study.TutorStage
import org.json.JSONArray
import org.json.JSONObject

/** The encrypted entry owns the accepted card and its exact review schedule while it is locked. */
internal class PrivateLibraryStudyAdapter(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun capture(target: PrivateLibraryTarget.StudyCard): PrivateLibraryItem {
        val study = database.studyDao()
        val card = requireNotNull(study.getPracticeItem(target.id))
        val session = requireNotNull(study.getSession(card.sessionId))
        check(session.learnerLevel == "ADULT" && session.lifecycle in availableSessions)
        check(card.accepted && card.sessionId == target.sessionId && card.question == target.question &&
            card.createdAtEpochMs == target.createdAt && card.lastReviewedAtEpochMs == target.lastReviewedAt)
        val reviews = study.getReviewEvents(card.id).sortedWith(
            compareBy(StudyReviewEventEntity::reviewedAtEpochMs, StudyReviewEventEntity::id),
        )
        val payload = JSONObject()
            .put("session", card.sessionId)
            .put("answer", card.answer)
            .put("sourceObjectId", card.sourceObjectId)
            .put("sourceRevision", card.sourceRevision)
            .put("sourceVersion", card.sourceVersion)
            .put("sourceLocator", card.sourceLocator)
            .put("step", card.intervalIndex)
            .put("dueAt", card.dueAtEpochMs)
            .put("reviewCount", card.reviewCount)
            .put("knownStreak", card.knownStreak)
            .put("wrongStreak", card.wrongStreak)
            .put("mastered", card.mastered)
            .put("edited", card.userEdited)
            .put("lastReviewedAt", card.lastReviewedAtEpochMs)
            .put("reviews", JSONArray(reviews.map(::encodeReview)))
        return PrivateLibraryItem(
            header = WorkspaceObjectEntity(
                id = card.id, type = "PRIVATE_STUDY_CARD", title = card.question, createdAt = card.createdAtEpochMs,
                updatedAt = card.lastReviewedAtEpochMs ?: card.createdAtEpochMs, source = "STUDY",
                lifecycle = "ACTIVE", inInbox = false, indexingState = "NONE", contentRef = "study-card:${card.id}",
            ),
            note = null, blocks = emptyList(), memberships = emptyList(), favorite = null,
            alias = null, file = null, studyCard = payload,
        )
    }

    suspend fun withdraw(item: PrivateLibraryItem) {
        val study = database.studyDao()
        check(study.deletePracticeItem(item.header.id) == 1)
        check(study.touchSession(requireNotNull(item.studyCard).getString("session"), clock()) == 1)
    }

    suspend fun restore(item: PrivateLibraryItem) {
        val study = database.studyDao()
        val header = item.header
        val payload = requireNotNull(item.studyCard)
        val sessionId = payload.getString("session")
        val now = clock()
        val session = study.getSession(sessionId)
        if (session == null) {
            check(database.privateLibraryDao().claims(listOf(com.halo.app.data.workspace.privateLibraryClaim(sessionId))).isEmpty()) {
                "Unlock the card's Study lesson before restoring this card"
            }
            // The user may have deleted the lesson while its card was sealed. Restore only the
            // requested card, with an empty adult lesson to own it and any other restored cards.
            study.insertSession(StudySessionEntity(
                id = sessionId, goal = null, assignmentLanguage = null, explanationLanguage = null,
                strongestLanguage = null, sourceObjectIdsJson = "[]", sourceRevision = null,
                conversationId = null, runId = null, interactionId = null, tutorStage = TutorStage.ASK.name,
                lifecycle = StudySessionLifecycle.ACTIVE.name, currentExerciseId = null, solutionRevealed = false,
                completedAtEpochMs = null, schemaVersion = StudySession.SCHEMA_VERSION,
                createdAtEpochMs = now, updatedAtEpochMs = now,
            ))
        } else {
            check(session.learnerLevel == "ADULT" && session.lifecycle in availableSessions)
        }
        study.insertPracticeItem(StudyPracticeItemEntity(
            id = header.id, sessionId = sessionId, question = header.title, answer = payload.getString("answer"),
            sourceObjectId = payload.optionalString("sourceObjectId"), sourceRevision = payload.optionalLong("sourceRevision"),
            accepted = true, intervalIndex = payload.getInt("step"), dueAtEpochMs = payload.getLong("dueAt"),
            reviewCount = payload.getInt("reviewCount"), knownStreak = payload.getInt("knownStreak"),
            wrongStreak = payload.getInt("wrongStreak"), mastered = payload.getBoolean("mastered"),
            userEdited = payload.getBoolean("edited"), createdAtEpochMs = header.createdAt,
            lastReviewedAtEpochMs = payload.optionalLong("lastReviewedAt"),
            sourceVersion = payload.optionalString("sourceVersion"), sourceLocator = payload.optionalString("sourceLocator"),
        ))
        val reviews = payload.getJSONArray("reviews")
        for (index in 0 until reviews.length()) {
            val review = reviews.getJSONObject(index)
            study.insertReviewEvent(StudyReviewEventEntity(
                id = review.getString("id"), itemId = header.id, sessionId = sessionId,
                known = review.getBoolean("known"), previousIntervalIndex = review.getInt("previous"),
                nextIntervalIndex = review.getInt("next"), nextDueAtEpochMs = review.getLong("dueAt"),
                reviewedAtEpochMs = review.getLong("at"), recallRating = review.getString("rating"),
            ))
        }
        check(study.touchSession(sessionId, now) == 1)
    }

    private fun encodeReview(review: StudyReviewEventEntity) = JSONObject()
        .put("id", review.id).put("known", review.known).put("previous", review.previousIntervalIndex)
        .put("next", review.nextIntervalIndex).put("dueAt", review.nextDueAtEpochMs)
        .put("at", review.reviewedAtEpochMs).put("rating", review.recallRating)

    private fun JSONObject.optionalString(key: String) = if (isNull(key)) null else getString(key)
    private fun JSONObject.optionalLong(key: String) = if (isNull(key)) null else getLong(key)

    private companion object {
        val availableSessions = setOf(StudySessionLifecycle.ACTIVE.name, StudySessionLifecycle.COMPLETED.name)
    }
}
