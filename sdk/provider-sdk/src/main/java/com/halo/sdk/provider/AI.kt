package com.halo.sdk.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class Role {
    SYSTEM, USER, ASSISTANT, TOOL
}

data class AIMessage(
    val role: Role,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null
)

enum class AiCapability {
    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    MULTIMODAL,
    STREAMING,
    TOOLS
}

data class AiRequest(
    val text: String? = null,
    val image: ByteArray? = null,
    val audio: ByteArray? = null,
    val video: ByteArray? = null,
    val mimeTypes: List<String> = emptyList(),
    val modelId: String? = null,
    val priority: InferencePriority = InferencePriority.NORMAL,
    /** Optional hard output cap used by bounded diagnostics and compact turns. */
    val maxOutputTokens: Int? = null,
    /** False when durable continuation already contains this user message in context. */
    val appendTextAsUserMessage: Boolean = true,
)

fun AiRequest.validateForTextGeneration() {
    require(!text.isNullOrBlank()) {
        "Text generation request must contain non-empty text"
    }
}

fun AiRequest.validateAgainst(capabilities: Set<AiCapability>) {
    fun requireSupported(present: Boolean, capability: AiCapability, label: String) {
        if (present && capability !in capabilities) {
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
    val errorType: AiErrorType? = null,
    val requestedModelId: String? = modelId,
    val resolvedModelId: String? = modelId,
)

data class AiModelRef(
    val providerId: String,
    val modelId: String,
)

enum class AiExecutionLocation { ON_DEVICE, LOCAL_NETWORK, REMOTE }
enum class AiCostClass { FREE, PAID, LOCAL, UNKNOWN }
enum class AiModelKind { CONCRETE, ROUTER }
enum class ModelCatalogueSource {
    LOCAL_INSTALLED, LOCAL_DOWNLOADABLE, REMOTE_LIVE, REMOTE_CACHED, LAN_LIVE
}
enum class AiWireProtocol {
    OPENAI_CHAT_COMPLETIONS, OPENAI_RESPONSES, ANTHROPIC_MESSAGES, GEMINI_GENERATE_CONTENT, OLLAMA, LITERT_NATIVE
}

data class AiModelRuntimeMetadata(
    val ref: AiModelRef,
    val location: AiExecutionLocation,
    val installed: Boolean?,
    val resident: Boolean?,
    val requiresNetwork: Boolean,
    val requiresCredential: Boolean,
    val costClass: AiCostClass = AiCostClass.UNKNOWN,
    /** Transport detail for diagnostics/adapters; cognition must not branch on it. */
    val wireProtocol: AiWireProtocol? = null,
)

data class AiModelCatalogEntry(
    val model: AiModel,
    val runtime: AiModelRuntimeMetadata,
    val kind: AiModelKind = AiModelKind.CONCRETE,
    val source: ModelCatalogueSource,
)

data class AiModelCatalogState(
    val models: List<AiModelCatalogEntry> = emptyList(),
    val refreshingProviders: Set<String> = emptySet(),
    val providerErrors: Map<String, String> = emptyMap(),
    val refreshedAtMs: Long? = null,
)

data class AiExecutionTargetSnapshot(
    val providerId: String,
    val requestedModelId: String,
    val resolvedModelId: String? = null,
    val executionLocation: AiExecutionLocation,
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
    val metrics: AiGenerationMetrics? = null,
    val requestedModelId: String? = modelId,
    val resolvedModelId: String? = modelId,
    /** Physical/runtime backend when the provider can report it (for example GPU or CPU). */
    val executionBackend: String? = null,
)

sealed interface AiStreamEvent {
    data object Started : AiStreamEvent
    data class Token(val text: String) : AiStreamEvent
    data class ToolCallDelta(
        val index: Int,
        val name: String? = null,
        val argumentsDelta: String? = null,
        /** Provider-issued identity. It must not be synthesized before execution. */
        val id: String? = null,
    ) : AiStreamEvent
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
 * Common metadata/diagnostic surface shared by all provider kinds.
 * Concrete execution contracts are intentionally split between AiInferenceProvider
 * and AiHandoffProvider.
 */
interface AiProvider {
    val id: String
    val displayName: String
    val isLocal: Boolean
    val status: StateFlow<ProviderStatus>

    /** Implementations must internally dispatch any disk, network, or native work. */
    suspend fun getStatus(): ProviderStatus = status.value
    suspend fun getCapabilities(): Set<AiCapability>
    /** Main-safe diagnostic probe; callers do not choose an IO dispatcher. */
    suspend fun testConnection(): Boolean = getStatus().state == ProviderStatus.State.READY
}

sealed interface ProviderReadiness {
    data object Unconfigured : ProviderReadiness
    data object ConfiguredCold : ProviderReadiness
    data object Loading : ProviderReadiness
    data object Ready : ProviderReadiness
    data class Failed(val reason: String) : ProviderReadiness
}

data class ProviderStatus(
    val state: State,
    val message: String? = null,
    val readiness: ProviderReadiness = ProviderReadiness.Unconfigured,
    val rejectionReason: TargetRejectionReason? = null,
    val cooldown: ProviderCooldown? = null,
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
