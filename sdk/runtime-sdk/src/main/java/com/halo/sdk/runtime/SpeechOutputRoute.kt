package com.halo.sdk.runtime

sealed interface SpeechOutputTarget {
    data object Phone : SpeechOutputTarget

    data class Wearable(
        val deviceId: String,
        val displayName: String? = null
    ) : SpeechOutputTarget
}

enum class SpeechOutputFallback {
    DISALLOW_PHONE,
    ALLOW_PHONE
}

data class SpeechTurnContext(
    val captureId: String?,
    val sourceDeviceId: String?,
    val inputRoute: SpeechInputRoute?,
    val outputTarget: SpeechOutputTarget
)
