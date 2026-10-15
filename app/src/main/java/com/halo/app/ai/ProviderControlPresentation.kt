package com.halo.app.ai

fun providerOutcomeLabel(outcome: ProviderTestOutcome): String = when (outcome) {
    ProviderTestOutcome.SAVED_UNTESTED -> "Configuration saved; connection not checked"
    ProviderTestOutcome.CHECKING -> "Checking"
    ProviderTestOutcome.READY_AUTH -> "Connection checked; answer not tested"
    ProviderTestOutcome.MODELS_AVAILABLE -> "Available models refreshed"
    ProviderTestOutcome.GENERATION_PROVEN -> "Generation proven"
    ProviderTestOutcome.AUTH_FAILED -> "Authentication failed"
    ProviderTestOutcome.PERMISSION_DENIED -> "This account cannot access this service"
    ProviderTestOutcome.PAYMENT_OR_CREDITS -> "Check payment or available credits"
    ProviderTestOutcome.RATE_LIMITED -> "Rate limited"
    ProviderTestOutcome.MODEL_NOT_FOUND -> "This model is not available to this account"
    ProviderTestOutcome.TIMEOUT -> "The check timed out"
    ProviderTestOutcome.TLS_ERROR -> "Secure connection failed"
    ProviderTestOutcome.DNS_ERROR -> "Provider address could not be reached"
    ProviderTestOutcome.OFFLINE -> "Offline"
    ProviderTestOutcome.BAD_ENDPOINT -> "Check the provider address"
    ProviderTestOutcome.SERVER_ERROR -> "Provider unavailable"
    ProviderTestOutcome.PROTOCOL_ERROR -> "The provider returned an unsupported response"
    ProviderTestOutcome.CANCELLED -> "Check cancelled"
    ProviderTestOutcome.SECRET_STORE_ERROR -> "Protected credential storage is unavailable"
    ProviderTestOutcome.CONSENT_REQUIRED -> "Confirm online usage before testing an answer"
    ProviderTestOutcome.MODEL_REQUIRED -> "Choose an available text model first"
    ProviderTestOutcome.NO_FINAL_ANSWER -> "A final answer was not received"
}

val ProviderConnectionState.label: String get() = when (this) {
    ProviderConnectionState.NOT_CONFIGURED -> "Setup required"
    ProviderConnectionState.CONFIGURED -> "Configured · not checked"
    ProviderConnectionState.CHECKING -> "Checking"
    ProviderConnectionState.READY -> "Ready"
    ProviderConnectionState.AUTH_FAILED -> "Authentication failed"
    ProviderConnectionState.OFFLINE -> "Offline"
    ProviderConnectionState.RATE_LIMITED -> "Rate limited"
    ProviderConnectionState.ERROR -> "Error"
}
val GenerationProof.label: String get() = when (this) {
    GenerationProof.NOT_TESTED -> "Generation not tested"
    GenerationProof.CHECKING -> "Checking generation"
    GenerationProof.PROVEN -> "Generation proven"
    GenerationProof.FAILED -> "Generation check failed"
}

val com.halo.sdk.provider.SpeechErrorCode.label: String get() = when (this) {
    com.halo.sdk.provider.SpeechErrorCode.NOT_CONFIGURED -> "Setup required"
    com.halo.sdk.provider.SpeechErrorCode.AUTHENTICATION_FAILED -> "Authentication failed"
    com.halo.sdk.provider.SpeechErrorCode.RATE_LIMITED -> "Rate limited"
    com.halo.sdk.provider.SpeechErrorCode.NETWORK_ERROR -> "Offline"
    com.halo.sdk.provider.SpeechErrorCode.VOICE_NOT_FOUND -> "Choose an available voice"
    com.halo.sdk.provider.SpeechErrorCode.MODEL_NOT_AVAILABLE -> "Choose an available speech model"
    com.halo.sdk.provider.SpeechErrorCode.CANCELLED -> "Cancelled"
    else -> "Speech synthesis failed"
}
