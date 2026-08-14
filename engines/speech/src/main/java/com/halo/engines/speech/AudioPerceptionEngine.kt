package com.halo.engines.speech

import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.sdk.runtime.*
import com.halo.runtime.services.Services
import com.halo.sdk.host.AudioSourceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class EngineMetrics(
    var segmentsCaptured: Int = 0,
    var framesProcessed: Int = 0,
    var droppedFrames: Int = 0,
    var activeCaptureTimeMillis: Long = 0L
)

class AudioPerceptionEngine(
    private val audioSource: com.halo.sdk.host.AudioSource,
    private val preprocessor: AudioPreprocessor,
    private val audioPipeline: AudioPipeline,
    private val eventBus: com.halo.sdk.runtime.EventBus
) : Engine {

    override val metadata = EngineMetadata(
        id = Identifier("engine.perception.audio"),
        version = Version(1, 0, 0),
        description = "Audio Capture Engine"
    )

    private val _health = MutableStateFlow(EngineHealth.Healthy)
    override val health: StateFlow<EngineHealth> = _health

    private val _state = MutableStateFlow<EngineState>(EngineState.Created)
    override val state: StateFlow<EngineState> = _state

    override val dependencies: Set<ServiceKey<*>> = emptySet()

    private lateinit var context: EngineContext
    private lateinit var scope: CoroutineScope
    private val lifecycleMutex = Mutex()
    private val sourceStartAttempted = AtomicBoolean(false)
    private var captureJob: Job? = null
    
    val metrics = EngineMetrics()

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

            captureJob = scope.launch {
                runCaptureLoop(eventBus)
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

    private suspend fun runCaptureLoop(eventBus: EventBus) {
        try {
            try {
                sourceStartAttempted.set(true)
                audioSource.start()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                reportCaptureFailure(
                    eventBus = eventBus,
                    reason = "Unable to start audio source: ${
                        exception.message ?: "Audio initialization failed"
                    }",
                    throwable = exception
                )
                return
            }

            _health.value = EngineHealth.Healthy
            audioPipeline.reset()
            
            // Bridge source lifecycle to event bus
            scope.launch {
                audioSource.state.collect { sourceState ->
                    when (sourceState) {
                        AudioSourceState.ACTIVE -> {
                            val obs = Observation(
                                modality = Modality.AUDIO,
                                source = Source.MICROPHONE,
                                payload = AudioObservationPayload(energy = null, isSpeechActive = true)
                            )
                            eventBus.publish(ObservationUpdated(observation = obs))
                            eventBus.publish(ListeningStarted())
                        }
                        AudioSourceState.IDLE -> {
                            val obs = Observation(
                                modality = Modality.AUDIO,
                                source = Source.MICROPHONE,
                                payload = AudioObservationPayload(energy = null, isSpeechActive = false)
                            )
                            eventBus.publish(ObservationUpdated(observation = obs))
                            // When source goes idle, we stop pipeline and signal end of capture
                            eventBus.publish(SpeechEnded())
                            // Transition to thinking/processing
                            eventBus.publish(RecognitionFinished())
                        }
                        AudioSourceState.ERROR -> {
                            reportCaptureFailure(eventBus, "Audio source reported error", RuntimeException("Source Error"))
                        }
                    }
                }
            }
            
            audioSource.frames.collect { rawFrame ->
                metrics.framesProcessed++
                val frame = preprocessor.process(rawFrame)
                audioPipeline.processFrame(frame)
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
                audioPipeline.reset()
            }
        }
    }



    private suspend fun reportCaptureFailure(
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
            eventBus.publish(RecognitionFailed(reason = reason))
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
        captureJob = null

        activeCaptureJob?.cancel()
        withContext(NonCancellable) {
            stopAudioSource()
        }
        activeCaptureJob?.join()

        _state.value = EngineState.Stopped
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
