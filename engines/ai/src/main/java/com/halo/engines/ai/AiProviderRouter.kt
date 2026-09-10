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
        val target = selectExecutionTarget(request)
        return target.provider
    }

    override suspend fun execute(request: AiRequest, context: AiContext): AiResponse {
        val policy = _routingPolicy.value

        logger?.info("AiProviderRouter", "Executing request with policy=$policy, selectedId=${_selectedProviderId.value}")

        val target = try {
            selectExecutionTarget(request)
        } catch (e: AiProviderException) {
            if (policy == AiRoutingPolicy.PREFERRED_WITH_FALLBACK || policy == AiRoutingPolicy.AUTOMATIC) {
                val fallbackTarget = getFallbackExecutionTarget(request, failedProviderId = _selectedProviderId.value)
                fallbackTarget ?: throw e
            } else {
                throw e
            }
        }

        val provider = target.provider
        val finalRequest = request.copy(modelId = target.modelId)
        val requestId = UUID.randomUUID().toString()

        logger?.info("AiProviderRouter", "AI execute start provider=${provider.id} model=${finalRequest.modelId}")
        eventBus?.publish(ProviderRequest(requestId = requestId, providerId = provider.id))
        return try {
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
                val fallbackTarget = getFallbackExecutionTarget(request, failedProviderId = provider.id)
                if (fallbackTarget != null) {
                    val fallbackRequest = request.copy(modelId = fallbackTarget.modelId)
                    val fallbackRequestId = UUID.randomUUID().toString()
                    eventBus?.publish(ProviderRequest(requestId = fallbackRequestId, providerId = fallbackTarget.provider.id))
                    try {
                        val fallbackResp = fallbackTarget.provider.generate(fallbackRequest, context)
                        validateResponse(fallbackTarget, fallbackResp)
                        eventBus?.publish(ProviderResult(requestId = fallbackRequestId, success = true))
                        return fallbackResp
                    } catch (fbErr: CancellationException) {
                        throw fbErr
                    } catch (fbErr: Throwable) {
                        eventBus?.publish(ProviderFailure(requestId = fallbackRequestId, providerId = fallbackTarget.provider.id, reason = fbErr.message ?: "Fallback failure"))
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

    private suspend fun resolveCandidateTarget(
        provider: AiProvider,
        request: AiRequest,
        preferSelectedModel: Boolean
    ): ProviderExecutionTarget? {
        val capabilities = provider.getCapabilities()

        if (!supportsProviderInput(capabilities, request)) {
            return null
        }

        val models = runCatching { provider.listModels() }.getOrDefault(emptyList())

        if (models.isEmpty()) {
            return null
        }

        val requestedModelId =
            request.modelId ?: if (preferSelectedModel) {
                _selectedModelId.value
            } else {
                null
            }

        val model = if (requestedModelId != null) {
            val matched = models.firstOrNull { it.id == requestedModelId }
            if (matched == null) {
                return null
            }
            if (supportsModelInput(matched, request) && supportsEffectiveCapabilities(capabilities, matched, request)) {
                matched
            } else {
                null
            }
        } else {
            models.firstOrNull {
                supportsModelInput(it, request) &&
                    supportsEffectiveCapabilities(capabilities, it, request)
            }
        }

        return model?.let {
            ProviderExecutionTarget(
                provider = provider,
                modelId = it.id,
                model = it,
                capabilities = capabilities
            )
        }
    }

    private suspend fun selectExecutionTarget(request: AiRequest): ProviderExecutionTarget {
        val policy = _routingPolicy.value
        val selectedId = _selectedProviderId.value
        val preferredProvider = providers.find { it.id == selectedId }

        val requestedModelId = request.modelId ?: _selectedModelId.value
        if (preferredProvider != null && preferredProvider.getStatus().state == ProviderStatus.State.READY) {
            val models = runCatching { preferredProvider.listModels() }.getOrDefault(emptyList())
            if (requestedModelId != null && models.isNotEmpty() && models.none { it.id == requestedModelId }) {
                throw AiProviderException(
                    AiErrorType.MODEL_NOT_FOUND,
                    "Model $requestedModelId is not advertised by provider ${preferredProvider.id}"
                )
            }
        }

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

            val target = resolveCandidateTarget(provider, request, preferSelectedModel = true)
                ?: if (request.modelId != null) {
                    throw AiProviderException(
                        AiErrorType.MODEL_NOT_FOUND,
                        "Model ${request.modelId} is not available from provider ${provider.id}"
                    )
                } else {
                    throw AiProviderException(
                        AiErrorType.PROVIDER_UNAVAILABLE,
                        "Explicitly selected provider does not support this request"
                    )
                }

            return target
        }

        val (target, rejections) = selectExecutionTargetWithDetails(policy, preferredProvider, request)
        if (target != null) return target

        val details = rejections.joinToString("\n") { "- ${it.providerId}: ${it.reason}" }
        logger?.error("AiProviderRouter", "No eligible execution target found. Details:\n$details")

        val reason = when (policy) {
            AiRoutingPolicy.LOCAL_ONLY -> "No local AI providers available.\nDetails:\n$details"
            else -> "No available AI provider/model target found for requested capabilities.\nDetails:\n$details"
        }

        throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, reason)
    }

    private data class ProviderRejection(val providerId: String, val reason: String)

    private suspend fun selectExecutionTargetWithDetails(
        policy: AiRoutingPolicy,
        preferred: AiProvider?,
        request: AiRequest
    ): Pair<ProviderExecutionTarget?, List<ProviderRejection>> {
        val readyTargets = mutableListOf<ProviderExecutionTarget>()
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

            if (policy == AiRoutingPolicy.LOCAL_ONLY && !provider.isLocal) {
                rejections.add(ProviderRejection(provider.id, "Not a local provider (policy is LOCAL_ONLY)"))
                continue
            }

            val target = resolveCandidateTarget(
                provider = provider,
                request = request,
                preferSelectedModel = provider == preferred
            )

            if (target == null) {
                rejections.add(ProviderRejection(provider.id, "No compatible model/capabilities available"))
                continue
            }

            readyTargets.add(target)
        }

        val preferredTarget = if (preferred != null) readyTargets.find { it.provider.id == preferred.id } else null

        val result = when (policy) {
            AiRoutingPolicy.EXPLICIT -> preferredTarget
            AiRoutingPolicy.LOCAL_ONLY, AiRoutingPolicy.AUTOMATIC, AiRoutingPolicy.PREFERRED_WITH_FALLBACK -> {
                preferredTarget ?: readyTargets.firstOrNull()
            }
        }

        logger?.info("AiProviderRouter", "Selected execution target provider=${result?.provider?.id} model=${result?.modelId} for policy=$policy")
        return Pair(result, rejections)
    }

    private suspend fun getFallbackExecutionTarget(
        request: AiRequest,
        failedProviderId: String?
    ): ProviderExecutionTarget? {
        val policy = _routingPolicy.value
        val readyTargets = mutableListOf<ProviderExecutionTarget>()

        for (provider in providers) {
            if (provider.id == failedProviderId) continue

            val state = provider.getStatus().state
            if (state != ProviderStatus.State.READY) continue
            if (policy == AiRoutingPolicy.LOCAL_ONLY && !provider.isLocal) continue

            val target = resolveCandidateTarget(
                provider = provider,
                request = request,
                preferSelectedModel = false
            )

            if (target != null) {
                readyTargets.add(target)
            }
        }

        return if (policy == AiRoutingPolicy.LOCAL_ONLY) {
            readyTargets.firstOrNull { it.provider.isLocal }
        } else {
            readyTargets.firstOrNull()
        }
    }

    private fun supportsProviderInput(
        capabilities: Set<AiCapability>,
        request: AiRequest
    ): Boolean {
        if (request.text != null && AiCapability.TEXT !in capabilities) return false
        if (request.image != null && AiCapability.IMAGE !in capabilities) return false
        if (request.audio != null && AiCapability.AUDIO !in capabilities) return false
        if (request.video != null && AiCapability.VIDEO !in capabilities) return false
        return true
    }

    private fun supportsModelInput(
        model: AiModel,
        request: AiRequest
    ): Boolean {
        val capabilities = model.inputCapabilities
        if (request.text != null && AiCapability.TEXT !in capabilities) return false
        if (request.image != null && AiCapability.IMAGE !in capabilities) return false
        if (request.audio != null && AiCapability.AUDIO !in capabilities) return false
        if (request.video != null && AiCapability.VIDEO !in capabilities) return false
        return true
    }

    private fun supportsEffectiveCapabilities(
        providerCapabilities: Set<AiCapability>,
        model: AiModel,
        request: AiRequest
    ): Boolean {
        if (!supportsProviderInput(providerCapabilities, request)) {
            return false
        }
        return supportsModelInput(model, request)
    }
}
