package com.halo.sdk.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class Role {
    SYSTEM, USER, ASSISTANT, TOOL
}

data class AIMessage(
    val role: Role,
    val content: String,
    /** Tool calls emitted by an assistant message, preserved for provider protocols. */
    val toolCalls: List<ToolCall> = emptyList(),
    /** Correlates a TOOL result with the assistant tool call that requested it. */
    val toolCallId: String? = null
)

/**
 * Multimodal AI capabilities.
 */
enum class AiCapability {
    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    MULTIMODAL,
    STREAMING,
    TOOLS
}

/**
 * Modern multimodal AI request.
 */
data class AiRequest(
    val text: String? = null,
    val image: ByteArray? = null,
    val audio: ByteArray? = null,
    val video: ByteArray? = null,
    val mimeTypes: List<String> = emptyList(),
    val modelId: String? = null
)

fun AiRequest.validateForTextGeneration() {
    require(!text.isNullOrBlank()) {
        "Text generation request must contain non-empty text"
    }
}

/**
 * Verifies that all bound inputs are supported by the selected provider.
 */
fun AiRequest.validateAgainst(capabilities: Set<AiCapability>) {
    fun supports(capability: AiCapability): Boolean = capabilities.contains(capability)
    fun requireSupported(present: Boolean, capability: AiCapability, label: String) {
        if (present && !supports(capability)) {
            throw AiProviderException(
                AiErrorType.UNSUPPORTED_CAPABILITY,
                "Provider does not support $label input"
            )
        }
    }
    requireSupported(!text.isNullOrBlank(), AiCapability.TEXT, "text")
    requireSupported(image != null, AiCapability.IMAGE, "image")
    requireSupported(audio != null, AiCapability.AUDIO, "audio")
    requireSupported(video != null, AiCapability.VIDEO, "video")
}

data class ToolCall(
    val id: String,
    val functionName: String,
    val arguments: Map<String, Any>
)

data class AiGenerationMetrics(
    val startedAtMs: Long,
    val firstTokenAtMs: Long? = null,
    val completedAtMs: Long? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null
) {
    val timeToFirstTokenMs: Long?
        get() = firstTokenAtMs?.minus(startedAtMs)
    val totalDurationMs: Long?
        get() = completedAtMs?.minus(startedAtMs)
}

data class AiExecutionMetadata(
    val requestId: String,
    val providerId: String,
    val modelId: String?,
    val startedAtMs: Long,
    val completedAtMs: Long?,
    val success: Boolean,
    val finishReason: String?,
    val errorType: AiErrorType? = null
)

data class AiProviderDiagnostic(
    val providerId: String,
    val displayName: String,
    val state: ProviderStatus.State,
    val message: String?,
    val capabilities: Set<AiCapability>,
    val selected: Boolean,
    val selectedModelId: String?,
    val local: Boolean
)

data class AiRuntimeSnapshot(
    val selectedProviderId: String?,
    val selectedModelId: String?,
    val routingPolicy: AiRoutingPolicy,
    val providers: List<AiProviderDiagnostic>
)

data class AiResponse(
    val text: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val finishReason: String? = null,
    val providerId: String,
    val modelId: String? = null,
    val metrics: AiGenerationMetrics? = null
)

sealed interface AiStreamEvent {
    data object Started : AiStreamEvent
    data class Token(val text: String) : AiStreamEvent
    data class ToolCallDelta(val index: Int, val name: String? = null, val argumentsDelta: String? = null) : AiStreamEvent
    data class Completed(val response: AiResponse) : AiStreamEvent
    data class Failed(val error: Throwable) : AiStreamEvent
}

sealed interface CognitiveSessionState {
    data object Idle : CognitiveSessionState
    data object PreparingContext : CognitiveSessionState
    data class Reasoning(val providerId: String, val modelId: String? = null) : CognitiveSessionState
    data class ExecutingTool(val toolId: String) : CognitiveSessionState
    data class Streaming(val text: String) : CognitiveSessionState
    data class Completed(val response: AiResponse) : CognitiveSessionState
    data class Failed(val message: String) : CognitiveSessionState
}

/**
 * Provider contract for actual inference backends.
 * External assistant handoff is modeled separately by AiHandoffProvider.
 */
interface AiProvider {
    val id: String
    val displayName: String
    val isLocal: Boolean
    val status: StateFlow<ProviderStatus>

    suspend fun getStatus(): ProviderStatus = status.value
    suspend fun getCapabilities(): Set<AiCapability>
    suspend fun generate(request: AiRequest, context: AiContext): AiResponse
    fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent>
    suspend fun listModels(): List<AiModel>
    suspend fun testConnection(): Boolean = getStatus().state == ProviderStatus.State.READY
}

data class ProviderStatus(
    val state: State,
    val message: String? = null
) {
    enum class State {
        READY,
        STANDBY,
        LOADING,
        UNCONFIGURED,
        OFFLINE,
        MODEL_MISSING,
        UNAVAILABLE,
        ERROR
    }

    companion object {
        val READY = ProviderStatus(State.READY)
        val UNAVAILABLE = ProviderStatus(State.UNAVAILABLE)
        val UNCONFIGURED = ProviderStatus(State.UNCONFIGURED)
        val OFFLINE = ProviderStatus(State.OFFLINE)
        val MODEL_MISSING = ProviderStatus(State.MODEL_MISSING)
        val LOADING = ProviderStatus(State.LOADING)
    }
}

data class AiModel(
    val id: String,
    val displayName: String,
    val providerId: String,
    val inputCapabilities: Set<AiCapability>,
    val contextLength: Long?,
    val supportsStreaming: Boolean,
    val supportsTools: Boolean,
    val isFree: Boolean = false
)

// Legacy compatibility (to be removed once migrated)
data class AIConversation(
    val id: String,
    val messages: MutableList<AIMessage>
)

data class AIRequestLegacy(
    val conversation: AIConversation,
    val temperature: Float = 0.2f,
    val maxTokens: Int = 512
)

data class AIResponseLegacy(
    val text: String,
    val finishReason: String,
    val latencyMs: Long
)
