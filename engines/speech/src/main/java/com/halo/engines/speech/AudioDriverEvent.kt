package com.halo.engines.speech

import com.halo.sdk.runtime.HaloEvent
import java.util.UUID

enum class SpeechFailureReason {
    Timeout,
    Permission,
    Network,
    Unknown,
    Busy
}

sealed interface AudioDriverEvent : HaloEvent

data class RawRecognitionStarted(
    override val id: String = UUID.randomUUID().toString(), 
    override val timestamp: Long = System.currentTimeMillis()
) : AudioDriverEvent

data class RawRecognitionFinished(
    override val id: String = UUID.randomUUID().toString(), 
    override val timestamp: Long = System.currentTimeMillis()
) : AudioDriverEvent

data class RawRecognitionResult(
    val text: String, 
    override val id: String = UUID.randomUUID().toString(), 
    override val timestamp: Long = System.currentTimeMillis()
) : AudioDriverEvent

data class RawRecognitionFailure(
    val reason: SpeechFailureReason, 
    val message: String? = null,
    override val id: String = UUID.randomUUID().toString(), 
    override val timestamp: Long = System.currentTimeMillis()
) : AudioDriverEvent
