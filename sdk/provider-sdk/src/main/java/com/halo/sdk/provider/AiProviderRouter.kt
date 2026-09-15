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
    val providerCapabilities: Set<AiCapability>
) {
    val providerId: String
        get() = provider.id

    val modelId: String
        get() = model.id
}

/**
 * Interface for routing AI requests to the appropriate provider.
 */
interface AiProviderRouter {

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
     * Resolve the complete execution target for a request.
     *
     * Implementations must validate:
     * - provider readiness
     * - routing policy
     * - provider capabilities
     * - advertised model existence
     * - model input capabilities
     */
    suspend fun resolveExecutionTarget(
        request: AiRequest
    ): AiExecutionTarget

    /**
     * Legacy provider-only resolution.
     *
     * Keep this temporarily for callers that only need provider selection.
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
