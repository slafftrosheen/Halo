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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.nehuatl.llamacpp.LlamaHelper
import java.io.File
import java.util.UUID
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

            // Android is a constrained host. The model's training context is not an
            // allocation request: keep a phone-safe ceiling and progressively
            // downshift if llama.cpp cannot create the KV/context buffers.
            val requestedContext = config.contextSize.coerceIn(MIN_MOBILE_CONTEXT_SIZE, MAX_MOBILE_CONTEXT_SIZE)
            val contextCandidates = contextCandidates(requestedContext)

            // Always fully release the previous native engine before constructing a
            // replacement. Never keep two llama contexts resident during a swap.
            unloadInternal()

            val mmprojUri = config.mmprojPath?.let { path ->
                val projector = File(path)
                if (projector.exists() && projector.isFile) Uri.fromFile(projector).toString() else null
            }

            var loadedHelper: LlamaHelper? = null
            var loadedEvents: MutableSharedFlow<LlamaHelper.LLMEvent>? = null
            var effectiveContext = requestedContext
            var lastFailure: Throwable? = null

            for (candidateContext in contextCandidates) {
                currentCoroutineContext().ensureActive()
                Log.i(
                    TAG,
                    "Loading llama.cpp model=${file.name} bytes=${file.length()} " +
                        "context=$candidateContext requested=${config.contextSize} mmproj=${mmprojUri != null}"
                )

                val events = MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity = 64)
                val loadDeferred = CompletableDeferred<String?>()
                val helper = LlamaHelper(context.contentResolver, scope, events)
                val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    events.collect { event ->
                        if (event is LlamaHelper.LLMEvent.Error && !loadDeferred.isCompleted) {
                            loadDeferred.complete(event.message ?: "Native llama error")
                        }
                    }
                }

                try {
                    helper.load(
                        Uri.fromFile(file).toString(),
                        candidateContext,
                        mmprojUri,
                    ) {
                        if (!loadDeferred.isCompleted) loadDeferred.complete(null)
                    }

                    val outcome = withTimeoutOrNull(LOAD_TIMEOUT_MS) { loadDeferred.await() }
                    if (outcome == null && !loadDeferred.isCompleted) {
                        throw IllegalStateException("llama.cpp model load timed out after ${LOAD_TIMEOUT_MS}ms")
                    }
                    if (outcome != null) throw IllegalStateException(outcome)

                    loadedHelper = helper
                    loadedEvents = events
                    effectiveContext = candidateContext
                    break
                } catch (cancelled: CancellationException) {
                    releaseHelper(helper)
                    throw cancelled
                } catch (failure: Throwable) {
                    lastFailure = failure
                    releaseHelper(helper)
                    Log.w(
                        TAG,
                        "llama.cpp load failed at context=$candidateContext: ${failure.message}",
                    )
                    if (!isContextInitializationFailure(failure) || candidateContext == contextCandidates.last()) {
                        break
                    }
                } finally {
                    collector.cancel()
                }
            }

            val helper = loadedHelper ?: run {
                throw (lastFailure ?: IllegalStateException("llama.cpp failed to initialize model"))
            }

            engine = helper
            engineEvents = loadedEvents
            _loadedModelId = modelId
            _state.value = LocalModelSessionState.IDLE

            val fallbacks = buildList {
                if (effectiveContext != config.contextSize) {
                    add("context reduced from ${config.contextSize} to $effectiveContext for Android native allocation")
                }
                if (config.computeBackend != LocalComputeBackend.CPU || config.gpuLayers != 0) {
                    add("installed llama.cpp binding runs this path on CPU")
                }
            }

            Result.success(
                RuntimeLoadResult(
                    activeBackend = LocalComputeBackend.CPU,
                    activeGpuLayers = 0,
                    effectiveContextSize = effectiveContext,
                    fallbackReason = fallbacks.takeIf { it.isNotEmpty() }?.joinToString("; "),
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

        var stagedImageFile: File? = null

        val eventChannel = Channel<LlamaHelper.LLMEvent>(Channel.UNLIMITED)
        val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            currentEvents.collect { eventChannel.send(it) }
        }

        try {
            currentEvents.subscriptionCount.first { it > 0 }
            currentCoroutineContext().ensureActive()

            val imageUri = config.imageBytes?.let { bytes ->
                val file = File(context.cacheDir, "halo_staged_img_${UUID.randomUUID()}.jpg")
                file.writeBytes(bytes)
                stagedImageFile = file
                Uri.fromFile(file).toString()
            }

            currentEngine.predict(config.prompt, imageUri, true)

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
            stagedImageFile?.delete()
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

    private suspend fun unloadInternal() {
        val old = engine
        engine = null
        engineEvents = null
        _loadedModelId = null
        if (old != null) releaseHelper(old)
    }

    private suspend fun releaseHelper(helper: LlamaHelper) {
        // Native release can block and must finish even if the caller is cancelled.
        withContext(NonCancellable + Dispatchers.Default) {
            runCatching { helper.release() }
                .onFailure { Log.w(TAG, "llama.cpp release failed", it) }
        }
    }

    private fun contextCandidates(requested: Int): List<Int> =
        listOf(requested, 3072, 2048, 1024, 512)
            .map { it.coerceIn(MIN_MOBILE_CONTEXT_SIZE, MAX_MOBILE_CONTEXT_SIZE) }
            .distinct()
            .filter { it <= requested }

    private fun isContextInitializationFailure(error: Throwable): Boolean {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        return message.contains("initialize llama context") ||
            message.contains("initialize the context") ||
            message.contains("create llama_context") ||
            message.contains("failed to allocate buffer") ||
            message.contains("kv cache") ||
            message.contains("context allocation")
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
        /** OneBox uses a deliberately small 4096-token phone-local runtime window.
         * Keep model training context separate from Android allocation truth. */
        const val MAX_MOBILE_CONTEXT_SIZE = 4096
        const val MIN_MOBILE_CONTEXT_SIZE = 512

        private const val LOAD_TIMEOUT_MS = 60_000L
        private const val GENERATION_TIMEOUT_MS = 180_000L
        private const val LOOP_REPETITIONS = 3
        private const val MIN_LOOP_PHRASE_CHARS = 12
        private const val MAX_LOOP_PHRASE_CHARS = 160
    }
}
