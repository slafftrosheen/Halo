package com.halo.app.data.study

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.halo.sdk.runtime.study.StudySessionLifecycle
import com.halo.sdk.runtime.study.StudySession
import com.halo.sdk.runtime.study.TutorStage
import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------------------------------------
// Durable records
// ---------------------------------------------------------------------------------------------

@Entity(
    tableName = "study_sessions",
    indices = [Index("lifecycle"), Index("updatedAtEpochMs")],
)
data class StudySessionEntity(
    @PrimaryKey val id: String,
    val goal: String?,
    val assignmentLanguage: String?,
    val explanationLanguage: String?,
    val strongestLanguage: String?,
    val sourceObjectIdsJson: String,
    val sourceRevision: Long?,
    val conversationId: String?,
    val runId: String?,
    val interactionId: String?,
    val tutorStage: String,
    val lifecycle: String,
    val currentExerciseId: String?,
    val solutionRevealed: Boolean,
    val completedAtEpochMs: Long?,
    val schemaVersion: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "study_attempts",
    indices = [Index("sessionId"), Index("exerciseId"), Index(value = ["submissionKey"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = StudySessionEntity::class,
            parentColumns = ["id"], childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StudyAttemptEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val response: String,
    val stage: String,
    val helpRevealedJson: String,
    /** Idempotency key: replaying the same submission never creates a second attempt. */
    val submissionKey: String,
    val submittedAtEpochMs: Long,
)

@Entity(
    tableName = "study_feedback",
    indices = [Index("sessionId"), Index("attemptId"), Index("generationId")],
    foreignKeys = [
        ForeignKey(
            entity = StudyAttemptEntity::class,
            parentColumns = ["id"], childColumns = ["attemptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StudyFeedbackEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val attemptId: String,
    val generationId: String,
    val verdict: String,
    val summary: String,
    val nextStep: String,
    val uncertainty: String?,
    val citationsJson: String,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "study_practice_items",
    indices = [Index("sessionId"), Index("sourceObjectId"), Index("dueAtEpochMs")],
    foreignKeys = [
        ForeignKey(
            entity = StudySessionEntity::class,
            parentColumns = ["id"], childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StudyPracticeItemEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val question: String,
    val answer: String,
    val sourceObjectId: String?,
    val sourceRevision: Long?,
    /** Generated cards stay drafts until the learner accepts them. */
    val accepted: Boolean,
    /** Index into `StudyReviewPolicy.steps`. */
    val intervalIndex: Int,
    val dueAtEpochMs: Long,
    val reviewCount: Int,
    @ColumnInfo(defaultValue = "0") val knownStreak: Int = 0,
    @ColumnInfo(defaultValue = "0") val wrongStreak: Int = 0,
    @ColumnInfo(defaultValue = "0") val mastered: Boolean = false,
    /** True once the learner edited the card; regeneration must never overwrite it silently. */
    @ColumnInfo(defaultValue = "0") val userEdited: Boolean = false,
    val createdAtEpochMs: Long,
    val lastReviewedAtEpochMs: Long?,
)

@Entity(
    tableName = "study_review_events",
    indices = [Index("itemId"), Index("reviewedAtEpochMs")],
    foreignKeys = [
        ForeignKey(
            entity = StudyPracticeItemEntity::class,
            parentColumns = ["id"], childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StudyReviewEventEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val sessionId: String,
    val known: Boolean,
    val previousIntervalIndex: Int,
    val nextIntervalIndex: Int,
    val nextDueAtEpochMs: Long,
    val reviewedAtEpochMs: Long,
)

// ---------------------------------------------------------------------------------------------
// Domain records
// ---------------------------------------------------------------------------------------------

data class StudySessionRecord(
    val id: String,
    val goal: String?,
    val languages: com.halo.sdk.runtime.study.StudyLanguagePreferences,
    val sourceObjectIds: List<String>,
    val sourceRevision: Long?,
    val conversationId: String?,
    val runId: String?,
    val interactionId: String?,
    val tutorStage: TutorStage,
    val lifecycle: StudySessionLifecycle,
    val currentExerciseId: String?,
    val solutionRevealed: Boolean,
    val completedAtEpochMs: Long?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    fun toPortable(): StudySession = StudySession(
        id = id,
        assignmentLanguage = languages.assignmentLanguage,
        explanationLanguage = languages.explanationLanguage,
        strongestLanguage = languages.strongestLanguage,
        sourceObjectIds = sourceObjectIds,
        interactionId = interactionId,
        tutorStage = tutorStage,
        goal = goal,
        sourceRevision = sourceRevision,
        conversationId = conversationId,
        runId = runId,
        lifecycle = lifecycle,
        currentExerciseId = currentExerciseId,
        solutionRevealed = solutionRevealed,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

data class StudyAttempt(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val response: String,
    val stage: TutorStage,
    val helpRevealed: List<TutorStage>,
    val submissionKey: String,
    val submittedAtEpochMs: Long,
)

enum class StudyVerdict {
    /** The attempt checks out against the source. */
    CHECKS_OUT,

    /** A concrete step needs work; feedback names it. */
    NEEDS_WORK,

    /** There is not enough evidence in the source to judge. */
    INSUFFICIENT_EVIDENCE,

    /** A model opinion that Halo has not verified. */
    UNVERIFIED,
    ;

    val label: String
        get() = when (this) {
            CHECKS_OUT -> "Checks out"
            NEEDS_WORK -> "Needs work"
            INSUFFICIENT_EVIDENCE -> "Not enough evidence"
            UNVERIFIED -> "Unverified suggestion"
        }
}

data class StudyCitation(
    val sourceObjectId: String,
    val sourceRevision: Long?,
    val locator: String?,
    val excerpt: String,
)

data class StudyFeedback(
    val id: String,
    val sessionId: String,
    val attemptId: String,
    val generationId: String,
    val verdict: StudyVerdict,
    val summary: String,
    val nextStep: String,
    val uncertainty: String?,
    val citations: List<StudyCitation>,
    val createdAtEpochMs: Long,
)

data class StudyPracticeItem(
    val id: String,
    val sessionId: String,
    val question: String,
    val answer: String,
    val sourceObjectId: String?,
    val sourceRevision: Long?,
    val accepted: Boolean,
    val intervalIndex: Int,
    val dueAtEpochMs: Long,
    val reviewCount: Int,
    val knownStreak: Int = 0,
    val wrongStreak: Int = 0,
    val mastered: Boolean = false,
    val userEdited: Boolean = false,
    val createdAtEpochMs: Long,
    val lastReviewedAtEpochMs: Long?,
) {
    /** A card whose source moved on must be flagged, not silently retargeted. */
    fun isStaleAgainst(currentSourceRevisions: Map<String, Long>): Boolean {
        val sourceId = sourceObjectId ?: return false
        val current = currentSourceRevisions[sourceId] ?: return false
        return sourceRevision != null && current != sourceRevision
    }
}

data class StudyReviewEvent(
    val id: String,
    val itemId: String,
    val sessionId: String,
    val known: Boolean,
    val previousIntervalIndex: Int,
    val nextIntervalIndex: Int,
    val nextDueAtEpochMs: Long,
    val reviewedAtEpochMs: Long,
)

// ---------------------------------------------------------------------------------------------
// DAO
// ---------------------------------------------------------------------------------------------

@Dao
interface StudyDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(entity: StudySessionEntity)

    @Query(
        """
        UPDATE study_sessions
        SET tutorStage = :stage, currentExerciseId = :exerciseId, solutionRevealed = :solutionRevealed,
            lifecycle = :lifecycle, completedAtEpochMs = :completedAt, updatedAtEpochMs = :updatedAt
        WHERE id = :sessionId
        """
    )
    suspend fun updateProgress(
        sessionId: String,
        stage: String,
        exerciseId: String?,
        solutionRevealed: Boolean,
        lifecycle: String,
        completedAt: Long?,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE study_sessions
        SET explanationLanguage = :explanationLanguage,
            strongestLanguage = :strongestLanguage,
            assignmentLanguage = COALESCE(:assignmentLanguage, assignmentLanguage),
            goal = COALESCE(:goal, goal),
            updatedAtEpochMs = :updatedAt
        WHERE id = :sessionId
        """
    )
    suspend fun updatePreferences(
        sessionId: String,
        assignmentLanguage: String?,
        explanationLanguage: String?,
        strongestLanguage: String?,
        goal: String?,
        updatedAt: Long,
    ): Int

    @Query("SELECT * FROM study_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): StudySessionEntity?

    @Query("SELECT * FROM study_sessions WHERE id = :sessionId LIMIT 1")
    fun observeSession(sessionId: String): kotlinx.coroutines.flow.Flow<StudySessionEntity?>

    @Query("SELECT * FROM study_sessions WHERE lifecycle = 'ACTIVE' ORDER BY updatedAtEpochMs DESC")
    fun observeActiveSessions(): kotlinx.coroutines.flow.Flow<List<StudySessionEntity>>

    /**
     * Source lookup without a JSON1 dependency: ids are stored as a compact JSON array, so a quoted
     * exact match on the element text is sufficient and portable to older devices.
     */
    @Query(
        """
        SELECT * FROM study_sessions
        WHERE sourceObjectIdsJson LIKE '%"' || :objectId || '"%'
        ORDER BY updatedAtEpochMs DESC
        """
    )
    fun observeSessionsForSource(objectId: String): kotlinx.coroutines.flow.Flow<List<StudySessionEntity>>

    @Query("DELETE FROM study_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttempt(entity: StudyAttemptEntity)

    @Query("SELECT * FROM study_attempts WHERE submissionKey = :submissionKey LIMIT 1")
    suspend fun findAttemptBySubmissionKey(submissionKey: String): StudyAttemptEntity?

    @Query("SELECT * FROM study_attempts WHERE sessionId = :sessionId ORDER BY submittedAtEpochMs ASC")
    fun observeAttempts(sessionId: String): kotlinx.coroutines.flow.Flow<List<StudyAttemptEntity>>

    @Query("SELECT * FROM study_attempts WHERE sessionId = :sessionId ORDER BY submittedAtEpochMs ASC")
    suspend fun getAttempts(sessionId: String): List<StudyAttemptEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFeedback(entity: StudyFeedbackEntity)

    @Query("SELECT * FROM study_feedback WHERE sessionId = :sessionId ORDER BY createdAtEpochMs ASC")
    fun observeFeedback(sessionId: String): kotlinx.coroutines.flow.Flow<List<StudyFeedbackEntity>>

    @Query("SELECT * FROM study_feedback WHERE sessionId = :sessionId ORDER BY createdAtEpochMs ASC")
    suspend fun getFeedback(sessionId: String): List<StudyFeedbackEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPracticeItem(entity: StudyPracticeItemEntity)

    @Query(
        """
        UPDATE study_practice_items
        SET question = :question, answer = :answer, accepted = :accepted, userEdited = 1
        WHERE id = :itemId
        """
    )
    suspend fun updatePracticeItemContent(itemId: String, question: String, answer: String, accepted: Boolean): Int

    @Query("UPDATE study_practice_items SET accepted = :accepted WHERE id = :itemId")
    suspend fun setPracticeAccepted(itemId: String, accepted: Boolean): Int

    @Query("DELETE FROM study_practice_items WHERE id = :itemId")
    suspend fun deletePracticeItem(itemId: String): Int

    @Query(
        """
        UPDATE study_practice_items
        SET intervalIndex = :intervalIndex, dueAtEpochMs = :dueAt, reviewCount = reviewCount + 1,
            knownStreak = :knownStreak, wrongStreak = :wrongStreak, mastered = :mastered,
            lastReviewedAtEpochMs = :reviewedAt, sourceRevision = COALESCE(:sourceRevision, sourceRevision)
        WHERE id = :itemId
        """
    )
    suspend fun recordReview(
        itemId: String,
        intervalIndex: Int,
        dueAt: Long,
        reviewedAt: Long,
        sourceRevision: Long?,
        knownStreak: Int,
        wrongStreak: Int,
        mastered: Boolean,
    ): Int

    @Query("SELECT * FROM study_practice_items WHERE sessionId = :sessionId ORDER BY createdAtEpochMs ASC")
    fun observePracticeItems(sessionId: String): kotlinx.coroutines.flow.Flow<List<StudyPracticeItemEntity>>

    @Query("SELECT * FROM study_practice_items WHERE sessionId = :sessionId ORDER BY createdAtEpochMs ASC")
    suspend fun getPracticeItems(sessionId: String): List<StudyPracticeItemEntity>

    @Query(
        """
        SELECT * FROM study_practice_items
        WHERE accepted = 1 AND dueAtEpochMs <= :now
        ORDER BY dueAtEpochMs ASC
        LIMIT :limit
        """
    )
    fun observeDuePracticeItems(now: Long, limit: Int): kotlinx.coroutines.flow.Flow<List<StudyPracticeItemEntity>>

    @Query("SELECT * FROM study_practice_items WHERE sessionId = :sessionId AND accepted = 1 AND dueAtEpochMs <= :now ORDER BY dueAtEpochMs ASC, id ASC")
    fun observeSessionDuePractice(sessionId: String, now: Long): kotlinx.coroutines.flow.Flow<List<StudyPracticeItemEntity>>

    @Query("SELECT * FROM study_practice_items WHERE id = :itemId LIMIT 1")
    suspend fun getPracticeItem(itemId: String): StudyPracticeItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReviewEvent(entity: StudyReviewEventEntity)

    @Query("SELECT * FROM study_review_events WHERE itemId = :itemId ORDER BY reviewedAtEpochMs ASC")
    suspend fun getReviewEvents(itemId: String): List<StudyReviewEventEntity>

    /**
     * All review events for a session. Mastery is derived from this history only, so the UI can
     * recompute every number it displays without a stored score that could drift.
     */
    @Query("SELECT * FROM study_review_events WHERE sessionId = :sessionId ORDER BY reviewedAtEpochMs ASC")
    fun observeReviewEvents(sessionId: String): kotlinx.coroutines.flow.Flow<List<StudyReviewEventEntity>>

    @Query("SELECT * FROM study_review_events WHERE sessionId = :sessionId ORDER BY reviewedAtEpochMs ASC")
    suspend fun getReviewEventsForSession(sessionId: String): List<StudyReviewEventEntity>

    @Query("DELETE FROM study_review_events WHERE sessionId = :sessionId")
    suspend fun deleteReviewEventsForSession(sessionId: String): Int

    /**
     * Export/delete review data: review history first, then the session, whose cascade removes
     * attempts, feedback and practice items.
     */
    @Transaction
    suspend fun deleteSessionData(sessionId: String): Int {
        deleteReviewEventsForSession(sessionId)
        return deleteSession(sessionId)
    }
}

// ---------------------------------------------------------------------------------------------
// Mapping / JSON
// ---------------------------------------------------------------------------------------------

internal object StudyJson {
    fun encodeIds(ids: List<String>): String = JSONArray(ids).toString()

    fun decodeIds(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }
        }.getOrDefault(emptyList())
    }

    fun encodeStages(stages: List<TutorStage>): String = JSONArray(stages.map(TutorStage::name)).toString()

    fun decodeStages(raw: String?): List<TutorStage> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length())
                .mapNotNull { index -> TutorStage.entries.firstOrNull { it.name == array.optString(index) } }
        }.getOrDefault(emptyList())
    }

    fun encodeCitations(citations: List<StudyCitation>): String {
        val array = JSONArray()
        citations.forEach { citation ->
            array.put(
                JSONObject()
                    .put("sourceObjectId", citation.sourceObjectId)
                    .put("sourceRevision", citation.sourceRevision ?: JSONObject.NULL)
                    .put("locator", citation.locator ?: JSONObject.NULL)
                    .put("excerpt", citation.excerpt)
            )
        }
        return array.toString()
    }

    fun decodeCitations(raw: String?): List<StudyCitation> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val sourceId = item.optString("sourceObjectId")
                if (sourceId.isBlank()) return@mapNotNull null
                StudyCitation(
                    sourceObjectId = sourceId,
                    sourceRevision = item.optLong("sourceRevision").takeIf { !item.isNull("sourceRevision") },
                    locator = item.optString("locator").takeIf { !item.isNull("locator") && it.isNotBlank() },
                    excerpt = item.optString("excerpt"),
                )
            }
        }.getOrDefault(emptyList())
    }
}

internal fun StudySessionEntity.toRecord(): StudySessionRecord = StudySessionRecord(
    id = id,
    goal = goal,
    languages = com.halo.sdk.runtime.study.StudyLanguagePreferences(
        assignmentLanguage = assignmentLanguage,
        explanationLanguage = explanationLanguage,
        strongestLanguage = strongestLanguage,
    ),
    sourceObjectIds = StudyJson.decodeIds(sourceObjectIdsJson),
    sourceRevision = sourceRevision,
    conversationId = conversationId,
    runId = runId,
    interactionId = interactionId,
    tutorStage = TutorStage.entries.firstOrNull { it.name == tutorStage } ?: TutorStage.ASK,
    lifecycle = StudySessionLifecycle.entries.firstOrNull { it.name == lifecycle }
        ?: StudySessionLifecycle.ACTIVE,
    currentExerciseId = currentExerciseId,
    solutionRevealed = solutionRevealed,
    completedAtEpochMs = completedAtEpochMs,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

internal fun StudyAttemptEntity.toDomain(): StudyAttempt = StudyAttempt(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    response = response,
    stage = TutorStage.entries.firstOrNull { it.name == stage } ?: TutorStage.ASK,
    helpRevealed = StudyJson.decodeStages(helpRevealedJson),
    submissionKey = submissionKey,
    submittedAtEpochMs = submittedAtEpochMs,
)

internal fun StudyFeedbackEntity.toDomain(): StudyFeedback = StudyFeedback(
    id = id,
    sessionId = sessionId,
    attemptId = attemptId,
    generationId = generationId,
    verdict = StudyVerdict.entries.firstOrNull { it.name == verdict } ?: StudyVerdict.UNVERIFIED,
    summary = summary,
    nextStep = nextStep,
    uncertainty = uncertainty,
    citations = StudyJson.decodeCitations(citationsJson),
    createdAtEpochMs = createdAtEpochMs,
)

internal fun StudyPracticeItemEntity.toDomain(): StudyPracticeItem = StudyPracticeItem(
    id = id,
    sessionId = sessionId,
    question = question,
    answer = answer,
    sourceObjectId = sourceObjectId,
    sourceRevision = sourceRevision,
    accepted = accepted,
    intervalIndex = intervalIndex,
    dueAtEpochMs = dueAtEpochMs,
    reviewCount = reviewCount,
    knownStreak = knownStreak,
    wrongStreak = wrongStreak,
    mastered = mastered,
    userEdited = userEdited,
    createdAtEpochMs = createdAtEpochMs,
    lastReviewedAtEpochMs = lastReviewedAtEpochMs,
)

internal fun StudyReviewEventEntity.toDomain(): StudyReviewEvent = StudyReviewEvent(
    id = id,
    itemId = itemId,
    sessionId = sessionId,
    known = known,
    previousIntervalIndex = previousIntervalIndex,
    nextIntervalIndex = nextIntervalIndex,
    nextDueAtEpochMs = nextDueAtEpochMs,
    reviewedAtEpochMs = reviewedAtEpochMs,
)
