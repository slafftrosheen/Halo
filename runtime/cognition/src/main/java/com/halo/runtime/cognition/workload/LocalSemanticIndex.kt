package com.halo.runtime.cognition.workload

import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import kotlin.math.sqrt

data class SemanticSource(val id: String,val version: String,val workspace: String,val text: String,
    val provenance: String,val updatedAtMs: Long)
data class SemanticChunk(val sourceId: String,val sourceVersion: String,val contentSha256: String,
    val workspace: String,val offset: Int,val text: String,val provenance: String,val updatedAtMs: Long,
    val vector: List<Float>)
data class SemanticSnapshot(val space: EmbeddingSpace,val chunks: List<SemanticChunk>)
data class SemanticReference(val sourceId: String,val sourceVersion: String,val workspace: String,
    val offset: Int,val excerpt: String,val provenance: String,val score: Double)
interface SemanticIndexStore {
    suspend fun read(): SemanticSnapshot?
    suspend fun replace(snapshot: SemanticSnapshot?)
}

/** Caller-owned bounded work, local-only vectors, atomic publication after complete embedding. */
class LocalSemanticIndex(private val provider: EmbeddingProvider,private val store: SemanticIndexStore,
    private val space: EmbeddingSpace,private val voiceBusy: ()->Boolean,
    private val permitted: suspend (workspace: String,sourceId: String,version: String)->Boolean) {
    private val operation=Mutex()
    suspend fun synchronize(workspace: String,sources: List<SemanticSource>,enabled: Boolean) {
        require(enabled) { "SEMANTIC_INDEX_OPT_IN_REQUIRED" }
        require(workspace.isNotBlank() && sources.size<=64 && sources.all { it.workspace==workspace && it.text.length<=65536 })
        require(sources.map { it.id }.distinct().size==sources.size)
        check(!voiceBusy()) { "VOICE_INTERACTION_ACTIVE" }
        check(operation.tryLock()) { "SEMANTIC_INDEX_BUSY" }
        try {
            val previous=store.read()?.takeIf { it.space==space }
            val retained=previous?.chunks.orEmpty().filter { it.workspace!=workspace }
            val prepared=mutableListOf<SemanticChunk>()
            for(source in sources) {
                currentCoroutineContext().ensureActive(); check(!voiceBusy()) { "VOICE_INTERACTION_ACTIVE" }
                if(!permitted(workspace,source.id,source.version)) continue
                val hash=sha(source.text)
                val existing=previous?.chunks.orEmpty().filter { it.workspace==workspace && it.sourceId==source.id &&
                    it.sourceVersion==source.version && it.contentSha256==hash }
                if(existing.isNotEmpty()) { prepared+=existing; continue }
                val pieces=source.text.indices.step(900).map { start -> start to source.text.substring(start,minOf(start+1024,source.text.length)) }
                    .filter { it.second.isNotBlank() }
                require((retained.size+prepared.size+pieces.size)*space.dimension.toLong()<=1_048_576) { "INDEX_MEMORY_LIMIT" }
                for(batch in pieces.chunked(16)) {
                    currentCoroutineContext().ensureActive(); check(!voiceBusy()) { "VOICE_INTERACTION_ACTIVE" }
                    val embedded=provider.embed(EmbeddingRequest(batch.map { it.second },space.modelSha256,space.dimension))
                    embedded.requireCompatible(space); require(embedded.vectors.size==batch.size)
                    batch.zip(embedded.vectors).forEach { (piece,vector) -> prepared+=SemanticChunk(source.id,source.version,hash,
                        workspace,piece.first,piece.second,source.provenance,source.updatedAtMs,vector) }
                }
            }
            require(retained.size+prepared.size<=512)
            // Recheck ownership/version after potentially long native work; stale sources never publish.
            val valid=prepared.filter { permitted(it.workspace,it.sourceId,it.sourceVersion) }
            store.replace(SemanticSnapshot(space,retained+valid))
        } finally { operation.unlock() }
    }
    suspend fun delete(workspace: String,sourceId: String)=operation.withLock {
        store.read()?.let { store.replace(it.copy(chunks=it.chunks.filterNot { row -> row.workspace==workspace && row.sourceId==sourceId })) }
    }
    suspend fun query(workspace: String,text: String,enabled: Boolean,limit: Int=8): List<SemanticReference> {
        require(enabled && text.length in 1..4096 && limit in 1..16)
        check(!voiceBusy()) { "VOICE_INTERACTION_ACTIVE" }
        val snapshot=operation.withLock { store.read() }?.takeIf { it.space==space } ?: return emptyList()
        val allowed=snapshot.chunks.filter { it.workspace==workspace && permitted(workspace,it.sourceId,it.sourceVersion) }
        if(allowed.isEmpty()) return emptyList()
        val vector=provider.embed(EmbeddingRequest(listOf(text),space.modelSha256,space.dimension))
        vector.requireCompatible(space)
        return allowed.filter { permitted(workspace,it.sourceId,it.sourceVersion) }.map { row ->
            val a=vector.vectors.single(); val b=row.vector
            val dot=a.indices.sumOf { a[it].toDouble()*b[it] }
            val norm=sqrt(a.sumOf { it.toDouble()*it }*b.sumOf { it.toDouble()*it })
            SemanticReference(row.sourceId,row.sourceVersion,workspace,row.offset,row.text,row.provenance,
                if(norm>0) dot/norm else 0.0)
        }.sortedByDescending { it.score }.take(limit)
    }
    companion object { private fun sha(text: String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) } }
}
