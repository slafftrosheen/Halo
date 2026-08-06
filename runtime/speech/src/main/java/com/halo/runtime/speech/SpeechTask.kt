package com.halo.runtime.speech

import com.halo.engines.speech.SpeechSegment

data class SpeechTask(
    val segment: SpeechSegment,
    val timeoutMillis: Long,
    val priority: Int = 0,
    val source: SpeechTrigger,
    val timestamp: Long = System.currentTimeMillis(),
    val language: String? = null
)
