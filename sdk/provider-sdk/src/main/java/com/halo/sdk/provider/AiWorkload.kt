package com.halo.sdk.provider

enum class AiWorkloadKind {
    CONVERSATION,
    QUESTION,
    SCREEN_CONTEXT,
    VISUAL_UNDERSTANDING,
    DOCUMENT_CONTEXT,
    TOOL_ASSISTED,
}

enum class AiFeatureRequirement {
    NOT_NEEDED,
    OPTIONAL,
    REQUIRED,
}

data class AiWorkloadSpec(
    val kind: AiWorkloadKind = AiWorkloadKind.CONVERSATION,
    val requiredCapabilities: Set<AiCapability> = setOf(AiCapability.TEXT),
    val optionalCapabilities: Set<AiCapability> = emptySet(),
    val tools: AiFeatureRequirement = AiFeatureRequirement.OPTIONAL,
    val streaming: AiFeatureRequirement = AiFeatureRequirement.OPTIONAL,
    val minimumContextTokens: Int = 0,
    val allowRemote: Boolean = true,
    val preferredRole: AiModelRole? = null,
    val allowFallback: Boolean = true,
)
