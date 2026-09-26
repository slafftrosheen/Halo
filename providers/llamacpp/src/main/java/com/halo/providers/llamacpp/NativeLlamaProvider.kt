package com.halo.providers.llamacpp

import android.content.Context
import android.util.Log
import com.halo.providers.llamacpp.runtime.LocalModelSession
import com.halo.providers.llamacpp.runtime.LlamaCppRuntime
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.sdk.runtime.NativeResourceKind
import com.halo.sdk.runtime.NativeResourceOwner
import com.halo.sdk.runtime.NativeResourceState
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class NativeLlamaProvider(
    private val context: Context,
    private val downloadManager: ModelDownloadManager,
    private val storage: Storage,
    private val nativeResourceCoordinator: NativeResourceCoordinator? = null,
    val modelSession: LocalModelSession = LocalModelSession(context),
) : AiInferenceProvider, NativeResourceOwner {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val modelLoadMutex = Mutex()
    private val inferenceMutex = Mutex()
    private val inferenceReservations = AtomicInteger(0)
    private val readinessService = LocalInferenceReadinessService(context, downloadManager, nativeResourceCoordinator)
    private val _status = MutableStateFlow(ProviderStatus.UNAVAILABLE)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()

    private val systemPrompt: String get() = storage.getString("ai_system_prompt") ?: "You are Halo, a helpful AI."
    private val contextSize: Int
        get() = storage.getInt("llamacpp_context", LlamaCppRuntime.MAX_MOBILE_CONTEXT_SIZE)
            .coerceIn(LlamaCppRuntime.MIN_MOBILE_CONTEXT_SIZE, LlamaCppRuntime.MAX_MOBILE_CONTEXT_SIZE)
    override val id: String = "provider.llamacpp.native"
    override val displayName: String = "llama.cpp (native)"
    override val isLocal: Boolean = true
    override val nativeResourceKind: NativeResourceKind = NativeResourceKind.HEAVY_LLM

    override suspend fun getCapabilities(): Set<AiCapability> = setOf(AiCapability.TEXT, AiCapability.STREAMING)

    override suspend fun getStatus(): ProviderStatus {
        val activeId = downloadManager.activeModelId.value
        val current = when {
            activeId == null -> ProviderStatus(ProviderStatus.State.UNCONFIGURED, "No model selected", ProviderReadiness.Unconfigured)
            modelSession.isLoaded() && modelSession.loadedModelId == activeId -> ProviderStatus(
                ProviderStatus.State.READY,
                runtimeStatusMessage(),
                ProviderReadiness.Ready,
            )
            downloadManager.getModelFile(activeId) != null -> ProviderStatus(ProviderStatus.State.STANDBY, "Model downloaded; ready to load: $activeId", ProviderReadiness.ConfiguredCold)
            else -> ProviderStatus(ProviderStatus.State.UNAVAILABLE, "Model not downloaded: $activeId", ProviderReadiness.Failed("Not downloaded"))
        }
        _status.value = current
        return current
    }

    override suspend fun testConnection(): Boolean = try {
        withContext(Dispatchers.IO) { loadModelIfNeeded() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "testConnection error", e)
        false
    }

    fun readiness(providerSelected: Boolean = true): LocalInferenceReadiness =
        readinessService.evaluate(providerSelected, nativeResourceId)

    suspend fun loadModelIfNeeded(providerSelected: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        modelLoadMutex.withLock {
            inferenceMutex.withLock {
                val activeId = downloadManager.activeModelId.value
                if (activeId == null) {
                    _status.value = ProviderStatus.UNCONFIGURED
                    return@withLock false
                }
                if (modelSession.loadedModelId == activeId && modelSession.isLoaded()) {
                    _status.value = ProviderStatus(
                        ProviderStatus.State.READY,
                        runtimeStatusMessage(),
                        ProviderReadiness.Ready,
                    )
                    return@withLock true
                }

                val readiness = readiness(providerSelected)
                if (!readiness.ready) {
                    val blocker = readiness.blockers.firstOrNull()
                    val type = when (blocker) {
                        ReadinessBlocker.NO_MODEL_SELECTED -> AiErrorType.MODEL_NOT_SELECTED
                        ReadinessBlocker.MODEL_FILE_MISSING,
                        ReadinessBlocker.DOWNLOAD_INCOMPLETE -> AiErrorType.LOCAL_MODEL_UNAVAILABLE
                        ReadinessBlocker.MODEL_UNSUPPORTED -> AiErrorType.MODEL_UNAVAILABLE
                        ReadinessBlocker.LOW_MEMORY -> AiErrorType.CONTEXT_TOO_LARGE
                        ReadinessBlocker.RESOURCE_BUSY -> AiErrorType.SESSION_CONFLICT
                        ReadinessBlocker.PROVIDER_NOT_SELECTED -> AiErrorType.PROVIDER_UNAVAILABLE
                        ReadinessBlocker.VALIDATION_PENDING, null -> AiErrorType.MODEL_NOT_READY
                    }
                    val message = "Local inference blocked: ${blocker?.name ?: "unknown readiness blocker"}"
                    _status.value = ProviderStatus(
                        state = ProviderStatus.State.STANDBY,
                        message = message,
                        readiness = ProviderReadiness.Failed(message),
                        rejectionReason = if (blocker == ReadinessBlocker.RESOURCE_BUSY) {
                            TargetRejectionReason.RESOURCE_BUSY
                        } else {
                            TargetRejectionReason.PROVIDER_NOT_READY
                        },
                    )
                    return@withLock false
                }

                val file = downloadManager.getModelFile(activeId)
                if (file == null || !file.exists() || !file.isFile || file.length() <= 0L) {
                    _status.value = ProviderStatus(ProviderStatus.State.UNAVAILABLE, "Model file not found: $activeId")
                    return@withLock false
                }

                if (nativeResourceCoordinator?.acquire(this@NativeLlamaProvider) == false) {
                    val message = "Native llama.cpp load deferred: another heavyweight native model is busy"
                    Log.w(TAG, message)
                    _status.value = ProviderStatus(ProviderStatus.State.ERROR, message)
                    return@withLock false
                }
                nativeResourceCoordinator?.mark(this@NativeLlamaProvider, NativeResourceState.LOADING, "loading llama.cpp model $activeId")

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
                    _status.value = ProviderStatus(ProviderStatus.State.ERROR, message, rejectionReason = TargetRejectionReason.MEMORY_PREFLIGHT_FAILED)
                    nativeResourceCoordinator?.release(this@NativeLlamaProvider)
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
                    _status.value = ProviderStatus(ProviderStatus.State.ERROR, message, rejectionReason = TargetRejectionReason.MEMORY_PREFLIGHT_FAILED)
                    nativeResourceCoordinator?.release(this@NativeLlamaProvider)
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

                return@withLock try {
                    val loadConfig = RuntimeLoadConfig(
                        contextSize = effectiveContextSize,
                        computeBackend = LocalComputeBackend.AUTO,
                    )
                    val result = modelSession.loadModel(
                        modelId = activeId,
                        modelPath = file.absolutePath,
                        format = LocalModelFormat.GGUF,
                        config = loadConfig
                    )

                    if (result.isSuccess) {
                        nativeResourceCoordinator?.mark(this@NativeLlamaProvider, NativeResourceState.RESIDENT, "llama.cpp model resident: $activeId")
                        _status.value = ProviderStatus(
                            ProviderStatus.State.READY,
                            runtimeStatusMessage(),
                            ProviderReadiness.Ready,
                        )
                        true
                    } else {
                        val error = result.exceptionOrNull()
                        val message = "Model load failed: ${error?.message ?: "Unknown native error"}"
                        _status.value = ProviderStatus(ProviderStatus.State.ERROR, message)
                        nativeResourceCoordinator?.release(this@NativeLlamaProvider)
                        false
                    }
                } catch (error: CancellationException) {
                    modelSession.unloadModel()
                    nativeResourceCoordinator?.release(this@NativeLlamaProvider)
                    _status.value = ProviderStatus(ProviderStatus.State.UNAVAILABLE, "Model loading cancelled")
                    throw error
                } catch (error: Throwable) {
                    modelSession.unloadModel()
                    nativeResourceCoordinator?.release(this@NativeLlamaProvider)
                    Log.e(TAG, "Failed loading model ${file.absolutePath}", error)
                    _status.value = ProviderStatus(
                        ProviderStatus.State.ERROR,
                        "Model load failed: ${error.message ?: error::class.simpleName}"
                    )
                    false
                }
            }
        }
    }

    private fun runtimeStatusMessage(): String {
        val backend = modelSession.activeBackend?.name ?: "UNKNOWN"
        val context = modelSession.activeConfig?.contextSize ?: contextSize
        val layers = modelSession.activeGpuLayers
        val active = "Runtime: $backend · Context: $context · GPU layers: $layers"
        return modelSession.fallbackReason?.let { "$active · Requested Auto; $it" } ?: active
    }

    override suspend fun releaseIfIdle(): Boolean {
        if (inferenceReservations.get() > 0) return false
        if (!modelLoadMutex.tryLock()) return false
        return try {
            if (inferenceReservations.get() > 0) return false
            if (!inferenceMutex.tryLock()) return false
            try {
                if (inferenceReservations.get() > 0) return false
                modelSession.unloadModel()
                true
            } finally {
                inferenceMutex.unlock()
            }
        } finally {
            modelLoadMutex.unlock()
        }
    }

    suspend fun health(): Boolean = downloadManager.activeModelId.value
        ?.let { downloadManager.getModelFile(it) } != null

    private suspend fun buildPrompt(request: AiRequest, context: AiContext): String = withContext(Dispatchers.IO) {
        buildString {
            appendChatMessage("system", systemPrompt)
            context.conversation.forEach { message ->
                appendChatMessage(message.role.name.lowercase(), message.content)
            }
            context.screenContext?.let { screen ->
                appendChatMessage("user", screen.asUntrustedPromptSection())
            }
            if (request.appendTextAsUserMessage) appendChatMessage("user", request.text!!.trim())
            append("<|im_start|>assistant\n")
        }
    }

    private fun StringBuilder.appendChatMessage(role: String, content: String) {
        if (content.isBlank()) return
        append("<|im_start|>").append(role).append('\n')
        append(content.trim()).append("<|im_end|>\n")
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
        inferenceReservations.incrementAndGet()
        try {
            if (!loadModelIfNeeded()) {
                throw readinessFailure()
            }
            val startedAt = System.currentTimeMillis()
            val prompt = buildPrompt(request, context)
            check(nativeResourceCoordinator?.acquire(this) ?: true) {
                "Local llama.cpp generation deferred: another heavyweight native model is busy"
            }
            nativeResourceCoordinator?.mark(this, NativeResourceState.IN_USE, "llama.cpp generation")
            var firstTokenAtMs = 0L
            return try {
                    val config = RuntimeGenerationConfig(
                        prompt = prompt,
                        priority = request.priority,
                        imageBytes = request.image,
                    )
                    val result = modelSession.generate(config) { token ->
                        if (firstTokenAtMs == 0L) firstTokenAtMs = System.currentTimeMillis()
                    }
                    if (result.isFailure) {
                        throw result.exceptionOrNull() ?: IllegalStateException("Generation failed")
                    }
                    val genResult = result.getOrThrow()
                    val text = genResult.text.trim()
                    if (text.isBlank()) throw AiProviderException(AiErrorType.EMPTY_RESPONSE, "llama.cpp returned an empty response")
                    AiResponse(
                        text = text,
                        finishReason = "stop",
                        providerId = id,
                        modelId = modelId,
                        metrics = AiGenerationMetrics(
                            startedAtMs = startedAt,
                            firstTokenAtMs = if (firstTokenAtMs > 0) firstTokenAtMs else startedAt + genResult.timeToFirstTokenMs,
                            completedAtMs = System.currentTimeMillis(),
                            outputTokens = genResult.tokenCount,
                        )
                    )
            } catch (error: CancellationException) {
                throw error
            } catch (error: AiProviderException) {
                throw error
            } catch (error: Throwable) {
                throw AiProviderException(AiErrorType.GENERATION_FAILED, "Native llama.cpp generation failed", error)
            } finally {
                if (modelSession.isLoaded()) {
                    nativeResourceCoordinator?.mark(this, NativeResourceState.RESIDENT, "llama.cpp generation idle")
                }
            }
        } finally {
            inferenceReservations.decrementAndGet()
        }
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
        request.validateForTextGeneration()
        emit(AiStreamEvent.Started)
        val startedAt = System.currentTimeMillis()
        var firstTokenAtMs = 0L
        var tokenCount = 0
        val output = StringBuilder()
        inferenceReservations.incrementAndGet()
        try {
            val modelId = requireSelectedModel(request)
            if (!loadModelIfNeeded()) {
                throw readinessFailure()
            }
            check(nativeResourceCoordinator?.acquire(this@NativeLlamaProvider) ?: true) {
                "Local llama.cpp generation deferred: another heavyweight native model is busy"
            }
            nativeResourceCoordinator?.mark(this@NativeLlamaProvider, NativeResourceState.IN_USE, "llama.cpp streaming generation")
            try {
                    val config = RuntimeGenerationConfig(
                        prompt = buildPrompt(request, context),
                        priority = request.priority,
                        imageBytes = request.image,
                    )
                    val result = modelSession.generate(config) { token ->
                        if (firstTokenAtMs == 0L) firstTokenAtMs = System.currentTimeMillis()
                        tokenCount++
                        output.append(token)
                        emit(AiStreamEvent.Token(token))
                    }
                    if (result.isFailure) {
                        throw result.exceptionOrNull() ?: IllegalStateException("Streaming generation failed")
                    }
            } finally {
                if (modelSession.isLoaded()) {
                    nativeResourceCoordinator?.mark(this@NativeLlamaProvider, NativeResourceState.RESIDENT, "llama.cpp streaming generation idle")
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
                        metrics = AiGenerationMetrics(
                            startedAtMs = startedAt,
                            firstTokenAtMs = if (firstTokenAtMs > 0) firstTokenAtMs else startedAt,
                            completedAtMs = System.currentTimeMillis(),
                            outputTokens = tokenCount,
                        )
                    )
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            emit(AiStreamEvent.Failed(error))
        } finally {
            inferenceReservations.decrementAndGet()
        }
    }

    fun stream(prompt: String): Flow<String> = flow {
        inferenceReservations.incrementAndGet()
        try {
            check(nativeResourceCoordinator?.acquire(this@NativeLlamaProvider) ?: true) {
                "Local llama.cpp generation deferred: another heavyweight native model is busy"
            }
            nativeResourceCoordinator?.mark(this@NativeLlamaProvider, NativeResourceState.IN_USE, "llama.cpp direct streaming generation")
            try {
                    val config = RuntimeGenerationConfig(prompt = prompt)
                    val result = modelSession.generate(config) { token ->
                        emit(token)
                    }
                    if (result.isFailure) throw result.exceptionOrNull() ?: IllegalStateException("Generation failed")
            } finally {
                if (modelSession.isLoaded()) {
                    nativeResourceCoordinator?.mark(this@NativeLlamaProvider, NativeResourceState.RESIDENT, "llama.cpp direct streaming idle")
                }
            }
        } finally {
            inferenceReservations.decrementAndGet()
        }
    }

    private fun readinessFailure(): AiProviderException {
        val result = readiness(providerSelected = true)
        val blocker = result.blockers.firstOrNull()
        val (type, recoverability) = when (blocker) {
            ReadinessBlocker.NO_MODEL_SELECTED -> AiErrorType.MODEL_NOT_SELECTED to Recoverability.USER_ACTION_REQUIRED
            ReadinessBlocker.MODEL_FILE_MISSING,
            ReadinessBlocker.DOWNLOAD_INCOMPLETE -> AiErrorType.LOCAL_MODEL_UNAVAILABLE to Recoverability.USER_ACTION_REQUIRED
            ReadinessBlocker.MODEL_UNSUPPORTED -> AiErrorType.MODEL_UNAVAILABLE to Recoverability.USER_ACTION_REQUIRED
            ReadinessBlocker.LOW_MEMORY -> AiErrorType.CONTEXT_TOO_LARGE to Recoverability.RETRY_AFTER
            ReadinessBlocker.RESOURCE_BUSY -> AiErrorType.SESSION_CONFLICT to Recoverability.SESSION_CONFLICT
            ReadinessBlocker.PROVIDER_NOT_SELECTED -> AiErrorType.PROVIDER_UNAVAILABLE to Recoverability.USER_ACTION_REQUIRED
            ReadinessBlocker.VALIDATION_PENDING -> AiErrorType.MODEL_NOT_READY to Recoverability.RETRY_AFTER
            null -> AiErrorType.MODEL_LOAD_FAILED to Recoverability.UNKNOWN
        }
        return AiProviderException(
            type = type,
            message = status.value.message ?: "Local model is not ready",
            details = AiFailureDetails(recoverability = recoverability),
        )
    }

    override suspend fun listModels(): List<AiModel> {
        val downloaded = downloadManager.getAllModels().filter { downloadManager.getModelFile(it.id) != null }
        val activeId = downloadManager.activeModelId.value
        return downloaded
            .sortedWith(compareByDescending<CognitiveModel> { it.id == activeId }.thenBy { it.name })
            .map { model ->
                val metadata = downloadManager.resolveModel(model.id)?.ggufMetadata
                AiModel(
                    id = model.id,
                    displayName = model.name,
                    providerId = id,
                    inputCapabilities = setOf(AiCapability.TEXT),
                    // Advertise the context Halo can actually allocate on Android,
                    // not the model's (often much larger) training context.
                    contextLength = minOf(
                        metadata?.contextLength?.toLong() ?: LlamaCppRuntime.MAX_MOBILE_CONTEXT_SIZE.toLong(),
                        LlamaCppRuntime.MAX_MOBILE_CONTEXT_SIZE.toLong(),
                    ),
                    supportsStreaming = true,
                    supportsTools = false
                )
            }
    }

    suspend fun close() {
        modelLoadMutex.withLock {
            inferenceMutex.withLock {
                modelSession.unloadModel()
            }
        }
        nativeResourceCoordinator?.unregister(this)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private companion object {
        private const val TAG = "NativeLlamaProvider"
        private const val BYTES_PER_MB = 1024L * 1024L
    }
}
