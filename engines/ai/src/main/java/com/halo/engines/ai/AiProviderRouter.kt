package com.halo.engines.ai

import com.halo.sdk.provider.AiProvider
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiCapability

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Routes AI requests to the appropriate provider based on capabilities and configuration.
 */
class AiProviderRouter(
    private val providers: List<AiProvider>
) {
    private val _selectedProviderId = MutableStateFlow<String?>(providers.firstOrNull()?.id)
    val selectedProviderId: StateFlow<String?> = _selectedProviderId.asStateFlow()

    fun selectProvider(id: String) {
        if (providers.any { it.id == id }) {
            _selectedProviderId.value = id
        }
    }

    /**
     * Returns all registered providers.
     */
    fun getAllProviders(): List<AiProvider> = providers

    /**
     * Returns only providers that report being available.
     */
    fun getAvailableProviders(): List<AiProvider> = providers.filter { it.isAvailable() }

    /**
     * Executes a request using the selected provider if it supports the capabilities,
     * otherwise falls back to the first available provider that supports them.
     */
    suspend fun execute(request: AiRequest): AiResponse {
        val selected = providers.find { it.id == _selectedProviderId.value }
        
        val provider = if (selected != null && selected.isAvailable() && supports(selected, request)) {
            selected
        } else {
            providers
                .filter { it.isAvailable() }
                .firstOrNull { supports(it, request) }
        } ?: throw IllegalStateException("No available AI provider found for requested capabilities")

        return provider.execute(request)
    }

    private fun supports(provider: AiProvider, request: AiRequest): Boolean {
        val requiredCapabilities = mutableSetOf<AiCapability>()
        if (request.text != null) requiredCapabilities.add(AiCapability.TEXT)
        if (request.image != null) requiredCapabilities.add(AiCapability.IMAGE)
        if (request.audio != null) requiredCapabilities.add(AiCapability.AUDIO)
        if (request.video != null) requiredCapabilities.add(AiCapability.VIDEO)

        // If no specific media is requested, assume TEXT is the baseline
        if (requiredCapabilities.isEmpty()) {
            requiredCapabilities.add(AiCapability.TEXT)
        }

        return provider.capabilities.containsAll(requiredCapabilities)
    }
}
