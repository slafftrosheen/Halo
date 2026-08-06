package com.halo.runtime.speech

import com.halo.engines.speech.SpeechRecognizer
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.halo.engines.speech.SpeechSegmentCaptured
import com.halo.engines.speech.RecognitionStarted
import com.halo.engines.speech.SpeechRecognized
import com.halo.engines.speech.RecognitionFinished
import com.halo.engines.speech.RecognitionTimeoutEvent
import com.halo.engines.speech.RecognitionFailed
import com.halo.engines.speech.SpeechRecognizedPayload

import com.halo.engines.agents.RuntimeOrchestrator

class SpeechSessionManager(
    private val recognizer: SpeechRecognizer,
    private val eventBus: EventBus,
    private val scope: CoroutineScope,
    private val orchestrator: RuntimeOrchestrator? = null
) : SpeechStateObserver {
    private val _sessionState = MutableStateFlow<SpeechSessionState>(SpeechSessionState.Idle)
    override val sessionState: StateFlow<SpeechSessionState> = _sessionState.asStateFlow()
    
    private val recognitionQueue = Channel<SpeechTask>(Channel.UNLIMITED)
    private var workerJob: Job? = null
    
    fun start() {
        if (workerJob?.isActive == true) return
        
        _sessionState.value = SpeechSessionState.PassiveListening
        workerJob = scope.launch {
            launch { runRecognitionWorker() }
            launch { listenForSegments() }
        }
    }
    
    fun stop() {
        workerJob?.cancel()
        workerJob = null
        _sessionState.value = SpeechSessionState.Idle
    }
    
    private suspend fun listenForSegments() {
        eventBus.observe(SpeechSegmentCaptured::class.java).collect { event ->
            // In Alpha, any captured segment triggers a default ManualButton task for now
            val task = SpeechTask(
                segment = event.segment,
                timeoutMillis = 30_000L,
                source = SpeechTrigger.ManualButton
            )
            enqueueTask(task)
        }
    }
    
    fun enqueueTask(task: SpeechTask) {
        if (_sessionState.value == SpeechSessionState.Idle) {
            _sessionState.value = SpeechSessionState.PassiveListening
        }
        recognitionQueue.trySend(task)
    }
    
    private suspend fun runRecognitionWorker() {
        while (currentCoroutineContext().isActive) {
            val task = try {
                recognitionQueue.receiveCatching().getOrNull() ?: return
            } catch (e: CancellationException) {
                throw e
            }
            
            _sessionState.value = SpeechSessionState.Recognizing
            eventBus.publish(RecognitionStarted())
            
            try {
                val result = withTimeout(task.timeoutMillis) {
                    recognizer.transcribe(task.segment)
                }
                
                eventBus.publish(SpeechRecognized(result.text))
                
                // Trigger the deterministic Alpha orchestration loop
                orchestrator?.onSpeechRecognized(result.text)
                
                eventBus.publish(
                    ObservationCreated(
                        Observation(
                            modality = Modality.AUDIO,
                            source = Source.MICROPHONE,
                            payload = SpeechRecognizedPayload(result.text, result.language ?: "en"),
                            confidence = result.confidence,
                            metadata = mapOf("engine" to "SpeechSessionManager")
                        )
                    )
                )
                eventBus.publish(RecognitionFinished())
            } catch (e: TimeoutCancellationException) {
                eventBus.publish(
                    RecognitionTimeoutEvent(
                        timeoutMillis = task.timeoutMillis,
                        segmentStartedAt = task.segment.startedAt,
                        segmentFinishedAt = task.segment.finishedAt
                    )
                )
                eventBus.publish(RecognitionFailed("Recognition timed out after ${task.timeoutMillis}ms"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventBus.publish(RecognitionFailed(e.message ?: "Unknown error"))
            } finally {
                _sessionState.value = SpeechSessionState.PassiveListening
            }
        }
    }
}
