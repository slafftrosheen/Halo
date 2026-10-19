package com.halo.providers.litert

import com.google.ai.edge.litertlm.*
import com.google.ai.edge.litertlm.Modality
import com.halo.providers.litert.models.LocalModelInventoryRepository
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EmbeddingUnavailableException(val reasonCode: String, cause: Throwable? = null): IllegalStateException(reasonCode, cause)
data class EmbeddingExecutionMetrics(val modelSha256: String,val dimension: Int,val batchSize: Int,
    val backend: String,val initMs: Long,val generationMs: Long,val estimatedMemoryBytes: Long)

/** Explicit local execution only; no conversation Engine, remote fallback, resizing or synthetic vectors. */
class LiteRtEmbeddingProvider(private val inventory: LocalModelInventoryRepository,
    private val resources: NativeResourceCoordinator,private val modelId: String) : EmbeddingProvider,NativeResourceOwner {
    override val id="provider.litert.embedding"
    override val nativeResourceId get()="$id:$modelId"
    // Same exclusive physical claim as the LLM, retaining the single-heavy-LiteRT invariant.
    override val nativeResourceKind=NativeResourceKind.HEAVY_LLM
    @Volatile private var estimate=512L*1024*1024
    override val defaultResourcePolicy get()=WorkloadResourcePolicy(RuntimeWorkloadClass.TEXT_CHAT,estimate,true)
    private val lock=Mutex()
    private var pendingTeardown: EmbeddingEngine? = null
    private val mutableMetrics=MutableStateFlow<EmbeddingExecutionMetrics?>(null)
    val metrics=mutableMetrics.asStateFlow()
    override suspend fun releaseIfIdle(): Boolean {
        if(!lock.tryLock()) return false
        return try {
            pendingTeardown?.close()
            pendingTeardown = null
            true
        } catch (_: Exception) { false } finally { lock.unlock() }
    }
    override suspend fun embed(request: EmbeddingRequest): EmbeddingBatch=withContext(Dispatchers.Default) { lock.withLock {
        pendingTeardown?.let { previous ->
            try { previous.close() } catch (failure: Exception) {
                throw EmbeddingUnavailableException("PREVIOUS_TEARDOWN_FAILED", failure)
            }
            pendingTeardown = null
            resources.unregister(this@LiteRtEmbeddingProvider)
        }
        val artifact=inventory.verifyPhysicalArtifact(modelId)
            ?: throw EmbeddingUnavailableException("ARTIFACT_NOT_INSTALLED")
        if(!artifact.verified || artifact.sha256!=request.modelSha256) throw EmbeddingUnavailableException("ARTIFACT_CHANGED")
        estimate=artifact.bytes+256L*1024*1024
        resources.budgetReevaluation?.invoke()
        if(resources.budget.value.pressure!=NativeMemoryPressure.NORMAL || resources.budget.value.availableBytes<estimate)
            throw EmbeddingUnavailableException("MEMORY_NOT_ADMITTED")
        if(resources.snapshot.value.any { it.kind==NativeResourceKind.HEAVY_LLM && it.ownerId!=nativeResourceId && it.state!=NativeResourceState.RELEASED })
            throw EmbeddingUnavailableException("UNLOAD_RESIDENT_LITERT_FIRST")
        if(!resources.acquire(this@LiteRtEmbeddingProvider)) {
            resources.unregister(this@LiteRtEmbeddingProvider)
            throw EmbeddingUnavailableException("NATIVE_RESOURCE_BUSY")
        }
        var engine: EmbeddingEngine?=null
        var primaryFailure: Throwable? = null
        try {
        ModelInfo.from(artifact.path).use { info ->
            if(info !is ModelInfo.Embedding) throw EmbeddingUnavailableException("TASK_NOT_EMBEDDING")
            if(info.dimension()!=request.dimension) throw EmbeddingUnavailableException("DIMENSION_UNSUPPORTED")
            if(BackendType.CPU !in info.supportedBackends(Modality.TEXT)) throw EmbeddingUnavailableException("CPU_UNSUPPORTED")
        }
            resources.mark(this@LiteRtEmbeddingProvider,NativeResourceState.LOADING,"Embedding initialization")
            val started=System.nanoTime()
            engine=EmbeddingEngine(EmbeddingEngineConfig(modelPath=artifact.path,backend=Backend.CPU()))
            engine.initialize()
            val loaded=System.nanoTime()
            resources.mark(this@LiteRtEmbeddingProvider,NativeResourceState.IN_USE,"Embedding computation")
            val vectors=request.texts.map { text ->
                currentCoroutineContext().ensureActive()
                requireNotNull(engine).computeEmbedding(listOf(InputData.Text(text)),EmbeddingOptions(normalize=true,insertSpecialTokens=true)).embedding.toList()
            }
            currentCoroutineContext().ensureActive()
            val result=EmbeddingBatch(EmbeddingSpace(request.modelSha256,request.dimension,true,
                "litertlm/0.18.0",request.preprocessingIdentity),vectors,System.currentTimeMillis())
            mutableMetrics.value=EmbeddingExecutionMetrics(request.modelSha256,request.dimension,request.texts.size,"CPU",
                (loaded-started)/1_000_000,(System.nanoTime()-loaded)/1_000_000,estimate)
            result
        } catch(cancelled: CancellationException) { primaryFailure = cancelled; throw cancelled }
        catch(unsupported: EmbeddingUnavailableException) { primaryFailure = unsupported; throw unsupported }
        catch(error: Exception) {
            val failure = EmbeddingUnavailableException("NATIVE_${error.javaClass.simpleName.take(64)}", error)
            primaryFailure = failure
            throw failure
        }
        finally { withContext(NonCancellable) {
            val cleanupFailure = try { engine?.close(); null } catch (failure: Exception) { failure }
            if (cleanupFailure == null) {
                resources.unregister(this@LiteRtEmbeddingProvider)
            } else {
                pendingTeardown = engine
                resources.mark(this@LiteRtEmbeddingProvider, NativeResourceState.RESIDENT, "Embedding teardown pending")
                val primary = primaryFailure
                if (primary != null) primary.addSuppressed(cleanupFailure)
                else throw EmbeddingUnavailableException("NATIVE_TEARDOWN_FAILED", cleanupFailure)
            }
        } }
    } }
}
