package com.halo.providers.llamacpp

import android.content.Context
import android.net.Uri
import android.util.Log
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.sdk.runtime.NativeResourceKind
import com.halo.sdk.runtime.NativeResourceOwner
import com.halo.sdk.runtime.NativeResourceState
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.nehuatl.llamacpp.LlamaHelper
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class NativeLlamaProvider(
    private val context: Context,
    private val downloadManager: ModelDownloadManager,
    private val storage: Storage,
    private val nativeResourceCoordinator: NativeResourceCoordinator? = null
) : AiInferenceProvider, NativeResourceOwner {
    private var engine: LlamaHelper? = null
    private var engineEvents: MutableSharedFlow<LlamaHelper.LLMEvent>? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loadedModelId: String? = null
    private var loadedContextSize: Int? = null
    private val modelLoadMutex = Mutex()
    private val inferenceMutex = Mutex()
    private val inferenceReservation = AtomicBoolean(false)
    private val _status = MutableStateFlow(ProviderStatus.UNAVAILABLE)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()
    private val systemPrompt: String get() = storage.getString("ai_system_prompt") ?: "You are Halo, a helpful AI."
    private val contextSize: Int get() = storage.getInt("llamacpp_context", 4096)
    override val id: String = "provider.llamacpp.native"
    override val displayName: String = "llama.cpp (native)"
    override val isLocal: Boolean = true
    override val nativeResourceKind: NativeResourceKind = NativeResourceKind.HEAVY_LLM

    override suspend fun getCapabilities(): Set<AiCapability> = setOf(AiCapability.TEXT, AiCapability.STREAMING)

    override suspend fun getStatus(): ProviderStatus {
        val activeId = downloadManager.activeModelId.value
        val current = when {
            activeId == null -> ProviderStatus(ProviderStatus.State.UNCONFIGURED, "No model selected", ProviderReadiness.Unconfigured)
            engine != null && loadedModelId == activeId -> ProviderStatus(ProviderStatus.State.READY, null, ProviderReadiness.Ready)
            downloadManager.getModelFile(activeId) != null -> ProviderStatus(ProviderStatus.State.STANDBY, "Model downloaded; ready to load: $activeId", ProviderReadiness.ConfiguredCold)
            else -> ProviderStatus(ProviderStatus.State.UNAVAILABLE, "Model not downloaded: $activeId", ProviderReadiness.Failed("Not downloaded"))
        }
        _status.value = current
        return current
    }

    override suspend fun testConnection(): Boolean = try {
        loadModelIfNeeded()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "testConnection error", e)
        false
    }

    suspend fun loadModelIfNeeded(): Boolean = modelLoadMutex.withLock {
        inferenceMutex.withLock {
            val activeId = downloadManager.activeModelId.value
            if (activeId == null) {
                _status.value = ProviderStatus.UNCONFIGURED
                return@withLock false
            }
            if (loadedModelId == activeId && engine != null) {
                _status.value = ProviderStatus.READY
                return@withLock true
            }

            val file = downloadManager.getModelFile(activeId)
            if (file == null || !file.exists() || !file.isFile || file.length() <= 0L) {
                _status.value = ProviderStatus(ProviderStatus.State.UNAVAILABLE, "Model file not found: $activeId")
                return@withLock false
            }

            if (nativeResourceCoordinator?.acquire(this) == false) {
                val message = "Native llama.cpp load deferred: another heavyweight native model is busy"
                Log.w(TAG, message)
                _status.value = ProviderStatus(ProviderStatus.State.ERROR, message)
                return@withLock false
            }
            nativeResourceCoordinator?.mark(this, NativeResourceState.LOADING, "loading llama.cpp model $activeId")

            val modelBytes = file.length()
            val androidMemory = AndroidMemorySnapshot.read(context)
            val memorySnapshot = ModelMemoryAdmission.Snapshot(
                androidMemory.totalBytes,
                androidMemory.availableBytes,
                androidMemory.lowMemory
            )
            val ggufMetadata = GgufMetadataReader.read(file)
            val modelProfile = ggufMetadata?.let {
                LocalModelMemoryPlanner.ModelProfile(
                    it.blockCount,
                    it.embeddingLength,
                    it.attentionHeadCount,
                    it.attentionHeadCountKv
                )
            }
            Log.i(
                TAG,
                "Memory admission snapshot: total=${memorySnapshot.totalBytes / BYTES_PER_MB}MB " +
                    "available=${memorySnapshot.availableBytes / BYTES_PER_MB}MB low=${memorySnapshot.lowMemory} " +
                    "appPss=${androidMemory.appPssBytes / BYTES_PER_MB}MB model=${modelBytes / BYTES_PER_MB}MB " +
                    "architecture=${ggufMetadata?.architecture} metadataContext=${ggufMetadata?.contextLength} " +
                    "blocks=${ggufMetadata?.blockCount} embedding=${ggufMetadata?.embeddingLength} " +
                    "heads=${ggufMetadata?.attentionHeadCount} kvHeads=${ggufMetadata?.attentionHeadCountKv}"
            )

            val admission = ModelMemoryAdmission.evaluate(modelBytes, memorySnapshot)
            if (admission is ModelMemoryAdmission.Decision.Reject) {
                val modelMb = modelBytes / BYTES_PER_MB
                val totalMb = memorySnapshot.totalBytes / BYTES_PER_MB
                val availableMb = memorySnapshot.availableBytes / BYTES_PER_MB
                val message = when (admission.reason) {
                    ModelMemoryAdmission.Reason.SYSTEM_LOW_MEMORY ->
                        "Native model load blocked: Android reports low memory (${availableMb}MB available). Close other apps and retry."
                    ModelMemoryAdmission.Reason.TOTAL_MEMORY_LIMIT -> {
                        val totalFractionLimit = (memorySnapshot.totalBytes / 20L) * 11L +
                            (memorySnapshot.totalBytes % 20L) * 11L / 20L
                        val reserveBytes = ModelMemoryAdmission.reserveBytesFor(modelBytes)
                        val maxMb = minOf(
                            totalFractionLimit,
                            (memorySnapshot.totalBytes - reserveBytes).coerceAtLeast(0L)
                        ) / BYTES_PER_MB
                        "Model $activeId is ${modelMb}MB but this device has ${totalMb}MB RAM; native loading is limited to ${maxMb}MB with ${reserveBytes / BYTES_PER_MB}MB reserved headroom. Select a smaller GGUF model."
                    }
                    ModelMemoryAdmission.Reason.AVAILABLE_MEMORY_LIMIT -> {
                        val reserveBytes = ModelMemoryAdmission.reserveBytesFor(modelBytes)
                        "Native model load blocked: $modelMb MB model needs ${reserveBytes / BYTES_PER_MB}MB additional RAM headroom, but only ${availableMb}MB is currently available. Close other apps and retry."
                    }
                    ModelMemoryAdmission.Reason.INVALID_MEMORY_SNAPSHOT ->
                        "Native model load blocked: Android returned invalid memory information."
                }
                Log.e(TAG, message)
                _status.value = ProviderStatus(ProviderStatus.State.ERROR, message)
                nativeResourceCoordinator?.release(this)
                return@withLock false
            }

            val plan = LocalModelMemoryPlanner.choose(
                modelBytes,
                LocalModelMemoryPlanner.MemorySnapshot(
                    memorySnapshot.totalBytes,
                    memorySnapshot.availableBytes,
                    memorySnapshot.lowMemory
                ),
                LocalModelMemoryPlanner.defaultCandidates(contextSize, 0, false),
                modelProfile
            )
            if (plan == null) {
                val message = "Native model load blocked: no safe context size fits the current memory envelope. Close other apps or lower the llama.cpp context setting."
                Log.e(TAG, message)
                _status.value = ProviderStatus(ProviderStatus.State.ERROR, message)
                nativeResourceCoordinator?.release(this)
                return@withLock false
            }

            val effectiveContextSize = plan.candidate.contextSize
            if (effectiveContextSize != contextSize) {
                Log.w(
                    TAG,
                    "Reducing llama.cpp context from requested=$contextSize to planned=$effectiveContextSize " +
                        "modelBytes=$modelBytes estimatedBytes=${plan.estimatedBytes} remainingBytes=${plan.remainingBytes}"
                )
            }
            _status.value = ProviderStatus(ProviderStatus.State.LOADING, "Loading $activeId...")
            releaseCurrentModel()

            return@withLock try {
                val modelUri = Uri.fromFile(file).toString()
                Log.i(
                    TAG,
                    "Loading GGUF model: uri=$modelUri scheme=${Uri.parse(modelUri).scheme} (${file.length()} bytes) " +
                        "architecture=${ggufMetadata?.architecture} metadataContext=${ggufMetadata?.contextLength} " +
                        "effectiveContext=$effectiveContextSize"
                )
                val events = MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity = 64)
                val loadResult = CompletableDeferred<String?>()
                val loadCollector = scope.launch {
                    events.collect { event ->
                        when (event) {
                            is LlamaHelper.LLMEvent.Error -> {
                                Log.e(TAG, "Native llama.cpp model load error: ${event.message}")
                                loadResult.complete(event.message ?: "Native llama.cpp model initialization failed")
                            }
                            is LlamaHelper.LLMEvent.Loaded ->
                                Log.i(TAG, "Native llama.cpp model load event received: ${event.path}")
                            else -> Unit
                        }
                    }
                }
                var helper: LlamaHelper? = null
                var installed = false
                try {
                    helper = LlamaHelper(context.contentResolver, scope, events)
                    helper.load(modelUri, effectiveContextSize, null) { contextId ->
                        Log.i(
                            TAG,
                            "Native llama.cpp model ready contextId=$contextId model=$activeId contextSize=$effectiveContextSize"
                        )
                        loadResult.complete(null)
                    }
                    val loadOutcome = withTimeoutOrNull(MODEL_LOAD_TIMEOUT_MS) { loadResult.await() }
                    if (loadOutcome == null && !loadResult.isCompleted) {
                        Log.e(TAG, "Native llama.cpp model load timed out after ${MODEL_LOAD_TIMEOUT_MS}ms model=$activeId")
                        _status.value = ProviderStatus(
                            ProviderStatus.State.ERROR,
                            "Native llama.cpp model load timed out after ${MODEL_LOAD_TIMEOUT_MS / 1000}s"
                        )
                        false
                    } else if (loadOutcome != null) {
                        _status.value = ProviderStatus(
                            ProviderStatus.State.ERROR,
                            "Native llama.cpp model initialization failed: $loadOutcome"
                        )
                        false
                    } else if (downloadManager.activeModelId.value != activeId) {
                        _status.value = ProviderStatus(
                            ProviderStatus.State.UNAVAILABLE,
                            "Model selection changed while loading"
                        )
                        false
                    } else {
                        engine = helper
                        engineEvents = events
                        loadedModelId = activeId
                        loadedContextSize = effectiveContextSize
                        installed = true
                        nativeResourceCoordinator?.mark(this, NativeResourceState.RESIDENT, "llama.cpp model resident: $activeId")
                        _status.value = ProviderStatus.READY
                        true
                    }
                } finally {
                    loadCollector.cancel()
                    if (!installed) runCatching { helper?.release() }
                }
            } catch (error: CancellationException) {
                releaseCurrentModel()
                nativeResourceCoordinator?.release(this)
                _status.value = ProviderStatus(ProviderStatus.State.UNAVAILABLE, "Model loading cancelled")
                throw error
            } catch (error: Throwable) {
                releaseCurrentModel()
                nativeResourceCoordinator?.release(this)
                Log.e(TAG, "Failed loading model ${file.absolutePath}", error)
                _status.value = ProviderStatus(
                    ProviderStatus.State.ERROR,
                    "Model load failed: ${error.message ?: error::class.simpleName}"
                )
                false
            }.also { loaded ->
                if (!loaded) nativeResourceCoordinator?.release(this)
            }
        }
    }

    override suspend fun releaseIfIdle(): Boolean {
        if (inferenceReservation.get()) return false
        if (!modelLoadMutex.tryLock()) return false
        return try {
            if (inferenceReservation.get()) return false
            if (!inferenceMutex.tryLock()) return false
            try {
                if (inferenceReservation.get()) return false
                releaseCurrentModel()
                true
            } finally {
                inferenceMutex.unlock()
            }
        } finally {
            modelLoadMutex.unlock()
        }
    }

    private fun releaseCurrentModel() {
        runCatching { engine?.release() }
        engine = null
        engineEvents = null
        loadedModelId = null
        loadedContextSize = null
    }

    suspend fun health(): Boolean = downloadManager.activeModelId.value
        ?.let { downloadManager.getModelFile(it) }
        ?.let { it.exists() && it.length() > 0 } == true

    private fun buildPrompt(request: AiRequest, context: AiContext): String {
        val history = context.conversation.joinToString("\n") { "${it.role.name.lowercase()}: ${it.content}" }
        return buildString {
            append("<|im_start|>system\n")
            append(systemPrompt)
            append("<|im_end|>\n")
            if (history.isNotBlank()) append(history).append("\n")
            append("<|im_start|>user\n")
            append(request.text!!.trim())
            append("<|im_end|>\n<|im_start|>assistant\n")
        }
    }

    private suspend fun requireSelectedModel(request: AiRequest): String {
        val activeModelId = downloadManager.activeModelId.value
            ?: throw AiProviderException(AiErrorType.MODEL_NOT_SELECTED, "No local llama.cpp model is selected")
        if (request.modelId != null && request.modelId != activeModelId) {
            throw AiProviderException(
                AiErrorType.MODEL_NOT_FOUND,
                "Selected llama.cpp model '${request.modelId}' is not the active local model '$activeModelId'"
            )
        }
        return activeModelId
    }

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
        request.validateForTextGeneration()
        val modelId = requireSelectedModel(request)
        inferenceReservation.set(true)
        try {
            if (!loadModelIfNeeded()) {
                throw AiProviderException(
                    AiErrorType.MODEL_LOAD_FAILED,
                    status.value.message ?: "Unable to load local model"
                )
            }
            val startedAt = System.currentTimeMillis()
            val prompt = buildPrompt(request, context)
            return inferenceMutex.withLock {
                check(nativeResourceCoordinator?.acquire(this) ?: true) {
                    "Local llama.cpp generation deferred: another heavyweight native model is busy"
                }
                nativeResourceCoordinator?.mark(this, NativeResourceState.IN_USE, "llama.cpp generation")
                val transactionId = UUID.randomUUID().toString().take(8)
                try {
                    val text = buildString {
                        streamInternal(prompt, transactionId).collect { append(it) }
                    }.trim()
                    if (text.isBlank()) throw AiProviderException(AiErrorType.EMPTY_RESPONSE, "llama.cpp returned an empty response")
                    AiResponse(
                        text = text,
                        finishReason = "stop",
                        providerId = id,
                        modelId = modelId,
                        metrics = AiGenerationMetrics(startedAtMs = startedAt, completedAtMs = System.currentTimeMillis())
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: AiProviderException) {
                    throw error
                } catch (error: Throwable) {
                    throw AiProviderException(AiErrorType.GENERATION_FAILED, "Native llama.cpp generation failed", error)
                } finally {
                    if (engine != null) {
                        nativeResourceCoordinator?.mark(this, NativeResourceState.RESIDENT, "llama.cpp generation idle")
                    }
                }
            }
        } finally {
            inferenceReservation.set(false)
        }
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
        request.validateForTextGeneration()
        emit(AiStreamEvent.Started)
        val startedAt = System.currentTimeMillis()
        val output = StringBuilder()
        val transactionId = UUID.randomUUID().toString().take(8)
        inferenceReservation.set(true)
        try {
            val modelId = requireSelectedModel(request)
            if (!loadModelIfNeeded()) {
                throw AiProviderException(AiErrorType.MODEL_LOAD_FAILED, status.value.message ?: "Unable to load llama.cpp model")
            }
            inferenceMutex.withLock {
                check(nativeResourceCoordinator?.acquire(this@NativeLlamaProvider) ?: true) {
                    "Local llama.cpp generation deferred: another heavyweight native model is busy"
                }
                nativeResourceCoordinator?.mark(this@NativeLlamaProvider, NativeResourceState.IN_USE, "llama.cpp streaming generation")
                try {
                    streamInternal(buildPrompt(request, context), transactionId).collect { token ->
                        output.append(token)
                        emit(AiStreamEvent.Token(token))
                    }
                } finally {
                    if (engine != null) {
                        nativeResourceCoordinator?.mark(this@NativeLlamaProvider, NativeResourceState.RESIDENT, "llama.cpp streaming generation idle")
                    }
                }
            }
            val text = output.toString().trim()
            if (text.isBlank()) throw AiProviderException(AiErrorType.EMPTY_RESPONSE, "llama.cpp produced no response text")
            emit(
                AiStreamEvent.Completed(
                    AiResponse(
                        text = text,
                        finishReason = "stop",
                        providerId = id,
                        modelId = modelId,
                        metrics = AiGenerationMetrics(startedAtMs = startedAt, completedAtMs = System.currentTimeMillis())
                    )
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            emit(AiStreamEvent.Failed(error))
        } finally {
            inferenceReservation.set(false)
        }
    }

    fun stream(prompt: String): Flow<String> = flow {
        val transactionId = UUID.randomUUID().toString().take(8)
        inferenceReservation.set(true)
        try {
            inferenceMutex.withLock {
                check(nativeResourceCoordinator?.acquire(this@NativeLlamaProvider) ?: true) {
                    "Local llama.cpp generation deferred: another heavyweight native model is busy"
                }
                nativeResourceCoordinator?.mark(this@NativeLlamaProvider, NativeResourceState.IN_USE, "llama.cpp direct streaming generation")
                try {
                    streamInternal(prompt, transactionId).collect { emit(it) }
                } finally {
                    if (engine != null) {
                        nativeResourceCoordinator?.mark(this@NativeLlamaProvider, NativeResourceState.RESIDENT, "llama.cpp direct streaming idle")
                    }
                }
            }
        } finally {
            inferenceReservation.set(false)
        }
    }

    private fun streamInternal(prompt: String, transactionId: String): Flow<String> = kotlinx.coroutines.flow.channelFlow {
        val currentEngine = engine
            ?: throw AiProviderException(AiErrorType.MODEL_NOT_READY, "Native llama.cpp engine is not loaded")
        val currentEvents = engineEvents
            ?: throw AiProviderException(AiErrorType.MODEL_NOT_READY, "Native llama.cpp event stream is not ready")
        val eventChannel = Channel<LlamaHelper.LLMEvent>(Channel.UNLIMITED)
        var eventCount = 0
        var tokenCount = 0
        var terminalEvent: String? = null
        val collector = launch { currentEvents.collect { eventChannel.send(it) } }
        try {
            currentEvents.subscriptionCount.first { it > 0 }
            currentCoroutineContext().ensureActive()
            currentEngine.predict(prompt, null, true)
            val completed = withTimeoutOrNull(GENERATION_TIMEOUT_MS) {
                for (event in eventChannel) {
                    currentCoroutineContext().ensureActive()
                    eventCount++
                    when (event) {
                        is LlamaHelper.LLMEvent.Ongoing -> if (event.word.isNotEmpty()) {
                            tokenCount++
                            send(event.word)
                        }
                        is LlamaHelper.LLMEvent.Done -> {
                            terminalEvent = "Done"
                            return@withTimeoutOrNull true
                        }
                        is LlamaHelper.LLMEvent.Error -> {
                            terminalEvent = "Error"
                            throw AiProviderException(
                                AiErrorType.GENERATION_FAILED,
                                "llama.cpp generation error: ${event.message ?: event::class.simpleName}"
                            )
                        }
                        else -> if (eventCount <= 8) {
                            Log.d(TAG, "[$transactionId] native event #$eventCount type=${event::class.qualifiedName}")
                        }
                    }
                }
                false
            } ?: false
            if (!completed) {
                throw AiProviderException(
                    AiErrorType.GENERATION_FAILED,
                    "llama.cpp generation timed out after ${GENERATION_TIMEOUT_MS / 1000}s without a terminal event"
                )
            }
        } finally {
            if (terminalEvent == null) {
                Log.w(TAG, "[$transactionId] native event stream ended without terminal event events=$eventCount tokens=$tokenCount")
            }
            collector.cancel()
            eventChannel.close()
        }
    }

    override suspend fun listModels(): List<AiModel> {
        val downloaded = downloadManager.getAllModels().filter { downloadManager.getModelFile(it.id) != null }
        val activeId = downloadManager.activeModelId.value
        return downloaded
            .sortedWith(compareByDescending<CognitiveModel> { it.id == activeId }.thenBy { it.name })
            .map { model ->
                val file = downloadManager.getModelFile(model.id)
                val metadata = file?.let(GgufMetadataReader::read)
                AiModel(
                    id = model.id,
                    displayName = model.name,
                    providerId = id,
                    inputCapabilities = setOf(AiCapability.TEXT),
                    contextLength = metadata?.contextLength?.toLong() ?: 4096L,
                    supportsStreaming = true,
                    supportsTools = false
                )
            }
    }

    suspend fun close() {
        modelLoadMutex.withLock {
            inferenceMutex.withLock {
                releaseCurrentModel()
            }
        }
        nativeResourceCoordinator?.unregister(this)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private companion object {
        private const val TAG = "NativeLlamaProvider"
        private const val MODEL_LOAD_TIMEOUT_MS = 120_000L
        private const val GENERATION_TIMEOUT_MS = 180_000L
        private const val BYTES_PER_MB = 1024L * 1024L
    }
}