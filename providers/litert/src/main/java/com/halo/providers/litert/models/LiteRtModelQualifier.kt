package com.halo.providers.litert.models

import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.LocalComputeBackend

enum class CapabilityState {
    DECLARED,
    VERIFIED,
    UNSUPPORTED,
    UNKNOWN,
}

data class LocalModelCapabilities(
    val text: CapabilityState = CapabilityState.VERIFIED,
    val streaming: CapabilityState = CapabilityState.VERIFIED,
    val tools: CapabilityState = CapabilityState.UNSUPPORTED,
    val vision: CapabilityState = CapabilityState.UNSUPPORTED,
    val audio: CapabilityState = CapabilityState.UNSUPPORTED,
)

object LiteRtModelQualifier {

    fun qualifyCapabilities(descriptor: LocalModelDescriptor): Set<AiCapability> {
        return buildSet {
            add(AiCapability.TEXT)
            add(AiCapability.STREAMING)
            if (descriptor.supportsVision) add(AiCapability.IMAGE)
            if (descriptor.supportsTools) add(AiCapability.TOOLS)
        }
    }
}

/** Stage at which a qualification run failed. */
enum class ModelVerificationStage {
    DOWNLOAD,
    INTEGRITY,
    ENGINE_INIT,
    CONVERSATION,
    GENERATION,
}

/**
 * Proof that a model actually *runs*, not merely that a file exists.
 *
 * `installed != verified`: only a completed qualification promotes an artifact to
 * "Verified on GPU/CPU" in the model browser.
 */
sealed interface ModelVerificationResult {
    data class Verified(
        val modelId: String,
        val backend: LocalComputeBackend,
        val loadMs: Long,
        val ttftMs: Long,
        val sampleText: String,
    ) : ModelVerificationResult

    data class Failed(
        val modelId: String,
        val stage: ModelVerificationStage,
        val reason: String,
        val technicalDetail: String? = null,
    ) : ModelVerificationResult
}
