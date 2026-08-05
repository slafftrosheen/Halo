package com.halo.engines.speech

import com.halo.sdk.runtime.HaloEvent

data class ListeningStarted(override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class SpeechStarted(override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class SpeechEnded(override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class RecognitionStarted(override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class RecognitionFinished(override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class RecognitionFailed(val reason: String, override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class SpeechRecognized(val text: String, override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
