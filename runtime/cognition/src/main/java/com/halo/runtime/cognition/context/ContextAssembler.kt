package com.halo.runtime.cognition.context

import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiContextPolicy

data class ContextOmission(
    val source: String,
    val reason: String
)

data class ContextAssemblyResult(
    val context: AiContext,
    val omissions: List<ContextOmission> = emptyList()
)

data class AiContextSummary(
    val conversationMessages: Int,
    val recentEvents: Int,
    val memories: Int,
    val tools: Int,
    val hasDeviceState: Boolean,
    val hasImage: Boolean
)

interface ContextAssembler {

    suspend fun assemble(
        policy: AiContextPolicy,
        capabilities: Set<AiCapability>
    ): ContextAssemblyResult
}
