package com.halo.engines.ai

import com.halo.runtime.provider.response.AiResponseValidator
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.RuntimeLogger
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.ProviderRequest
import com.halo.sdk.runtime.ProviderResult
import com.halo.sdk.runtime.ProviderFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.UUID

/** Routes AI requests while preserving explicit intent and provider boundaries. */
class DefaultAiProviderRouter(
    private val providerRegistry: AiProviderRegistry,
    private val storage: Storage,
    private val eventBus: EventBus? = null,
    private val logger: RuntimeLogger? = null
) : AiProviderRouter {
    constructor(
        providers: List<AiProvider>,
        storage: Storage,
        eventBus: EventBus? = null,
        logger: RuntimeLogger? = null
    ) : this(AiProviderRegistry.from(providers), storage, eventBus, logger)

    private val inferenceProviders: List<AiInferenceProvider> = providerRegistry.inference
    private val allProviders: List<AiProvider> = providerRegistry.all
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _selectedProviderId = MutableStateFlow<String?>(null)
    override val selectedProviderId: StateFlow<String?> = _selectedProviderId.asStateFlow()
    private val _selectedModelId = MutableStateFlow<String?>(null)
    override val selectedModelId: StateFlow<String?> = _selectedModelId.asStateFlow()

    private val _diagnosticsFlow = MutableStateFlow<List<AiProviderDiagnostic>>(
        allProviders.map { provider ->
            AiProviderDiagnostic(provider.id, provider.displayName, ProviderStatus.State.LOADING, null, emptySet(), false, null, provider.isLocal)
        }
    )
    override val diagnosticsFlow: StateFlow<List<AiProviderDiagnostic>> = _diagnosticsFlow.asStateFlow()
    private val _routingPolicy = MutableStateFlow(AiRoutingPolicy.LOCAL_ONLY)
    override val routingPolicy: StateFlow<AiRoutingPolicy> = _routingPolicy.asStateFlow()

    init {
        // Startup must only restore local persisted state. Provider health checks
        // are explicit diagnostics work; some providers (for example Ollama)
        // perform network I/O from getStatus().
        restoreStoredRoutingState()
    }

    private fun restoreStoredRoutingState() {
        val storedProvider = storage.getString("active_ai_provider")
            ?.takeIf { id -> inferenceProviders.any { it.id == id } }
        val restoredProvider = storedProvider ?: inferenceProviders.firstOrNull { it.isLocal }?.id ?: inferenceProviders.firstOrNull()?.id

        _routingPolicy.value = storage.getString("ai_routing_policy")?.let {
            runCatching { enumValueOf<AiRoutingPolicy>(it) }.getOrDefault(AiRoutingPolicy.EXPLICIT)
        } ?: AiRoutingPolicy.EXPLICIT

        _selectedProviderId.value = restoredProvider
        if (restoredProvider != null) {
            restoreStoredModel(restoredProvider)
        } else {
            _selectedModelId.value = null
            storage.putString("active_ai_model", null)
        }
    }

    override fun start() {
        // Intentionally no background polling. Provider diagnostics are on-demand.
    }

    override fun stop() = Unit

    fun setRoutingPolicy(policy: AiRoutingPolicy) {
        _routingPolicy.value = policy
        storage.putString("ai_routing_policy", policy.name)
    }

    override fun selectProvider(id: String) {
        if (allProviders.any { it.id == id }) {
            val providerChanged = _selectedProviderId.value != id
            _selectedProviderId.value = id
            storage.putString("active_ai_provider", id)
            setRoutingPolicy(AiRoutingPolicy.EXPLICIT)
            if (providerChanged) restoreStoredModel(id)
            updateDiagnosticsSelection()
            scope.launch { refreshDiagnostics() }
        }
    }

    override fun selectModel(modelId: String?) {
        _selectedModelId.value = modelId
        val providerId = _selectedProviderId.value
        storage.putString("active_ai_model", modelId)
        if (providerId != null && modelId != null) persistProviderModel(providerId, modelId)
        updateDiagnosticsSelection()
        scope.launch { refreshDiagnostics() }
    }

    override fun getAllProviders(): List<AiProvider> = allProviders
    override suspend fun diagnostics(): List<AiProviderDiagnostic> = refreshDiagnostics()

    private fun restoreStoredModel(providerId: String) {
        val modelId = when (providerId) {
            "openrouter" -> storage.getString("openrouter_model")
            "provider.ollama" -> storage.getString("ollama_model")
            "provider.llamacpp.native" -> storage.getString("llamacpp_model")
            else -> storage.getString("ai_model_$providerId")
        }?.trim()?.ifBlank { null }
        _selectedModelId.value = modelId
        storage.putString("active_ai_model", modelId)
    }

    private fun persistProviderModel(providerId: String, modelId: String) {
        when (providerId) {
            "openrouter" -> storage.putString("openrouter_model", modelId)
            "provider.ollama" -> storage.putString("ollama_model", modelId)
            "provider.llamacpp.native" -> storage.putString("llamacpp_model", modelId)
            else -> storage.putString("ai_model_$providerId", modelId)
        }
    }

    private fun updateDiagnosticsSelection() {
        val selectedId = _selectedProviderId.value
        _diagnosticsFlow.value = _diagnosticsFlow.value.map { diag ->
            diag.copy(
                selected = diag.providerId == selectedId,
                selectedModelId = if (diag.providerId == selectedId) _selectedModelId.value else null
            )
        }
    }

    private suspend fun refreshDiagnostics(): List<AiProviderDiagnostic> {
        val selectedId = _selectedProviderId.value
        val list = allProviders.map { provider ->
            val status = runCatching { provider.getStatus() }.getOrElse { ProviderStatus(ProviderStatus.State.ERROR, it.message ?: "Status check failed") }
            val capabilities = runCatching { provider.getCapabilities() }.getOrDefault(emptySet())
            AiProviderDiagnostic(
                provider.id, provider.displayName, status.state, status.message, capabilities,
                provider.id == selectedId, if (provider.id == selectedId) _selectedModelId.value else null, provider.isLocal
            )
        }
        _diagnosticsFlow.value = list
        return list
    }

    override suspend fun getEligibleProvider(request: AiRequest): AiProvider = resolveExecutionTarget(request).provider

    override suspend fun resolveExecutionTarget(request: AiRequest): AiExecutionTarget {
        val selectedProvider = allProviders.firstOrNull { it.id == _selectedProviderId.value }
        if (selectedProvider is AiHandoffProvider && _routingPolicy.value == AiRoutingPolicy.EXPLICIT) {
            throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY, "Provider ${selectedProvider.id} is a handoff target, not a normal inference backend")
        }
        val preferredProvider = if (selectedProvider is AiHandoffProvider) null else selectedProvider as? AiInferenceProvider
        return when (_routingPolicy.value) {
            AiRoutingPolicy.EXPLICIT -> resolveExplicitTarget(preferredProvider, selectedProvider, request)
            AiRoutingPolicy.PREFERRED_WITH_FALLBACK -> resolvePreferredWithFallbackTarget(preferredProvider, request)
            AiRoutingPolicy.LOCAL_ONLY -> resolveAutomaticTarget(preferredProvider, request, localOnly = true)
            AiRoutingPolicy.AUTOMATIC -> resolveAutomaticTarget(preferredProvider, request, localOnly = false)
        }
    }

    private suspend fun resolveExplicitTarget(preferredProvider: AiInferenceProvider?, selectedProvider: AiProvider?, request: AiRequest): AiExecutionTarget {
        val provider = preferredProvider ?: throw AiProviderException(
            if (selectedProvider is AiHandoffProvider) AiErrorType.UNSUPPORTED_CAPABILITY else AiErrorType.PROVIDER_UNAVAILABLE,
            if (selectedProvider is AiHandoffProvider) "Provider ${selectedProvider.id} is a handoff target, not a normal inference backend" else "Explicitly selected provider not found: ${_selectedProviderId.value}"
        )
        val status = provider.getStatus()
        if (status.state != ProviderStatus.State.READY) throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, "Explicit provider is not ready: ${provider.id}: ${status.state}")
        return resolveTargetForProvider(provider, request, allowSelectedModelFallback = false)
    }

    private suspend fun resolvePreferredWithFallbackTarget(preferredProvider: AiInferenceProvider?, request: AiRequest): AiExecutionTarget {
        if (preferredProvider != null && preferredProvider.getStatus().state == ProviderStatus.State.READY) {
            try { return resolveTargetForProvider(preferredProvider, request, allowSelectedModelFallback = false) }
            catch (e: AiProviderException) {
                if (e.type == AiErrorType.MODEL_NOT_FOUND || e.type == AiErrorType.UNSUPPORTED_CAPABILITY || e.type == AiErrorType.INVALID_REQUEST || e.type == AiErrorType.CONTEXT_TOO_LARGE) throw e
            }
        }
        return getFallbackExecutionTarget(request, failedProviderId = preferredProvider?.id)
            ?: throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, "No provider/model target could satisfy the request")
    }

    private suspend fun resolveAutomaticTarget(preferredProvider: AiInferenceProvider?, request: AiRequest, localOnly: Boolean): AiExecutionTarget {
        val orderedProviders = buildList {
            preferredProvider?.let(::add)
            inferenceProviders.filter { it.id != preferredProvider?.id }.forEach(::add)
        }
        val failures = mutableListOf<String>()
        for (provider in orderedProviders) {
            if (localOnly && !provider.isLocal) { failures += "${provider.id}: not local"; continue }
            val status = runCatching { provider.getStatus() }.getOrElse { failures += "${provider.id}: status check failed"; continue }
            if (status.state != ProviderStatus.State.READY) { failures += "${provider.id}: state=${status.state}"; continue }
            val result = runCatching { resolveTargetForProvider(provider, request, allowSelectedModelFallback = provider.id != preferredProvider?.id) }
            result.getOrNull()?.let { return it }
            val error = result.exceptionOrNull()
            failures += "${provider.id}: ${error?.message ?: "target unavailable"}"
        }
        throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, buildString {
            append("No provider/model target could satisfy the request.")
            failures.take(6).forEach { append("\n- ").append(it) }
        })
    }

    private suspend fun resolveTargetForProvider(provider: AiInferenceProvider, request: AiRequest, allowSelectedModelFallback: Boolean): AiExecutionTarget {
        val providerCapabilities = provider.getCapabilities()
        request.validateAgainst(providerCapabilities)
        val models = provider.listModels()
        if (models.isEmpty()) throw AiProviderException(AiErrorType.MODEL_UNAVAILABLE, "Provider ${provider.id} did not advertise any models")
        request.modelId?.let { explicitId ->
            val model = models.firstOrNull { it.id == explicitId } ?: throw AiProviderException(AiErrorType.MODEL_NOT_FOUND, "Model $explicitId is not advertised by provider ${provider.id}")
            validateModelInput(model, request)
            return AiExecutionTarget(provider, model, providerCapabilities)
        }
        val selectedModelId = if (allowSelectedModelFallback) null else _selectedModelId.value
        selectedModelId?.let { selectedId ->
            val model = models.firstOrNull { it.id == selectedId } ?: throw AiProviderException(AiErrorType.MODEL_NOT_FOUND, "Selected model $selectedId is not advertised by provider ${provider.id}")
            validateModelInput(model, request)
            return AiExecutionTarget(provider, model, providerCapabilities)
        }
        val compatible = models.firstOrNull { supportsModelInput(it, request) }
            ?: throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY, "Provider ${provider.id} has no advertised model compatible with this request")
        return AiExecutionTarget(provider, compatible, providerCapabilities)
    }

    private suspend fun getFallbackExecutionTarget(request: AiRequest, failedProviderId: String?): AiExecutionTarget? {
        val localOnly = _routingPolicy.value == AiRoutingPolicy.LOCAL_ONLY
        for (provider in inferenceProviders.filter { it.id != failedProviderId }) {
            if (localOnly && !provider.isLocal) continue
            if (runCatching { provider.getStatus() }.getOrNull()?.state != ProviderStatus.State.READY) continue
            val target = runCatching { resolveTargetForProvider(provider, request, allowSelectedModelFallback = true) }.getOrNull()
            if (target != null) return target
        }
        return null
    }

    private fun validateModelInput(model: AiModel, request: AiRequest) {
        if (!supportsModelInput(model, request)) throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY, "Model ${model.id} does not support this request")
    }

    private fun supportsModelInput(model: AiModel, request: AiRequest): Boolean {
        val capabilities = model.inputCapabilities
        return (request.text.isNullOrBlank() || AiCapability.TEXT in capabilities) &&
            (request.image == null || AiCapability.IMAGE in capabilities) &&
            (request.audio == null || AiCapability.AUDIO in capabilities) &&
            (request.video == null || AiCapability.VIDEO in capabilities)
    }

    private fun fallbackEligible(error: Throwable): Boolean = error is AiProviderException && error.type in setOf(
        AiErrorType.PROVIDER_UNAVAILABLE, AiErrorType.MODEL_UNAVAILABLE, AiErrorType.MODEL_LOAD_FAILED,
        AiErrorType.MODEL_NOT_READY, AiErrorType.GENERATION_FAILED, AiErrorType.EMPTY_RESPONSE,
        AiErrorType.TIMEOUT, AiErrorType.NETWORK_UNAVAILABLE, AiErrorType.NETWORK_ERROR,
        AiErrorType.LOCAL_MODEL_UNAVAILABLE, AiErrorType.UNKNOWN_ERROR
    )

    override suspend fun execute(request: AiRequest, context: AiContext): AiResponse {
        val policy = _routingPolicy.value
        val target = resolveExecutionTarget(request)
        val finalRequest = request.copy(modelId = target.modelId)
        val requestId = UUID.randomUUID().toString()
        logger?.info("AiProviderRouter", "AI execute start provider=${target.providerId} model=${target.modelId}")
        eventBus?.publish(ProviderRequest(requestId = requestId, providerId = target.providerId))
        return try {
            finalRequest.validateAgainst(target.providerCapabilities)
            validateModelInput(target.model, finalRequest)
            val response = target.provider.generate(finalRequest, context)
            validateResponse(target, response)
            eventBus?.publish(ProviderResult(requestId = requestId, success = true))
            response
        } catch (e: CancellationException) { throw e }
        catch (e: Throwable) {
            eventBus?.publish(ProviderFailure(requestId = requestId, providerId = target.providerId, reason = e.message ?: "Execution failed"))
            if ((policy == AiRoutingPolicy.PREFERRED_WITH_FALLBACK || policy == AiRoutingPolicy.AUTOMATIC) && fallbackEligible(e)) {
                val fallbackTarget = getFallbackExecutionTarget(request, failedProviderId = target.providerId)
                if (fallbackTarget != null) {
                    val fallbackRequest = request.copy(modelId = fallbackTarget.modelId)
                    val fallbackRequestId = UUID.randomUUID().toString()
                    eventBus?.publish(ProviderRequest(requestId = fallbackRequestId, providerId = fallbackTarget.providerId))
                    try {
                        fallbackRequest.validateAgainst(fallbackTarget.providerCapabilities)
                        validateModelInput(fallbackTarget.model, fallbackRequest)
                        val fallbackResponse = fallbackTarget.provider.generate(fallbackRequest, context)
                        validateResponse(fallbackTarget, fallbackResponse)
                        eventBus?.publish(ProviderResult(requestId = fallbackRequestId, success = true))
                        logger?.info("AiProviderRouter", "AI fallback success provider=${fallbackTarget.providerId} model=${fallbackTarget.modelId}")
                        return fallbackResponse
                    } catch (fallbackError: CancellationException) { throw fallbackError }
                    catch (fallbackError: Throwable) {
                        eventBus?.publish(ProviderFailure(requestId = fallbackRequestId, providerId = fallbackTarget.providerId, reason = fallbackError.message ?: "Fallback execution failed"))
                    }
                }
            }
            throw e
        }
    }

    private fun validateResponse(target: AiExecutionTarget, response: AiResponse) {
        AiResponseValidator.validate(target.provider, target.providerCapabilities, target.modelId, response)
    }

    private fun loadStoredModels(): Map<String, String> = inferenceProviders.mapNotNull { provider ->
        val value = when (provider.id) {
            "openrouter" -> storage.getString("openrouter_model")
            "provider.ollama" -> storage.getString("ollama_model")
            "provider.llamacpp.native" -> storage.getString("llamacpp_model")
            else -> storage.getString("ai_model_${provider.id}")
        } ?: return@mapNotNull null
        provider.id to value
    }.toMap()
}
