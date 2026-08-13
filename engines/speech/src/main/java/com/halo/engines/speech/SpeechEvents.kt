package com.halo.engines.speech

import com.halo.sdk.runtime.HaloEvent
import java.util.UUID

data class ListeningStarted(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class SpeechStarted(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class SpeechEnded(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class RecognitionStarted(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class RecognitionFinished(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class HandoffStarted(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class RecognitionFailed(override val id: String = UUID.randomUUID().toString(), val reason: String, override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class RecognitionTimeoutEvent(
    override val id: String = UUID.randomUUID().toString(),
    val timeoutMillis: Long,
    val segmentStartedAt: Long,
    val segmentFinishedAt: Long,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent
data class SpeechRecognized(override val id: String = UUID.randomUUID().toString(), val text: String, override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class SpeechSegmentCaptured(override val id: String = UUID.randomUUID().toString(), val segment: SpeechSegment, override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class SpeechOutputStarted(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class SpeechOutputFinished(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class RawWakeWordDetected(override val id: String = UUID.randomUUID().toString(), val word: String, override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class WakeWordDetected(override val id: String = UUID.randomUUID().toString(), val word: String, override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
