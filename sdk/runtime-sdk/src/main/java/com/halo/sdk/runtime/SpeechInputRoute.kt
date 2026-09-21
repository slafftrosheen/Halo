package com.halo.sdk.runtime

import com.halo.sdk.host.ObservationSource

sealed interface SpeechInputRoute {
    data object Phone : SpeechInputRoute
    data class Wearable(val deviceId: String, val displayName: String? = null) : SpeechInputRoute
}

enum class CaptureBoundary { VOICE_ACTIVITY, EXTERNAL }

data class SpeechInputTurn(
    val captureId: String,
    val route: SpeechInputRoute = SpeechInputRoute.Phone,
    val source: ObservationSource = ObservationSource.AndroidPhoneMic,
    val boundary: CaptureBoundary = CaptureBoundary.VOICE_ACTIVITY
)

data class SpeechInputStatus(
    val turn: SpeechInputTurn? = null,
    val capturing: Boolean = false,
    val inputLevel: Float = 0f,
    val rejection: String? = null
)
