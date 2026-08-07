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
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import com.halo.sdk.runtime.EngineMetrics
import com.halo.sdk.runtime.EngineSnapshot

class AndroidWakeWordProcess(
    private val context: Context,
    private val perceptionBus: PerceptionBus,
    private val eventBus: com.halo.sdk.runtime.EventBus,
    override val id: String,
    override val name: String,
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
                override fun onReadyForSpeech(params: Bundle?) {
                    // Delay unmuting to ensure the system beep is fully swallowed
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
                        kotlinx.coroutines.delay(400)
                        unmuteBeep()
                    }
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    unmuteBeep()
                    // Ignore errors like SPEECH_TIMEOUT and DO NOT restart loop
                    // if (_state.value == ProcessState.Running) {
                    //    restartLoop()
                    // }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0].lowercase()
                        if (text.contains("halo") || text.contains("hello")) {
                            publishWakeWordEvent(text)
                        }
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
        
        muteBeep()
        speechRecognizer?.startListening(intent)
        isListening = true
    }
    
    private fun muteBeep() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
            am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
        } catch (e: Exception) {
            // Ignore if permission denied
        }
    }

    private fun unmuteBeep() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
            am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        } catch (e: Exception) {
            // Ignore if permission denied
        }
    }
    
    private fun restartLoop() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        startListeningLoop()
    }

    private fun publishWakeWordEvent(word: String) {
        scope.launch {
            eventBus.publish(com.halo.engines.speech.RawWakeWordDetected(word = word))
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
