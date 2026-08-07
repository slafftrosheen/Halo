package com.halo.engines.speech

import com.halo.sdk.runtime.AudioFrame

data class WakeWordResult(
    val detected: Boolean,
    val confidence: Float = 0f,
    val startIndex: Int = -1,
    val endIndex: Int = -1
)

interface WakeWordDetector {
    fun process(frame: AudioFrame): WakeWordResult
}
