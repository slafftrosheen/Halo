package com.halo.engines.speech

import com.halo.sdk.provider.SpeechRecognitionProvider
import com.halo.sdk.provider.WakeWordDetector
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.host.AudioFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

class AudioPipeline(
    private val vad: VoiceActivityDetector,
    private val wakeWordDetector: WakeWordDetector,
    private val sttProvider: SpeechRecognitionProvider,
    private val eventBus: EventBus,
    private val scope: CoroutineScope
) {
    private var buffer = AudioBuffer()
    private var isRecording = false
    private var isAwake = false

    fun processFrame(frame: AudioFrame) {
        if (vad.isSpeech(frame)) {
            if (!isRecording) {
                isRecording = true
                scope.launch { eventBus.publish(SpeechStarted()) }
            }
            buffer.append(frame)
            
            if (!isAwake) {
                val wwResult = wakeWordDetector.process(frame)
                if (wwResult.detected) {
                    isAwake = true
                    scope.launch { eventBus.publish(RecognitionStarted(id = UUID.randomUUID().toString())) }
                }
            }
        } else if (isRecording) {
            isRecording = false
            scope.launch { eventBus.publish(SpeechEnded()) }

            val segment = buffer.snapshot()
            buffer.clear()
            
            if (isAwake) {
                isAwake = false
                scope.launch { processSegment(segment) }
            }
        }
    }

    private suspend fun processSegment(segment: SpeechSegment) {
        try {
            val result = sttProvider.recognize(segment.audioBuffer)
            eventBus.publish(SpeechRecognized(
                id = UUID.randomUUID().toString(),
                text = result.text
            ))
            eventBus.publish(RecognitionFinished(id = UUID.randomUUID().toString()))
        } catch (e: Exception) {
            eventBus.publish(RecognitionFailed(reason = e.message ?: "Recognition failed"))
        }
    }

    fun reset() {
        buffer.clear()
        isRecording = false
        isAwake = false
    }
}
