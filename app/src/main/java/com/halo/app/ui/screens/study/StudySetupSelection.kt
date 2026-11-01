package com.halo.app.ui.screens.study

import com.halo.sdk.runtime.study.StudySessionSource
import com.halo.sdk.runtime.study.StudyTextAnchor
import com.halo.app.study.StudySourceLimits
import org.json.JSONArray
import org.json.JSONObject

internal data class StudySetupSelection(val id: String, val contextVersion: String?, val source: StudySessionSource?)

/** Saved-state metadata only. Reopening must resolve and revalidate each selected source. */
internal object StudySetupSelectionCodec {
    fun encode(entries: List<StudySetupSelection>): String {
        require(entries.size <= StudySourceLimits.MAX_SOURCES && entries.map { it.id }.distinct().size == entries.size)
        return JSONArray().apply {
            entries.forEach { entry ->
                require(entry.id.isNotBlank() && entry.id.length <= 200)
                entry.source?.let { require(it.objectId == entry.id && !it.revision.isNullOrBlank()) }
                put(JSONObject().put("id", entry.id).apply {
                    entry.contextVersion?.let { put("context", it) }
                    entry.source?.let { ref ->
                        put("revision", ref.revision)
                        ref.textAnchor?.let { anchor -> put("anchor", JSONObject().put("block", anchor.blockId)
                            .put("start", anchor.start).put("end", anchor.end)) }
                    }
                })
            }
        }.toString().also { require(it.length <= 20_000) }
    }
    fun decode(raw: String?): List<StudySetupSelection> {
        if (raw == null) return emptyList()
        require(raw.length <= 20_000)
        val array = JSONArray(raw)
        require(array.length() <= StudySourceLimits.MAX_SOURCES)
        return List(array.length()) { index ->
            val row = array.getJSONObject(index)
            val id = row.getString("id")
            val revision = if (row.has("revision")) row.getString("revision") else null
            val anchor = if (row.has("anchor")) row.getJSONObject("anchor").let {
                require(revision != null)
                StudyTextAnchor(it.getString("block"), it.getInt("start"), it.getInt("end"))
            } else null
            StudySetupSelection(id, if (row.has("context")) row.getString("context") else null,
                revision?.let { StudySessionSource(id, it, anchor) })
        }.also { encode(it) }
    }
}
