package com.halo.sdk.provider

object AiResponseValidator {
    fun validate(
        provider: AiProvider,
        response: AiResponse
    ) {
        if (response.providerId != provider.id) {
            throw AiProviderException(
                AiErrorType.INVALID_RESPONSE,
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
}
