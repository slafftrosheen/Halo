package com.halo.engines.ai

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage

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
    private val storage: Storage
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

    /**
     * Executes a request using the selected provider if it supports the capabilities,
     * otherwise falls back to the first available provider that supports them.
     */
    override suspend fun execute(request: AiRequest, context: AiContext): AiResponse {
        val policy = _routingPolicy.value
        val selectedId = _selectedProviderId.value
        val preferredProvider = providers.find { it.id == selectedId }

        val provider = selectProviderForRequest(policy, preferredProvider, request)
        
        if (provider == null) {
            val reason = when (policy) {
                AiRoutingPolicy.LOCAL_ONLY -> "No local AI providers available"
                AiRoutingPolicy.EXPLICIT -> "Explicitly selected provider ($selectedId) is unavailable"
                else -> "No available AI provider found for requested capabilities"
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
            provider.generate(finalRequest, context)
        } catch (e: AiProviderException) {
            if (policy == AiRoutingPolicy.PREFERRED_WITH_FALLBACK || policy == AiRoutingPolicy.AUTOMATIC) {
                val fallbackProvider = getFallbackProvider(provider, request, policy)
                if (fallbackProvider != null) {
                    return fallbackProvider.generate(finalRequest, context)
                }
            }
            throw e
        }
    }

    private suspend fun selectProviderForRequest(policy: AiRoutingPolicy, preferred: AiProvider?, request: AiRequest): AiProvider? {
        val readyProviders = mutableListOf<AiProvider>()
        for (provider in providers) {
            val state = provider.status.value.state
            if ((state == ProviderStatus.State.READY || state == ProviderStatus.State.STANDBY || state == ProviderStatus.State.LOADING) && supports(provider, request)) {
                readyProviders.add(provider)
            }
        }
        
        return when (policy) {
            AiRoutingPolicy.EXPLICIT -> {
                if (preferred != null && readyProviders.contains(preferred)) preferred else null
            }
            AiRoutingPolicy.LOCAL_ONLY -> {
                val localProviders = readyProviders.filter { it.isLocal }
                if (preferred != null && localProviders.contains(preferred)) preferred else localProviders.firstOrNull()
            }
            AiRoutingPolicy.AUTOMATIC, AiRoutingPolicy.PREFERRED_WITH_FALLBACK -> {
                if (preferred != null && readyProviders.contains(preferred)) {
                    preferred
                } else {
                    readyProviders.firstOrNull()
                }
            }
        }
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
        if (request.image != null && !caps.contains(AiCapability.IMAGE) && !caps.contains(AiCapability.MULTIMODAL)) return false
        if (request.audio != null && !caps.contains(AiCapability.AUDIO) && !caps.contains(AiCapability.MULTIMODAL)) return false
        if (request.video != null && !caps.contains(AiCapability.VIDEO) && !caps.contains(AiCapability.MULTIMODAL)) return false
        return true
    }
}
