package com.halo.host.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.halo.runtime.kernel.Process
import com.halo.runtime.kernel.ProcessState
import com.halo.runtime.kernel.bus.PerceptionBus
import com.halo.icl.ontology.Observation
import com.halo.icl.core.CognitiveId
import com.halo.icl.core.Confidence
import com.halo.icl.core.Provenance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class AndroidSpeechProcess(
    private val context: Context,
    private val perceptionBus: PerceptionBus,
    override val id: String = "driver.android.speech_recognizer",
    override val name: String = "Android Built-in Speech Recognizer",
    private val scope: CoroutineScope
) : Process {
    
    private val _state = MutableStateFlow(ProcessState.Stopped)
    override val state: StateFlow<ProcessState> = _state

    private val _metrics = MutableStateFlow(com.halo.sdk.runtime.EngineMetrics(
        latencyMs = null,
        uptime = java.time.Duration.ZERO,
        eventsProcessed = 0L,
        queueSize = 0,
        cpuUsage = 0.0
    ))
    override val metrics: StateFlow<com.halo.sdk.runtime.EngineMetrics> = _metrics

    override fun engineSnapshot(): com.halo.sdk.runtime.EngineSnapshot = com.halo.sdk.runtime.EngineSnapshot(
        id = id,
        state = com.halo.sdk.runtime.EngineState.Running,
        health = com.halo.sdk.runtime.EngineHealth.Healthy,
        metrics = _metrics.value,
        lastActivity = java.time.Instant.now()
    )

    private var speechRecognizer: SpeechRecognizer? = null
    
    override suspend fun start(): Unit = coroutineScope {
        _state.value = ProcessState.Running
        
        withContext(Dispatchers.Main) {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                initializeRecognizer()
            } else {
                _state.value = ProcessState.Crashed
            }
        }
        
        launch {
            perceptionBus.subscribe(id).filterIsInstance<Observation>().collect { obs ->
                if (obs.modality == "WakeWord") {
                    withContext(Dispatchers.Main) {
                        startCapturing()
                    }
                }
            }
        }
    }

    private fun initializeRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    // Optionally notify that we are ready
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    // No auto-looping in Push-to-Talk mode
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        publishTranscription(text)
                    }
                    // No auto-looping in Push-to-Talk mode
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        // Don't start automatically; wait for startCapturing()
    }
    
    fun startCapturing() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopCapturing() {
        speechRecognizer?.stopListening()
    }

    private fun publishTranscription(text: String) {
        val observation = Observation(
            id = CognitiveId(),
            created = Clock.System.now(),
            confidence = Confidence(0.9f),
            provenance = Provenance(id, null),
            edges = emptyList(),
            rawData = text,
            modality = "Speech"
        )
        // Fire and forget onto the perception bus
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Default) {
            perceptionBus.publish(id, observation)
        }
    }

    override suspend fun pause() {
        _state.value = ProcessState.Paused
        withContext(Dispatchers.Main) {
            speechRecognizer?.stopListening()
        }
    }
    
    override suspend fun resume() {
        _state.value = ProcessState.Running
        withContext(Dispatchers.Main) {
            // Nothing to do for resume unless push-to-talk triggers
        }
    }
    
    override suspend fun stop() {
        _state.value = ProcessState.Stopped
        withContext(Dispatchers.Main) {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }
}
