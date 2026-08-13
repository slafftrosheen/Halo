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

import kotlin.time.Duration.Companion.seconds

/**
 * Routes AI requests to the appropriate provider based on capabilities and configuration.
 */
class DefaultAiProviderRouter(
    private val providers: List<AiProvider>,
    private val storage: Storage
) : AiProviderRouter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Periodically refresh provider statuses
        scope.launch {
            while (isActive) {
                providers.forEach { provider ->
                    runCatching { provider.getStatus() }
                }
                delay(30.seconds)
            }
        }
    }

    private val _selectedProviderId = MutableStateFlow<String?>(
        storage.getString("active_ai_provider") ?: providers.firstOrNull()?.id
    )
    override val selectedProviderId: StateFlow<String?> = _selectedProviderId.asStateFlow()

    private val _selectedModelId = MutableStateFlow<String?>(
        storage.getString("active_ai_model")
    )
    override val selectedModelId: StateFlow<String?> = _selectedModelId.asStateFlow()

    override fun selectProvider(id: String) {
        if (providers.any { it.id == id }) {
            _selectedProviderId.value = id
            storage.putString("active_ai_provider", id)
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
        val selectedId = _selectedProviderId.value
        val selected = providers.find { it.id == selectedId }
        
        val provider = if (selected != null && selected.status.value.state == ProviderStatus.State.READY && supports(selected, request)) {
            selected
        } else {
            providers
                .firstOrNull { it.status.value.state == ProviderStatus.State.READY && supports(it, request) }
        } ?: throw IllegalStateException("No available AI provider found for requested capabilities")

        val modelId = request.modelId ?: _selectedModelId.value
        val finalRequest = if (request.modelId == null && modelId != null) {
            request.copy(modelId = modelId)
        } else {
            request
        }

        return provider.generate(finalRequest, context)
    }

    private fun supports(provider: AiProvider, request: AiRequest): Boolean {
        // In the future, validate modality against provider.getCapabilities()
        return true 
    }
}
