package com.halo.app.ai

import android.util.AtomicFile
import com.halo.runtime.cognition.workload.*
import com.halo.sdk.provider.EmbeddingSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Private derived data, deliberately outside Android backup and diagnostic exports. */
class FileSemanticIndexStore(file: File): SemanticIndexStore {
    private val atomic=AtomicFile(file)
    private val lock=Mutex()
    override suspend fun read(): SemanticSnapshot?=withContext(Dispatchers.IO) { lock.withLock {
        if(!atomic.baseFile.exists()) return@withLock null
        require(atomic.baseFile.length()<=32L*1024*1024)
        val json=atomic.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        val schema = json.getInt("schema")
        require(schema in 1..2)
        // Schema 1 was written only by the text-only Notebook index. Schema 2
        // records modality explicitly so a future media index cannot reuse these rows.
        if (schema >= 2) {
            val modalities = json.getJSONArray("modalities")
            require(modalities.length() == 1 && modalities.getString(0) == "TEXT")
        }
        val space=EmbeddingSpace(json.getString("sha"),json.getInt("dimension"),json.getBoolean("normalized"),
            json.getString("runtime"),json.getString("preprocessing"))
        val rows=json.getJSONArray("chunks"); require(rows.length()<=512 && rows.length()*space.dimension.toLong()<=1_048_576)
        val chunks=(0 until rows.length()).map { i ->
            val row=rows.getJSONObject(i); val vector=row.getJSONArray("vector")
            require(vector.length()==space.dimension)
            val values=(0 until vector.length()).map { vector.getDouble(it).toFloat().also { v -> require(v.isFinite()) } }
            SemanticChunk(row.getString("id"),row.getString("version"),row.getString("contentSha"),row.getString("workspace"),
                row.getInt("offset"),row.getString("text").also { require(it.length<=1024) },row.getString("provenance"),row.getLong("at"),values)
        }
        SemanticSnapshot(space,chunks)
    } }
    override suspend fun replace(snapshot: SemanticSnapshot?)=withContext(Dispatchers.IO) { lock.withLock {
        if(snapshot==null) { atomic.delete(); return@withLock }
        val json=JSONObject().put("schema",2).put("modalities", JSONArray().put("TEXT"))
            .put("sha",snapshot.space.modelSha256).put("dimension",snapshot.space.dimension)
            .put("normalized",snapshot.space.normalized).put("runtime",snapshot.space.runtimeIdentity)
            .put("preprocessing",snapshot.space.preprocessingIdentity)
        json.put("chunks",JSONArray().apply { snapshot.chunks.forEach { row -> put(JSONObject()
            .put("id",row.sourceId).put("version",row.sourceVersion).put("contentSha",row.contentSha256)
            .put("workspace",row.workspace).put("offset",row.offset).put("text",row.text)
            .put("provenance",row.provenance).put("at",row.updatedAtMs).put("vector",JSONArray(row.vector))) } })
        val bytes=json.toString().toByteArray(); require(bytes.size<=32*1024*1024)
        atomic.baseFile.parentFile?.mkdirs()
        val stream=atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) } catch(t: Throwable) { atomic.failWrite(stream); throw t }
    } }
}
