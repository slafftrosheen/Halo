package com.halo.engines.speech

import com.halo.sdk.provider.BufferedSpeechRecognitionProvider
import com.halo.sdk.provider.LiveSpeechRecognitionProvider
import com.halo.sdk.provider.SpeechRecognitionMode
import com.halo.sdk.provider.RecognitionEvent
import com.halo.sdk.provider.SpeechRecognitionProvider
import com.halo.sdk.provider.WakeWordDetector
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.SpeechState
import com.halo.sdk.host.AudioFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state
    private var liveListeningJob: Job? = null

    init {
        scope.launch {
            eventBus.observe(ListeningStarted::class.java).collect {
                onListeningStarted()
            }
        }
        scope.launch {
            eventBus.observe(SpeechEnded::class.java).collect {
                onSpeechEnded()
            }
        }
    }

    private fun onListeningStarted() {
        if (_state.value == SpeechState.Recognizing) return
        _state.value = SpeechState.Recognizing
        scope.launch { eventBus.publish(RecognitionStarted(id = UUID.randomUUID().toString())) }
        if (sttProvider.mode == SpeechRecognitionMode.LIVE) {
            startLiveListening()
        }
    }

    private fun onSpeechEnded() {
        if (sttProvider.mode == SpeechRecognitionMode.BUFFERED) {
            val segment = buffer.snapshot()
            buffer.clear()
            if (segment.audioBuffer.frames.isNotEmpty()) {
                _state.value = SpeechState.Recognizing
                scope.launch { processSegment(segment) }
            } else {
                _state.value = SpeechState.Idle
            }
        }
    }

    fun processFrame(frame: AudioFrame) {
        val currentState = _state.value
        
        if (vad.isSpeech(frame)) {
            if (currentState == SpeechState.Idle) {
                _state.value = SpeechState.Listening
                scope.launch { eventBus.publish(SpeechStarted()) }
            }
            
            if (sttProvider.mode == SpeechRecognitionMode.BUFFERED) {
                buffer.append(frame)
            }
            
            if (currentState != SpeechState.WakeDetected && currentState != SpeechState.Recognizing) {
                val wwResult = wakeWordDetector.process(frame)
                if (wwResult.detected) {
                    _state.value = SpeechState.WakeDetected
                    scope.launch { eventBus.publish(RecognitionStarted(id = UUID.randomUUID().toString())) }
                    
                    if (sttProvider.mode == SpeechRecognitionMode.LIVE) {
                        _state.value = SpeechState.Recognizing
                        startLiveListening()
                    }
                }
            }
        } else if (currentState == SpeechState.Listening || currentState == SpeechState.WakeDetected || currentState == SpeechState.Recognizing) {
            if (sttProvider.mode == SpeechRecognitionMode.BUFFERED) {
                scope.launch { eventBus.publish(SpeechEnded()) }
                
                val segment = buffer.snapshot()
                buffer.clear()
                
                if (currentState == SpeechState.WakeDetected || currentState == SpeechState.Recognizing) {
                    _state.value = SpeechState.Recognizing
                    scope.launch { processSegment(segment) }
                } else {
                    _state.value = SpeechState.Idle
                }
            } else if (sttProvider.mode == SpeechRecognitionMode.LIVE) {
                // For live providers, we let the provider handle its own endpointing.
                // We just keep the state until the provider stops.
                scope.launch { eventBus.publish(SpeechEnded()) }
            }
        }
    }

    private fun startLiveListening() {
        if (sttProvider !is LiveSpeechRecognitionProvider) return
        
        liveListeningJob?.cancel()
        liveListeningJob = scope.launch {
            try {
                sttProvider.startListening().collect { event ->
                    when (event) {
                        is RecognitionEvent.Ready -> {
                            _state.value = SpeechState.Listening
                        }
                        is RecognitionEvent.Listening -> {
                            _state.value = SpeechState.Recognizing
                        }
                        is RecognitionEvent.Partial -> {
                            // Can emit partial event here if required in the future
                        }
                        is RecognitionEvent.Final -> {
                            _state.value = SpeechState.Thinking
                            eventBus.publish(SpeechRecognized(
                                id = UUID.randomUUID().toString(),
                                text = event.result.text
                            ))
                            eventBus.publish(RecognitionFinished(id = UUID.randomUUID().toString()))
                        }
                        is RecognitionEvent.Error -> {
                            _state.value = SpeechState.Idle
                            eventBus.publish(RecognitionFailed(reason = event.reason))
                        }
                        is RecognitionEvent.Stopped -> {
                            if (_state.value != SpeechState.Thinking) {
                                _state.value = SpeechState.Idle
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                eventBus.publish(RecognitionFailed(reason = e.message ?: "Live recognition failed"))
                _state.value = SpeechState.Idle
            }
        }
    }

    private suspend fun processSegment(segment: SpeechSegment) {
        if (sttProvider !is BufferedSpeechRecognitionProvider) return
        
        try {
            val result = sttProvider.recognize(segment.audioBuffer)
            _state.value = SpeechState.Thinking
            eventBus.publish(SpeechRecognized(
                id = UUID.randomUUID().toString(),
                text = result.text
            ))
            eventBus.publish(RecognitionFinished(id = UUID.randomUUID().toString()))
        } catch (e: Exception) {
            _state.value = SpeechState.Idle
            eventBus.publish(RecognitionFailed(reason = e.message ?: "Recognition failed"))
        }
    }

    fun reset() {
        buffer.clear()
        _state.value = SpeechState.Idle
        liveListeningJob?.cancel()
        liveListeningJob = null
    }
}
