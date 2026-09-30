package com.halo.app.ai

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
