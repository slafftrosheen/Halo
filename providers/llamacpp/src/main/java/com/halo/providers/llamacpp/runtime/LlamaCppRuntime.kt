package com.halo.providers.llamacpp.runtime

import android.content.Context
import android.net.Uri
import android.util.Log
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.LocalInferenceRuntime
import com.halo.sdk.provider.LocalModelFormat
import com.halo.sdk.provider.LocalModelSessionState
import com.halo.sdk.provider.RuntimeGenerationConfig
import com.halo.sdk.provider.RuntimeGenerationResult
import com.halo.sdk.provider.RuntimeLoadConfig
import com.halo.sdk.provider.RuntimeLoadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import org.nehuatl.llamacpp.LlamaHelper
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class LlamaCppRuntime(
    private val context: Context,
    private val scope: CoroutineScope,
) : LocalInferenceRuntime {

    override val format: LocalModelFormat = LocalModelFormat.GGUF

    private val _state = MutableStateFlow(LocalModelSessionState.IDLE)
    override val state: StateFlow<LocalModelSessionState> = _state.asStateFlow()

    private var _loadedModelId: String? = null
    override val loadedModelId: String? get() = _loadedModelId

    private var engine: LlamaHelper? = null
    private var engineEvents: MutableSharedFlow<LlamaHelper.LLMEvent>? = null

    private val mutex = Mutex()
    private val cancelRequested = AtomicBoolean(false)

    override fun isLoaded(): Boolean = engine != null && _state.value != LocalModelSessionState.UNLOADING

    override suspend fun loadModel(
        modelId: String,
        modelPath: String,
        config: RuntimeLoadConfig,
    ): Result<RuntimeLoadResult> = mutex.withLock {
        _state.value = LocalModelSessionState.LOADING
        try {
            val file = File(modelPath)
            require(file.exists() && file.isFile) { "Model file does not exist: $modelPath" }

            val events = MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity = 64)
            val loadDeferred = CompletableDeferred<String?>()
            val helper = LlamaHelper(context.contentResolver, scope, events)
            val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                events.collect { event ->
                    if (event is LlamaHelper.LLMEvent.Error) {
                        loadDeferred.complete(event.message ?: "Native llama error")
                    }
                }
            }
            try {
                helper.load(Uri.fromFile(file).toString(), config.contextSize, null) {
                    loadDeferred.complete(null)
                }
                val loadOutcome = withTimeoutOrNull(LOAD_TIMEOUT_MS) { loadDeferred.await() }
                if (!loadDeferred.isCompleted) {
                    throw IllegalStateException("llama.cpp model load timed out after ${LOAD_TIMEOUT_MS}ms")
                }
                if (loadOutcome != null) throw IllegalStateException(loadOutcome)
            } catch (error: Throwable) {
                runCatching { helper.release() }
                throw error
            } finally {
                collector.cancel()
            }

            unloadInternal()
            engine = helper
            engineEvents = events
            _loadedModelId = modelId
            _state.value = LocalModelSessionState.IDLE

            val fallbackReason = if (config.computeBackend == LocalComputeBackend.CPU && config.gpuLayers == 0) {
                null
            } else {
                "The installed llama.cpp binding does not expose GPU-layer configuration; running on CPU"
            }
            Result.success(
                RuntimeLoadResult(
                    activeBackend = LocalComputeBackend.CPU,
                    activeGpuLayers = 0,
                    effectiveContextSize = config.contextSize,
                    fallbackReason = fallbackReason,
                )
            )
        } catch (e: CancellationException) {
            _state.value = LocalModelSessionState.IDLE
            throw e
        } catch (e: Throwable) {
            _state.value = LocalModelSessionState.ERROR
            Result.failure(e)
        }
    }

    override suspend fun generate(
        config: RuntimeGenerationConfig,
        onToken: suspend (String) -> Unit,
    ): Result<RuntimeGenerationResult> = mutex.withLock {
        val currentEngine = engine ?: return Result.failure(IllegalStateException("No model currently loaded"))
        val currentEvents = engineEvents ?: return Result.failure(IllegalStateException("No active engine event stream"))

        _state.value = LocalModelSessionState.INFERRING
        cancelRequested.set(false)

        val startTime = System.currentTimeMillis()
        var firstTokenTime: Long? = null
        var tokenCount = 0
        val textBuilder = StringBuilder()

        val eventChannel = Channel<LlamaHelper.LLMEvent>(Channel.UNLIMITED)
        val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            currentEvents.collect { eventChannel.send(it) }
        }

        try {
            currentEvents.subscriptionCount.first { it > 0 }
            currentCoroutineContext().ensureActive()

            currentEngine.predict(config.prompt, null, true)

            withTimeout(GENERATION_TIMEOUT_MS) {
                for (event in eventChannel) {
                    currentCoroutineContext().ensureActive()
                    if (cancelRequested.get()) {
                        runCatching { currentEngine.stopPrediction() }
                        break
                    }

                    when (event) {
                        is LlamaHelper.LLMEvent.Ongoing -> {
                            if (event.word.isNotEmpty()) {
                                if (firstTokenTime == null) {
                                    firstTokenTime = System.currentTimeMillis()
                                }
                                tokenCount++
                                textBuilder.append(event.word)
                                if (hasGenerationLoop(textBuilder)) {
                                    Log.w(TAG, "llama.cpp generation stopped early: repetitive output loop detected")
                                    runCatching { currentEngine.stopPrediction() }
                                    break
                                }
                                onToken(event.word)
                            }
                        }
                        is LlamaHelper.LLMEvent.Done -> break
                        is LlamaHelper.LLMEvent.Error -> {
                            throw IllegalStateException("llama.cpp prediction error: ${event.message}")
                        }
                        else -> Unit
                    }
                }
            }

            val endTime = System.currentTimeMillis()
            val durationMs = (endTime - startTime).coerceAtLeast(1L)
            val ttft = firstTokenTime?.let { it - startTime } ?: durationMs
            val tokensPerSec = (tokenCount.toDouble() / (durationMs.toDouble() / 1000.0))

            _state.value = LocalModelSessionState.IDLE

            Result.success(
                RuntimeGenerationResult(
                    text = textBuilder.toString(),
                    tokenCount = tokenCount,
                    timeToFirstTokenMs = ttft,
                    tokensPerSecond = tokensPerSec,
                    durationMs = durationMs,
                )
            )
        } catch (e: CancellationException) {
            runCatching { currentEngine.stopPrediction() }
            _state.value = LocalModelSessionState.IDLE
            throw e
        } catch (e: Throwable) {
            _state.value = LocalModelSessionState.ERROR
            Result.failure(e)
        } finally {
            collector.cancel()
            eventChannel.close()
        }
    }

    override suspend fun cancelGeneration() {
        cancelRequested.set(true)
        runCatching { engine?.stopPrediction() }
    }

    override suspend fun unloadModel(): Result<Unit> = mutex.withLock {
        _state.value = LocalModelSessionState.UNLOADING
        unloadInternal()
        _state.value = LocalModelSessionState.IDLE
        Result.success(Unit)
    }

    private fun unloadInternal() {
        runCatching { engine?.release() }
        engine = null
        engineEvents = null
        _loadedModelId = null
    }

    private fun hasGenerationLoop(output: CharSequence): Boolean {
        val normalized = output.toString().lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.length < MIN_LOOP_PHRASE_CHARS * LOOP_REPETITIONS) return false
        val maxPhrase = minOf(MAX_LOOP_PHRASE_CHARS, normalized.length / LOOP_REPETITIONS)
        for (phraseLength in MIN_LOOP_PHRASE_CHARS..maxPhrase) {
            val suffixLength = phraseLength * LOOP_REPETITIONS
            val suffix = normalized.takeLast(suffixLength)
            val phrase = suffix.take(phraseLength)
            if (phrase.isNotBlank() && suffix == phrase.repeat(LOOP_REPETITIONS)) return true
        }
        return false
    }

    companion object {
        private const val TAG = "LlamaCppRuntime"
        private const val LOAD_TIMEOUT_MS = 60_000L
        private const val GENERATION_TIMEOUT_MS = 180_000L
        private const val LOOP_REPETITIONS = 3
        private const val MIN_LOOP_PHRASE_CHARS = 12
        private const val MAX_LOOP_PHRASE_CHARS = 160
    }
}
