package com.halo.app.nativeactions

import com.halo.app.data.study.StudyAttemptEntity
import com.halo.app.data.study.StudyFeedbackEntity
import com.halo.app.data.study.StudyPracticeItemEntity
import com.halo.app.data.study.StudyReviewEventEntity
import com.halo.app.data.study.StudySessionEntity
import com.halo.app.ui.screens.study.StudyPrivateSidecars
import org.json.JSONArray
import org.json.JSONObject

internal data class PrivateStudySessionPayload(
    val session: StudySessionEntity,
    val attempts: List<StudyAttemptEntity>,
    val feedback: List<StudyFeedbackEntity>,
    val cards: List<StudyPracticeItemEntity>,
    val reviews: List<StudyReviewEventEntity>,
    val sidecars: StudyPrivateSidecars,
    val sourceIds: Set<String>,
) {
    init {
        val attemptIds = attempts.mapTo(hashSetOf(), StudyAttemptEntity::id)
        val cardIds = cards.mapTo(hashSetOf(), StudyPracticeItemEntity::id)
        check(attemptIds.size == attempts.size && cardIds.size == cards.size)
        check(attempts.all { it.sessionId == session.id } && cards.all { it.sessionId == session.id })
        check(feedback.all { it.sessionId == session.id && it.attemptId in attemptIds })
        check(reviews.all { it.sessionId == session.id && it.itemId in cardIds })
        require(sourceIds.size <= 400 && sourceIds.all { it.isNotBlank() && it.length <= 200 })
    }

    fun encode(): JSONObject = JSONObject().put("version", 1).put("session", encodeSession(session))
        .put("attempts", JSONArray(attempts.map(::encodeAttempt))).put("feedback", JSONArray(feedback.map(::encodeFeedback)))
        .put("cards", JSONArray(cards.map(::encodeCard))).put("reviews", JSONArray(reviews.map(::encodeReview)))
        .put("sidecars", sidecars.encode()).put("sources", JSONArray(sourceIds.sorted()))

    companion object {
        fun decode(json: JSONObject): PrivateStudySessionPayload {
            require(json.getInt("version") == 1)
            val session = decodeSession(json.getJSONObject("session"))
            return PrivateStudySessionPayload(session,
                json.rows("attempts").map { decodeAttempt(session.id, it) },
                json.rows("feedback").map { decodeFeedback(session.id, it) },
                json.rows("cards").map { decodeCard(session.id, it) },
                json.rows("reviews").map { decodeReview(session.id, it) },
                StudyPrivateSidecars.decode(json.getJSONObject("sidecars")),
                json.getJSONArray("sources").stringIds(),
            )
        }

        private fun encodeSession(row: StudySessionEntity) = with(row) {
            JSONObject().put("id", id).put("goal", goal).put("assignmentLanguage", assignmentLanguage)
                .put("explanationLanguage", explanationLanguage).put("strongestLanguage", strongestLanguage)
                .put("sourceObjectIds", sourceObjectIdsJson).put("sourceRevision", sourceRevision)
                .put("conversationId", conversationId).put("runId", runId).put("interactionId", interactionId)
                .put("tutorStage", tutorStage).put("lifecycle", lifecycle).put("currentExerciseId", currentExerciseId)
                .put("solutionRevealed", solutionRevealed).put("completedAt", completedAtEpochMs).put("schemaVersion", schemaVersion)
                .put("createdAt", createdAtEpochMs).put("updatedAt", updatedAtEpochMs).put("sources", sourcesJson)
                .put("learnerLevel", learnerLevel).put("supportStyle", supportStyle).put("knowledgePolicy", knowledgePolicy)
        }

        private fun decodeSession(row: JSONObject) = with(row) {
            StudySessionEntity(id = getString("id"), goal = stringOrNull("goal"), assignmentLanguage = stringOrNull("assignmentLanguage"),
                explanationLanguage = stringOrNull("explanationLanguage"), strongestLanguage = stringOrNull("strongestLanguage"),
                sourceObjectIdsJson = getString("sourceObjectIds"), sourceRevision = longOrNull("sourceRevision"),
                conversationId = stringOrNull("conversationId"), runId = stringOrNull("runId"), interactionId = stringOrNull("interactionId"),
                tutorStage = getString("tutorStage"), lifecycle = getString("lifecycle"), currentExerciseId = stringOrNull("currentExerciseId"),
                solutionRevealed = getBoolean("solutionRevealed"), completedAtEpochMs = longOrNull("completedAt"),
                schemaVersion = getInt("schemaVersion"), createdAtEpochMs = getLong("createdAt"), updatedAtEpochMs = getLong("updatedAt"),
                sourcesJson = getString("sources"), learnerLevel = getString("learnerLevel"), supportStyle = getString("supportStyle"),
                knowledgePolicy = getString("knowledgePolicy"))
        }

        private fun encodeAttempt(row: StudyAttemptEntity) = with(row) {
            JSONObject().put("id", id).put("exerciseId", exerciseId).put("response", response).put("stage", stage)
                .put("helpRevealed", helpRevealedJson).put("submissionKey", submissionKey).put("submittedAt", submittedAtEpochMs)
        }

        private fun decodeAttempt(id: String, row: JSONObject) = with(row) {
            StudyAttemptEntity(getString("id"), id, getString("exerciseId"), getString("response"), getString("stage"),
                getString("helpRevealed"), getString("submissionKey"), getLong("submittedAt"))
        }

        private fun encodeFeedback(row: StudyFeedbackEntity) = with(row) {
            JSONObject().put("id", id).put("attemptId", attemptId).put("generationId", generationId).put("verdict", verdict)
                .put("summary", summary).put("nextStep", nextStep).put("uncertainty", uncertainty).put("citations", citationsJson)
                .put("createdAt", createdAtEpochMs)
        }

        private fun decodeFeedback(id: String, row: JSONObject) = with(row) {
            StudyFeedbackEntity(getString("id"), id, getString("attemptId"), getString("generationId"), getString("verdict"),
                getString("summary"), getString("nextStep"), stringOrNull("uncertainty"), getString("citations"), getLong("createdAt"))
        }

        private fun encodeCard(row: StudyPracticeItemEntity) = with(row) {
            JSONObject().put("id", id).put("question", question).put("answer", answer).put("sourceObjectId", sourceObjectId)
                .put("sourceRevision", sourceRevision).put("sourceVersion", sourceVersion).put("sourceLocator", sourceLocator)
                .put("accepted", accepted).put("intervalIndex", intervalIndex).put("dueAt", dueAtEpochMs).put("reviewCount", reviewCount)
                .put("knownStreak", knownStreak).put("wrongStreak", wrongStreak).put("mastered", mastered).put("edited", userEdited)
                .put("createdAt", createdAtEpochMs).put("lastReviewedAt", lastReviewedAtEpochMs)
        }

        private fun decodeCard(id: String, row: JSONObject) = with(row) {
            StudyPracticeItemEntity(id = getString("id"), sessionId = id, question = getString("question"), answer = getString("answer"),
                sourceObjectId = stringOrNull("sourceObjectId"), sourceRevision = longOrNull("sourceRevision"),
                accepted = getBoolean("accepted"), intervalIndex = getInt("intervalIndex"), dueAtEpochMs = getLong("dueAt"),
                reviewCount = getInt("reviewCount"), knownStreak = getInt("knownStreak"), wrongStreak = getInt("wrongStreak"),
                mastered = getBoolean("mastered"), userEdited = getBoolean("edited"), createdAtEpochMs = getLong("createdAt"),
                lastReviewedAtEpochMs = longOrNull("lastReviewedAt"), sourceVersion = stringOrNull("sourceVersion"),
                sourceLocator = stringOrNull("sourceLocator"))
        }

        private fun encodeReview(row: StudyReviewEventEntity) = with(row) {
            JSONObject().put("id", id).put("itemId", itemId).put("known", known).put("previous", previousIntervalIndex)
                .put("next", nextIntervalIndex).put("dueAt", nextDueAtEpochMs).put("reviewedAt", reviewedAtEpochMs).put("rating", recallRating)
        }

        private fun decodeReview(id: String, row: JSONObject) = with(row) {
            StudyReviewEventEntity(getString("id"), getString("itemId"), id, getBoolean("known"), getInt("previous"),
                getInt("next"), getLong("dueAt"), getLong("reviewedAt"), getString("rating"))
        }

        private fun JSONObject.rows(key: String): List<JSONObject> = getJSONArray(key).let { values ->
            require(values.length() <= 50_000)
            (0 until values.length()).map(values::getJSONObject)
        }
        private fun JSONArray.stringIds(): Set<String> = (0 until length()).mapTo(linkedSetOf(), ::getString)
        private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
        private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    }
}
