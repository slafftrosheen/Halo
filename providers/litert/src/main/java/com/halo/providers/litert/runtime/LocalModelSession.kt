package com.halo.providers.litert.runtime

import android.content.Context
import android.util.Log
import com.halo.sdk.provider.InferenceAdmission
import com.halo.sdk.provider.InferencePriority
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.LocalInferenceRuntime
import com.halo.sdk.provider.LocalModelActivityState
import com.halo.sdk.provider.LocalModelFormat
import com.halo.sdk.provider.LocalModelSessionState
import com.halo.sdk.provider.LocalRuntimeBackendReporting
import com.halo.sdk.provider.RuntimeGenerationConfig
import com.halo.sdk.provider.RuntimeGenerationResult
import com.halo.sdk.provider.RuntimeLoadConfig
import com.halo.sdk.provider.RuntimeLoadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class LocalModelSession(
    private val runtimeProvider: (LocalModelFormat) -> LocalInferenceRuntime,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    constructor(
        context: Context,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        /** DOGFOOD-005 durable native-attempt journal forwarded to the LiteRT runtime. */
        nativeAttemptJournal: LiteRtNativeAttemptJournal? = null,
    ) : this(
        runtimeProvider = { _ ->
            LiteRtRuntime(context, scope, nativeAttemptJournal)
        },
        scope = scope,
    )

    private val _state = MutableStateFlow(LocalModelSessionState.IDLE)
    val state: StateFlow<LocalModelSessionState> = _state.asStateFlow()
    private val _activity = MutableStateFlow<LocalModelActivityState>(LocalModelActivityState.Idle)
    val activity: StateFlow<LocalModelActivityState> = _activity.asStateFlow()

    @Volatile private var activeRuntime: LocalInferenceRuntime? = null
    /** Includes failed teardown: mapped native ownership is not the same as readiness. */
    val hasRuntimeOwnership: Boolean get() = activeRuntime != null
    @Volatile private var initializingModelId: String? = null
    /** Protect files during initialization and failed teardown as well as warm residency. */
    val ownedModelId: String? get() = loadedModelId ?: initializingModelId

    var loadedArtifactSha256: String? = null
        private set
    @Volatile var loadedModelId: String? = null
        private set
    var loadedModelPath: String? = null
        private set
    var activeFormat: LocalModelFormat? = null
        private set
    var activeConfig: RuntimeLoadConfig? = null
        private set
    /** Original settings requested for this residency; activeConfig may be downshifted. */
    var requestedLoadConfig: RuntimeLoadConfig? = null
        private set
    /** Avoid reusing a resident engine after artifact/config changes or failed teardown. */
    fun isResidentFor(modelId: String, modelPath: String, sha256: String?, config: RuntimeLoadConfig): Boolean =
        isLoaded() && loadedModelId == modelId && loadedModelPath == modelPath &&
            activeFormat == LocalModelFormat.LITERT_LM &&
            (sha256 == null || (loadedArtifactSha256 != null && loadedArtifactSha256 == sha256)) &&
            requestedLoadConfig == config
    var activeBackend: LocalComputeBackend? = null
        private set
    var activeGpuLayers: Int = 0
        private set
    var fallbackReason: String? = null
        private set

    /** Context Halo originally requested for the resident model, when it differs from the effective one. */
    val requestedContextSize: Int?
        get() = (activeRuntime as? LiteRtRuntime)?.snapshot()?.requestedContextTokens

    /** Bounded reason a bounded context downshift occurred, or null. */
    val contextFallbackReason: String?
        get() = (activeRuntime as? LiteRtRuntime)?.snapshot()?.contextFallbackReason

    /** Native engine initialization duration, when the runtime can report it. */
    val engineInitDurationMs: Long?
        get() = (activeRuntime as? LiteRtRuntime)?.snapshot()?.engineInitDurationMs

    /** Conversation creation duration, when the runtime can report it. */
    val conversationCreateDurationMs: Long?
        get() = (activeRuntime as? LiteRtRuntime)?.snapshot()?.conversationCreateDurationMs

    /** Context actually allocated for the resident model. */
    val effectiveContextSize: Int?
        get() {
            val native = activeRuntime as? LiteRtRuntime
            return if (native != null) native.snapshot().contextTokens else activeConfig?.contextSize
        }

    /**
     * MINING-P0-001 content-free native tool-channel observation from the last generation, when the
     * active runtime reports one. Structural facts and counts only: no argument values, no text.
     */
    val lastToolChannelObservation: LiteRtNativeToolChannelObservation?
        get() = (activeRuntime as? LiteRtRuntime)?.lastToolChannelObservation?.value

    private val lifecycleMutex = Mutex()
    private val generationMutex = Mutex()
    private val generationCounter = AtomicLong(0L)
    private val currentGenerationId = AtomicLong(0L)
    private data class ActiveGeneration(val id: Long, val job: Deferred<Result<RuntimeGenerationResult>>,
        val cancellationMutex: Mutex = Mutex())
    private val activeGeneration = AtomicReference<ActiveGeneration?>(null)
    private val residencyVersion = AtomicLong(0L)
    private var consecutiveInteractive = 0
    private val benchmarkOwner = AtomicReference<String?>(null)

    private data class QueuedGeneration(
        val id: Long,
        val config: RuntimeGenerationConfig,
        val onToken: suspend (String) -> Unit,
        val deferred: CompletableDeferred<Result<RuntimeGenerationResult>>,
        val residencyVersion: Long,
        val runtime: LocalInferenceRuntime,
        val finished: CompletableDeferred<Unit> = CompletableDeferred(),
        val benchmarkRequestId: String? = null,
        val timestamp: Long = System.currentTimeMillis(),
    ) : Comparable<QueuedGeneration> {
        override fun compareTo(other: QueuedGeneration): Int {
            val priorityComp = this.config.priority.ordinal.compareTo(other.config.priority.ordinal)
            return if (priorityComp != 0) priorityComp else this.timestamp.compareTo(other.timestamp)
        }
    }

    private val queueMutex = Mutex()
    private val pendingQueue = PriorityQueue<QueuedGeneration>()
    private val queueSignal = Channel<Unit>(Channel.CONFLATED)
    private val isProcessorRunning = AtomicBoolean(false)

    init {
        startQueueProcessor()
    }

    private fun startQueueProcessor() {
        if (!isProcessorRunning.compareAndSet(false, true)) return
        scope.launch {
            for (signal in queueSignal) {
                while (true) {
                    val nextItem = queueMutex.withLock { pollNextFairly() } ?: break

                    try {
                        if (!nextItem.deferred.isCancelled) processGenerationItem(nextItem)
                    } finally {
                        // The caller's cancellation must not release provider/native compute
                        // ownership while this worker still owns native inference or cleanup.
                        nextItem.finished.complete(Unit)
                    }
                }
            }
        }
    }

    private fun pollNextFairly(): QueuedGeneration? {
        if (pendingQueue.isEmpty()) return null
        if (consecutiveInteractive >= MAX_INTERACTIVE_BURST) {
            val background = pendingQueue
                .filter { it.config.priority == InferencePriority.BACKGROUND }
                .minByOrNull { it.timestamp }
            if (background != null) {
                pendingQueue.remove(background)
                consecutiveInteractive = 0
                return background
            }
        }
        return pendingQueue.poll()?.also {
            consecutiveInteractive = if (it.config.priority == InferencePriority.INTERACTIVE) {
                consecutiveInteractive + 1
            } else {
                0
            }
        }
    }

    private suspend fun processGenerationItem(item: QueuedGeneration) {
        generationMutex.withLock {
            if (item.deferred.isCancelled) return@withLock
            if (item.residencyVersion != residencyVersion.get() || activeRuntime !== item.runtime) {
                item.deferred.complete(Result.failure(IllegalStateException("Local model changed while request was queued")))
                return@withLock
            }
            val runtime = activeRuntime
            if (runtime == null || !runtime.isLoaded()) {
                item.deferred.complete(Result.failure(IllegalStateException("No model loaded in session")))
                return@withLock
            }

            val genId = item.id
            currentGenerationId.set(genId)
            _state.value = LocalModelSessionState.INFERRING
            _activity.value = if (item.benchmarkRequestId != null) {
                LocalModelActivityState.Benchmarking(item.benchmarkRequestId)
            } else {
                LocalModelActivityState.Generating(item.id.toString(), item.config.priority)
            }

            try {
                val tokenCallback: suspend (String) -> Unit = { token ->
                    if (currentGenerationId.get() == genId && !item.deferred.isCancelled) {
                        item.onToken(token)
                    }
                }
                // Cancellation belongs to this request's job, never a later
                // native conversation that happens to reuse the same engine.
                val result = supervisorScope {
                    val job = async(start = CoroutineStart.LAZY) { runtime.generate(item.config, tokenCallback) }
                    val active = ActiveGeneration(genId, job)
                    activeGeneration.set(active)
                    try {
                        if (item.deferred.isCancelled) job.cancel()
                        job.start()
                        job.await()
                    } finally {
                        withContext(NonCancellable) {
                            // A stop API call must finish before this generation is detached
                            // and the next request can acquire the same runtime.
                            active.cancellationMutex.withLock { activeGeneration.compareAndSet(active, null) }
                        }
                    }
                }
                item.deferred.complete(result)
            } catch (c: CancellationException) {
                item.deferred.cancel(c)
            } catch (t: Throwable) {
                item.deferred.complete(Result.failure(t))
            } finally {
                currentGenerationId.compareAndSet(genId, 0L)
                // A runtime may switch accelerators after load (late GPU failure -> CPU rebuild),
                // so residency facts are re-read instead of trusting the value captured at load.
                syncBackendFromRuntime()
                _state.value = if (activeRuntime?.isLoaded() == true) LocalModelSessionState.IDLE else LocalModelSessionState.ERROR
                _activity.value = if (activeRuntime?.isLoaded() != true) LocalModelActivityState.Error("Local model needs to be reloaded")
                    else benchmarkOwner.get()?.let(LocalModelActivityState::Benchmarking)
                        ?: loadedModelId?.let(LocalModelActivityState::Ready)
                        ?: LocalModelActivityState.Error("No model loaded")
            }
        }
    }

    fun isLoaded(): Boolean = activeRuntime?.isLoaded() == true &&
        _state.value != LocalModelSessionState.UNLOADING && _state.value != LocalModelSessionState.ERROR

    suspend fun loadModel(
        modelId: String,
        modelPath: String,
        format: LocalModelFormat = LocalModelFormat.LITERT_LM,
        config: RuntimeLoadConfig = RuntimeLoadConfig(),
        artifactSha256: String? = null,
    ): Result<RuntimeLoadResult> = lifecycleMutex.withLock lifecycleLock@{
        generationMutex.withLock generationLock@{
        if (format == LocalModelFormat.LITERT_LM &&
            isResidentFor(modelId, modelPath, artifactSha256, config)) {
            // The native runtime may have downshifted context after an allocation
            // failure. Reuse the proven resident context instead of retrying the
            // larger requested value on every prompt.
            return@generationLock Result.success(
                RuntimeLoadResult(
                    activeBackend = activeBackend ?: LocalComputeBackend.CPU,
                    activeGpuLayers = activeGpuLayers,
                    effectiveContextSize = activeConfig?.contextSize ?: config.contextSize,
                    fallbackReason = fallbackReason,
                    requestedContextSize = requestedContextSize,
                    contextFallbackReason = contextFallbackReason,
                )
            )
        }

        _state.value = LocalModelSessionState.LOADING
        _activity.value = LocalModelActivityState.Loading(modelId)
        var loadingRuntime: LocalInferenceRuntime? = null
        try {
            val released = unloadInternal()
            if (released.isFailure) {
                // Native weights may still be mapped. Never allocate a second engine
                // unless the old runtime confirmed teardown.
                val failure = released.exceptionOrNull() ?: IllegalStateException("Local model teardown failed")
                return@generationLock Result.failure(failure)
            }
            _state.value = LocalModelSessionState.LOADING
            _activity.value = LocalModelActivityState.Loading(modelId)
            val runtime = runtimeProvider(format)
            loadingRuntime = runtime
            initializingModelId = modelId
            activeRuntime = runtime

            val result = runtime.loadModel(modelId, modelPath, config)
            // Native initialization may ignore cancellation until it returns.
            // Release that allocation instead of publishing an obsolete owner.
            currentCoroutineContext().ensureActive()
            result.onSuccess { loadResult ->
                residencyVersion.incrementAndGet()
                loadedArtifactSha256 = artifactSha256
                loadedModelId = modelId
                initializingModelId = null
                loadedModelPath = modelPath
                activeFormat = format
                activeConfig = config.copy(contextSize = loadResult.effectiveContextSize)
                requestedLoadConfig = config
                activeBackend = loadResult.activeBackend
                activeGpuLayers = loadResult.activeGpuLayers
                fallbackReason = loadResult.fallbackReason
                _state.value = LocalModelSessionState.IDLE
                _activity.value = LocalModelActivityState.Ready(modelId)
                Log.i(TAG, "Local model loaded successfully: $modelId on $activeBackend ($activeGpuLayers layers)")
            }.onFailure { err ->
                _state.value = LocalModelSessionState.ERROR
                _activity.value = LocalModelActivityState.Error(err.message ?: "Model load failed")
                Log.e(TAG, "Failed to load local model: $modelId", err)
            }
            if (result.isFailure) {
                discardFailedRuntime(runtime, result.exceptionOrNull())
            }
            result
        } catch (c: CancellationException) {
            loadingRuntime?.let { discardFailedRuntime(it, c) }
            _state.value = if (activeRuntime == null) LocalModelSessionState.IDLE else LocalModelSessionState.ERROR
            _activity.value = if (activeRuntime == null) LocalModelActivityState.Idle
                else LocalModelActivityState.Error("Could not release the cancelled local model")
            throw c
        } catch (t: Throwable) {
            loadingRuntime?.let { discardFailedRuntime(it, t) }
            _state.value = LocalModelSessionState.ERROR
            _activity.value = LocalModelActivityState.Error(t.message ?: "Model load failed")
            Result.failure(t)
        }
        }
    }

    /** Release a runtime that never became resident without replacing its original failure. */
    private suspend fun discardFailedRuntime(runtime: LocalInferenceRuntime, primary: Throwable?) {
        val cleanupFailure = withContext(NonCancellable) {
            try {
                runtime.unloadModel().exceptionOrNull()
            } catch (cleanup: Throwable) {
                cleanup
            }
        }
        if (primary != null && cleanupFailure != null && cleanupFailure !== primary) {
            primary.addSuppressed(cleanupFailure)
        }
        // Keep failed teardown ownership so another allocation must first retry
        // unloading the still-mapped native engine.
        if (cleanupFailure == null && activeRuntime === runtime) {
            activeRuntime = null
            initializingModelId = null
        }
    }

    suspend fun generate(
        config: RuntimeGenerationConfig,
        benchmarkRequestId: String? = null,
        onToken: suspend (String) -> Unit = {},
    ): Result<RuntimeGenerationResult> {
        val runtime = activeRuntime
        if (runtime == null || !runtime.isLoaded()) {
            return Result.failure(IllegalStateException("No local model is loaded in LocalModelSession"))
        }
        benchmarkOwner.get()?.let { owner ->
            if (owner != benchmarkRequestId) {
                return Result.failure(
                    com.halo.sdk.provider.AiProviderException(
                        type = com.halo.sdk.provider.AiErrorType.SESSION_CONFLICT,
                        message = "Local model is currently reserved for a benchmark",
                        details = com.halo.sdk.provider.AiFailureDetails(
                            activeSessionId = owner,
                            activeMode = "benchmark",
                            recoverability = com.halo.sdk.provider.Recoverability.SESSION_CONFLICT,
                        ),
                    )
                )
            }
        }

        val deferred = CompletableDeferred<Result<RuntimeGenerationResult>>()
        val item = QueuedGeneration(
            id = generationCounter.incrementAndGet(),
            config = config,
            onToken = onToken,
            deferred = deferred,
            residencyVersion = residencyVersion.get(),
            runtime = runtime,
            benchmarkRequestId = benchmarkRequestId,
        )

        queueMutex.withLock {
            if (pendingQueue.size >= MAX_PENDING_GENERATIONS) return Result.failure(
                com.halo.sdk.provider.AiProviderException(com.halo.sdk.provider.AiErrorType.SESSION_CONFLICT,
                    "Local request queue is full. Wait for the current request, then retry.",
                    com.halo.sdk.provider.AiFailureDetails(retryAfterMs = 1000L,
                        recoverability = com.halo.sdk.provider.Recoverability.RETRY_AFTER)))
            pendingQueue.add(item)
        }
        queueSignal.trySend(Unit)

        return try {
            deferred.await()
        } catch (c: CancellationException) {
            withContext(NonCancellable) {
                deferred.cancel(c)
                val removedBeforeDispatch = queueMutex.withLock { pendingQueue.remove(item) }
                if (removedBeforeDispatch) {
                    item.finished.complete(Unit)
                } else {
                    // If dispatch is between dequeue and publication, it sees the cancelled
                    // deferred before starting its job. Never cancel another request's job.
                    activeGeneration.get()?.takeIf { it.id == item.id }?.let { active ->
                        active.cancellationMutex.withLock {
                            if (activeGeneration.get() === active) {
                                active.job.cancel(c)
                                // Coroutine cancellation alone cannot interrupt a blocking JNI
                                // prefill. Forward stop to this still-owned native generation.
                                runCatching { runtime.cancelGeneration() }
                            }
                        }
                    }
                    item.finished.await()
                }
            }
            throw c
        }
    }

    fun tryBeginBenchmark(requestId: String): InferenceAdmission {
        val current = _activity.value
        if (current is LocalModelActivityState.Generating || current is LocalModelActivityState.Loading) {
            return InferenceAdmission.Busy(
                activeRequestId = (current as? LocalModelActivityState.Generating)?.requestId,
                mode = current::class.simpleName,
            )
        }
        val existing = benchmarkOwner.get()
        if (existing != null || !benchmarkOwner.compareAndSet(null, requestId)) {
            return InferenceAdmission.Busy(existing, "benchmark")
        }
        _activity.value = LocalModelActivityState.Benchmarking(requestId)
        return InferenceAdmission.Granted
    }

    fun endBenchmark(requestId: String) {
        if (benchmarkOwner.compareAndSet(requestId, null)) {
            _activity.value = loadedModelId?.let(LocalModelActivityState::Ready) ?: LocalModelActivityState.Idle
        }
    }

    /** Keeps [activeBackend] / [fallbackReason] truthful when the runtime changes accelerator. */
    private fun syncBackendFromRuntime() {
        val reporting = activeRuntime as? LocalRuntimeBackendReporting ?: return
        val observed = reporting.activeBackend ?: return
        val observedReason = reporting.backendFallbackReason
        if (observed == activeBackend && observedReason == fallbackReason) return
        activeBackend = observed
        fallbackReason = observedReason
        Log.i(TAG, "Local model backend updated: $observed fallback=${observedReason?.take(120)}")
    }

    suspend fun cancelGeneration() {
        activeGeneration.get()?.job?.cancel(CancellationException("Local generation stopped"))
    }

    suspend fun unloadModel(): Result<Unit> = lifecycleMutex.withLock {
        generationMutex.withLock { unloadInternal() }
    }

    private suspend fun unloadInternal(): Result<Unit> {
        _state.value = LocalModelSessionState.UNLOADING
        _activity.value = LocalModelActivityState.Unloading
        val current = activeRuntime
        val res = withContext(NonCancellable) {
            try { current?.unloadModel() ?: Result.success(Unit) }
            catch (failure: Throwable) { Result.failure(failure) }
        }
        if (res.isFailure) {
            _state.value = LocalModelSessionState.ERROR
            _activity.value = LocalModelActivityState.Error("Could not release the previous local model")
            Log.e(TAG, "Local model teardown failed; refusing another allocation", res.exceptionOrNull())
            return res
        }
        activeRuntime = null
        initializingModelId = null
        loadedArtifactSha256 = null
        loadedModelId = null
        loadedModelPath = null
        activeFormat = null
        activeConfig = null
        requestedLoadConfig = null
        activeBackend = null
        activeGpuLayers = 0
        fallbackReason = null
        residencyVersion.incrementAndGet()
        _state.value = LocalModelSessionState.IDLE
        _activity.value = LocalModelActivityState.Idle
        return res
    }

    companion object {
        private const val TAG = "LocalModelSession"
        private const val MAX_INTERACTIVE_BURST = 5
        private const val MAX_PENDING_GENERATIONS = 32
    }
}
