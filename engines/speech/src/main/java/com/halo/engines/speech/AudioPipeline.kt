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
import com.halo.sdk.runtime.SpeechRecognized
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

    private var activeSpeechTransactionId: String? = null

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
        if (_state.value == SpeechState.Recognizing || _state.value == SpeechState.Listening) return

        activeSpeechTransactionId = UUID.randomUUID().toString()
        if (sttProvider.mode == SpeechRecognitionMode.LIVE) {
            _state.value = SpeechState.Recognizing
            scope.launch { eventBus.publish(RecognitionStarted(id = activeSpeechTransactionId!!)) }
            startLiveListening()
        } else {
            _state.value = SpeechState.Listening
            buffer.clear()
            preRollBuffer.clear()
        }
    }

    private fun onSpeechEnded() {
        if (sttProvider.mode == SpeechRecognitionMode.LIVE) return
        // No longer processing SpeechEnded as a driver for recognition since
        // finishCaptureBuffered acts as the explicit state transition.
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
            } else if (stateAfterWake == SpeechState.Listening || stateAfterWake == SpeechState.WakeDetected) {
                buffer.append(frame)
            }
        } else if (
            sttProvider.mode == SpeechRecognitionMode.BUFFERED &&
            stateAfterWake == SpeechState.Listening &&
            buffer.duration() > 0L
        ) {
            _state.value = SpeechState.Recognizing

            // Snapshot and hand the utterance to recognition before notifying
            // the capture owner that physical microphone input can be released.
            finishCaptureBuffered()

            scope.launch {
                eventBus.publish(SpeechEnded())
            }
        }
    }

    private fun finishCaptureBuffered() {
        if (buffer.duration() == 0L) {
            reset()
            return
        }
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
                            eventBus.publish(
                                SpeechRecognized(
                                    id = UUID.randomUUID().toString(),
                                    text = event.result.text,
                                    locale = "en",
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                            eventBus.publish(RecognitionFinished(id = UUID.randomUUID().toString()))
                        }
                        is RecognitionEvent.Error -> {
                            _state.value = SpeechState.Idle
                            eventBus.publish(RecognitionFailed(id = UUID.randomUUID().toString(), reason = event.reason))
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
                eventBus.publish(RecognitionFailed(id = UUID.randomUUID().toString(), reason = exception.message ?: "Live recognition failed"))
                _state.value = SpeechState.Idle
            } finally {
                runCatching { provider.stopListening() }
                liveListeningJob = null
            }
        }
    }

    private suspend fun processSegment(segment: SpeechSegment) {
        val provider = sttProvider as? BufferedSpeechRecognitionProvider ?: return
        val transactionId = activeSpeechTransactionId ?: UUID.randomUUID().toString()

        _state.value = SpeechState.Recognizing
        eventBus.publish(RecognitionStarted(id = transactionId))

        try {
            val result = provider.recognize(segment.audioBuffer)
            if (!currentCoroutineContext().isActive) return

            val text = result.text.trim()
            if (text.isBlank()) {
                _state.value = SpeechState.Idle
                eventBus.publish(RecognitionFinished(id = transactionId))
                return
            }

            _state.value = SpeechState.Thinking
            eventBus.publish(
                SpeechRecognized(
                    id = transactionId,
                    text = text,
                    locale = "en",
                    timestamp = System.currentTimeMillis()
                )
            )
            eventBus.publish(RecognitionFinished(id = transactionId))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            if (!currentCoroutineContext().isActive) return
            _state.value = SpeechState.Idle
            eventBus.publish(RecognitionFailed(id = transactionId, reason = exception.message ?: "Recognition failed"))
        } finally {
            activeSpeechTransactionId = null
        }
    }

    fun reset() {
        buffer.clear()
        preRollBuffer.clear()
        vad.reset()
        activeSpeechTransactionId = null
        _state.value = SpeechState.Idle
        liveListeningJob?.cancel()
        liveListeningJob = null
        bufferedRecognitionJob?.cancel()
        bufferedRecognitionJob = null
    }
}
