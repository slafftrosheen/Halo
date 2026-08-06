package com.halo.runtime.speech

sealed interface SpeechMode {
    data object Passive : SpeechMode
    data object Active : SpeechMode
}
