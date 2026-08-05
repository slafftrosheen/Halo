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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

class AudioPerceptionEngine(
    private val audioSource: AudioSource,
    private val vad: VoiceActivityDetector,
    private val preprocessor: AudioPreprocessor,
    private val recognizer: SpeechRecognizer,
    private val recognitionTimeoutMillis: Long = DEFAULT_RECOGNITION_TIMEOUT_MILLIS
) : Engine {
    companion object {
        private const val DEFAULT_RECOGNITION_TIMEOUT_MILLIS = 30_000L
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
    private var job: Job? = null
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
            if (job?.isActive == true) {
                return@withLock
            }

            _state.value = EngineState.Running
            _speechState.value = Listening

            val eventBus = context.registry.resolve(Services.EventBus)
            job = scope.launch {
                runPerceptionLoop(eventBus)
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

    private suspend fun runPerceptionLoop(eventBus: EventBus) {
        try {
            try {
                sourceStartAttempted.set(true)
                audioSource.start()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                reportFailure(
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
                    _speechState.value = Transcribing
                    eventBus.publish(SpeechEnded())

                    val segment = buffer.snapshot()
                    buffer.clear()
                    recognize(segment, eventBus)
                    _speechState.value = Listening
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            reportFailure(
                eventBus = eventBus,
                reason = "Audio perception failed: ${exception.message ?: "Unknown error"}",
                throwable = exception
            )
        } finally {
            withContext(NonCancellable) {
                stopAudioSource()
            }
            buffer.clear()
            if (_speechState.value !is Error) {
                _speechState.value = Idle
            }
        }
    }

    private suspend fun recognize(segment: AudioBuffer, eventBus: EventBus) {
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
        } catch (exception: TimeoutCancellationException) {
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

    private suspend fun reportFailure(
        eventBus: EventBus,
        reason: String,
        throwable: Throwable
    ) {
        _health.value = EngineHealth.Degraded
        _speechState.value = Error(reason)
        reportRecognitionFailure(eventBus, reason, throwable)
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
        eventBus.publish(RecognitionFailed(reason))
    }

    private suspend fun stopLocked() {
        val activeJob = job
        job = null

        activeJob?.cancel()
        stopAudioSource()
        activeJob?.join()

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
