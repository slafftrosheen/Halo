package com.halo.providers.litert.models

import com.halo.sdk.provider.AiCapability

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
