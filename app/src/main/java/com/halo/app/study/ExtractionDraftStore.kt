package com.halo.app.study

import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ExtractionDraft(val parts: List<ExtractionPart>, val savedNoteId: String?)

/** One private review draft, outside backups. Never restores an operation or sends text to AI. */
class ExtractionDraftStore(file: File) {
    private val atomic = AtomicFile(file)

    suspend fun load(): ExtractionDraft? = withContext(Dispatchers.IO) {
        if (!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) return@withContext null
        val bytes = atomic.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_BYTES) { "Saved text draft is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val json = JSONObject(Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString())
        require(json.getInt("version") == 1) { "Saved draft format is unsupported" }
        val array = json.getJSONArray("parts")
        require(array.length() <= 32) { "Saved draft has too many parts" }
        val parts = (0 until array.length()).map { index ->
            val part = array.getJSONObject(index)
            ExtractionPart(part.getString("sourceId"), part.optional("sourceVersion"), part.getString("label"),
                part.getString("locator"), part.getString("method"), part.getString("text"), part.optional("warning"))
        }
        validate(parts)
        ExtractionDraft(parts, json.optional("savedNoteId"))
    }

    suspend fun save(draft: ExtractionDraft) = withContext(Dispatchers.IO) {
        validate(draft.parts)
        if (draft.parts.isEmpty()) { atomic.delete(); return@withContext }
        val parts = JSONArray(draft.parts.map { part -> JSONObject().put("sourceId", part.sourceId)
            .put("sourceVersion", part.sourceVersion).put("label", part.label).put("locator", part.locator)
            .put("method", part.method).put("text", part.text).put("warning", part.warning) })
        val bytes = JSONObject().put("version", 1).put("parts", parts).put("savedNoteId", draft.savedNoteId)
            .toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Text draft exceeds storage limit" }
        atomic.baseFile.parentFile?.mkdirs()
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (error: Throwable) { atomic.failWrite(output); throw error }
    }

    private fun validate(parts: List<ExtractionPart>) {
        require(parts.size <= 32 && parts.sumOf { it.text.length.toLong() } <= StudySourceLimits.DECODED_CHARS)
        require(parts.all { part -> listOfNotNull(part.sourceId, part.sourceVersion, part.label, part.locator,
            part.method, part.warning).all { it.length <= 4096 } }) { "Draft metadata exceeds storage limit" }
    }
    private fun JSONObject.optional(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
    companion object { private const val MAX_BYTES = 1024 * 1024 }
}
