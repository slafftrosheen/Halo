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

enum class SpeechReplyPolicy {
    SAME_DEVICE,
    PHONE,
    USER_SELECTED
}

enum class SpeechRoutePendingReason {
    PAIRING_REQUIRED,
    WAITING_FOR_ENDPOINT
}

enum class SpeechRouteFailureReason {
    DEVICE_DISCONNECTED,
    COMMAND_FAILED,
    AUDIO_TRANSPORT_NOT_CONNECTED,
    WRONG_DEVICE
}

data class SpeechRouteEvidence(
    val vendorRouteCommandSent: Boolean,
    val androidDeviceType: Int?,
    val androidDeviceName: String?,
    val androidDeviceAddress: String?,
    val verifiedAtMs: Long = System.currentTimeMillis(),
    val androidDeviceId: Int? = null
)

sealed interface SpeechRoutePreparation {
    data class Ready(
        val target: SpeechOutputTarget,
        val evidence: SpeechRouteEvidence
    ) : SpeechRoutePreparation

    data class Pending(
        val target: SpeechOutputTarget,
        val reason: SpeechRoutePendingReason
    ) : SpeechRoutePreparation

    data class Unavailable(
        val target: SpeechOutputTarget,
        val reason: SpeechRouteFailureReason
    ) : SpeechRoutePreparation
}

data class SpeechTurnContext(
    val captureId: String?,
    val sourceDeviceId: String?,
    val inputRoute: SpeechInputRoute?,
    val outputTarget: SpeechOutputTarget
)
