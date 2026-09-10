package com.halo.runtime.cognition.response

import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiProvider
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiResponse

/**
 * Central validation of provider responses.
 *
 * Every generation path must pass through this validator.
 */
object AiResponseValidator {

    fun validate(
        provider: AiProvider,
        capabilities: Set<AiCapability>,
        expectedModelId: String,
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

        if (response.modelId != null && response.modelId != expectedModelId) {
            throw AiProviderException(
                AiErrorType.INVALID_RESPONSE,
                "Model identity mismatch: expected=$expectedModelId, actual=${response.modelId}"
            )
        }

        return response.copy(
            providerId = provider.id,
            // Some backends omit this field. The transaction target is the
            // authoritative identity in that case, never an ambiguous null.
            modelId = expectedModelId
        )
    }
}
