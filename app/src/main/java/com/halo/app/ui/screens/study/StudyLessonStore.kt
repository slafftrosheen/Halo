package com.halo.app.ui.screens.study

import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.study.TutorStage
import com.halo.app.data.study.StudyCitation
import com.halo.app.data.study.StudyVerdict
import com.halo.app.study.StudySessionLocalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class StudyMaterialFocus(val objectId: String, val version: String, val page: Int? = null)

data class StudyRecoveryState(val draft: String = "", val submissionKey: String? = null,
    val submittedDraft: String? = null, val pendingRequestId: String? = null,
    val materialFocus: StudyMaterialFocus? = null)

/** Bounded local lesson history, independent of notebook sources and graded attempts. */
class StudyLessonStore(private val storage: Storage) : StudySessionLocalData {
    private val recoveryMutex = Mutex()
    private fun deletedKey(id: String) = "study_deleted_v1_$id"
    private fun requireWritable(id: String) { check(!storage.getBoolean(deletedKey(id), false)) { "Study session was deleted" } }
    private fun recoveryKey(id: String) = "study_recovery_v1_$id"
    private fun readRecovery(id: String): StudyRecoveryState = try {
        val raw = storage.getString(recoveryKey(id))
        if (raw == null || raw.length > 12_000) StudyRecoveryState() else JSONObject(raw).let {
            StudyRecoveryState(it.optString("draft").take(4000),
                it.optString("submission").takeIf(String::isNotBlank),
                it.optString("submitted").takeIf(String::isNotBlank),
                it.optString("pending").takeIf(String::isNotBlank),
                it.optJSONObject("focus")?.let { focus -> StudyMaterialFocus(
                    focus.getString("object"), focus.getString("version"),
                    if (focus.isNull("page")) null else focus.getInt("page")) })
        }
    } catch (_: Exception) { StudyRecoveryState() }
    suspend fun loadRecovery(id: String) = recoveryMutex.withLock { withContext(Dispatchers.IO) { readRecovery(id) } }
    private suspend fun updateRecovery(id: String, allowDeletedCleanup: Boolean = false, update: (StudyRecoveryState) -> StudyRecoveryState): StudyRecoveryState =
        recoveryMutex.withLock { withContext(Dispatchers.IO) {
            if (allowDeletedCleanup && storage.getBoolean(deletedKey(id), false)) return@withContext StudyRecoveryState()
            requireWritable(id)
            val previous = readRecovery(id)
            val next = update(previous)
            if (next == previous) return@withContext next
            storage.putString(recoveryKey(id), JSONObject().put("draft", next.draft.take(4000))
                .put("submission", next.submissionKey.orEmpty()).put("submitted", next.submittedDraft.orEmpty().take(4000))
                .put("pending", next.pendingRequestId.orEmpty())
                .put("focus", next.materialFocus?.let { focus -> JSONObject().put("object", focus.objectId)
                    .put("version", focus.version).put("page", focus.page ?: JSONObject.NULL) } ?: JSONObject.NULL).toString())
            next
        } }
    suspend fun saveDraft(id: String, draft: String) { updateRecovery(id) { state ->
        if (state.submittedDraft == draft.trim()) state.copy(draft = draft.take(4000))
        else state.copy(draft = draft.take(4000), submissionKey = null, submittedDraft = null)
    } }
    suspend fun prepareAttempt(id: String, text: String, candidateKey: String): String = requireNotNull(updateRecovery(id) {
        if (it.submittedDraft == text && it.submissionKey != null) it
        else it.copy(draft = text.take(4000), submissionKey = candidateKey, submittedDraft = text.take(4000))
    }.submissionKey)
    suspend fun markPendingTurn(id: String, requestId: String) { updateRecovery(id) { it.copy(pendingRequestId = requestId) } }
    suspend fun saveMaterialFocus(id: String, focus: StudyMaterialFocus?) {
        updateRecovery(id) { it.copy(materialFocus = focus) }
    }
    suspend fun clearPendingTurn(id: String, requestId: String) { updateRecovery(id, allowDeletedCleanup = true) {
        if (it.pendingRequestId == requestId) it.copy(pendingRequestId = null) else it
    } }
    private val revision = MutableStateFlow(0L)
    fun observeSavedResponseCount(id: String) = revision.map { load(id).size }.distinctUntilChanged()
    private fun key(id: String) = "study_lesson_v1_$id"
    suspend fun load(id: String): List<StudyTurn> = withContext(Dispatchers.IO) {
        val raw = storage.getString(key(id)) ?: return@withContext emptyList()
        runCatching {
            val array = JSONArray(raw.take(600_000))
            (0 until array.length().coerceAtMost(30)).map { index ->
                val item = array.getJSONObject(index)
                val citations = item.optJSONArray("citations") ?: JSONArray()
                StudyTurn(materialFocus = item.optString("materialFocus").takeIf(String::isNotBlank), sourceVersions = item.optJSONObject("sourceVersions")?.let { versions ->
                    versions.keys().asSequence().take(4).associateWith { versions.getString(it) }
                }, exerciseId = item.optString("exercise").takeIf(String::isNotBlank), sourceObjectIds = item.optJSONArray("origins")?.let { array -> (0 until array.length()).map { array.getString(it) } }, id = item.getString("id"), question = item.optString("question").takeIf { it.isNotBlank() }, stage = TutorStage.valueOf(item.getString("stage")), text = item.getString("text"),
                    nextStep = item.optString("next").takeIf { it.isNotBlank() },
                    uncertainty = item.optString("uncertainty").takeIf { it.isNotBlank() },
                    verdict = StudyVerdict.entries.firstOrNull { it.name == item.optString("verdict") },
                    citations = (0 until citations.length()).map { n ->
                        val cite = citations.getJSONObject(n)
                        StudyCitation(cite.getString("source"), if (cite.isNull("revision")) null else cite.getLong("revision"),
                            cite.getString("locator"), cite.getString("excerpt"),
                            cite.optString("version").takeIf { it.isNotBlank() }, cite.optString("title").takeIf { it.isNotBlank() })
                    })
            }
        }.getOrDefault(emptyList())
    }
    suspend fun save(id: String, turns: List<StudyTurn>) = recoveryMutex.withLock { withContext(Dispatchers.IO) {
        requireWritable(id)
        val array = JSONArray()
        turns.filter { it.failure == null }.takeLast(30).forEach { turn ->
            val citations = JSONArray()
            turn.citations.take(4).forEach { cite -> citations.put(JSONObject().put("source", cite.sourceObjectId)
                .put("revision", cite.sourceRevision ?: JSONObject.NULL).put("locator", cite.locator ?: "")
                .put("excerpt", cite.excerpt.take(400)).put("version", cite.sourceVersion.orEmpty()).put("title", cite.sourceTitle.orEmpty())) }
            array.put(JSONObject().put("exercise", turn.exerciseId.orEmpty()).put("origins", turn.sourceObjectIds?.let { JSONArray(it) } ?: JSONObject.NULL).put("id", turn.id).put("stage", turn.stage.name).put("text", turn.text.take(12000))
                .put("sourceVersions", turn.sourceVersions?.let { JSONObject(it) } ?: JSONObject.NULL)
                .put("materialFocus", turn.materialFocus.orEmpty())
                .put("next", turn.nextStep.orEmpty().take(1000)).put("uncertainty", turn.uncertainty.orEmpty().take(1000))
                .put("question", turn.question.orEmpty().take(2000)).put("verdict", turn.verdict?.name.orEmpty()).put("citations", citations))
        }
        while (array.length() > 1 && array.toString().length > 590_000) array.remove(0)
        storage.putString(key(id), array.toString())
        revision.update { it + 1 }
    } }
    suspend fun loadJourney(id: String): LearningJourney? = withContext(Dispatchers.IO) {
        val raw = storage.getString("study_journey_v1_$id") ?: return@withContext null
        runCatching {
            val json = JSONObject(raw)
            val branches = json.optJSONArray("branches") ?: JSONArray()
            LearningJourney(LearningStage.valueOf(json.getString("stage")),
                json.optString("prior"), (0 until branches.length()).map { branches.getString(it) },
                json.optString("branch").takeIf(String::isNotBlank), json.optString("reflection"),
                exercises = json.optJSONArray("exercises")?.let { items ->
                    (0 until items.length().coerceAtMost(20)).map { index ->
                        val item = items.getJSONObject(index)
                        HomeworkExercise(item.getString("id"), item.getString("text").take(4000),
                            item.optString("source").takeIf(String::isNotBlank),
                            item.optString("locator").takeIf(String::isNotBlank),
                            item.optString("uncertainty").takeIf(String::isNotBlank), item.optBoolean("reviewed"), item.optBoolean("finished"))
                    }
                }.orEmpty(),
                concepts = json.optJSONArray("concepts")?.let { items -> (0 until items.length().coerceAtMost(80)).map { index ->
                    val item = items.getJSONObject(index)
                    LearningConcept(item.getString("id"), item.getString("title"),
                        item.optString("parent").takeIf(String::isNotBlank), item.optBoolean("explored"))
                } }.orEmpty(),
                explainBack = json.optJSONObject("explainBack")?.let { review ->
                    val items = review.optJSONArray("observations") ?: JSONArray()
                    val questions = review.optJSONArray("questions") ?: JSONArray()
                    ExplainBackReview(review.getString("transcript"), (0 until items.length().coerceAtMost(3)).map { index ->
                        val item = items.getJSONObject(index)
                        ExplainBackObservation(item.getString("quote"), item.getString("suggestion"))
                    }, (0 until questions.length().coerceAtMost(3)).map { questions.getString(it) })
                }, probeSkipped = json.optBoolean("probeSkipped", false),
                explanationDepth = runCatching { com.halo.app.study.StudyExplanationDepth.valueOf(json.optString("explanationDepth")) }
                    .getOrDefault(com.halo.app.study.StudyExplanationDepth.BALANCED),
                summaryNoteId = json.optString("summaryNoteId").takeIf(String::isNotBlank),
                summaryNoteRevision = if (json.has("summaryNoteRevision") && !json.isNull("summaryNoteRevision"))
                    json.getLong("summaryNoteRevision") else null)
        }.getOrNull()
    }
    suspend fun saveJourney(id: String, journey: LearningJourney) = recoveryMutex.withLock { withContext(Dispatchers.IO) {
        requireWritable(id)
        storage.putString("study_journey_v1_$id", JSONObject()
            .put("stage", journey.stage.name).put("prior", journey.priorKnowledge.take(2000))
            .put("probeSkipped", journey.probeSkipped)
            .put("explanationDepth", journey.explanationDepth.name)
            .put("summaryNoteId", journey.summaryNoteId.orEmpty())
            .put("summaryNoteRevision", journey.summaryNoteRevision ?: JSONObject.NULL)
            .put("branches", JSONArray(journey.branches.takeLast(40)))
            .put("branch", journey.currentBranch.orEmpty().take(300))
            .put("reflection", journey.reflection.take(4000))
            .put("concepts", JSONArray().apply { journey.concepts.take(80).forEach { concept ->
                put(JSONObject().put("id", concept.id).put("title", concept.title.take(300))
                    .put("parent", concept.parentId.orEmpty()).put("explored", concept.explored))
            } })
            .put("explainBack", journey.explainBack?.let { review -> JSONObject()
                .put("transcript", review.transcript.take(4000)).put("observations", JSONArray().apply {
                    review.observations.take(3).forEach { put(JSONObject().put("quote", it.quote).put("suggestion", it.suggestion)) }
                }).put("questions", JSONArray(review.questions.take(3))) } ?: JSONObject.NULL)
            .put("exercises", JSONArray().apply { journey.exercises.take(20).forEach { exercise ->
                put(JSONObject().put("id", exercise.id).put("text", exercise.text.take(4000))
                    .put("source", exercise.sourceObjectId.orEmpty()).put("locator", exercise.locator.orEmpty())
                    .put("uncertainty", exercise.uncertainty.orEmpty()).put("reviewed", exercise.reviewed).put("finished", exercise.finished))
            } }).toString())
    } }
    private fun snapshot(id: String) = listOf(key(id), "study_journey_v1_$id", recoveryKey(id)).associateWith { storage.getString(it) }
    private fun fingerprint(id: String, values: Map<String, String?>): String {
        val encoded = JSONArray(values.values.toList() + storage.getBoolean(deletedKey(id), false)).toString()
        return "study-local:" + java.security.MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    override suspend fun version(sessionId: String): String = recoveryMutex.withLock { withContext(Dispatchers.IO) {
        fingerprint(sessionId, snapshot(sessionId))
    } }

    override suspend fun deleteWith(sessionId: String, expectedVersion: String?, deleteRecords: suspend (() -> Unit) -> Boolean): Boolean =
        recoveryMutex.withLock { withContext(NonCancellable + Dispatchers.IO) {
            // Once admitted, finish the bounded local transaction and its outcome handoff.
            // Cancellation after SQLite commits must not restore deleted local history.
            val before = snapshot(sessionId)
            val wasDeleted = storage.getBoolean(deletedKey(sessionId), false)
            if (expectedVersion != null && expectedVersion != fingerprint(sessionId, before)) return@withContext false
            var changed = false
            fun restore() {
                storage.putBatch(strings = before.filterValues { it != null }, booleans = mapOf(deletedKey(sessionId) to wasDeleted))
                revision.update { it + 1 }
            }
            try {
                val result = deleteRecords {
                    changed = true
                    storage.putBatch(strings = before.mapValues { null }, booleans = mapOf(deletedKey(sessionId) to true))
                    revision.update { it + 1 }
                }
                if (!result && changed) restore()
                result
            } catch (failure: Exception) {
                if (changed) try { restore() } catch (restoreFailure: Exception) { failure.addSuppressed(restoreFailure) }
                throw failure
            }
        } }

    suspend fun delete(id: String) { check(deleteWith(id, null) { clear -> clear(); true }) }
}
