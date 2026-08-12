package com.halo.engines.ai

import com.halo.sdk.provider.AiProvider
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiCapability

/**
 * Routes AI requests to the appropriate provider based on capabilities and configuration.
 */
class AiProviderRouter(
    private val providers: List<AiProvider>
) {
    suspend fun execute(request: AiRequest): AiResponse {
        val provider = providers
            .firstOrNull { supports(it, request) }
            ?: throw IllegalStateException("No compatible AI provider found for request")

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
