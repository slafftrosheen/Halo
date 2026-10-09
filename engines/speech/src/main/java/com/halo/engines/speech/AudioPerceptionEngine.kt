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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

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
    private val commandBus: CommandBus,
    private val originatingConversation: suspend () -> String? = { null }
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
    @Volatile private var diagnosticJob: Job? = null
    private var lastLevelNanos = 0L
    private var selectedSource: AudioSource = audioSource
    private val sourceStarted = java.util.concurrent.atomic.AtomicBoolean(false)
    private val _inputStatus = MutableStateFlow(SpeechInputStatus())
    val inputStatus: StateFlow<SpeechInputStatus> = _inputStatus

    /** Reserve the speech transaction during diagnostic native work, including cleanup. */
    suspend fun <T> withDiagnosticRecognitionOwnership(block: suspend () -> T): T {
        check(transactionMutex.tryLock()) { "Speech transaction is busy" }
        var ownsTransaction = false
        try {
            check(_state.value == EngineState.Running) { "Audio engine is not running" }
            check(captureJob?.isCompleted != false && !audioPipeline.recognitionInFlight) { "Normal speech transaction is busy" }
            ownsTransaction = true
            diagnosticJob = currentCoroutineContext()[Job]
            return block()
        } finally {
            if (ownsTransaction) diagnosticJob = null
            transactionMutex.unlock()
        }
    }

    /** Shares the physical owner and preprocessor, without publishing production events. */
    suspend fun captureDiagnosticAudio(
        maxDurationMs: Long = 15_000,
        finish: kotlinx.coroutines.Deferred<Unit>? = null
    ): DiagnosticAudioCapture {
        require(maxDurationMs in 1..15_000)
        check(transactionMutex.tryLock()) { "Microphone transaction is busy" }
        val frames = mutableListOf<com.halo.sdk.host.AudioFrame>()
        var originalRate = 0
        var ownsInput = false
        try {
            check(_state.value == EngineState.Running) { "Audio engine is not running" }
            check(captureJob?.isCompleted != false && !audioPipeline.recognitionInFlight) { "Microphone transaction is busy" }
            ownsInput = true
            diagnosticJob = currentCoroutineContext()[Job]
            preprocessor.reset()
            selectedSource = audioSource
            sourceStarted.set(true)
            audioSource.start()
            kotlinx.coroutines.coroutineScope {
                val reader = launch {
                    audioSource.frames.collect { raw ->
                        if (originalRate == 0) originalRate = raw.sampleRate
                        val processed = try { preprocessor.process(raw) } catch (error: Exception) {
                            throw SpeechDiagnosticException(SpeechDiagnosticStage.PREPROCESS, error)
                        }
                        val remaining = maxDurationMs * 16 - frames.sumOf { it.samples.size }
                        if (remaining > 0) frames.add(processed.copy(samples = processed.samples.take(remaining.toInt()).toShortArray()))
                    }
                }
                try {
                    kotlinx.coroutines.withTimeoutOrNull(maxDurationMs) {
                        if (finish != null) finish.await() else reader.join()
                    }
                } finally {
                    withContext(NonCancellable) { stopAudioSource(); reader.cancel(); reader.join() }
                }
            }
            check(frames.any { it.samples.isNotEmpty() }) { "No processed PCM captured" }
            return DiagnosticAudioCapture(com.halo.sdk.host.AudioBuffer(frames.toList(), UUID.randomUUID().toString()),
                "phone", originalRate, 16_000)
        } catch (error: Throwable) {
            frames.forEach { it.samples.fill(0) }
            throw error
        } finally {
            if (ownsInput) {
                withContext(NonCancellable) { stopAudioSource() }
                preprocessor.reset()
                diagnosticJob = null
            }
            transactionMutex.unlock()
        }
    }

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
                launch { commandBus.commands().collect { command ->
                    when (command) {
                        is StartListening -> startCapture()
                        is StartListeningWithActivation -> startCapture(SpeechInputTurn(UUID.randomUUID().toString(), activation = command.activation, conversationId = command.conversationId))
                        else -> Unit
                    }
                } }
                launch { commandBus.commands().filterIsInstance<StopListening>().collect { stopCapture(finalizeUserCapture = true) } }
                launch {
                    eventBus.observe(SpeechEnded::class.java).collect {
                        stopBufferedInputAfterSpeechEnd()
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
        startCapture(SpeechInputTurn(UUID.randomUUID().toString()))
    }

    /** Starts a correlated phone-input turn without creating a second microphone stack. */
    suspend fun startCapture(turn: SpeechInputTurn): Boolean = acquireInput(turn, audioSource)

    /** The engine is the sole capture owner for every physical input. */
    suspend fun acquireInput(requestedTurn: SpeechInputTurn, source: AudioSource): Boolean {
        suspend fun rejectBusy(): Boolean {
            eventBus.publish(SpeechRejected(text = "", reason = "INPUT_BUSY", captureId = requestedTurn.captureId, input = requestedTurn))
            return false
        }
        if (diagnosticJob != null) return rejectBusy()
        if (!transactionMutex.tryLock()) return rejectBusy()
        return try {
            val turn = requestedTurn.takeIf { it.conversationId != null }
                ?: requestedTurn.copy(conversationId = originatingConversation())
            if (_state.value != EngineState.Running || captureJob?.isCompleted == false || audioPipeline.recognitionInFlight) {
                val reason = "active_turn_owns_input"
                _inputStatus.value = _inputStatus.value.copy(rejection = reason)
                if (::context.isInitialized) context.runtime.logger.info("SPEECH_INPUT_ROUTE", "rejected=$reason")
                return rejectBusy()
            }
            if (!audioPipeline.isRecognitionAvailable()) {
                _inputStatus.value = SpeechInputStatus(turn, rejection = audioPipeline.unavailableReason())
                reportCaptureFailure(audioPipeline.unavailableReason())
                return false
            }
            selectedSource = source
            preprocessor.reset()
            audioPipeline.beginCapture(turn)
            _inputStatus.value = SpeechInputStatus(turn, capturing = true)
            try {
                if (!audioPipeline.usesLiveRecognition) { sourceStarted.set(true); source.start() }
                _health.value = EngineHealth.Healthy
                eventBus.publish(ListeningStarted(captureId = turn.captureId, source = turn.route, activation = turn.activation))
                context.runtime.logger.info("SPEECH_INPUT_ROUTE", "capture=${turn.captureId} source=${turn.source}")
                captureJob = scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    try {
                        source.frames.collect { rawFrame ->
                            val frame = preprocessor.process(rawFrame)
                            metrics.framesProcessed++
                            audioPipeline.processFrame(frame)
                            val now = System.nanoTime()
                            if (_inputStatus.subscriptionCount.value > 0 && now - lastLevelNanos >= 100_000_000L) {
                                lastLevelNanos = now
                                val level = if (frame.samples.isEmpty()) 0f else
                                    kotlin.math.sqrt(frame.samples.sumOf { it.toDouble() * it } / frame.samples.size).toFloat() / 32768f
                                _inputStatus.value = _inputStatus.value.copy(inputLevel = level.coerceIn(0f, 1f))
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        audioPipeline.reset()
                        reportCaptureFailure("Audio capture failed: ${error.message}", error)
                    } finally {
                        val completed = currentCoroutineContext().isActive
                        withContext(NonCancellable) {
                            stopAudioSource()
                            if (completed && turn.boundary == CaptureBoundary.EXTERNAL) {
                                audioPipeline.finishCapture()
                            }
                        }
                        _inputStatus.value = _inputStatus.value.copy(capturing = false, inputLevel = 0f)
                    }
                }
                true
            } catch (error: Exception) {
                withContext(NonCancellable) { stopAudioSource() }
                audioPipeline.reset()
                _inputStatus.value = SpeechInputStatus(turn, rejection = error.message)
                if (error is CancellationException) throw error
                reportCaptureFailure("Audio capture failed: ${error.message}", error)
                false
            }
        } finally { transactionMutex.unlock() }
    }

    suspend fun cancelExternalInput(captureId: String) {
        stopCapture(finalizeUserCapture = false, expectedCaptureId = captureId)
    }

    suspend fun finishExternalInput(captureId: String) = transactionMutex.withLock {
        if (_inputStatus.value.turn?.captureId != captureId) return@withLock
        // The external source has sent EOS. Let its bounded channel empty before
        // the capture coroutine snapshots the turn in its normal-completion path.
        val job = captureJob ?: return@withLock
        job.join()
        if (captureJob === job) captureJob = null
    }

    private suspend fun stopBufferedInputAfterSpeechEnd() {
        // Live recognizers own an active microphone-backed recognition session.
        // This automatic release is specifically for the buffered path, where
        // the utterance has already been snapshotted for recognition.
        if (audioPipeline.usesLiveRecognition) return

        transactionMutex.withLock {
            val job = captureJob ?: return@withLock

            if (job.isActive) {
                job.cancel()

                // AudioRecord.read() may currently be blocked. Stopping the
                // source explicitly unblocks the platform read before join().
                withContext(NonCancellable) {
                    stopAudioSource()
                }

                job.join()
            }

            if (captureJob === job) {
                captureJob = null
            }
        }
    }

    private suspend fun stopCapture(finalizeUserCapture: Boolean, expectedCaptureId: String? = null) {
        if (diagnosticJob != null) return
        transactionMutex.withLock {
            if (expectedCaptureId != null && _inputStatus.value.turn?.captureId != expectedCaptureId) return@withLock
            val job = captureJob
            val hadActiveCapture = job?.isActive == true
            val turn = _inputStatus.value.turn
            val hadRecognition = audioPipeline.recognitionInFlight

            if (job != null) {
                job.cancel()
                withContext(NonCancellable) { stopAudioSource() }
                job.join()
                if (captureJob === job) captureJob = null
            }

            when {
                !finalizeUserCapture || audioPipeline.usesLiveRecognition -> audioPipeline.reset()
                hadActiveCapture -> audioPipeline.finishCapture()
                else -> {
                    // If VAD already ended capture, StopListening means the user
                    // is cancelling the recognition transaction rather than
                    // asking us to finalize the same buffered audio again.
                    audioPipeline.reset()
                }
            }
            if (turn != null && (hadActiveCapture || hadRecognition) &&
                (!finalizeUserCapture || audioPipeline.usesLiveRecognition || !hadActiveCapture)) {
                eventBus.publish(SpeechRejected(text = "", reason = "CANCELLED", captureId = turn.captureId, input = turn))
            }
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
            eventBus.publish(RecognitionFailed(id = _inputStatus.value.turn?.captureId ?: UUID.randomUUID().toString(), reason = reason))
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
        diagnosticJob?.let { it.cancel(); stopAudioSource(); it.join() }
        val activeCommandJob = commandJob
        commandJob = null
        activeCommandJob?.cancel()
        withContext(NonCancellable) { stopCapture(finalizeUserCapture = false) }
        activeCommandJob?.join()
        audioPipeline.stop()
        _state.value = EngineState.Stopped
    }

    private suspend fun stopAudioSource() {
        if (!sourceStarted.compareAndSet(true, false)) return
        try {
            selectedSource.stop()
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
