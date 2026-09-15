package com.halo.engines.speech

import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.sdk.runtime.*
import com.halo.sdk.host.AudioSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
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

/**
 * Owns the explicit speech-session lifecycle.
 *
 * The engine itself is passive at runtime startup. A StartListening command
 * begins one user-requested session and StopListening ends it.
 *
 * For a live STT provider, the provider owns microphone capture. For a
 * buffered provider, this engine owns AudioSource capture and feeds frames
 * through the local VAD/pipeline.
 */
class AudioPerceptionEngine(
    private val audioSource: AudioSource,
    private val preprocessor: AudioPreprocessor,
    private val audioPipeline: AudioPipeline,
    private val eventBus: EventBus,
    private val commandBus: CommandBus
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
    private var commandJob: Job? = null
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
            if (commandJob?.isActive == true) return@withLock

            _state.value = EngineState.Running
            _health.value = EngineHealth.Healthy
            audioPipeline.start()

            commandJob = scope.launch {
                launch {
                    commandBus.commands().filterIsInstance<StartListening>().collect {
                        startCapture()
                    }
                }
                launch {
                    commandBus.commands().filterIsInstance<StopListening>().collect {
                        stopCapture()
                    }
                }
            }
        }
    }

    override suspend fun stop() {
        withContext(NonCancellable) {
            lifecycleMutex.withLock {
                if (_state.value != EngineState.Disposed) stopLocked()
            }
        }
    }

    override suspend fun dispose() {
        withContext(NonCancellable) {
            lifecycleMutex.withLock {
                if (_state.value == EngineState.Disposed) return@withLock
                stopLocked()
                if (::scope.isInitialized) scope.cancel()
                _state.value = EngineState.Disposed
            }
        }
    }

    private suspend fun startCapture() {
        if (captureJob?.isActive == true) return

        // Do not touch the microphone unless the selected STT provider is usable.
        // In particular, the unavailable local provider must fail before AudioRecord
        // starts so tapping the orb never creates a fake listening session.
        if (!audioPipeline.isRecognitionAvailable()) {
            reportCaptureFailure(audioPipeline.unavailableReason())
            return
        }

        // Live providers own microphone capture themselves. Starting AudioRecord
        // here would create two competing microphone clients and can produce OEM
        // audio feedback.
        if (audioPipeline.usesLiveRecognition) {
            audioPipeline.reset()
            _health.value = EngineHealth.Healthy
            eventBus.publish(ListeningStarted())
            return
        }

        captureJob = scope.launch {
            try {
                sourceStartAttempted.set(true)
                try {
                    audioSource.start()
                } catch (exception: CancellationException) {
                    sourceStartAttempted.set(false)
                    throw exception
                } catch (exception: Exception) {
                    sourceStartAttempted.set(false)
                    reportCaptureFailure(
                        "Unable to start audio source: ${exception.message ?: "Audio initialization failed"}",
                        exception
                    )
                    return@launch
                }

                audioPipeline.reset()
                _health.value = EngineHealth.Healthy
                eventBus.publish(ListeningStarted())

                audioSource.frames.collect { rawFrame ->
                    metrics.framesProcessed++
                    val frame = preprocessor.process(rawFrame)
                    audioPipeline.processFrame(frame)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                reportCaptureFailure(
                    "Audio capture failed: ${exception.message ?: "Unknown error"}",
                    exception
                )
            } finally {
                withContext(NonCancellable) {
                    stopAudioSource()
                    audioPipeline.reset()
                }
                captureJob = null
            }
        }
    }

    private suspend fun stopCapture() {
        if (audioPipeline.usesLiveRecognition) {
            audioPipeline.reset()
            return
        }

        val job = captureJob ?: return
        captureJob = null
        job.cancel()
        withContext(NonCancellable) { stopAudioSource() }
        job.join()
        audioPipeline.reset()
    }

    private suspend fun reportCaptureFailure(reason: String, throwable: Throwable? = null) {
        _health.value = EngineHealth.Degraded
        if (::context.isInitialized) {
            context.runtime.logger.error(
                category = "speech",
                message = reason,
                throwable = throwable
            )
        }
        publishFailure(reason)
    }

    private suspend fun publishFailure(reason: String) {
        try {
            eventBus.publish(RecognitionFailed(reason = reason))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (::context.isInitialized) {
                context.runtime.logger.error(
                    category = "speech",
                    message = "Failed to publish speech failure",
                    throwable = exception
                )
            }
        }
    }

    private suspend fun stopLocked() {
        val activeCommandJob = commandJob
        commandJob = null
        activeCommandJob?.cancel()

        withContext(NonCancellable) { stopCapture() }
        activeCommandJob?.join()
        audioPipeline.stop()
        _state.value = EngineState.Stopped
    }

    private suspend fun stopAudioSource() {
        if (!sourceStartAttempted.compareAndSet(true, false)) return
        try {
            audioSource.stop()
        } catch (exception: Exception) {
            _health.value = EngineHealth.Degraded
            if (::context.isInitialized) {
                context.runtime.logger.error(
                    category = "speech",
                    message = "Failed to stop microphone capture",
                    throwable = exception
                )
            }
        }
    }
}
