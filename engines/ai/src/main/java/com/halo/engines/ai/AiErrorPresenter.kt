package com.halo.engines.ai

import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiProviderException

interface AiErrorPresenter {
    fun message(error: AiProviderException): String
}

class DefaultAiErrorPresenter : AiErrorPresenter {
    override fun message(error: AiProviderException): String {
        return when (error.type) {
            AiErrorType.MODEL_NOT_SELECTED -> "No AI model selected."
            AiErrorType.MODEL_NOT_FOUND -> "Selected AI model could not be found."
            AiErrorType.MODEL_LOAD_FAILED -> "The local AI model could not be loaded."
            AiErrorType.MODEL_NOT_READY -> "The selected AI model is not ready."
            AiErrorType.PROVIDER_UNAVAILABLE -> "The selected AI provider is unavailable."
            AiErrorType.AUTHENTICATION_FAILED -> "Authentication failed. Check your API credentials."
            AiErrorType.RATE_LIMITED -> "The AI provider is temporarily rate limited. Please try again shortly."
            AiErrorType.NETWORK_UNAVAILABLE, AiErrorType.NETWORK_ERROR -> "The AI provider could not be reached over the network."
            AiErrorType.EMPTY_RESPONSE -> "The AI returned an empty response."
            AiErrorType.UNSUPPORTED_CAPABILITY -> "This AI provider cannot perform that type of request."
            AiErrorType.INVALID_REQUEST -> "The AI request was invalid."
            AiErrorType.INVALID_RESPONSE -> "The AI provider returned an invalid response format."
            AiErrorType.CONTEXT_TOO_LARGE -> "The conversation context exceeds the model's token limit."
            AiErrorType.LOCAL_MODEL_UNAVAILABLE -> "The local AI model is unavailable."
            AiErrorType.CANCELLED -> "AI generation was cancelled."
            AiErrorType.GENERATION_FAILED -> error.message ?: "AI generation failed."
            else -> error.message ?: "An unexpected AI error occurred."
        }
    }
}
