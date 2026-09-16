package com.halo.engines.speech

import com.halo.engines.speech.UnavailableSpeechRecognitionProvider
import com.halo.sdk.provider.BufferedSpeechRecognitionProvider
import com.halo.sdk.provider.LiveSpeechRecognitionProvider
import com.halo.sdk.provider.RecognitionEvent
import com.halo.sdk.provider.SpeechRecognitionMode
import com.halo.sdk.provider.SpeechRecognitionProvider
import com.halo.sdk.provider.WakeWordDetector
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.SpeechState
import com.halo.sdk.host.AudioFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class AudioPipeline(
    private val vad: VoiceActivityDetector,
    private val wakeWordDetector: WakeWordDetector,
    private val sttProvider: SpeechRecognitionProvider,
    private val eventBus: EventBus,
    private val scope: CoroutineScope,
    preRollMillis: Long = 400
) {
    private val preRollBuffer = AudioBuffer(maxMillis = preRollMillis)
    private var buffer = AudioBuffer()
    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state
    private var observerJobs: List<Job> = emptyList()
    private var liveListeningJob: Job? = null
    private var bufferedRecognitionJob: Job? = null

    val usesLiveRecognition: Boolean
        get() = sttProvider.mode == SpeechRecognitionMode.LIVE

    suspend fun isRecognitionAvailable(): Boolean = sttProvider.health()

    suspend fun unavailableReason(): String =
        if (sttProvider is UnavailableSpeechRecognitionProvider) {
            sttProvider.healthDetails().message ?: "Local speech recognition is unavailable"
        } else {
            "Local speech recognition is unavailable"
        }

    @Volatile
    var wakeWordEnabled: Boolean = false
        private set

    init {
        require(preRollMillis > 0) { "preRollMillis must be > 0" }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        wakeWordEnabled = enabled
        if (!enabled && _state.value == SpeechState.WakeDetected) reset()
    }

    fun start() {
        if (observerJobs.any { it.isActive }) return
        observerJobs = listOf(
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                eventBus.observe(ListeningStarted::class.java).collect { onListeningStarted() }
            },
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                eventBus.observe(SpeechEnded::class.java).collect { onSpeechEnded() }
            }
        )
    }

    fun stop() {
        observerJobs.forEach { it.cancel() }
        observerJobs = emptyList()
        reset()
    }

    private fun onListeningStarted() {
        if (_state.value == SpeechState.Recognizing) return
        _state.value = SpeechState.Recognizing
        scope.launch { eventBus.publish(RecognitionStarted(id = UUID.randomUUID().toString())) }
        if (sttProvider.mode == SpeechRecognitionMode.LIVE) startLiveListening()
    }

    private fun onSpeechEnded() {
        if (sttProvider.mode == SpeechRecognitionMode.LIVE) return
        val stateAtEnd = _state.value
        val shouldRecognize = stateAtEnd == SpeechState.WakeDetected ||
            stateAtEnd == SpeechState.Recognizing ||
            stateAtEnd == SpeechState.Listening
        if (!shouldRecognize) {
            buffer.clear()
            preRollBuffer.clear()
            _state.value = SpeechState.Idle
            return
        }
        if (buffer.duration() == 0L) return
        if (bufferedRecognitionJob?.isActive == true) return
        val segment = buffer.snapshot()
        buffer.clear()
        preRollBuffer.clear()
        _state.value = SpeechState.Recognizing
        bufferedRecognitionJob = scope.launch {
            try {
                processSegment(segment)
            } finally {
                bufferedRecognitionJob = null
            }
        }
    }

    suspend fun finishCapture() {
        if (sttProvider.mode == SpeechRecognitionMode.LIVE) {
            val provider = sttProvider as? LiveSpeechRecognitionProvider
            if (provider != null) {
                try {
                    provider.stopListening()
                } catch (_: Exception) {
                    // The engine is already stopping capture; provider cleanup
                    // errors must not prevent the runtime from returning Idle.
                }
                liveListeningJob?.join()
            }
            _state.value = SpeechState.Idle
            return
        }

        val existingRecognition = bufferedRecognitionJob
        if (existingRecognition?.isActive == true) {
            existingRecognition.join()
            return
        }

        val stateAtStop = _state.value
        if (stateAtStop != SpeechState.Listening &&
            stateAtStop != SpeechState.Recognizing &&
            stateAtStop != SpeechState.WakeDetected
        ) {
            reset()
            return
        }

        if (buffer.duration() == 0L) {
            reset()
            return
        }

        val segment = buffer.snapshot()
        buffer.clear()
        preRollBuffer.clear()
        _state.value = SpeechState.Recognizing
        processSegment(segment)
    }

    fun processFrame(frame: AudioFrame) {
        preRollBuffer.append(frame)
        val currentState = _state.value
        val speechDetected = vad.isSpeech(frame)

        if (wakeWordEnabled &&
            currentState != SpeechState.WakeDetected &&
            currentState != SpeechState.Recognizing
        ) {
            val shouldRunWakeDetector = !wakeWordDetector.requiresSpeechGate || speechDetected
            if (shouldRunWakeDetector) {
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
        }

        val stateAfterWake = _state.value
        if (speechDetected) {
            if (stateAfterWake == SpeechState.Idle) {
                _state.value = SpeechState.Listening
                buffer.clear()
                preRollBuffer.frames().forEach(buffer::append)
                scope.launch { eventBus.publish(SpeechStarted()) }
            } else if (sttProvider.mode == SpeechRecognitionMode.BUFFERED &&
                stateAfterWake != SpeechState.Recognizing
            ) {
                buffer.append(frame)
            }
        } else if (
            sttProvider.mode == SpeechRecognitionMode.BUFFERED &&
            (stateAfterWake == SpeechState.Listening ||
                stateAfterWake == SpeechState.WakeDetected ||
                stateAfterWake == SpeechState.Recognizing) &&
            buffer.duration() > 0L
        ) {
            scope.launch { eventBus.publish(SpeechEnded()) }
        }
    }

    private fun startLiveListening() {
        val provider = sttProvider as? LiveSpeechRecognitionProvider ?: return
        if (liveListeningJob?.isActive == true) return
        liveListeningJob = scope.launch {
            try {
                provider.startListening().collect { event ->
                    when (event) {
                        is RecognitionEvent.Ready -> _state.value = SpeechState.Listening
                        is RecognitionEvent.Listening -> _state.value = SpeechState.Recognizing
                        is RecognitionEvent.Partial -> Unit
                        is RecognitionEvent.Final -> {
                            _state.value = SpeechState.Thinking
                            eventBus.publish(SpeechRecognized(id = UUID.randomUUID().toString(), text = event.result.text))
                            eventBus.publish(RecognitionFinished(id = UUID.randomUUID().toString()))
                        }
                        is RecognitionEvent.Error -> {
                            _state.value = SpeechState.Idle
                            eventBus.publish(RecognitionFailed(reason = event.reason))
                        }
                        is RecognitionEvent.Stopped -> {
                            if (_state.value != SpeechState.Thinking) _state.value = SpeechState.Idle
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                if (_state.value != SpeechState.Idle && _state.value != SpeechState.Thinking) _state.value = SpeechState.Idle
                throw cancellation
            } catch (exception: Exception) {
                eventBus.publish(RecognitionFailed(reason = exception.message ?: "Live recognition failed"))
                _state.value = SpeechState.Idle
            } finally {
                runCatching { provider.stopListening() }
                liveListeningJob = null
            }
        }
    }

    private suspend fun processSegment(segment: SpeechSegment) {
        val provider = sttProvider as? BufferedSpeechRecognitionProvider ?: return
        try {
            val result = provider.recognize(segment.audioBuffer)
            if (!currentCoroutineContext().isActive) return
            _state.value = SpeechState.Thinking
            eventBus.publish(SpeechRecognized(id = UUID.randomUUID().toString(), text = result.text))
            eventBus.publish(RecognitionFinished(id = UUID.randomUUID().toString()))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            if (!currentCoroutineContext().isActive) return
            _state.value = SpeechState.Idle
            eventBus.publish(RecognitionFailed(reason = exception.message ?: "Recognition failed"))
        }
    }

    fun reset() {
        buffer.clear()
        preRollBuffer.clear()
        vad.reset()
        _state.value = SpeechState.Idle
        liveListeningJob?.cancel()
        liveListeningJob = null
        bufferedRecognitionJob?.cancel()
        bufferedRecognitionJob = null
    }
}
