package com.halo.app.ui.screens.study

import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.study.TutorStage
import com.halo.app.data.study.StudyCitation
import com.halo.app.data.study.StudyVerdict
import com.halo.app.study.StudySessionLocalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

data class StudyMaterialFocus(val objectId: String, val version: String, val page: Int? = null)

data class StudyRecoveryState(val draft: String = "", val submissionKey: String? = null,
    val submittedDraft: String? = null, val pendingRequestId: String? = null,
    val materialFocus: StudyMaterialFocus? = null,
    val cardAnswers: Map<String, String> = emptyMap())

/** Bounded local lesson history, independent of notebook sources and graded attempts. */
class StudyLessonStore(private val storage: Storage, private val sessionExists: (suspend (String) -> Boolean)? = null) : StudySessionLocalData {
    internal val course = StudyCourseOrder(storage)
    private val recoveryMutex = Mutex()
    private val privateJournal = StudySidecarJournal(storage)
    private class VaultWrites(val owner: StudyLessonStore) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<VaultWrites>
    }
    private val journeyChanges = MutableStateFlow(0)
    internal val widgetJourneyRevision: kotlinx.coroutines.flow.StateFlow<Int> = journeyChanges
    private fun deletedKey(id: String) = "study_deleted_v1_$id"
    private fun requireWritable(id: String) { check(!storage.getBoolean(deletedKey(id), false)) { "Study session was deleted" } }
    private fun recoveryKey(id: String) = "study_recovery_v1_$id"
    private fun readRecovery(id: String): StudyRecoveryState {
        val raw = storage.getString(recoveryKey(id))
        if (raw == null) return StudyRecoveryState()
        check(raw.length <= 200_000) { "Study recovery exceeds the supported size" }
        return JSONObject(raw).let {
            StudyRecoveryState(it.optString("draft").take(4000),
                it.optString("submission").takeIf(String::isNotBlank),
                it.optString("submitted").takeIf(String::isNotBlank),
                it.optString("pending").takeIf(String::isNotBlank),
                it.optJSONObject("focus")?.let { focus -> StudyMaterialFocus(
                    focus.getString("object"), focus.getString("version"),
                    if (focus.isNull("page")) null else focus.getInt("page")) },
                it.optJSONObject("answers")?.let { answers ->
                    check(answers.length() <= 100)
                    answers.keys().asSequence().associateWith { key -> answers.getString(key).also { answer -> check(answer.length <= 1500) } }
                }.orEmpty())
        }
    }
    suspend fun loadRecovery(id: String) = readWithSidecars(id) { readRecovery(id) }
    private suspend fun updateRecovery(id: String, allowDeletedCleanup: Boolean = false, update: (StudyRecoveryState) -> StudyRecoveryState): StudyRecoveryState =
        recoveryMutex.withLock { withContext(Dispatchers.IO) {
            settlePrivateSidecar(id)
            if (allowDeletedCleanup && storage.getBoolean(deletedKey(id), false)) return@withContext StudyRecoveryState()
            requireWritable(id)
            val previous = readRecovery(id)
            val next = update(previous)
            if (next == previous) return@withContext next
            val encoded = JSONObject().put("draft", next.draft.take(4000))
                .put("submission", next.submissionKey.orEmpty()).put("submitted", next.submittedDraft.orEmpty().take(4000))
                .put("pending", next.pendingRequestId.orEmpty())
                .put("answers", JSONObject(next.cardAnswers))
                .put("focus", next.materialFocus?.let { focus -> JSONObject().put("object", focus.objectId)
                    .put("version", focus.version).put("page", focus.page ?: JSONObject.NULL) } ?: JSONObject.NULL).toString()
            // JSON escaping can expand otherwise bounded answers. Never replace a readable record with an unreadable one.
            require(encoded.length <= 200_000) { "Study recovery exceeds the supported size" }
            storage.putString(recoveryKey(id), encoded)
            check(readRecovery(id) == next) { "Study recovery write was not confirmed" }
            next
        } }
    suspend fun saveDraft(id: String, draft: String) { updateRecovery(id) { state ->
        if (state.submittedDraft == draft.trim()) state.copy(draft = draft.take(4000))
        else state.copy(draft = draft.take(4000), submissionKey = null, submittedDraft = null)
    } }
    suspend fun saveCardAnswers(id: String, answers: Map<String, String>) {
        require(answers.size <= 100 && answers.all { it.key.length in 1..200 && it.value.length <= 1500 })
        updateRecovery(id) { it.copy(cardAnswers = answers.toMap()) }
    }
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
    suspend fun load(id: String): List<StudyTurn> = readWithSidecars(id) { readSavedTurns(id) }

    /** Refuse damaged or unexpectedly oversized history rather than interpreting it as empty.
     * A later generated reply must not overwrite records the current version cannot decode.
     */
    private fun readSavedTurns(id: String): List<StudyTurn> {
        val raw = storage.getString(key(id)) ?: return emptyList()
        require(raw.length <= 600_000) { "Saved lesson history exceeds its supported size; preserve for recovery" }
        val array = JSONArray(raw)
        require(array.length() <= 30) { "Saved lesson history exceeds its supported turn limit" }
        return (0 until array.length()).map { index ->
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
    }
    suspend fun save(id: String, turns: List<StudyTurn>) = recoveryMutex.withLock { withContext(Dispatchers.IO) {
        settlePrivateSidecar(id)
        requireWritable(id)
        // Old history might have become unreadable after a downgrade/storage fault.
        // Do not overwrite it with a new answer assembled from an empty UI projection.
        readSavedTurns(id)
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
        val encoded = array.toString()
        storage.putString(key(id), encoded)
        check(storage.getString(key(id)) == encoded) { "Study replies were not confirmed in local storage" }
        revision.update { it + 1 }
    } }
    suspend fun loadJourney(id: String): LearningJourney? = readWithSidecars(id) { readSavedJourney(id) }

    private fun readSavedJourney(id: String): LearningJourney? {
        val raw = storage.getString("study_journey_v1_$id") ?: return null
        require(raw.length <= 200_000) { "Saved learning progression exceeds its supported size; preserve for recovery" }
        val json = JSONObject(raw)
        val branches = json.optJSONArray("branches") ?: JSONArray()
        return LearningJourney(LearningStage.valueOf(json.getString("stage")),
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
    }
    suspend fun saveJourney(id: String, journey: LearningJourney) = recoveryMutex.withLock { withContext(Dispatchers.IO) {
        settlePrivateSidecar(id)
        requireWritable(id)
        val previousStage = readSavedJourney(id)?.stage
        val key = "study_journey_v1_$id"
        val encoded = JSONObject()
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
            } }).toString()
        // The read path refuses an oversized document. Never write an artifact that
        // this same version would refuse to reopen after process recreation.
        require(encoded.length <= 200_000) { "Learning progression exceeds its supported storage size" }
        storage.putString(key, encoded)
        check(storage.getString(key) == encoded) { "Study progression was not confirmed in local storage" }
        if (previousStage != journey.stage) journeyChanges.update { it + 1 }
    } }
    private fun snapshot(id: String) = listOf(key(id), "study_journey_v1_$id", recoveryKey(id)).associateWith { storage.getString(it) }
    private fun fingerprint(id: String, values: Map<String, String?>): String {
        val encoded = JSONArray(values.values.toList() + storage.getBoolean(deletedKey(id), false)).toString()
        return "study-local:" + java.security.MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    override suspend fun version(sessionId: String): String = recoveryMutex.withLock { withContext(Dispatchers.IO) {
        settlePrivateSidecar(sessionId)
        fingerprint(sessionId, snapshot(sessionId))
    } }

    override suspend fun deleteWith(sessionId: String, expectedVersion: String?, deleteRecords: suspend (() -> Unit) -> Boolean): Boolean =
        recoveryMutex.withLock { withContext(NonCancellable + Dispatchers.IO) {
            settlePrivateSidecar(sessionId)
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

    private suspend fun <T> readWithSidecars(id: String, read: () -> T): T {
        if (currentCoroutineContext()[VaultWrites]?.owner === this) return read()
        return recoveryMutex.withLock { withContext(Dispatchers.IO) { settlePrivateSidecar(id); read() } }
    }

    internal suspend fun <T> withPrivateLibrarySidecars(action: suspend () -> T): T = recoveryMutex.withLock {
        withContext(Dispatchers.IO + VaultWrites(this)) {
            settleAllPrivateSidecarsLocked()
            try { action() }
            finally { withContext(NonCancellable) { settleAllPrivateSidecarsLocked() } }
        }
    }

    internal suspend fun settlePrivateLibrarySidecars() = recoveryMutex.withLock {
        withContext(Dispatchers.IO) { settleAllPrivateSidecarsLocked() }
    }

    internal suspend fun capturePrivateSidecars(id: String): StudyPrivateSidecars {
        requireVaultWrites()
        return rawPrivateSidecars(id)
    }

    internal suspend fun withdrawPrivateSidecars(id: String, expected: StudyPrivateSidecars) {
        requireVaultWrites()
        check(rawPrivateSidecars(id) == expected) { "Study drafts changed since review" }
        check(sessionExists != null) { "Study private recovery is unavailable" }
        privateJournal.stage(id, StudySidecarMove.WITHDRAW, expected, sidecarValues(id, null), mapOf(deletedKey(id) to true))
        publishSidecarChanges()
    }

    internal suspend fun restorePrivateSidecars(id: String, expected: StudyPrivateSidecars) {
        requireVaultWrites()
        check(snapshot(id).values.all { it == null }) { "Existing Study drafts must be preserved" }
        check(sessionExists != null) { "Study private recovery is unavailable" }
        privateJournal.stage(id, StudySidecarMove.RESTORE, expected, emptyMap(), emptyMap())
    }

    private suspend fun requireVaultWrites() {
        check(currentCoroutineContext()[VaultWrites]?.owner === this) { "Study private storage was not reserved" }
    }

    private fun rawPrivateSidecars(id: String) = StudyPrivateSidecars(storage.getString(key(id)),
        storage.getString("study_journey_v1_$id"), storage.getString(recoveryKey(id)), storage.getBoolean(deletedKey(id), false))

    private fun sidecarValues(id: String, values: StudyPrivateSidecars?): Map<String, String?> = mapOf(
        key(id) to values?.replies, "study_journey_v1_$id" to values?.journey, recoveryKey(id) to values?.recovery,
    )

    private suspend fun settleAllPrivateSidecarsLocked() {
        privateJournal.pending().forEach { settlePrivateSidecar(it) }
    }

    private suspend fun settlePrivateSidecar(id: String) {
        val move = privateJournal.move(id) ?: return
        val isPublic = requireNotNull(sessionExists) { "Study private recovery is unavailable" }(id)
        val restored = if (isPublic) privateJournal.read(id, move) else null
        privateJournal.finish(id, sidecarValues(id, restored), mapOf(deletedKey(id) to (restored?.deleted ?: true)), remainsPrivate = !isPublic)
        publishSidecarChanges()
    }

    private fun publishSidecarChanges() {
        revision.update { it + 1 }
        journeyChanges.update { it + 1 }
    }
}
