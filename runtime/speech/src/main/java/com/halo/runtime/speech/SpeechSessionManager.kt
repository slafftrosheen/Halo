package com.halo.runtime.speech


import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import com.halo.engines.speech.SpeechSegmentCaptured
import com.halo.engines.speech.ListeningStarted
import com.halo.engines.speech.SpeechEnded
import com.halo.engines.speech.RecognitionStarted
import com.halo.engines.speech.SpeechRecognized
import com.halo.engines.speech.RecognitionFinished
import com.halo.engines.speech.HandoffStarted
import com.halo.engines.speech.RecognitionTimeoutEvent
import com.halo.engines.speech.RecognitionFailed
import com.halo.engines.speech.SpeechRecognizedPayload


class SpeechSessionManager(
    private val eventBus: EventBus,
    private val commandBus: CommandBus,
    private val scope: CoroutineScope
) : SpeechStateObserver {
    private val _sessionState = MutableStateFlow<SpeechSessionState>(SpeechSessionState.Idle)
    override val sessionState: StateFlow<SpeechSessionState> = _sessionState.asStateFlow()
    
    private var workerJob: Job? = null
    
    fun start() {
        if (workerJob?.isActive == true) return
        
        workerJob = scope.launch {
            launch { observeCommands() }
            launch { observeRecognitionEvents() }
        }
    }
    
    fun stop() {
        workerJob?.cancel()
        workerJob = null
        _sessionState.value = SpeechSessionState.Idle
    }
    
    private fun observeCommands() {
        scope.launch {
            commandBus.commands().filterIsInstance<StartListening>().collect {
                _sessionState.value = SpeechSessionState.Capturing
            }
        }
        scope.launch {
            commandBus.commands().filterIsInstance<StopListening>().collect {
                if (_sessionState.value == SpeechSessionState.Capturing) {
                    _sessionState.value = SpeechSessionState.Idle
                }
            }
        }
    }

    private fun observeRecognitionEvents() {
        // Domain Events -> State Translation
        scope.launch {
            eventBus.observe(ListeningStarted::class.java).collect {
                _sessionState.value = SpeechSessionState.Capturing
            }
        }
        scope.launch {
            eventBus.observe(SpeechEnded::class.java).collect {
                _sessionState.value = SpeechSessionState.Thinking
            }
        }
        scope.launch {
            eventBus.observe(RecognitionStarted::class.java).collect {
                _sessionState.value = SpeechSessionState.Recognizing
            }
        }
        scope.launch {
            eventBus.observe(SpeechRecognized::class.java).collect { event ->
                _sessionState.value = SpeechSessionState.Thinking
            }
        }
        scope.launch {
            eventBus.observe(HandoffStarted::class.java).collect {
                _sessionState.value = SpeechSessionState.Handoff
            }
        }
        scope.launch {
            eventBus.observe(RecognitionFinished::class.java).collect {
                // Keep Thinking or Handoff until TTS starts or outcome finishes
            }
        }
        scope.launch {
            eventBus.observe(RecognitionFailed::class.java).collect { event ->
                _sessionState.value = SpeechSessionState.Recovering
                delay(500)
                _sessionState.value = SpeechSessionState.Idle
            }
        }
        
        // Listen for Outcome to return to Idle
        scope.launch {
            eventBus.observe(OutcomePublished::class.java).collect { event ->
                if (_sessionState.value == SpeechSessionState.Thinking) {
                    _sessionState.value = SpeechSessionState.Idle
                }
            }
        }
    }
}
