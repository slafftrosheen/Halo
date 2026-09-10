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

/**
 * Routes AI requests to the appropriate provider based on capabilities and configuration.
 */
class DefaultAiProviderRouter(
    private val providers: List<AiProvider>,
    private val storage: Storage,
    private val eventBus: EventBus? = null,
    private val logger: RuntimeLogger? = null
) : AiProviderRouter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var pollingJob: Job? = null

    override fun start() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                providers.forEach { provider ->
                    runCatching { provider.getStatus() }
                }
                delay(30.seconds)
            }
        }
    }

    override fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private val _selectedProviderId = MutableStateFlow<String?>(
        storage.getString("active_ai_provider") ?: providers.firstOrNull()?.id
    )
    override val selectedProviderId: StateFlow<String?> = _selectedProviderId.asStateFlow()

    private val _selectedModelId = MutableStateFlow<String?>(
        storage.getString("active_ai_model")
    )
    override val selectedModelId: StateFlow<String?> = _selectedModelId.asStateFlow()

    private val _routingPolicy = MutableStateFlow<AiRoutingPolicy>(
        storage.getString("ai_routing_policy")?.let { 
            runCatching { enumValueOf<AiRoutingPolicy>(it) }.getOrDefault(AiRoutingPolicy.AUTOMATIC)
        } ?: AiRoutingPolicy.AUTOMATIC
    )
    
    fun setRoutingPolicy(policy: AiRoutingPolicy) {
        _routingPolicy.value = policy
        storage.putString("ai_routing_policy", policy.name)
    }

    override fun selectProvider(id: String) {
        if (providers.any { it.id == id }) {
            val providerChanged = _selectedProviderId.value != id
            _selectedProviderId.value = id
            storage.putString("active_ai_provider", id)
            if (providerChanged) {
                // A model ID is provider-scoped. Never carry an unverified
                // selection across providers; the caller must select one of
                // the new provider's advertised models.
                _selectedModelId.value = null
                storage.putString("active_ai_model", null)
            }
            scope.launch {
                runCatching { providers.find { it.id == id }?.getStatus() }
            }
        }
    }

    override fun selectModel(modelId: String?) {
        _selectedModelId.value = modelId
        storage.putString("active_ai_model", modelId)
    }

    override fun getAllProviders(): List<AiProvider> = providers

    override suspend fun diagnostics(): List<AiProviderDiagnostic> {
        val selectedId = _selectedProviderId.value
        return providers.map { provider ->
            val status = provider.getStatus()
            AiProviderDiagnostic(
                providerId = provider.id,
                displayName = provider.displayName,
                state = status.state,
                message = status.message,
                capabilities = provider.getCapabilities(),
                selected = provider.id == selectedId,
                selectedModelId = if (provider.id == selectedId) _selectedModelId.value else null,
                local = provider.isLocal
            )
        }
    }

    override suspend fun getEligibleProvider(request: AiRequest): AiProvider {
        val policy = _routingPolicy.value
        val selectedId = _selectedProviderId.value
        val preferredProvider = providers.find { it.id == selectedId }

        if (policy == AiRoutingPolicy.EXPLICIT) {
            val provider = providers.find { it.id == selectedId }
                ?: throw AiProviderException(
                    AiErrorType.PROVIDER_UNAVAILABLE,
                    "Explicitly selected provider not found: $selectedId"
                )

            val status = provider.getStatus()
            if (status.state != ProviderStatus.State.READY) {
                throw AiProviderException(
                    AiErrorType.PROVIDER_UNAVAILABLE,
                    "Explicitly selected provider is not ready: ${provider.id}, state=${status.state}, message=${status.message}"
                )
            }

            if (!supports(provider, request)) {
                throw AiProviderException(
                    AiErrorType.UNSUPPORTED_CAPABILITY,
                    "Explicitly selected provider does not support this request"
                )
            }

            return provider
        }
        
        val (provider, rejections) = selectProviderWithDetails(policy, preferredProvider, request)
        if (provider != null) return provider

        val details = rejections.joinToString("\n") { "- ${it.providerId}: ${it.reason}" }
        logger?.error("AiProviderRouter", "No eligible provider found. Details:\n$details")
        
        val reason = when (policy) {
            AiRoutingPolicy.LOCAL_ONLY -> "No local AI providers available.\nDetails:\n$details"
            else -> "No available AI provider found for requested capabilities.\nDetails:\n$details"
        }
        throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, reason)
    }

    override suspend fun execute(request: AiRequest, context: AiContext): AiResponse {
        val policy = _routingPolicy.value
        val selectedId = _selectedProviderId.value
        val preferredProvider = providers.find { it.id == selectedId }

        logger?.info("AiProviderRouter", "Executing request with policy=$policy, selectedId=$selectedId")

        if (policy == AiRoutingPolicy.EXPLICIT) {
            val provider = getEligibleProvider(request)
            val target = resolveExecutionTarget(provider, request)
            val finalRequest = request.copy(modelId = target.modelId)
            val requestId = UUID.randomUUID().toString()

            logger?.info("AiProviderRouter", "AI execute start provider=${provider.id} model=${finalRequest.modelId}")
            eventBus?.publish(ProviderRequest(requestId = requestId, providerId = provider.id))
            return try {
                validateRequestAgainstTarget(finalRequest, target)
                val response = provider.generate(finalRequest, context)
                validateResponse(target, response)
                logger?.info("AiProviderRouter", "AI execute complete provider=${response.providerId} model=${response.modelId} chars=${response.text.length}")
                eventBus?.publish(ProviderResult(requestId = requestId, success = true))
                response
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger?.error("AiProviderRouter", "AI execute failed provider=${provider.id} message=${e.message}")
                eventBus?.publish(ProviderFailure(requestId = requestId, providerId = provider.id, reason = e.message ?: "Execution failed"))
                throw e
            }
        }

        val (provider, rejections) = selectProviderWithDetails(policy, preferredProvider, request)
        
        if (provider == null) {
            val details = rejections.joinToString("\n") { "- ${it.providerId}: ${it.reason}" }
            logger?.error("AiProviderRouter", "No suitable provider found. Details:\n$details")
            
            val reason = when (policy) {
                AiRoutingPolicy.LOCAL_ONLY -> "No local AI providers available.\nDetails:\n$details"
                else -> "No available AI provider found for requested capabilities.\nDetails:\n$details"
            }
            throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, reason)
        }

        val target = resolveExecutionTarget(provider, request)
        val finalRequest = request.copy(modelId = target.modelId)
        val requestId = UUID.randomUUID().toString()

        logger?.info("AiProviderRouter", "AI execute start provider=${provider.id} model=${finalRequest.modelId}")
        eventBus?.publish(ProviderRequest(requestId = requestId, providerId = provider.id))
        return try {
            validateRequestAgainstTarget(finalRequest, target)
            val response = provider.generate(finalRequest, context)
            validateResponse(target, response)
            logger?.info("AiProviderRouter", "AI execute complete provider=${response.providerId} model=${response.modelId} chars=${response.text.length}")
            eventBus?.publish(ProviderResult(requestId = requestId, success = true))
            response
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiProviderException) {
            eventBus?.publish(ProviderFailure(requestId = requestId, providerId = provider.id, reason = e.message ?: "Provider failure"))
            if (policy == AiRoutingPolicy.PREFERRED_WITH_FALLBACK || policy == AiRoutingPolicy.AUTOMATIC) {
                val fallbackProvider = getFallbackProvider(provider, request, policy)
                if (fallbackProvider != null) {
                    val fallbackTarget = resolveExecutionTarget(
                        provider = fallbackProvider,
                        request = request,
                        preferSelectedModel = false
                    )
                    val fallbackRequest = request.copy(modelId = fallbackTarget.modelId)
                    val fallbackRequestId = UUID.randomUUID().toString()
                    eventBus?.publish(ProviderRequest(requestId = fallbackRequestId, providerId = fallbackProvider.id))
                    try {
                        validateRequestAgainstTarget(fallbackRequest, fallbackTarget)
                        val fallbackResp = fallbackProvider.generate(fallbackRequest, context)
                        validateResponse(fallbackTarget, fallbackResp)
                        eventBus?.publish(ProviderResult(requestId = fallbackRequestId, success = true))
                        return fallbackResp
                    } catch (fbErr: CancellationException) {
                        throw fbErr
                    } catch (fbErr: Throwable) {
                        eventBus?.publish(ProviderFailure(requestId = fallbackRequestId, providerId = fallbackProvider.id, reason = fbErr.message ?: "Fallback failure"))
                        throw fbErr
                    }
                }
            }
            throw e
        }
    }

    private fun validateResponse(
        target: ProviderExecutionTarget,
        response: AiResponse
    ) {
        AiResponseValidator.validate(
            provider = target.provider,
            capabilities = target.capabilities,
            expectedModelId = target.modelId,
            response = response
        )
    }

    private data class ProviderExecutionTarget(
        val provider: AiProvider,
        val modelId: String,
        val model: AiModel,
        val capabilities: Set<AiCapability>
    )

    private suspend fun resolveExecutionTarget(
        provider: AiProvider,
        request: AiRequest,
        preferSelectedModel: Boolean = true
    ): ProviderExecutionTarget {
        val capabilities = provider.getCapabilities()
        val models = provider.listModels()
        val requestedModelId = request.modelId ?: _selectedModelId.value

        val model = when {
            requestedModelId == null -> throw AiProviderException(
                AiErrorType.MODEL_NOT_SELECTED,
                "No AI model selected for provider ${provider.id}"
            )
            preferSelectedModel -> models.firstOrNull { it.id == requestedModelId }
                ?: throw AiProviderException(
                    AiErrorType.MODEL_NOT_FOUND,
                    "Model $requestedModelId is not available from provider ${provider.id}"
                )
            else -> models.firstOrNull { it.id == requestedModelId && supportsModelInput(it, request) }
                ?: models.firstOrNull { supportsModelInput(it, request) }
                ?: throw AiProviderException(
                    AiErrorType.MODEL_NOT_FOUND,
                    "Provider ${provider.id} has no model compatible with this request"
                )
        }

        return ProviderExecutionTarget(provider, model.id, model, capabilities)
    }

    private fun validateRequestAgainstTarget(
        request: AiRequest,
        target: ProviderExecutionTarget
    ) {
        request.validateAgainst(target.capabilities)
        if (!supportsModelInput(target.model, request)) {
            throw AiProviderException(
                AiErrorType.UNSUPPORTED_CAPABILITY,
                "Model ${target.modelId} does not support this request"
            )
        }
    }

    private fun supportsModelInput(model: AiModel, request: AiRequest): Boolean {
        val capabilities = model.inputCapabilities
        return (request.text.isNullOrBlank() || capabilities.contains(AiCapability.TEXT)) &&
            (request.image == null || capabilities.contains(AiCapability.IMAGE)) &&
            (request.audio == null || capabilities.contains(AiCapability.AUDIO)) &&
            (request.video == null || capabilities.contains(AiCapability.VIDEO))
    }

    private data class ProviderRejection(val providerId: String, val reason: String)

    private suspend fun selectProviderWithDetails(
        policy: AiRoutingPolicy,
        preferred: AiProvider?,
        request: AiRequest
    ): Pair<AiProvider?, List<ProviderRejection>> {
        val readyProviders = mutableListOf<AiProvider>()
        val rejections = mutableListOf<ProviderRejection>()

        for (provider in providers) {
            val status = provider.getStatus()
            val state = status.state
            
            logger?.debug("AiProviderRouter", "Checking provider=${provider.id} state=$state")

            if (state != ProviderStatus.State.READY) {
                rejections.add(
                    ProviderRejection(
                        provider.id,
                        "Provider is not executable: state=$state (${status.message ?: "No message"})"
                    )
                )
                continue
            }

            val caps = provider.getCapabilities()
            val unsupported = mutableListOf<String>()
            if (request.text != null && !caps.contains(AiCapability.TEXT)) unsupported.add("TEXT")
            if (request.image != null && !caps.contains(AiCapability.IMAGE)) unsupported.add("IMAGE")
            if (request.audio != null && !caps.contains(AiCapability.AUDIO)) unsupported.add("AUDIO")
            if (request.video != null && !caps.contains(AiCapability.VIDEO)) unsupported.add("VIDEO")
            
            if (unsupported.isNotEmpty()) {
                rejections.add(ProviderRejection(provider.id, "Missing capabilities: ${unsupported.joinToString()}"))
                continue
            }

            if (policy == AiRoutingPolicy.LOCAL_ONLY && !provider.isLocal) {
                rejections.add(ProviderRejection(provider.id, "Not a local provider (policy is LOCAL_ONLY)"))
                continue
            }

            readyProviders.add(provider)
        }
        
        val result = when (policy) {
            AiRoutingPolicy.EXPLICIT -> {
                if (preferred != null && readyProviders.contains(preferred)) preferred else null
            }
            AiRoutingPolicy.LOCAL_ONLY, AiRoutingPolicy.AUTOMATIC, AiRoutingPolicy.PREFERRED_WITH_FALLBACK -> {
                if (preferred != null && readyProviders.contains(preferred)) {
                    preferred
                } else {
                    readyProviders.firstOrNull()
                }
            }
        }
        
        logger?.info("AiProviderRouter", "Selected provider=${result?.id} for policy=$policy")
        return Pair(result, rejections)
    }

    private suspend fun getFallbackProvider(
        failedProvider: AiProvider,
        request: AiRequest,
        policy: AiRoutingPolicy
    ): AiProvider? {
        val readyProviders = mutableListOf<AiProvider>()

        for (provider in providers) {
            if (provider.id == failedProvider.id) continue

            val state = provider.getStatus().state

            if (state == ProviderStatus.State.READY && supports(provider, request)) {
                readyProviders.add(provider)
            }
        }

        return if (policy == AiRoutingPolicy.LOCAL_ONLY) {
            readyProviders.firstOrNull { it.isLocal }
        } else {
            readyProviders.firstOrNull()
        }
    }

    private suspend fun supports(provider: AiProvider, request: AiRequest): Boolean {
        val caps = provider.getCapabilities()
        if (request.text != null && !caps.contains(AiCapability.TEXT)) return false
        if (request.image != null && !caps.contains(AiCapability.IMAGE)) return false
        if (request.audio != null && !caps.contains(AiCapability.AUDIO)) return false
        if (request.video != null && !caps.contains(AiCapability.VIDEO)) return false
        return true
    }
}
