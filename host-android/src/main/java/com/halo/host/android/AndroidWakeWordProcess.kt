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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.media.AudioManager
import com.halo.sdk.runtime.EngineHealth
import java.util.concurrent.atomic.AtomicLong

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

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var beepRestoreJob: Job? = null
    private val sessionGeneration = AtomicLong(0L)

    override fun engineSnapshot(): com.halo.sdk.runtime.EngineSnapshot = com.halo.sdk.runtime.EngineSnapshot(
        id = id,
        state = if (_state.value == ProcessState.Running) com.halo.sdk.runtime.EngineState.Running else com.halo.sdk.runtime.EngineState.Created,
        health = if (_state.value == ProcessState.Crashed) EngineHealth.Failed else EngineHealth.Healthy,
        metrics = _metrics.value,
        lastActivity = java.time.Instant.now()
    )

    override suspend fun start(): Unit {
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
        if (_state.value != ProcessState.Running || isListening) return

        val generation = sessionGeneration.incrementAndGet()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        speechRecognizer = recognizer
        isListening = true

        recognizer.setRecognitionListener(object : RecognitionListener {
            private fun current(): Boolean = sessionGeneration.get() == generation && recognizer === speechRecognizer

            override fun onReadyForSpeech(params: Bundle?) {
                if (!current()) return
                beepRestoreJob?.cancel()
                muteBeep()
                beepRestoreJob = scope.launch(Dispatchers.Main) {
                    delay(400L)
                    if (current()) unmuteBeep()
                }
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (current()) {
                    isListening = false
                    scheduleRestart(generation)
                }
            }

            override fun onError(error: Int) {
                if (!current()) return
                isListening = false
                unmuteBeep()
                scheduleRestart(generation, error)
            }

            override fun onResults(results: Bundle?) {
                if (!current()) return
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()?.lowercase().orEmpty()
                if (text.contains("halo") || text.contains("hello")) {
                    publishWakeWordEvent(text)
                }
                scheduleRestart(generation)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        muteBeep()
        try {
            recognizer.startListening(intent)
        } catch (error: RuntimeException) {
            isListening = false
            unmuteBeep()
            recognizer.destroy()
            if (currentProcessGeneration(generation)) speechRecognizer = null
            if (_state.value == ProcessState.Running) _state.value = ProcessState.Crashed
        }
    }

    private var consecutiveErrors = 0

    private fun scheduleRestart(generation: Long, error: Int? = null) {
        if (!currentProcessGeneration(generation) || _state.value != ProcessState.Running) return
        
        val isNormalTimeout = error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH
        if (error != null && !isNormalTimeout) {
            consecutiveErrors++
        } else {
            consecutiveErrors = 0
        }
        
        val backoffDelay = if (consecutiveErrors > 0) {
            minOf(30000L, 1000L * (1L shl minOf(consecutiveErrors, 5)))
        } else {
            150L
        }

        scope.launch(Dispatchers.Main) {
            delay(backoffDelay)
            if (!currentProcessGeneration(generation) || isListening || _state.value != ProcessState.Running) return@launch
            speechRecognizer?.destroy()
            speechRecognizer = null
            startListeningLoop()
        }
    }

    private fun currentProcessGeneration(generation: Long): Boolean = sessionGeneration.get() == generation

    private fun muteBeep() {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
            am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
        }
    }

    private fun unmuteBeep() {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
            am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        }
    }

    private fun publishWakeWordEvent(word: String) {
        scope.launch {
            eventBus.publish(com.halo.engines.speech.RawWakeWordDetected(word = word))
        }
    }

    override suspend fun pause() {
        _state.value = ProcessState.Paused
        sessionGeneration.incrementAndGet()
        withContext(Dispatchers.Main) {
            beepRestoreJob?.cancel()
            isListening = false
            speechRecognizer?.cancel()
        }
    }

    override suspend fun resume() {
        _state.value = ProcessState.Running
        withContext(Dispatchers.Main) {
            isListening = false
            speechRecognizer?.destroy()
            speechRecognizer = null
            startListeningLoop()
        }
    }

    override suspend fun stop() {
        _state.value = ProcessState.Stopped
        sessionGeneration.incrementAndGet()
        withContext(Dispatchers.Main) {
            beepRestoreJob?.cancel()
            isListening = false
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
            unmuteBeep()
        }
    }
}
