package com.halo.engines.speech

import com.halo.sdk.runtime.ObservationPayload
import com.halo.sdk.runtime.SpeechObservationPayload

data class VoiceDetectedPayload(val energy: Float = 0f) : ObservationPayload
data class WakeWordDetectedPayload(val wakeWord: String) : ObservationPayload

/**
 * Compatibility name for the canonical runtime speech observation payload.
 * Keeping one payload type prevents keyboard and microphone observations from
 * diverging at the interpretation boundary.
 */
typealias SpeechRecognizedPayload = SpeechObservationPayload
