package com.halo.providers.llamacpp

import com.halo.sdk.provider.AiCapability

enum class LocalModelBlocker {
    NO_MODEL_SELECTED,
    MODEL_FILE_MISSING,
    INVALID_GGUF,
    MODEL_ARCHITECTURE_UNSUPPORTED,
    MMPROJ_MISSING,
    MMPROJ_INVALID,
    MMPROJ_LOAD_FAILED,
    MODEL_LOAD_FAILED,
    INSUFFICIENT_MEMORY,
    CONTEXT_ALLOCATION_FAILED,
    CHAT_TEMPLATE_UNSUPPORTED,
    IMAGE_INPUT_FAILED,
    GENERATION_FAILED,
    NATIVE_RUNTIME_UNAVAILABLE,
}

data class LocalModelDiagnosticSnapshot(
    val modelId: String?,
    val architecture: String?,
    val contextLength: Int?,
    val baseModelPresent: Boolean,
    val projectorRequired: Boolean,
    val projectorPresent: Boolean,
    val memoryAdmission: String,
    val loaded: Boolean,
    val effectiveCapabilities: Set<AiCapability>,
    val blocker: LocalModelBlocker?,
    val statusMessage: String
)
