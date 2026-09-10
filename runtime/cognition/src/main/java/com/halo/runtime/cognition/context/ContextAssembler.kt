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
    val omissions: List<ContextOmission> = emptyList(),
    val image: ImageContext? = null
)

data class ImageContext(
    val imageBytes: ByteArray,
    val mimeType: String,
    val timestampMs: Long
) {

    val sizeBytes: Int
        get() = imageBytes.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageContext) return false
        if (!imageBytes.contentEquals(other.imageBytes)) return false
        if (mimeType != other.mimeType) return false
        if (timestampMs != other.timestampMs) return false
        return true
    }

    override fun hashCode(): Int {
        var result = imageBytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + timestampMs.hashCode()
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
}
