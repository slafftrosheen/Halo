package com.halo.runtime.cognition.context

import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.runtime.VisualFreshness
import com.halo.sdk.runtime.skills.SkillId

data class ContextOmission(
    val source: String,
    val reason: String
)

data class ContextAssemblyResult(
    val context: AiContext,
    val omissions: List<ContextOmission> = emptyList(),
    val image: ImageContext? = null,
    val unsatisfiedRequired: List<ContextNeed> = emptyList(),
)

data class ImageContext(
    val imageBytes: ByteArray,
    val mimeType: String,
    val timestampMs: Long,
    val assetId: String? = null,
    val interactionId: String? = null,
    val originDeviceId: String? = null,
    val source: ContextSource = ContextSource.CurrentRequest,
    val trust: ContextTrust = ContextTrust.EXTERNAL_UNTRUSTED,
    val freshness: VisualFreshness = VisualFreshness.RECENT_CAPTURE,
) {

    val sizeBytes: Int
        get() = imageBytes.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageContext) return false
        return imageBytes.contentEquals(other.imageBytes) &&
            mimeType == other.mimeType &&
            timestampMs == other.timestampMs &&
            assetId == other.assetId &&
            interactionId == other.interactionId &&
            originDeviceId == other.originDeviceId &&
            source == other.source &&
            trust == other.trust &&
            freshness == other.freshness
    }

    override fun hashCode(): Int {
        var result = imageBytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + (assetId?.hashCode() ?: 0)
        result = 31 * result + (interactionId?.hashCode() ?: 0)
        result = 31 * result + (originDeviceId?.hashCode() ?: 0)
        result = 31 * result + source.hashCode()
        result = 31 * result + trust.hashCode()
        result = 31 * result + freshness.hashCode()
        return result
    }
}

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

    suspend fun assemble(
        policy: AiContextPolicy,
        capabilities: Set<AiCapability>,
        request: ContextAssemblyRequest,
    ): ContextAssemblyResult = assemble(policy, capabilities)
}

data class ContextAssemblyRequest(
    val conversationId: String? = null,
    val interactionId: String? = null,
    val allowGenericFallback: Boolean = true,
    val acquisition: ContextAcquisitionResult? = null,
    val contextWindowTokens: Long? = null,
    val requiredNeeds: List<ContextNeed> = emptyList(),
    val targetIsLocal: Boolean = true,
    val userText: String = "",
    val queryText: String = "",
    val mode: CognitiveOperatingMode = CognitiveOperatingMode.AUTO,
    val activatedSkillIds: Set<SkillId> = emptySet(),
)
