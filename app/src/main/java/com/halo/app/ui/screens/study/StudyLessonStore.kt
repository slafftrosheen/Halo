package com.halo.app.ui.screens.study

import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.study.TutorStage
import com.halo.app.data.study.StudyCitation
import com.halo.app.data.study.StudyVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Bounded local lesson history, independent of notebook sources and graded attempts. */
class StudyLessonStore(private val storage: Storage) {
    private fun key(id: String) = "study_lesson_v1_$id"
    suspend fun load(id: String): List<StudyTurn> = withContext(Dispatchers.IO) {
        val raw = storage.getString(key(id)) ?: return@withContext emptyList()
        runCatching {
            val array = JSONArray(raw.take(600_000))
            (0 until array.length().coerceAtMost(30)).map { index ->
                val item = array.getJSONObject(index)
                val citations = item.optJSONArray("citations") ?: JSONArray()
                StudyTurn(sourceObjectIds = item.optJSONArray("origins")?.let { array -> (0 until array.length()).map { array.getString(it) } }, id = item.getString("id"), question = item.optString("question").takeIf { it.isNotBlank() }, stage = TutorStage.valueOf(item.getString("stage")), text = item.getString("text"),
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
            array.put(JSONObject().put("origins", turn.sourceObjectIds?.let { JSONArray(it) } ?: JSONObject.NULL).put("id", turn.id).put("stage", turn.stage.name).put("text", turn.text.take(12000))
                .put("next", turn.nextStep.orEmpty().take(1000)).put("uncertainty", turn.uncertainty.orEmpty().take(1000))
                .put("question", turn.question.orEmpty().take(2000)).put("verdict", turn.verdict?.name.orEmpty()).put("citations", citations))
        }
        while (array.length() > 1 && array.toString().length > 590_000) array.remove(0)
        storage.putString(key(id), array.toString())
    }
    suspend fun delete(id: String) = withContext(Dispatchers.IO) { storage.remove(key(id)) }
}
