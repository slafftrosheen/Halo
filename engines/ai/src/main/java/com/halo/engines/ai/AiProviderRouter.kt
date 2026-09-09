package com.halo.engines.ai

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.RuntimeLogger

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job

import kotlin.time.Duration.Companion.seconds

/**
 * Routes AI requests to the appropriate provider based on capabilities and configuration.
 */
class DefaultAiProviderRouter(
    private val providers: List<AiProvider>,
    private val storage: Storage,
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
            _selectedProviderId.value = id
            storage.putString("active_ai_provider", id)
            scope.launch {
                runCatching { providers.find { it.id == id }?.getStatus() }
            }
        }
    }

    override fun selectModel(modelId: String?) {
        _selectedModelId.value = modelId
        storage.putString("active_ai_model", modelId)
    }

    /**
     * Returns all registered providers.
     */
    override fun getAllProviders(): List<AiProvider> = providers

    override suspend fun getEligibleProvider(request: AiRequest): AiProvider {
        val policy = _routingPolicy.value
        val selectedId = _selectedProviderId.value
        val preferredProvider = providers.find { it.id == selectedId }
        
        val (provider, rejections) = selectProviderWithDetails(policy, preferredProvider, request)
        if (provider != null) return provider

        val details = rejections.joinToString("\n") { "- ${it.providerId}: ${it.reason}" }
        logger?.error("AiProviderRouter", "No eligible provider found. Details:\n$details")
        
        val reason = when (policy) {
            AiRoutingPolicy.LOCAL_ONLY -> "No local AI providers available.\nDetails:\n$details"
            AiRoutingPolicy.EXPLICIT -> "Explicitly selected provider ($selectedId) is unavailable or unsupported.\nDetails:\n$details"
            else -> "No available AI provider found for requested capabilities.\nDetails:\n$details"
        }
        throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, reason)
    }
    override suspend fun execute(request: AiRequest, context: AiContext): AiResponse {
        val policy = _routingPolicy.value
        val selectedId = _selectedProviderId.value
        val preferredProvider = providers.find { it.id == selectedId }

        logger?.info("AiProviderRouter", "Executing request with policy=$policy, selectedId=$selectedId")

        val (provider, rejections) = selectProviderWithDetails(policy, preferredProvider, request)
        
        if (provider == null) {
            val details = rejections.joinToString("\n") { "- ${it.providerId}: ${it.reason}" }
            logger?.error("AiProviderRouter", "No suitable provider found. Details:\n$details")
            
            val reason = when (policy) {
                AiRoutingPolicy.LOCAL_ONLY -> "No local AI providers available.\nDetails:\n$details"
                AiRoutingPolicy.EXPLICIT -> "Explicitly selected provider ($selectedId) is unavailable or unsupported.\nDetails:\n$details"
                else -> "No available AI provider found for requested capabilities.\nDetails:\n$details"
            }
            throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, reason)
        }

        val modelId = request.modelId ?: _selectedModelId.value
        val finalRequest = if (request.modelId == null && modelId != null) {
            request.copy(modelId = modelId)
        } else {
            request
        }

        return try {
            val response = provider.generate(finalRequest, context)
            validateResponse(provider, response)
            response
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AiProviderException) {
            if (policy == AiRoutingPolicy.PREFERRED_WITH_FALLBACK || policy == AiRoutingPolicy.AUTOMATIC) {
                val fallbackProvider = getFallbackProvider(provider, request, policy)
                if (fallbackProvider != null) {
                    val fallbackResp = fallbackProvider.generate(finalRequest, context)
                    validateResponse(fallbackProvider, fallbackResp)
                    return fallbackResp
                }
            }
            throw e
        }
    }

    private fun validateResponse(
        provider: AiProvider,
        response: AiResponse
    ) {
        if (response.providerId != provider.id) {
            throw AiProviderException(
                AiErrorType.GENERATION_FAILED,
                "Provider identity mismatch: expected=${provider.id}, actual=${response.providerId}"
            )
        }

        if (response.text.isBlank() && response.toolCalls.isEmpty()) {
            throw AiProviderException(
                AiErrorType.EMPTY_RESPONSE,
                "Provider returned neither text nor tool calls"
            )
        }
    }

    private data class ProviderRejection(val providerId: String, val reason: String)

    private suspend fun selectProviderWithDetails(policy: AiRoutingPolicy, preferred: AiProvider?, request: AiRequest): Pair<AiProvider?, List<ProviderRejection>> {
        val readyProviders = mutableListOf<AiProvider>()
        val rejections = mutableListOf<ProviderRejection>()

        for (provider in providers) {
            val status = provider.getStatus()
            val state = status.state
            
            logger?.debug("AiProviderRouter", "Checking provider=${provider.id} state=$state")

            if (state != ProviderStatus.State.READY && state != ProviderStatus.State.STANDBY && state != ProviderStatus.State.LOADING) {
                rejections.add(ProviderRejection(provider.id, "Status is $state (${status.message ?: "No message"})"))
                continue
            }

            val caps = provider.getCapabilities()
            val unsupported = mutableListOf<String>()
            if (request.text != null && !caps.contains(AiCapability.TEXT) && !caps.contains(AiCapability.MULTIMODAL)) unsupported.add("TEXT")
            if (request.image != null && !caps.contains(AiCapability.IMAGE) && !caps.contains(AiCapability.MULTIMODAL)) unsupported.add("IMAGE")
            if (request.audio != null && !caps.contains(AiCapability.AUDIO) && !caps.contains(AiCapability.MULTIMODAL)) unsupported.add("AUDIO")
            if (request.video != null && !caps.contains(AiCapability.VIDEO) && !caps.contains(AiCapability.MULTIMODAL)) unsupported.add("VIDEO")
            
            if (unsupported.isNotEmpty()) {
                rejections.add(ProviderRejection(provider.id, "Missing capabilities: ${unsupported.joinToString()}"))
                continue
            }

            if (policy == AiRoutingPolicy.LOCAL_ONLY && !provider.isLocal) {
                rejections.add(ProviderRejection(provider.id, "Not a local provider (policy is LOCAL_ONLY)"))
                continue
            }

            if (policy == AiRoutingPolicy.EXPLICIT && preferred != null && provider.id != preferred.id) {
                rejections.add(ProviderRejection(provider.id, "Not the explicitly selected provider (policy is EXPLICIT)"))
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
                    val prefState = preferred.status.value.state
                    if (prefState == ProviderStatus.State.READY) {
                        preferred
                    } else {
                        readyProviders.find { it.status.value.state == ProviderStatus.State.READY } ?: preferred
                    }
                } else {
                    readyProviders.find { it.status.value.state == ProviderStatus.State.READY } ?: readyProviders.firstOrNull()
                }
            }
        }
        
        logger?.info("AiProviderRouter", "Selected provider=${result?.id} for policy=$policy")
        return Pair(result, rejections)
    }

    private suspend fun selectProviderForRequest(policy: AiRoutingPolicy, preferred: AiProvider?, request: AiRequest): AiProvider? {
        return selectProviderWithDetails(policy, preferred, request).first
    }

    private suspend fun getFallbackProvider(failedProvider: AiProvider, request: AiRequest, policy: AiRoutingPolicy): AiProvider? {
        val readyProviders = mutableListOf<AiProvider>()
        for (provider in providers) {
            val state = provider.status.value.state
            if (provider.id != failedProvider.id && (state == ProviderStatus.State.READY || state == ProviderStatus.State.STANDBY || state == ProviderStatus.State.LOADING) && supports(provider, request)) {
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
        if (request.text != null && !caps.contains(AiCapability.TEXT) && !caps.contains(AiCapability.MULTIMODAL)) return false
        if (request.image != null && !caps.contains(AiCapability.IMAGE) && !caps.contains(AiCapability.MULTIMODAL)) return false
        if (request.audio != null && !caps.contains(AiCapability.AUDIO) && !caps.contains(AiCapability.MULTIMODAL)) return false
        if (request.video != null && !caps.contains(AiCapability.VIDEO) && !caps.contains(AiCapability.MULTIMODAL)) return false
        return true
    }
}
