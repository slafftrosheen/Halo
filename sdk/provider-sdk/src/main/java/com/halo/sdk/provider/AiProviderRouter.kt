package com.halo.sdk.provider

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Immutable execution target resolved by the AI routing boundary.
 *
 * Every cognitive transaction must resolve exactly one target before
 * generation starts. Provider/model/capabilities must not be independently
 * re-resolved later in the same transaction.
 */
data class AiExecutionTarget(
    val provider: AiInferenceProvider,
    val model: AiModel,
    val providerCapabilities: Set<AiCapability>,
    val effectiveCapabilities: Set<AiCapability> = providerCapabilities,
) {
    val providerId: String
        get() = provider.id

    val modelId: String
        get() = model.id
}

enum class TargetRejectionReason {
    PROVIDER_NOT_READY,
    MODEL_NOT_INSTALLED,
    MODEL_NOT_ADVERTISED,
    CAPABILITY_MISMATCH,
    INPUT_MODALITY_UNSUPPORTED,
    TOOLS_UNSUPPORTED,
    STREAMING_UNSUPPORTED,
    CONTEXT_WINDOW_TOO_SMALL,
    REMOTE_CONTEXT_FORBIDDEN,
    MEMORY_PREFLIGHT_FAILED,
    NETWORK_UNAVAILABLE,
    CONFIGURATION_MISSING,
    POLICY_EXCLUDED,
    PROVIDER_COOLDOWN,
    RESOURCE_BUSY,
}

data class RejectedAiTarget(
    val providerId: String,
    val modelId: String?,
    val reason: TargetRejectionReason,
)

data class AiRoutingDecision(
    val policy: AiRoutingPolicy,
    val selectedProviderId: String?,
    val selectedModelId: String?,
    val resolvedProviderId: String?,
    val resolvedModelId: String?,
    val rejectedTargets: List<RejectedAiTarget>
)

/**
 * Interface for routing AI requests to the appropriate provider.
 */
interface AiProviderRouter {
    val lastRoutingDecision: StateFlow<AiRoutingDecision?>
        get() = MutableStateFlow(null)

    val selectedProviderId: StateFlow<String?>
    val selectedModelId: StateFlow<String?>
    val diagnosticsFlow: StateFlow<List<AiProviderDiagnostic>>
        get() = MutableStateFlow(emptyList())

    /** Policy used to resolve execution targets. Read-only projection for observers. */
    val routingPolicy: StateFlow<AiRoutingPolicy>
        get() = MutableStateFlow(AiRoutingPolicy.AUTOMATIC)

    fun selectProvider(id: String)

    fun selectModel(modelId: String?)

    fun getAllProviders(): List<AiProvider>

    /**
     * Resolve the complete execution target for a request and workload spec.
     */
    suspend fun resolveExecutionTarget(
        request: AiRequest,
        workload: AiWorkloadSpec = AiWorkloadSpec(),
    ): AiExecutionTarget

    suspend fun resolveExecutionTarget(
        request: AiRequest
    ): AiExecutionTarget = resolveExecutionTarget(request, AiWorkloadSpec())

    /**
     * Legacy provider-only resolution.
     */
    suspend fun getEligibleProvider(
        request: AiRequest
    ): AiProvider

    suspend fun execute(
        request: AiRequest,
        context: AiContext = AiContext.EMPTY
    ): AiResponse

    suspend fun diagnostics(): List<AiProviderDiagnostic> = emptyList()

    fun start()

    fun stop()
}
