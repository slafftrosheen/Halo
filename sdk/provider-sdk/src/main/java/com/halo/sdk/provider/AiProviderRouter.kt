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
    val effectiveCapabilities: Set<AiCapability> = providerCapabilities.intersect(
        model.inputCapabilities +
            (if (model.supportsTools && AiCapability.TOOLS in providerCapabilities) {
                setOf(AiCapability.TOOLS)
            } else emptySet()) +
            (if (model.supportsStreaming && AiCapability.STREAMING in providerCapabilities) {
                setOf(AiCapability.STREAMING)
            } else emptySet())
    ),
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

    /** Atomically changes provider and model so observers never see a mixed target. */
    fun selectTarget(ref: AiModelRef) {
        selectProvider(ref.providerId)
        selectModel(ref.modelId)
    }

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

    /** Exact target resolution for durable recovery; never falls back or mutates selection. */
    suspend fun resolvePinnedExecutionTarget(
        request: AiRequest,
        workload: AiWorkloadSpec,
        providerId: String,
        modelId: String,
    ): AiExecutionTarget {
        val provider = getAllProviders().firstOrNull { it.id == providerId } as? AiInferenceProvider
            ?: throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, "Durable provider $providerId is no longer available")
        if (!workload.allowRemote && !provider.isLocal) {
            throw AiProviderException(
                AiErrorType.UNSUPPORTED_CAPABILITY,
                "Durable remote provider is no longer permitted for this workload",
                AiFailureDetails(recoverability = Recoverability.USER_ACTION_REQUIRED),
            )
        }
        val status = provider.getStatus()
        status.cooldown?.takeIf { it.isActive() }?.let { cooldown ->
            throw AiProviderException(
                cooldown.reason,
                status.message ?: "Durable provider is temporarily cooling down",
                AiFailureDetails(retryAfterMs = cooldown.remainingMs(), recoverability = Recoverability.RETRY_AFTER),
            )
        }
        val ready = status.state == ProviderStatus.State.READY ||
            status.readiness is ProviderReadiness.ConfiguredCold || status.readiness is ProviderReadiness.Ready
        if (!ready) throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, "Durable provider $providerId is not ready")
        val providerCapabilities = provider.getCapabilities()
        val model = provider.listModels().firstOrNull { it.id == modelId }
            ?: throw AiProviderException(AiErrorType.MODEL_NOT_FOUND, "Durable model $modelId is no longer advertised by $providerId")
        val effectiveCapabilities = providerCapabilities.intersect(
            model.inputCapabilities +
                (if (model.supportsTools && AiCapability.TOOLS in providerCapabilities) setOf(AiCapability.TOOLS) else emptySet()) +
                (if (model.supportsStreaming && AiCapability.STREAMING in providerCapabilities) setOf(AiCapability.STREAMING) else emptySet())
        )
        request.validateAgainst(effectiveCapabilities)
        if (workload.requiredCapabilities.any { it !in effectiveCapabilities } ||
            (workload.tools == AiFeatureRequirement.REQUIRED && AiCapability.TOOLS !in effectiveCapabilities) ||
            (workload.streaming == AiFeatureRequirement.REQUIRED && AiCapability.STREAMING !in effectiveCapabilities) ||
            (workload.minimumContextTokens > 0 && (model.contextLength == null || model.contextLength < workload.minimumContextTokens))
        ) throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY, "Durable target $providerId/$modelId no longer satisfies the workload")
        return AiExecutionTarget(provider, model, providerCapabilities, effectiveCapabilities)
    }

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
