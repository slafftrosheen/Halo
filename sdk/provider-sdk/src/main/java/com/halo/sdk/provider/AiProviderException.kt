package com.halo.sdk.provider

/**
 * Normalized semantic failures for AI providers.
 */
enum class AiErrorType {
    PROVIDER_UNAVAILABLE,
    MODEL_UNAVAILABLE,
    MODEL_NOT_SELECTED,
    MODEL_NOT_FOUND,
    MODEL_LOAD_FAILED,
    MODEL_NOT_READY,
    INVALID_REQUEST,
    GENERATION_FAILED,
    EMPTY_RESPONSE,
    CANCELLED,
    AUTHENTICATION_FAILED,
    RATE_LIMITED,
    TIMEOUT,
    NETWORK_UNAVAILABLE,
    NETWORK_ERROR,
    INVALID_RESPONSE,
    UNSUPPORTED_CAPABILITY,
    CONTEXT_TOO_LARGE,
    LOCAL_MODEL_UNAVAILABLE,
    UNKNOWN_ERROR
}

/**
 * Semantic exception thrown by AI providers.
 */
class AiProviderException(
    val type: AiErrorType,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
