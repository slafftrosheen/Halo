package com.halo.runtime.speech

sealed interface SpeechTrigger {
    data object WakeWord : SpeechTrigger
    data object ManualButton : SpeechTrigger
    data object DeveloperCommand : SpeechTrigger
}
