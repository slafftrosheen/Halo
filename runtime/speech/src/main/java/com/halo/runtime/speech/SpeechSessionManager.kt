package com.halo.runtime.speech

import com.halo.engines.speech.SpeechRecognizer
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import com.halo.engines.speech.SpeechSegmentCaptured
import com.halo.engines.speech.RecognitionStarted
import com.halo.engines.speech.SpeechRecognized
import com.halo.engines.speech.RecognitionFinished
import com.halo.engines.speech.RecognitionTimeoutEvent
import com.halo.engines.speech.RecognitionFailed
import com.halo.engines.speech.SpeechRecognizedPayload

import com.halo.engines.agents.RuntimeOrchestrator

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
        // Driver Events -> Domain Events Translation
        scope.launch {
            eventBus.observe(com.halo.engines.speech.RawRecognitionStarted::class.java).collect {
                _sessionState.value = SpeechSessionState.Recognizing
                eventBus.publish(RecognitionStarted(id = java.util.UUID.randomUUID().toString()))
            }
        }
        scope.launch {
            eventBus.observe(com.halo.engines.speech.RawRecognitionResult::class.java).collect { event ->
                _sessionState.value = SpeechSessionState.Thinking
                eventBus.publish(SpeechRecognized(id = java.util.UUID.randomUUID().toString(), text = event.text))
            }
        }
        scope.launch {
            eventBus.observe(com.halo.engines.speech.RawRecognitionFinished::class.java).collect {
                eventBus.publish(RecognitionFinished(id = java.util.UUID.randomUUID().toString()))
            }
        }
        scope.launch {
            eventBus.observe(com.halo.engines.speech.RawRecognitionFailure::class.java).collect { event ->
                eventBus.publish(RecognitionFailed(id = java.util.UUID.randomUUID().toString(), reason = event.message ?: event.reason.name))
                _sessionState.value = SpeechSessionState.Recovering
                delay(500)
                _sessionState.value = SpeechSessionState.Idle
            }
        }
        
        // Listen for Outcome to return to Idle
        scope.launch {
            eventBus.observe(OutcomePublished::class.java).collect {
                if (_sessionState.value == SpeechSessionState.Thinking || _sessionState.value == SpeechSessionState.Speaking) {
                    _sessionState.value = SpeechSessionState.Idle
                }
            }
        }
    }
}
