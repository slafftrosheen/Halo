package com.halo.app.ai

import android.util.AtomicFile
import com.halo.sdk.provider.EmbeddingSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A sampled-frame video vector and source pointer; video and frame bytes are never persisted. */
internal data class VideoSemanticEntry(
    val sourceId: String,
    val sourceVersion: String,
    val contentSha256: String,
    val title: String,
    val provenance: String,
    val vector: List<Float>,
)

internal data class VideoSemanticSnapshot(
    val space: EmbeddingSpace,
    val entries: List<VideoSemanticEntry>,
)

/** Separate VIDEO_FRAME namespace; schema identity includes sampling and frame preprocessing. */
internal class FileVideoSemanticIndexStore(file: File) {
    private val atomic = AtomicFile(file)
    private val lock = Mutex()

    suspend fun read(): VideoSemanticSnapshot? = withContext(Dispatchers.IO) { lock.withLock {
        if (!atomic.baseFile.exists()) return@withLock null
        require(atomic.baseFile.length() <= MAX_BYTES)
        val json = atomic.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        require(json.getInt("schema") == SCHEMA)
        val modalities = json.getJSONArray("modalities")
        require(modalities.length() == 1 && modalities.getString(0) == MODALITY)
        val space = EmbeddingSpace(json.getString("sha"), json.getInt("dimension"),
            json.getBoolean("normalized"), json.getString("runtime"), json.getString("preprocessing"))
        require(space.modelSha256.matches(SHA256) && space.dimension in 1..16_384)
        require(space.normalized && space.runtimeIdentity == RUNTIME_ID &&
            space.preprocessingIdentity == PREPROCESSING_ID)
        val rows = json.getJSONArray("entries")
        require(rows.length() <= MAX_ENTRIES && rows.length().toLong() * space.dimension <= MAX_VECTOR_VALUES)
        val entries = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val vector = row.getJSONArray("vector")
            require(vector.length() == space.dimension)
            VideoSemanticEntry(
                sourceId = row.getString("id").also { require(it.startsWith("attachment:") && it.length <= 200) },
                sourceVersion = row.getString("version").also { require(it.matches(SHA256)) },
                contentSha256 = row.getString("contentSha").also { require(it.matches(SHA256)) },
                title = row.getString("title").also { require(it.length <= 400) },
                provenance = row.getString("provenance").also { require(it.length <= 400) },
                vector = (0 until vector.length()).map {
                    vector.getDouble(it).toFloat().also { value -> require(value.isFinite()) }
                },
            )
        }
        require(entries.map { it.sourceId }.distinct().size == entries.size)
        VideoSemanticSnapshot(space, entries)
    } }

    suspend fun replace(snapshot: VideoSemanticSnapshot?) = withContext(Dispatchers.IO) { lock.withLock {
        if (snapshot == null) {
            atomic.delete()
            return@withLock
        }
        require(snapshot.entries.size <= MAX_ENTRIES)
        require(snapshot.space.modelSha256.matches(SHA256) && snapshot.space.dimension in 1..16_384)
        require(snapshot.space.normalized && snapshot.space.runtimeIdentity == RUNTIME_ID &&
            snapshot.space.preprocessingIdentity == PREPROCESSING_ID)
        require(snapshot.entries.size.toLong() * snapshot.space.dimension <= MAX_VECTOR_VALUES)
        require(snapshot.entries.map { it.sourceId }.distinct().size == snapshot.entries.size)
        val json = JSONObject()
            .put("schema", SCHEMA)
            .put("modalities", JSONArray().put(MODALITY))
            .put("sha", snapshot.space.modelSha256)
            .put("dimension", snapshot.space.dimension)
            .put("normalized", snapshot.space.normalized)
            .put("runtime", snapshot.space.runtimeIdentity)
            .put("preprocessing", snapshot.space.preprocessingIdentity)
            .put("entries", JSONArray().apply {
                snapshot.entries.forEach { entry ->
                    require(entry.sourceVersion.matches(SHA256) && entry.contentSha256.matches(SHA256))
                    require(entry.sourceId.startsWith("attachment:") && entry.sourceId.length <= 200)
                    require(entry.title.length <= 400 && entry.provenance.length <= 400)
                    require(entry.vector.size == snapshot.space.dimension && entry.vector.all { it.isFinite() })
                    put(JSONObject().put("id", entry.sourceId).put("version", entry.sourceVersion)
                        .put("contentSha", entry.contentSha).put("title", entry.title.take(400))
                        .put("provenance", entry.provenance.take(400)).put("vector", JSONArray(entry.vector)))
                }
            })
        val bytes = json.toString().toByteArray()
        require(bytes.size <= MAX_BYTES)
        atomic.baseFile.parentFile?.mkdirs()
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (failure: Throwable) {
            atomic.failWrite(stream)
            throw failure
        }
    } }

    private companion object {
        const val SCHEMA = 1
        const val MODALITY = "VIDEO_FRAME"
        const val MAX_BYTES = 32L * 1024 * 1024
        const val MAX_ENTRIES = 64
        const val MAX_VECTOR_VALUES = 1_048_576L
        const val RUNTIME_ID = "litertlm/0.18.0"
        const val PREPROCESSING_ID = com.halo.providers.litert.LocalMediaEmbeddingSafety.CROSS_MODAL_VIDEO_FRAME_SEARCH_V2
        val SHA256 = Regex("[a-f0-9]{64}")
    }
}
