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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class EngineMetrics(
    var segmentsCaptured: Int = 0,
    var framesProcessed: Int = 0,
    var droppedFrames: Int = 0,
    var activeCaptureTimeMillis: Long = 0L
)

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
    private val transactionMutex = Mutex()
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
            check(::scope.isInitialized) { "AudioPerceptionEngine must be initialized before it starts" }
            check(_state.value != EngineState.Disposed) { "AudioPerceptionEngine cannot be started after it is disposed" }
            if (commandJob?.isActive == true) return@withLock
            _state.value = EngineState.Running
            _health.value = EngineHealth.Healthy
            audioPipeline.start()
            commandJob = scope.launch {
                launch { commandBus.commands().filterIsInstance<StartListening>().collect { startCapture() } }
                launch { commandBus.commands().filterIsInstance<StopListening>().collect { stopCapture() } }
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
                runCatching { audioSource.close() }
                    .onFailure {
                        if (::context.isInitialized) {
                            context.runtime.logger.error(
                                category = "speech",
                                message = "Failed to close microphone source",
                                throwable = it
                            )
                        }
                    }
                if (::scope.isInitialized) scope.cancel()
                _state.value = EngineState.Disposed
            }
        }
    }

    private suspend fun startCapture() {
        transactionMutex.withLock {
            if (captureJob?.isActive == true) return@withLock
            if (!audioPipeline.isRecognitionAvailable()) {
                reportCaptureFailure(audioPipeline.unavailableReason())
                return@withLock
            }
            if (audioPipeline.usesLiveRecognition) {
                audioPipeline.reset()
                _health.value = EngineHealth.Healthy
                eventBus.publish(ListeningStarted())
                return@withLock
            }

            val job = scope.launch {
                try {
                    currentCoroutineContext().ensureActive()
                    audioSource.start()
                    currentCoroutineContext().ensureActive()
                    audioPipeline.reset()
                    _health.value = EngineHealth.Healthy
                    eventBus.publish(ListeningStarted())
                    audioSource.frames.collect { rawFrame ->
                        currentCoroutineContext().ensureActive()
                        metrics.framesProcessed++
                        audioPipeline.processFrame(preprocessor.process(rawFrame))
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
                }
            }
            captureJob = job
        }
    }

    private suspend fun stopCapture() {
        transactionMutex.withLock {
            if (audioPipeline.usesLiveRecognition) {
                audioPipeline.reset()
                return@withLock
            }
            val job = captureJob
            if (job != null) {
                job.cancel()
                withContext(NonCancellable) { stopAudioSource() }
                job.join()
                if (captureJob === job) captureJob = null
            }
            audioPipeline.reset()
        }
    }

    private suspend fun reportCaptureFailure(reason: String, throwable: Throwable? = null) {
        _health.value = EngineHealth.Degraded
        if (::context.isInitialized) {
            context.runtime.logger.error(category = "speech", message = reason, throwable = throwable)
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
