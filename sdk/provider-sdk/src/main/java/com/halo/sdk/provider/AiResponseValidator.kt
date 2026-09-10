package com.halo.sdk.provider

/**
 * Central validation of provider responses.
 *
 * Every generation path must pass through this validator.
 */
object AiResponseValidator {

    suspend fun validate(
        provider: AiProvider,
        response: AiResponse
    ): AiResponse {

        if (response.providerId.isNotBlank() &&
            response.providerId != provider.id
        ) {
            throw AiProviderException(
                AiErrorType.INVALID_RESPONSE,
                "Provider identity mismatch: expected=${provider.id}, actual=${response.providerId}"
            )
        }

        val capabilities = provider.getCapabilities()

        if (response.toolCalls.isNotEmpty() &&
            !capabilities.contains(AiCapability.TOOLS)
        ) {
            throw AiProviderException(
                AiErrorType.INVALID_RESPONSE,
                "Provider returned tool calls without TOOLS capability"
            )
        }

        if (response.text.isBlank() &&
            response.toolCalls.isEmpty()
        ) {
            throw AiProviderException(
                AiErrorType.EMPTY_RESPONSE,
                "Provider returned no usable response"
            )
        }

        return response.copy(
            providerId = provider.id,
            modelId = response.modelId
        )
    }
}
