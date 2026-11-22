package com.halo.app.nativeactions

import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.study.StudyFeedbackEntity
import com.halo.app.data.study.StudyPracticeItemEntity
import com.halo.app.data.study.StudySessionEntity
import com.halo.app.ui.screens.study.StudyLessonStore
import com.halo.app.ui.screens.study.StudyPrivateSidecars
import org.json.JSONArray
import org.json.JSONObject

/** Moves a complete public Study lesson and its durable local UI state behind one Vault envelope. */
internal class PrivateLibraryStudySessionAdapter(private val database: AppDatabase, private val lessons: StudyLessonStore) {
    private val study get() = database.studyDao()
    private val history = PrivateLibraryHistory(database)

    suspend fun <T> withSidecars(action: suspend () -> T): T = lessons.withPrivateLibrarySidecars(action)
    suspend fun settle() = lessons.settlePrivateLibrarySidecars()

    suspend fun derivedWorkspaceIds(sessionId: String): List<String> = listOfNotNull(
        database.workspaceContextDao().captureObject("study-summary:$sessionId", "v1"),
        lessons.capturePrivateSidecars(sessionId).journey?.let(::JSONObject)?.reference("summaryNoteId"),
    ).distinct().filter { database.notebookDao().getWorkspaceObject(it) != null }.sorted()

    suspend fun linkedSessions(sourceId: String): List<String> {
        val relational = study.privateSourceCandidates(sourceId).filter { id ->
            val session = requireNotNull(study.getSession(id))
            sourceId in listOf(session.conversationId, session.runId, session.interactionId) ||
                sourceId in sessionReferences(session, study.getPracticeItems(id), study.getFeedback(id)) ||
                session.interactionId?.takeIf(String::isNotBlank)?.let { interaction ->
                    history.linked(interaction).any { it.id == sourceId }
                } == true
        }
        val local = study.privateSidecarSessionIds().filter { id ->
            val sidecars = lessons.capturePrivateSidecars(id)
            (sidecars.replies?.contains(sourceId) == true || sidecars.journey?.contains(sourceId) == true ||
                sidecars.recovery?.contains(sourceId) == true) && sourceId in sidecarReferences(sidecars)
        }
        return (relational + local).distinct().sorted()
    }

    suspend fun relatedHistory(sessionId: String): Set<PrivateHistoryRef> = relatedHistory(requireNotNull(study.getSession(sessionId)))

    private suspend fun relatedHistory(session: StudySessionEntity): Set<PrivateHistoryRef> = buildSet {
        session.conversationId?.takeIf(String::isNotBlank)?.let { add(PrivateHistoryRef(PrivateHistoryKind.CONVERSATION, it)) }
        session.runId?.takeIf(String::isNotBlank)?.let { add(PrivateHistoryRef(PrivateHistoryKind.AGENT_RUN, it)) }
        session.interactionId?.takeIf(String::isNotBlank)?.let { addAll(history.linked(it)) }
    }

    suspend fun capture(sessionId: String, coveredHistory: Set<PrivateHistoryRef> = emptySet()): PrivateLibraryItem {
        val existing = requireNotNull(study.getSession(sessionId))
        val related = relatedHistory(existing)
        val interaction = existing.interactionId?.takeIf(String::isNotBlank)
        check(coveredHistory.containsAll(related) && (interaction == null || history.linked(interaction).isNotEmpty())) {
            "This lesson cannot be sealed without its linked conversation or execution history"
        }
        val attempts = study.getAttempts(sessionId).sortedBy { it.id }
        val feedback = study.getFeedback(sessionId).sortedBy { it.id }
        val cards = study.getPracticeItems(sessionId).sortedBy { it.id }
        val reviews = study.getReviewEventsForSession(sessionId).sortedBy { it.id }
        val sidecars = lessons.capturePrivateSidecars(sessionId)
        check(sidecars.recovery?.let { JSONObject(it).optString("pending").isBlank() } != false) {
            "Finish the active Study request before locking the lesson."
        }
        val references = sessionReferences(existing, cards, feedback) + sidecarReferences(sidecars) + related.map { it.id }
        val payload = PrivateStudySessionPayload(existing, attempts, feedback, cards, reviews, sidecars, references)
        return PrivateLibraryItem(
            WorkspaceObjectEntity(existing.id, "PRIVATE_STUDY_SESSION", existing.goal.orEmpty().ifBlank { "Study lesson" },
                existing.createdAtEpochMs, existing.updatedAtEpochMs, "STUDY", "ACTIVE", false, "NONE", "study-session:${existing.id}"),
            note = null, blocks = emptyList(), memberships = emptyList(), favorite = null, alias = null, file = null,
            studySession = payload.encode(),
        )
    }

    fun sourceIds(item: PrivateLibraryItem): Set<String> = payload(item).sourceIds
    fun ownedIds(item: PrivateLibraryItem): Set<String> = payload(item).cards.mapTo(linkedSetOf(), StudyPracticeItemEntity::id)

    suspend fun withdraw(item: PrivateLibraryItem) {
        val snapshot = payload(item)
        check(requireNotNull(study.getSession(item.header.id)) == snapshot.session)
        lessons.withdrawPrivateSidecars(item.header.id, snapshot.sidecars)
        check(study.deleteSessionData(item.header.id) == 1)
    }

    suspend fun restore(item: PrivateLibraryItem) {
        val snapshot = payload(item)
        check(study.getSession(item.header.id) == null) { "The public Study lesson already exists" }
        lessons.restorePrivateSidecars(item.header.id, snapshot.sidecars)
        study.insertSession(snapshot.session)
        snapshot.attempts.forEach { study.insertAttempt(it) }
        snapshot.feedback.forEach { study.insertFeedback(it) }
        snapshot.cards.forEach { study.insertPracticeItem(it) }
        snapshot.reviews.forEach { study.insertReviewEvent(it) }
    }

    private fun payload(item: PrivateLibraryItem): PrivateStudySessionPayload =
        PrivateStudySessionPayload.decode(requireNotNull(item.studySession)).also { check(it.session.id == item.header.id) }

    private suspend fun sessionReferences(session: StudySessionEntity, cards: List<StudyPracticeItemEntity>,
        feedback: List<StudyFeedbackEntity>): Set<String> = buildSet {
        session.sourceObjectIdsJson.rows().filterIsInstance<String>().forEach { add(it) }
        session.sourcesJson.rows().filterIsInstance<JSONObject>().forEach { it.reference("objectId")?.let(::add) }
        cards.mapNotNullTo(this, StudyPracticeItemEntity::sourceObjectId)
        feedback.forEach { row -> row.citationsJson.rows().filterIsInstance<JSONObject>().forEach { it.reference("sourceObjectId")?.let(::add) } }
        database.workspaceContextDao().captureObject("study-summary:${session.id}", "v1")?.let(::add)
    }

    private fun sidecarReferences(sidecars: StudyPrivateSidecars): Set<String> = buildSet {
        sidecars.replies?.rows()?.filterIsInstance<JSONObject>()?.forEach { turn ->
            turn.optJSONArray("origins")?.rows()?.filterIsInstance<String>()?.forEach { add(it) }
            turn.optJSONObject("sourceVersions")?.keys()?.forEach { add(it) }
            turn.optJSONArray("citations")?.rows()?.filterIsInstance<JSONObject>()?.forEach { it.reference("source")?.let(::add) }
        }
        sidecars.journey?.let(::JSONObject)?.let { journey ->
            journey.reference("summaryNoteId")?.let(::add)
            journey.optJSONArray("exercises")?.rows()?.filterIsInstance<JSONObject>()?.forEach { it.reference("source")?.let(::add) }
        }
        sidecars.recovery?.let(::JSONObject)?.optJSONObject("focus")?.reference("object")?.let(::add)
    }

    private fun String.rows() = if (isBlank()) emptyList() else JSONArray(this).rows()
    private fun JSONArray.rows(): List<Any> = (0 until length()).map(::get)
    private fun JSONObject.reference(key: String): String? = if (isNull(key)) null else getString(key).takeIf(String::isNotBlank)
}
