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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/** Routes AI requests while preserving explicit intent and provider boundaries. */
class DefaultAiProviderRouter(
    private val providers: List<AiProvider>,
    private val storage: Storage,
    private val eventBus: EventBus? = null,
    private val logger: RuntimeLogger? = null
) : AiProviderRouter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null

    private val _selectedProviderId = MutableStateFlow<String?>(
        storage.getString("active_ai_provider")?.takeIf { id -> providers.any { it.id == id } }
            ?: providers.firstOrNull()?.id
    )
    override val selectedProviderId: StateFlow<String?> = _selectedProviderId.asStateFlow()

    private val selectedModelsByProvider = MutableStateFlow(loadStoredModels())
    private val _selectedModelId = MutableStateFlow(
        _selectedProviderId.value?.let { selectedModelsByProvider.value[it] }
    )
    override val selectedModelId: StateFlow<String?> = _selectedModelId.asStateFlow()

    private val _diagnosticsFlow = MutableStateFlow<List<AiProviderDiagnostic>>(emptyList())
    override val diagnosticsFlow: StateFlow<List<AiProviderDiagnostic>> = _diagnosticsFlow.asStateFlow()

    private val _routingPolicy = MutableStateFlow(
        storage.getString("ai_routing_policy")?.let {
            runCatching { enumValueOf<AiRoutingPolicy>(it) }.getOrDefault(AiRoutingPolicy.AUTOMATIC)
        } ?: AiRoutingPolicy.AUTOMATIC
    )

    init {
        scope.launch { refreshDiagnostics() }
    }

    override fun start() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                refreshDiagnostics()
                delay(30.seconds)
            }
        }
    }

    override fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun setRoutingPolicy(policy: AiRoutingPolicy) {
        _routingPolicy.value = policy
        storage.putString("ai_routing_policy", policy.name)
    }

    override fun selectProvider(id: String) {
        if (providers.none { it.id == id }) return
        _selectedProviderId.value = id
        storage.putString("active_ai_provider", id)
        _selectedModelId.value = selectedModelsByProvider.value[id]
        storage.putString("active_ai_model", _selectedModelId.value)
        scope.launch { refreshDiagnostics() }
    }

    override fun selectModel(modelId: String?) {
        val providerId = _selectedProviderId.value ?: return
        val normalized = modelId?.trim()?.ifBlank { null }
        _selectedModelId.value = normalized
        selectedModelsByProvider.value = selectedModelsByProvider.value.toMutableMap().apply {
            if (normalized == null) remove(providerId) else put(providerId, normalized)
        }
        persistStoredModels(selectedModelsByProvider.value)
        storage.putString("active_ai_model", normalized)
        scope.launch { refreshDiagnostics() }
    }

    override fun getAllProviders(): List<AiProvider> = providers

    override suspend fun refreshDiagnostics(): List<AiProviderDiagnostic> {
        val selectedId = _selectedProviderId.value
        val list = providers.map { provider ->
            val status = runCatching { provider.getStatus() }.getOrElse {
                ProviderStatus(ProviderStatus.State.ERROR, it.message ?: "Status check failed")
            }
            val capabilities = runCatching { provider.getCapabilities() }.getOrDefault(emptySet())
            AiProviderDiagnostic(
                providerId = provider.id,
                displayName = provider.displayName,
                state = status.state,
                message = status.message,
                capabilities = capabilities,
                selected = provider.id == selectedId,
                selectedModelId = if (provider.id == selectedId) _selectedModelId.value else null,
                local = provider.isLocal
            )
        }
        _diagnosticsFlow.value = list
        return list
    }

    override suspend fun diagnostics(): List<AiProviderDiagnostic> = refreshDiagnostics()

    override suspend fun getEligibleProvider(request: AiRequest): AiProvider = resolveExecutionTarget(request).provider

    override suspend fun resolveExecutionTarget(request: AiRequest): AiExecutionTarget {
        val preferredProvider = providers.firstOrNull { it.id == _selectedProviderId.value }
        return when (_routingPolicy.value) {
            AiRoutingPolicy.EXPLICIT -> resolveExplicitTarget(preferredProvider, request)
            AiRoutingPolicy.PREFERRED_WITH_FALLBACK -> resolvePreferredWithFallbackTarget(preferredProvider, request)
            AiRoutingPolicy.LOCAL_ONLY -> resolveAutomaticTarget(preferredProvider, request, localOnly = true)
            AiRoutingPolicy.AUTOMATIC -> resolveAutomaticTarget(preferredProvider, request, localOnly = false)
        }
    }

    private fun isHandoffOnly(provider: AiProvider): Boolean = provider is AiHandoffProvider

    private suspend fun resolveExplicitTarget(preferredProvider: AiProvider?, request: AiRequest): AiExecutionTarget {
        val provider = preferredProvider ?: throw AiProviderException(
            AiErrorType.PROVIDER_UNAVAILABLE,
            "Explicitly selected provider not found: ${_selectedProviderId.value}"
        )
        if (isHandoffOnly(provider)) {
            throw AiProviderException(
                AiErrorType.UNSUPPORTED_CAPABILITY,
                "Provider ${provider.id} is a handoff target, not a normal inference backend"
            )
        }
        val status = provider.getStatus()
        if (status.state != ProviderStatus.State.READY) {
            throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, "Explicit provider is not ready: ${provider.id}: ${status.state}")
        }
        return resolveTargetForProvider(provider, request, allowSelectedModelFallback = false)
    }

    private suspend fun resolvePreferredWithFallbackTarget(preferredProvider: AiProvider?, request: AiRequest): AiExecutionTarget {
        if (preferredProvider != null && !isHandoffOnly(preferredProvider) && preferredProvider.getStatus().state == ProviderStatus.State.READY) {
            try {
                return resolveTargetForProvider(preferredProvider, request, allowSelectedModelFallback = false)
            } catch (e: AiProviderException) {
                if (e.type == AiErrorType.MODEL_NOT_FOUND || e.type == AiErrorType.MODEL_UNAVAILABLE ||
                    e.type == AiErrorType.UNSUPPORTED_CAPABILITY || e.type == AiErrorType.INVALID_REQUEST ||
                    e.type == AiErrorType.CONTEXT_TOO_LARGE) throw e
            }
        }
        return getFallbackExecutionTarget(request, failedProviderId = preferredProvider?.id)
            ?: throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, "No provider/model target could satisfy the request")
    }

    private suspend fun resolveAutomaticTarget(preferredProvider: AiProvider?, request: AiRequest, localOnly: Boolean): AiExecutionTarget {
        val orderedProviders = buildList {
            preferredProvider?.let(::add)
            providers.filter { it.id != preferredProvider?.id }.forEach(::add)
        }
        val failures = mutableListOf<String>()
        for (provider in orderedProviders) {
            if (isHandoffOnly(provider)) {
                failures += "${provider.id}: external handoff target"
                continue
            }
            if (localOnly && !provider.isLocal) {
                failures += "${provider.id}: not local"
                continue
            }
            val status = runCatching { provider.getStatus() }.getOrElse {
                failures += "${provider.id}: status check failed"
                continue
            }
            if (status.state != ProviderStatus.State.READY) {
                failures += "${provider.id}: state=${status.state}"
                continue
            }
            val result = runCatching {
                resolveTargetForProvider(provider, request, allowSelectedModelFallback = provider.id != preferredProvider?.id)
            }
            result.getOrNull()?.let { return it }
            val error = result.exceptionOrNull()
            if (provider.id == preferredProvider?.id && error is AiProviderException) {
                if (error.type == AiErrorType.MODEL_NOT_FOUND || error.type == AiErrorType.MODEL_UNAVAILABLE ||
                    error.type == AiErrorType.UNSUPPORTED_CAPABILITY || error.type == AiErrorType.INVALID_REQUEST ||
                    error.type == AiErrorType.CONTEXT_TOO_LARGE) throw error
            }
            failures += "${provider.id}: ${error?.message ?: "target unavailable"}"
        }
        throw AiProviderException(
            AiErrorType.PROVIDER_UNAVAILABLE,
            buildString {
                append("No provider/model target could satisfy the request.")
                failures.take(6).forEach { append("\n- ").append(it) }
            }
        )
    }

    private suspend fun resolveTargetForProvider(provider: AiProvider, request: AiRequest, allowSelectedModelFallback: Boolean): AiExecutionTarget {
        if (isHandoffOnly(provider)) {
            throw AiProviderException(
                AiErrorType.UNSUPPORTED_CAPABILITY,
                "Provider ${provider.id} is reserved for external assistant handoff"
            )
        }
        val providerCapabilities = provider.getCapabilities()
        request.validateAgainst(providerCapabilities)
        val models = provider.listModels()
        if (models.isEmpty()) throw AiProviderException(AiErrorType.MODEL_UNAVAILABLE, "Provider ${provider.id} did not advertise any models")

        request.modelId?.let { explicitId ->
            val model = models.firstOrNull { it.id == explicitId }
                ?: throw AiProviderException(AiErrorType.MODEL_NOT_FOUND, "Model $explicitId is not advertised by provider ${provider.id}")
            validateModelInput(model, request)
            return AiExecutionTarget(provider, model, providerCapabilities)
        }

        val selectedModelId = if (allowSelectedModelFallback) null else _selectedModelId.value
        selectedModelId?.let { selectedId ->
            val model = models.firstOrNull { it.id == selectedId }
                ?: throw AiProviderException(AiErrorType.MODEL_NOT_FOUND, "Selected model $selectedId is not advertised by provider ${provider.id}")
            validateModelInput(model, request)
            return AiExecutionTarget(provider, model, providerCapabilities)
        }

        val compatible = models.firstOrNull { supportsModelInput(it, request) }
            ?: throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY, "Provider ${provider.id} has no advertised model compatible with this request")
        return AiExecutionTarget(provider, compatible, providerCapabilities)
    }

    private suspend fun getFallbackExecutionTarget(request: AiRequest, failedProviderId: String?): AiExecutionTarget? {
        val localOnly = _routingPolicy.value == AiRoutingPolicy.LOCAL_ONLY
        for (provider in providers.filter { it.id != failedProviderId && !isHandoffOnly(it) }) {
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

    private fun fallbackEligible(error: Throwable): Boolean =
        error is AiProviderException && error.type in setOf(
            AiErrorType.PROVIDER_UNAVAILABLE,
            AiErrorType.MODEL_UNAVAILABLE,
            AiErrorType.MODEL_NOT_READY,
            AiErrorType.MODEL_LOAD_FAILED,
            AiErrorType.TIMEOUT,
            AiErrorType.NETWORK_UNAVAILABLE,
            AiErrorType.NETWORK_ERROR,
            AiErrorType.RATE_LIMITED,
            AiErrorType.UNKNOWN_ERROR
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
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
                    } catch (fallbackError: CancellationException) {
                        throw fallbackError
                    } catch (fallbackError: Throwable) {
                        eventBus?.publish(ProviderFailure(requestId = fallbackRequestId, providerId = fallbackTarget.providerId, reason = fallbackError.message ?: "Fallback execution failed"))
                    }
                }
            }
            throw e
        }
    }

    private fun validateResponse(target: AiExecutionTarget, response: AiResponse) {
        AiResponseValidator.validate(target.model, response)
    }

    private fun loadStoredModels(): Map<String, String> {
        return providers.mapNotNull { provider ->
            val value = when (provider.id) {
                "openrouter" -> storage.getString("openrouter_model")
                "provider.ollama" -> storage.getString("ollama_model")
                "provider.llamacpp.native" -> storage.getString("llamacpp_model")
                "android.system.assistant" -> null
                else -> storage.getString("ai_model_${provider.id}")
            }?.trim()?.ifBlank { null }
            value?.let { provider.id to it }
        }.toMap()
    }

    private fun persistStoredModels(models: Map<String, String>) {
        models.forEach { (providerId, modelId) ->
            when (providerId) {
                "openrouter" -> storage.putString("openrouter_model", modelId)
                "provider.ollama" -> storage.putString("ollama_model", modelId)
                "provider.llamacpp.native" -> storage.putString("llamacpp_model", modelId)
                "android.system.assistant" -> Unit
                else -> storage.putString("ai_model_$providerId", modelId)
            }
        }
    }
}
