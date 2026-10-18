package com.halo.app.ui.screens.study

import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.study.TutorStage
import com.halo.app.data.study.StudyCitation
import com.halo.app.data.study.StudyVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** Bounded local lesson history, independent of notebook sources and graded attempts. */
class StudyLessonStore(private val storage: Storage) {
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
                StudyTurn(exerciseId = item.optString("exercise").takeIf(String::isNotBlank), sourceObjectIds = item.optJSONArray("origins")?.let { array -> (0 until array.length()).map { array.getString(it) } }, id = item.getString("id"), question = item.optString("question").takeIf { it.isNotBlank() }, stage = TutorStage.valueOf(item.getString("stage")), text = item.getString("text"),
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
    suspend fun save(id: String, turns: List<StudyTurn>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        turns.filter { it.failure == null }.takeLast(30).forEach { turn ->
            val citations = JSONArray()
            turn.citations.take(4).forEach { cite -> citations.put(JSONObject().put("source", cite.sourceObjectId)
                .put("revision", cite.sourceRevision ?: JSONObject.NULL).put("locator", cite.locator ?: "")
                .put("excerpt", cite.excerpt.take(400)).put("version", cite.sourceVersion.orEmpty()).put("title", cite.sourceTitle.orEmpty())) }
            array.put(JSONObject().put("exercise", turn.exerciseId.orEmpty()).put("origins", turn.sourceObjectIds?.let { JSONArray(it) } ?: JSONObject.NULL).put("id", turn.id).put("stage", turn.stage.name).put("text", turn.text.take(12000))
                .put("next", turn.nextStep.orEmpty().take(1000)).put("uncertainty", turn.uncertainty.orEmpty().take(1000))
                .put("question", turn.question.orEmpty().take(2000)).put("verdict", turn.verdict?.name.orEmpty()).put("citations", citations))
        }
        while (array.length() > 1 && array.toString().length > 590_000) array.remove(0)
        storage.putString(key(id), array.toString())
        revision.update { it + 1 }
    }
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
                            item.optString("uncertainty").takeIf(String::isNotBlank), item.optBoolean("reviewed"))
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
                })
        }.getOrNull()
    }
    suspend fun saveJourney(id: String, journey: LearningJourney) = withContext(Dispatchers.IO) {
        storage.putString("study_journey_v1_$id", JSONObject()
            .put("stage", journey.stage.name).put("prior", journey.priorKnowledge.take(2000))
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
                    .put("uncertainty", exercise.uncertainty.orEmpty()).put("reviewed", exercise.reviewed))
            } }).toString())
    }
    suspend fun delete(id: String) = withContext(Dispatchers.IO) { storage.remove(key(id)); storage.remove("study_journey_v1_$id"); revision.update { it + 1 } }
}
