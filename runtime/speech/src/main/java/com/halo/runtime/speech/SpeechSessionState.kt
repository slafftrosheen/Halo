package com.halo.runtime.speech

sealed interface SpeechSessionState {
    data object Idle : SpeechSessionState
    data object PassiveListening : SpeechSessionState
    data object Triggered : SpeechSessionState
    data object Capturing : SpeechSessionState
    data object Recognizing : SpeechSessionState
    data object Processing : SpeechSessionState
    data object Speaking : SpeechSessionState
    data class Error(val reason: String) : SpeechSessionState
}
