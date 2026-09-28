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
    ) : this(
        runtimeProvider = { _ ->
            LiteRtRuntime(context, scope)
        },
        scope = scope,
    )

    private val _state = MutableStateFlow(LocalModelSessionState.IDLE)
    val state: StateFlow<LocalModelSessionState> = _state.asStateFlow()
    private val _activity = MutableStateFlow<LocalModelActivityState>(LocalModelActivityState.Idle)
    val activity: StateFlow<LocalModelActivityState> = _activity.asStateFlow()

    private var activeRuntime: LocalInferenceRuntime? = null

    var loadedModelId: String? = null
        private set
    var loadedModelPath: String? = null
        private set
    var activeFormat: LocalModelFormat? = null
        private set
    var activeConfig: RuntimeLoadConfig? = null
        private set
    var activeBackend: LocalComputeBackend? = null
        private set
    var activeGpuLayers: Int = 0
        private set
    var fallbackReason: String? = null
        private set

    private val lifecycleMutex = Mutex()
    private val generationMutex = Mutex()
    private val generationCounter = AtomicLong(0L)
    private val currentGenerationId = AtomicLong(0L)
    private val residencyVersion = AtomicLong(0L)
    private var consecutiveInteractive = 0
    private val benchmarkOwner = AtomicReference<String?>(null)

    private data class QueuedGeneration(
        val id: Long,
        val config: RuntimeGenerationConfig,
        val onToken: suspend (String) -> Unit,
        val deferred: CompletableDeferred<Result<RuntimeGenerationResult>>,
        val residencyVersion: Long,
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

                    if (nextItem.deferred.isCancelled) continue

                    processGenerationItem(nextItem)
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
            if (item.residencyVersion != residencyVersion.get()) {
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
                    if (currentGenerationId.get() == genId) {
                        item.onToken(token)
                    }
                }
                val result = runtime.generate(item.config, tokenCallback)
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
                _activity.value = benchmarkOwner.get()?.let(LocalModelActivityState::Benchmarking)
                    ?: loadedModelId?.let(LocalModelActivityState::Ready)
                    ?: LocalModelActivityState.Error("No model loaded")
            }
        }
    }

    fun isLoaded(): Boolean = activeRuntime?.isLoaded() == true && _state.value != LocalModelSessionState.UNLOADING

    suspend fun loadModel(
        modelId: String,
        modelPath: String,
        format: LocalModelFormat = LocalModelFormat.LITERT_LM,
        config: RuntimeLoadConfig = RuntimeLoadConfig(),
    ): Result<RuntimeLoadResult> = lifecycleMutex.withLock lifecycleLock@{
        generationMutex.withLock generationLock@{
        if (isLoaded() && loadedModelId == modelId && activeFormat == format) {
            // The native runtime may have downshifted context after an allocation
            // failure. Reuse the proven resident context instead of retrying the
            // larger requested value on every turn.
            return@generationLock Result.success(
                RuntimeLoadResult(
                    activeBackend = activeBackend ?: LocalComputeBackend.CPU,
                    activeGpuLayers = activeGpuLayers,
                    effectiveContextSize = activeConfig?.contextSize ?: config.contextSize,
                    fallbackReason = fallbackReason,
                )
            )
        }

        _state.value = LocalModelSessionState.LOADING
        _activity.value = LocalModelActivityState.Loading(modelId)
        var loadingRuntime: LocalInferenceRuntime? = null
        try {
            unloadInternal()

            val runtime = runtimeProvider(format)
            loadingRuntime = runtime
            activeRuntime = runtime

            val result = runtime.loadModel(modelId, modelPath, config)
            result.onSuccess { loadResult ->
                residencyVersion.incrementAndGet()
                loadedModelId = modelId
                loadedModelPath = modelPath
                activeFormat = format
                activeConfig = config.copy(contextSize = loadResult.effectiveContextSize)
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
            _state.value = LocalModelSessionState.IDLE
            _activity.value = LocalModelActivityState.Idle
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
        if (activeRuntime === runtime) activeRuntime = null
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
            benchmarkRequestId = benchmarkRequestId,
        )

        queueMutex.withLock {
            pendingQueue.add(item)
        }
        queueSignal.trySend(Unit)

        return try {
            deferred.await()
        } catch (c: CancellationException) {
            deferred.cancel(c)
            queueMutex.withLock { pendingQueue.remove(item) }
            if (currentGenerationId.get() == item.id) cancelGeneration()
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
        val runtime = activeRuntime
        if (runtime != null) {
            runtime.cancelGeneration()
        }
    }

    suspend fun unloadModel(): Result<Unit> = lifecycleMutex.withLock {
        generationMutex.withLock { unloadInternal() }
    }

    private suspend fun unloadInternal(): Result<Unit> {
        _state.value = LocalModelSessionState.UNLOADING
        _activity.value = LocalModelActivityState.Unloading
        val current = activeRuntime
        val res = current?.unloadModel() ?: Result.success(Unit)
        activeRuntime = null
        loadedModelId = null
        loadedModelPath = null
        activeFormat = null
        activeConfig = null
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
    }
}
