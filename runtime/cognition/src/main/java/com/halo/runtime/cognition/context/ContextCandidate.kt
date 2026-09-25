package com.halo.runtime.cognition.context

import com.halo.cognition.language.InteractionId
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.runtime.ObservationOrigin

@JvmInline
value class ContextCandidateId(val value: String)

enum class ContextTrust {
    USER_AUTHORED,
    HALO_GENERATED,
    TRUSTED_RUNTIME,
    EXTERNAL_UNTRUSTED,
}

enum class ContextPriority {
    CRITICAL,
    HIGH,
    NORMAL,
    LOW,
}

enum class ContextFreshness {
    REALTIME,
    RECENT,
    STALE,
}

enum class ContextOmissionReason {
    BUDGET,
    LOW_RELEVANCE,
    DUPLICATE,
    POLICY,
    PROVIDER_CAPABILITY,
    PRIVACY,
    TOO_LARGE,
    STALE,
    UNAVAILABLE,
}

sealed interface ContextSource {
    data object CurrentRequest : ContextSource
    data object Conversation : ContextSource
    data object ExplicitWorkspaceContext : ContextSource
    data object WorkingSet : ContextSource
    data object Memory : ContextSource
    data object WorkspaceSearch : ContextSource
    data object DeviceState : ContextSource
    data object RuntimeState : ContextSource
    data object ToolResult : ContextSource
    data object Notification : ContextSource
    data object ScreenContext : ContextSource
    data object AssistantScreenshot : ContextSource
    data object VisualObservation : ContextSource
    data object ImportedFile : ContextSource
}

sealed interface ContextContent {
    data class Text(val text: String) : ContextContent

    /**
     * Structured screen content remains data. Provider adapters must never
     * reinterpret text captured from another app as system/tool instructions.
     */
    data class ScreenContent(
        val packageName: String?,
        val title: String?,
        val text: String,
        val uri: String?,
        val capturedAtMs: Long,
        val provenance: String,
    ) : ContextContent

    /** The bytes and provenance travel together; no global "latest image" lookup. */
    data class ImageContent(val image: ImageContext) : ContextContent

    data class FactContent(val key: String, val value: String) : ContextContent
}

data class ContextCandidate(
    val id: ContextCandidateId,
    val source: ContextSource,
    val content: ContextContent,
    val trust: ContextTrust,
    val relevance: Float,
    val priority: ContextPriority,
    val estimatedTokens: Int,
    val freshness: ContextFreshness,
    val objectId: String? = null,
)

data class OmittedContextCandidate(
    val candidate: ContextCandidate,
    val reason: ContextOmissionReason,
)

data class ContextQuery(
    val interactionId: InteractionId,
    val text: String,
    val requestedCapabilities: Set<AiCapability> = emptySet(),
    val activeWorkspaceObjectId: String? = null,
    val origin: ObservationOrigin? = null,
    val maximumCandidates: Int = 20,
)

interface ContextSourceProvider {
    val source: ContextSource
    suspend fun retrieve(query: ContextQuery, limit: Int): List<ContextCandidate>
}
