package com.halo.engines.speech

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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class AudioPipeline(
    private val vad: VoiceActivityDetector,
    private val wakeWordDetector: WakeWordDetector,
    private val sttProvider: SpeechRecognitionProvider,
    private val eventBus: EventBus,
    private val scope: CoroutineScope,
    private val speechActivityEstimator: SpeechActivityEstimator = EnergySpeechActivityEstimator(),
    preRollMillis: Long = SpeechCaptureProfile.Production.preRollMs,
    private val partialIntent: suspend (String)->com.halo.sdk.runtime.ProvisionalSpeechIntent? = { null },
) {
    private val preRollBuffer = AudioBuffer(maxMillis = preRollMillis)
    private var buffer = AudioBuffer(maxMillis = 120_000)
    private var inputTurn: com.halo.sdk.runtime.SpeechInputTurn? = null
    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state
    private var observerJobs: List<Job> = emptyList()
    private var liveListeningJob: Job? = null
    @Volatile
    private var bufferedRecognitionJob: Job? = null

    val usesLiveRecognition: Boolean
        get() = sttProvider.mode == SpeechRecognitionMode.LIVE

    /** True until the buffered recognition coroutine has actually returned. */
    val recognitionInFlight: Boolean
        get() = bufferedRecognitionJob?.isCompleted == false || liveListeningJob?.isCompleted == false

    suspend fun isRecognitionAvailable(): Boolean = sttProvider.health()

    suspend fun unavailableReason(): String =
        sttProvider.healthDetails().message ?: "Local speech recognition is unavailable"

    @Volatile
    var wakeWordEnabled: Boolean = false
        private set

    private var activeSpeechTransactionId: String? = null
    private var captureStartedAtMs: Long? = null

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

    fun beginCapture(turn: com.halo.sdk.runtime.SpeechInputTurn) {
        reset()
        inputTurn = turn
        onListeningStarted()
    }

    private fun onListeningStarted() {
        if (_state.value == SpeechState.Recognizing || _state.value == SpeechState.Listening) return

        activeSpeechTransactionId = inputTurn?.captureId ?: UUID.randomUUID().toString()
        val capture = requireNotNull(activeSpeechTransactionId)
        val startedAt = System.currentTimeMillis()
        captureStartedAtMs = startedAt
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            eventBus.publish(com.halo.sdk.runtime.InteractionLatencyUpdated(
                trace = com.halo.sdk.runtime.InteractionLatencyTrace(capture, startedAt)))
        }
        if (sttProvider.mode == SpeechRecognitionMode.LIVE) {
            val transactionId = requireNotNull(activeSpeechTransactionId)
            _state.value = SpeechState.Recognizing
            scope.launch { if (activeSpeechTransactionId == transactionId) eventBus.publish(RecognitionStarted(id = transactionId)) }
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
        if (existingRecognition != null && !existingRecognition.isCompleted) {
            // Recognition owns its coroutine; releasing an input source must not join blocking JNI.
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
            val transactionId = activeSpeechTransactionId ?: UUID.randomUUID().toString()
            reset()
            eventBus.publish(RecognitionFailed(id = transactionId, reason = "No audio captured"))
            return
        }

        val segment = buffer.snapshot()
        buffer.clear()
        preRollBuffer.clear()
        _state.value = SpeechState.Recognizing
        launchRecognition(segment)
    }

    fun processFrame(frame: AudioFrame) {
        if (frame.samples.isEmpty()) return
        if (inputTurn?.boundary == com.halo.sdk.runtime.CaptureBoundary.EXTERNAL) {
            observeSpeech(frame, vad.isSpeech(frame))
            if (_state.value == SpeechState.Listening) {
                check(buffer.duration() + frame.samples.size * 1000L / frame.sampleRate <= 120_000) { "Wearable capture exceeded the two-minute limit" }
                buffer.append(frame)
            }
            return
        }
        preRollBuffer.append(frame)
        val currentState = _state.value
        val speechDetected = vad.isSpeech(frame)
        observeSpeech(frame, speechDetected)

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
                if (buffer.duration() == 0L) {
                    // Explicit listening starts before VAD admits speech. Include the onset
                    // already buffered while VAD was deciding, including this frame once.
                    preRollBuffer.frames().forEach(buffer::append)
                } else {
                    buffer.append(frame)
                }
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
        if (bufferedRecognitionJob?.isCompleted == false) return
        val segment = buffer.snapshot()
        buffer.clear()
        preRollBuffer.clear()
        _state.value = SpeechState.Recognizing
        launchRecognition(segment)
    }

    private fun launchRecognition(segment: SpeechSegment) {
        val turn = inputTurn
        val capture = turn?.captureId ?: activeSpeechTransactionId
        val endpointAt = System.currentTimeMillis()
        val startedAt = captureStartedAtMs
        // Start lazily so the field is assigned before the coroutine can finish.
        // JNI decode is blocking: cancellation can mark a Job inactive before the
        // native call actually returns. Only the owning coroutine may clear this
        // reference, otherwise a second recognition can be stacked behind it.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (capture != null && startedAt != null) {
                    // This is the actual buffered capture boundary, before resource wait/load/decode.
                    // Live provider final text is not an endpoint timestamp and never takes this path.
                    eventBus.publish(com.halo.sdk.runtime.InteractionLatencyUpdated(
                        trace = com.halo.sdk.runtime.InteractionLatencyTrace(capture, startedAt,
                            speechEndedAtMs = endpointAt)))
                }
                processSegment(segment, turn)
            } finally {
                if (bufferedRecognitionJob === currentCoroutineContext()[Job]) {
                    bufferedRecognitionJob = null
                }
            }
        }
        bufferedRecognitionJob = job
        job.start()
    }

    private fun startLiveListening() {
        val provider = sttProvider as? LiveSpeechRecognitionProvider ?: return
        if (liveListeningJob?.isCompleted == false) return
        val turn = inputTurn
        val transactionId = activeSpeechTransactionId ?: return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var finalPublished = false
            var partialRevision=0
            try {
                provider.startListening().collect { event ->
                    currentCoroutineContext().ensureActive()
                    if (activeSpeechTransactionId != transactionId || finalPublished) return@collect
                    when (event) {
                        is RecognitionEvent.Ready -> _state.value = SpeechState.Listening
                        is RecognitionEvent.Listening -> _state.value = SpeechState.Recognizing
                        is RecognitionEvent.Partial -> {
                            val hint=try { kotlinx.coroutines.withTimeoutOrNull(100) { partialIntent(event.text.take(4096)) } }
                                catch(cancelled: CancellationException) { throw cancelled }
                                catch(_: Exception) { null }
                            if(activeSpeechTransactionId==transactionId && !finalPublished)
                                eventBus.publish(com.halo.sdk.runtime.SpeechProvisionalIntentUpdated(transactionId,++partialRevision,hint))
                        }
                        is RecognitionEvent.Final -> {
                            finalPublished = true
                            eventBus.publish(com.halo.sdk.runtime.SpeechProvisionalIntentUpdated(transactionId,++partialRevision,null,true))
                            _state.value = SpeechState.Thinking
                            eventBus.publish(SpeechRecognized(id = transactionId, text = event.result.text,
                                locale = event.result.language, timestamp = System.currentTimeMillis(), input = turn,
                                confidence = event.result.confidence, quality = event.result.quality, metrics = event.result.metrics))
                            eventBus.publish(RecognitionFinished(id = transactionId))
                        }
                        is RecognitionEvent.Error -> {
                            _state.value = SpeechState.Idle
                            eventBus.publish(RecognitionFailed(id = transactionId, reason = event.reason))
                        }
                        is RecognitionEvent.Stopped -> if (_state.value != SpeechState.Thinking) _state.value = SpeechState.Idle
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                if (activeSpeechTransactionId == transactionId) {
                    _state.value = SpeechState.Idle
                    eventBus.publish(RecognitionFailed(id = transactionId, reason = exception.message ?: "Live recognition failed"))
                }
            } finally {
                withContext(NonCancellable) { runCatching { provider.stopListening() } }
                if (liveListeningJob === currentCoroutineContext()[Job]) liveListeningJob = null
            }
        }
        liveListeningJob = job
        job.start()
    }
    private suspend fun processSegment(segment: SpeechSegment, turn: com.halo.sdk.runtime.SpeechInputTurn?) {
        val provider = sttProvider as? BufferedSpeechRecognitionProvider ?: return
        val transactionId = activeSpeechTransactionId ?: UUID.randomUUID().toString()

        _state.value = SpeechState.Recognizing
        eventBus.publish(RecognitionStarted(id = transactionId))

        try {
            val result = provider.recognize(segment.audioBuffer.copy(captureId = transactionId))
            val acousticQuality = withContext(NonCancellable + kotlinx.coroutines.Dispatchers.Default) {
                speechActivityEstimator.estimate(segment.audioBuffer)
            }
            val resultQuality = result.quality.copy(
                audioDurationMs = acousticQuality.durationMs,
                peakAmplitude = acousticQuality.peakAmplitude,
                voicedDurationMs = acousticQuality.voicedDurationMs,
                voicedRatio = acousticQuality.voicedRatio,
                segmentCount = acousticQuality.segmentCount,
                longestSegmentMs = acousticQuality.longestSegmentMs,
                qualityEstimator = acousticQuality.estimator.name
            )

            // Transaction ownership, not coroutine activity, decides whether a
            // completed native result is still valid. Buffered JNI decode is
            // blocking, so a coroutine can be marked cancelled while decode
            // still returns a perfectly valid transcript. Explicit user/runtime
            // cancellation calls reset(), which clears this transaction id.
            if (activeSpeechTransactionId != transactionId) return

            val text = result.text.trim()

            // Once Whisper has returned a result for the still-owned turn, make
            // the handoff atomic with respect to coroutine cancellation. This
            // prevents capture-job teardown from dropping a completed transcript
            // between native decode and EventBus publication.
            withContext(NonCancellable) {
                if (activeSpeechTransactionId != transactionId) return@withContext

                if (text.isBlank()) {
                    _state.value = SpeechState.Idle
                    eventBus.publish(com.halo.sdk.runtime.SpeechRejected(
                        text = text, reason = SpeechRejectionReason.EMPTY.name,
                        captureId = transactionId, quality = resultQuality, metrics = result.metrics, input = turn
                    ))
                    eventBus.publish(
                        RecognitionFailed(
                            id = transactionId,
                            reason = "No speech recognized"
                        )
                    )
                    return@withContext
                }

                _state.value = SpeechState.Thinking
                eventBus.publish(
                    SpeechRecognized(
                        id = transactionId,
                        text = text,
                        locale = result.language,
                        timestamp = System.currentTimeMillis(),
                        input = turn,
                        confidence = result.confidence,
                        quality = resultQuality, metrics = result.metrics
                    )
                )
                eventBus.publish(RecognitionFinished(id = transactionId))
            }
        } catch (cancellation: CancellationException) {
            // reset() is the authoritative cancellation path and clears
            // activeSpeechTransactionId. A cancellation that arrives after a
            // native result has already been handed off is safe to propagate.
            withContext(NonCancellable) {
                if (activeSpeechTransactionId == transactionId && _state.value == SpeechState.Recognizing) {
                    _state.value = SpeechState.Idle
                    eventBus.publish(RecognitionFailed(id = transactionId, reason = "Recognition cancelled"))
                }
            }
            throw cancellation
        } catch (exception: Exception) {
            if (activeSpeechTransactionId != transactionId) return
            withContext(NonCancellable) {
                if (activeSpeechTransactionId != transactionId) return@withContext
                _state.value = SpeechState.Idle
                eventBus.publish(
                    RecognitionFailed(
                        id = transactionId,
                        reason = exception.message ?: "Recognition failed"
                    )
                )
            }
        } finally {
            if (activeSpeechTransactionId == transactionId) {
                activeSpeechTransactionId = null
            }
        }
    }

    private var sustainedVoicedMs = 0L
    private var lastActivityMs = 0L
    private fun observeSpeech(frame: AudioFrame, voiced: Boolean) {
        val turn = inputTurn ?: return
        if (!voiced) { sustainedVoicedMs = 0; lastActivityMs = 0; return }
        sustainedVoicedMs += frame.samples.size * 1000L / frame.sampleRate.coerceAtLeast(1) / frame.channels.coerceAtLeast(1)
        if (sustainedVoicedMs - lastActivityMs < 100) return
        lastActivityMs = sustainedVoicedMs
        val evidence = com.halo.sdk.runtime.SpeechActivityObserved(turn.captureId,
            (turn.route as? com.halo.sdk.runtime.SpeechInputRoute.Device)?.deviceId,
            true, sustainedVoicedMs, true)
        scope.launch { eventBus.publish(evidence) }
    }

    fun reset() {
        sustainedVoicedMs = 0; lastActivityMs = 0
        buffer.clear()
        preRollBuffer.clear()
        vad.reset()
        activeSpeechTransactionId = null
        captureStartedAtMs = null
        inputTurn = null
        _state.value = SpeechState.Idle
        liveListeningJob?.cancel()
        // Do not clear the reference here. A cancelled coroutine can still be
        // blocked inside sherpa-onnx JNI decode; its finally block clears ownership
        // only after native execution has really returned.
        bufferedRecognitionJob?.cancel()
    }
}
