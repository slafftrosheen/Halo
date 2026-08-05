package com.halo.engines.speech

import com.halo.sdk.runtime.ObservationPayload

data class VoiceDetectedPayload(val energy: Float = 0f) : ObservationPayload
data class WakeWordDetectedPayload(val wakeWord: String) : ObservationPayload
data class SpeechRecognizedPayload(val transcript: String, val language: String) : ObservationPayload
