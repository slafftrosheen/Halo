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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class AndroidWakeWordProcess(
    private val context: Context,
    private val perceptionBus: PerceptionBus
) : Process {
    override val id: String = "driver.android.wakeword"
    override val name: String = "Android Wake-Word Detector"
    
    private val _state = MutableStateFlow(ProcessState.Created)
    override val state: StateFlow<ProcessState> = _state

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    
    override suspend fun start(): Unit = coroutineScope {
        _state.value = ProcessState.Running
        
        withContext(Dispatchers.Main) {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                startListeningLoop()
            } else {
                _state.value = ProcessState.Crashed
            }
        }
    }

    private fun startListeningLoop() {
        if (_state.value != ProcessState.Running) return
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    // Ignore errors like SPEECH_TIMEOUT and restart loop
                    if (_state.value == ProcessState.Running) {
                        restartLoop()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0].lowercase()
                        if (text.contains("halo") || text.contains("hello")) {
                            publishWakeWordEvent()
                        }
                    }
                    if (_state.value == ProcessState.Running) {
                        restartLoop()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        speechRecognizer?.startListening(intent)
        isListening = true
    }
    
    private fun restartLoop() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        startListeningLoop()
    }

    private fun publishWakeWordEvent() {
        val observation = Observation(
            id = CognitiveId(),
            created = Clock.System.now(),
            confidence = Confidence(0.9f),
            provenance = Provenance(id, null),
            edges = emptyList(),
            rawData = "WakeWordDetected",
            modality = "WakeWord"
        )
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Default) {
            perceptionBus.publish(id, observation)
        }
    }

    override suspend fun pause() {
        _state.value = ProcessState.Paused
        withContext(Dispatchers.Main) {
            speechRecognizer?.cancel()
        }
    }
    
    override suspend fun resume() {
        _state.value = ProcessState.Running
        withContext(Dispatchers.Main) {
            startListeningLoop()
        }
    }
    
    override suspend fun stop() {
        _state.value = ProcessState.Stopped
        withContext(Dispatchers.Main) {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }
}
