package com.halo.sdk.provider

import com.halo.sdk.host.AudioFrame

data class WakeWordResult(
    val detected: Boolean,
    val confidence: Float = 0f,
    val startIndex: Int = -1,
    val endIndex: Int = -1
)

interface WakeWordDetector {
    fun process(frame: AudioFrame): WakeWordResult
    suspend fun health(): Boolean
}
