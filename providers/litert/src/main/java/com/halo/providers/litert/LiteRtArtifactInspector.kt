package com.halo.providers.litert

import com.google.ai.edge.litertlm.ModelInfo
import com.google.ai.edge.litertlm.Modality
import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*

data class LiteRtArtifactMetadata(val task: String,val textBackends: List<String>,val vision: Boolean,
    val nativeTools: Boolean,val embeddingDimension: Int?)

/** Explicit inspection adapter; native SDK types do not escape into app/portable contracts. */
class LiteRtArtifactInspector(private val resources: NativeResourceCoordinator) {
    suspend fun inspect(artifact: LocalModelDescriptor): LiteRtArtifactMetadata {
        require(artifact.verified)
        val owner=object: NativeResourceOwner {
            override val nativeResourceId="workload.manual-inspection"
            override val nativeResourceKind=NativeResourceKind.HEAVY_LLM
            override suspend fun releaseIfIdle()=false
        }
        if(!resources.acquire(owner,diagnostic=true)) { resources.unregister(owner); error("NATIVE_RESOURCE_BUSY") }
        try { return withContext(Dispatchers.Default) {
            ModelInfo.from(artifact.path).use { info -> LiteRtArtifactMetadata(info.modelType.name,
                info.supportedBackends(Modality.TEXT).map { it.name },info.inputModalities().vision,
                (info as? ModelInfo.Llm)?.supportsFunctionCalling()==true,(info as? ModelInfo.Embedding)?.dimension()) }
        } } finally { withContext(NonCancellable) { resources.unregister(owner) } }
    }
}
