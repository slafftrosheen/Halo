package com.halo.engines.speech

import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.sdk.runtime.*
import com.halo.runtime.services.Services
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AudioPerceptionEngine(
    private val audioSource: AudioSource,
    private val vad: VoiceActivityDetector,
    private val preprocessor: AudioPreprocessor,
    private val recognizer: SpeechRecognizer,
    private val recognitionTimeoutMillis: Long = DEFAULT_RECOGNITION_TIMEOUT_MILLIS
) : Engine {
    companion object {
        private const val DEFAULT_RECOGNITION_TIMEOUT_MILLIS = 30_000L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }

    init {
        require(recognitionTimeoutMillis > 0) {
            "recognitionTimeoutMillis must be greater than zero"
        }
    }

    override val metadata = EngineMetadata(
        id = Identifier("engine.perception.audio"),
        version = Version(1, 0, 0),
        description = "Audio Perception Engine"
    )

    private val _health = MutableStateFlow(EngineHealth.Healthy)
    override val health: StateFlow<EngineHealth> = _health

    private val _state = MutableStateFlow<EngineState>(EngineState.Created)
    override val state: StateFlow<EngineState> = _state

    private val _speechState = MutableStateFlow<SpeechState>(Idle)
    val speechState: StateFlow<SpeechState> = _speechState

    override val dependencies: Set<ServiceKey<*>> = setOf(Services.EventBus)

    private lateinit var context: EngineContext
    private lateinit var scope: CoroutineScope
    private val lifecycleMutex = Mutex()
    private val sourceStartAttempted = AtomicBoolean(false)
    private val queuedSegmentCount = AtomicInteger()
    private var captureJob: Job? = null
    private var recognitionJob: Job? = null
    private var recognitionQueue: Channel<SpeechSegment>? = null
    private val buffer = AudioBuffer()

    override suspend fun initialize(context: EngineContext) {
        lifecycleMutex.withLock {
            this.context = context
            scope = CoroutineScope(context.runtime.scheduler.io + SupervisorJob())
            _state.value = EngineState.Initializing
        }
    }

    override suspend fun start() {
        lifecycleMutex.withLock {
            check(::scope.isInitialized) {
                "AudioPerceptionEngine must be initialized before it starts"
            }
            check(_state.value != EngineState.Disposed) {
                "AudioPerceptionEngine cannot be started after it is disposed"
            }
            if (captureJob?.isActive == true) {
                return@withLock
            }

            _state.value = EngineState.Running
            _speechState.value = Listening

            val eventBus = context.registry.resolve(Services.EventBus)
            val queue = recognitionQueue
                ?.takeIf { recognitionJob?.isActive == true }
                ?: Channel<SpeechSegment>(Channel.UNLIMITED).also { newQueue ->
                    recognitionQueue = newQueue
                    queuedSegmentCount.set(0)
                    recognitionJob = scope.launch {
                        runRecognitionWorker(eventBus, newQueue)
                    }
                }

            if (recognitionJob?.isActive != true) {
                recognitionJob = scope.launch {
                    runRecognitionWorker(eventBus, queue)
                }
            }
            captureJob = scope.launch {
                runCaptureLoop(eventBus, queue)
            }
        }
    }

    override suspend fun stop() {
        withContext(NonCancellable) {
            lifecycleMutex.withLock {
                if (_state.value != EngineState.Disposed) {
                    stopLocked()
                }
            }
        }
    }

    override suspend fun dispose() {
        withContext(NonCancellable) {
            lifecycleMutex.withLock {
                if (_state.value == EngineState.Disposed) {
                    return@withLock
                }

                stopLocked()
                if (::scope.isInitialized) {
                    scope.cancel()
                }
                _state.value = EngineState.Disposed
            }
        }
    }

    private suspend fun runCaptureLoop(
        eventBus: EventBus,
        queue: Channel<SpeechSegment>
    ) {
        try {
            try {
                sourceStartAttempted.set(true)
                audioSource.start()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                reportCaptureFailure(
                    eventBus = eventBus,
                    reason = "Unable to start microphone: ${
                        exception.message ?: "Audio initialization failed"
                    }",
                    throwable = exception
                )
                return
            }

            _health.value = EngineHealth.Healthy
            eventBus.publish(ListeningStarted())

            var isRecording = false
            audioSource.frames.collect { rawFrame ->
                val frame = preprocessor.process(rawFrame)

                if (vad.isSpeech(frame)) {
                    if (!isRecording) {
                        isRecording = true
                        _speechState.value = Recording
                        eventBus.publish(SpeechStarted())
                    }
                    buffer.append(frame)
                } else if (isRecording) {
                    isRecording = false
                    eventBus.publish(SpeechEnded())

                    val segment = buffer.snapshot()
                    try {
                        enqueueSegment(queue, segment)
                    } finally {
                        buffer.clear()
                        _speechState.value = Listening
                    }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            reportCaptureFailure(
                eventBus = eventBus,
                reason = "Audio capture failed: ${exception.message ?: "Unknown error"}",
                throwable = exception
            )
        } finally {
            withContext(NonCancellable) {
                stopAudioSource()
                buffer.clear()
                if (_speechState.value !is Error) {
                    _speechState.value = Idle
                }
            }
        }
    }

    private suspend fun enqueueSegment(
        queue: Channel<SpeechSegment>,
        segment: SpeechSegment
    ) {
        val queueSize = queuedSegmentCount.incrementAndGet()
        try {
            queue.send(segment)
        } catch (exception: Exception) {
            queuedSegmentCount.decrementAndGet()
            throw exception
        }

        context.runtime.logger.info(
            category = "speech",
            message = "Captured segment durationMs=${segment.durationMillis} " +
                "pcmSamples=${segment.sampleCount} queueSize=$queueSize"
        )
    }

    private suspend fun runRecognitionWorker(
        eventBus: EventBus,
        queue: ReceiveChannel<SpeechSegment>
    ) {
        while (currentCoroutineContext().isActive) {
            val segment = try {
                queue.receiveCatching().getOrNull() ?: return
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                reportRecognitionFailure(
                    eventBus = eventBus,
                    reason = "Recognition worker failed: ${exception.message ?: "Unknown error"}",
                    throwable = exception
                )
                continue
            }

            queuedSegmentCount.updateAndGet { count -> (count - 1).coerceAtLeast(0) }
            try {
                recognize(segment, eventBus)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                reportRecognitionFailure(
                    eventBus = eventBus,
                    reason = "Recognition worker failed: ${exception.message ?: "Unknown error"}",
                    throwable = exception
                )
            }
        }
    }

    private suspend fun recognize(segment: SpeechSegment, eventBus: EventBus) {
        val startedAt = System.nanoTime()
        val provider = recognizer.javaClass.simpleName.ifBlank {
            recognizer.javaClass.name
        }
        context.runtime.logger.info(
            category = "speech",
            message = "Recognition started provider=$provider durationMs=${segment.durationMillis} " +
                "pcmSamples=${segment.sampleCount} queueSize=${queuedSegmentCount.get()}"
        )
        eventBus.publish(RecognitionStarted())
        try {
            val result = withTimeout(recognitionTimeoutMillis) {
                recognizer.transcribe(segment)
            }
            eventBus.publish(SpeechRecognized(result.text))
            eventBus.publish(
                ObservationCreated(
                    Observation(
                        modality = Modality.AUDIO,
                        source = Source.MICROPHONE,
                        payload = SpeechRecognizedPayload(result.text, result.language),
                        confidence = result.confidence,
                        metadata = mapOf("engine" to "AudioPerceptionEngine")
                    )
                )
            )
            eventBus.publish(RecognitionFinished())
            _health.value = EngineHealth.Healthy
            val latencyMillis = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND
            context.runtime.logger.info(
                category = "speech",
                message = "Recognition finished provider=$provider latencyMs=$latencyMillis " +
                    "confidence=${result.confidence} characters=${result.text.length}"
            )
        } catch (exception: TimeoutCancellationException) {
            eventBus.publish(
                RecognitionTimeoutEvent(
                    timeoutMillis = recognitionTimeoutMillis,
                    segmentStartedAt = segment.startedAt,
                    segmentFinishedAt = segment.finishedAt
                )
            )
            reportRecognitionFailure(
                eventBus = eventBus,
                reason = "Speech recognition timed out after ${recognitionTimeoutMillis}ms",
                throwable = exception
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            reportRecognitionFailure(
                eventBus = eventBus,
                reason = "Speech recognition failed: ${exception.message ?: "Unknown error"}",
                throwable = exception
            )
        }
    }

    private suspend fun reportCaptureFailure(
        eventBus: EventBus,
        reason: String,
        throwable: Throwable
    ) {
        _health.value = EngineHealth.Degraded
        _speechState.value = Error(reason)
        context.runtime.logger.error(
            category = "speech",
            message = reason,
            throwable = throwable
        )
        publishFailure(eventBus, reason)
    }

    private suspend fun reportRecognitionFailure(
        eventBus: EventBus,
        reason: String,
        throwable: Throwable
    ) {
        _health.value = EngineHealth.Degraded
        context.runtime.logger.error(
            category = "speech",
            message = reason,
            throwable = throwable
        )
        publishFailure(eventBus, reason)
    }

    private suspend fun publishFailure(eventBus: EventBus, reason: String) {
        try {
            eventBus.publish(RecognitionFailed(reason))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            context.runtime.logger.error(
                category = "speech",
                message = "Failed to publish speech failure",
                throwable = exception
            )
        }
    }

    private suspend fun stopLocked() {
        val activeCaptureJob = captureJob
        val activeRecognitionJob = recognitionJob
        val queue = recognitionQueue
        captureJob = null
        recognitionJob = null
        recognitionQueue = null

        activeCaptureJob?.cancel()
        withContext(NonCancellable) {
            // Releasing AudioRecord unblocks a capture read so the job can reach its cleanup.
            stopAudioSource()
        }
        activeCaptureJob?.join()

        queue?.close()
        activeRecognitionJob?.cancel()
        activeRecognitionJob?.join()

        queuedSegmentCount.set(0)
        buffer.clear()
        _state.value = EngineState.Stopped
        if (_speechState.value !is Error) {
            _speechState.value = Idle
        }
    }

    private suspend fun stopAudioSource() {
        if (!sourceStartAttempted.compareAndSet(true, false)) {
            return
        }

        try {
            audioSource.stop()
        } catch (exception: Exception) {
            _health.value = EngineHealth.Degraded
            context.runtime.logger.error(
                category = "speech",
                message = "Failed to stop microphone capture",
                throwable = exception
            )
        }
    }

}
