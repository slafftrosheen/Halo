package com.halo.providers.litert

import android.content.Context
import android.util.Log
import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.models.LocalModelInventoryRepository
import com.halo.providers.litert.models.LocalModelInventoryState
import com.halo.providers.litert.models.LocalModelValidation
import com.halo.providers.litert.models.ModelVerificationResult
import com.halo.providers.litert.models.ModelVerificationStage
import com.halo.providers.litert.runtime.LiteRtConversationMapper
import com.halo.providers.litert.runtime.LiteRtModelLoadException
import com.halo.providers.litert.runtime.LocalModelSession
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiFailureDetails
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.Recoverability
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.LocalModelActivityState
import com.halo.sdk.provider.ProviderReadiness
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.RuntimeGenerationConfig
import com.halo.sdk.provider.RuntimeConversationMessage
import com.halo.sdk.provider.RuntimeLoadConfig
import com.halo.sdk.provider.validateAgainst
import com.halo.sdk.provider.validateForTextGeneration
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.sdk.runtime.NativeResourceKind
import com.halo.sdk.runtime.NativeResourceOwner
import com.halo.sdk.runtime.NativeResourceState
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

enum class LiteRtFailureStage {
    DOWNLOAD, HASH_VERIFY, INVENTORY, ENGINE_INIT_GPU, ENGINE_INIT_CPU,
    CONVERSATION_CREATE, FIRST_TOKEN, GENERATION, CANCEL, TEARDOWN,
}

/**
 * Bounded technical diagnostics for the last failed local load. Surfaced through System Check /
 * Activity so a native failure is never reduced to "Failed to load <model>".
 */
data class LiteRtLoadDiagnostics(
    val diagnosticsId: String = UUID.randomUUID().toString(),
    val modelId: String,
    val displayName: String,
    val artifactSha256: String?,
    val bytes: Long,
    val requestedBackend: LocalComputeBackend,
    val effectiveBackend: LocalComputeBackend?,
    val contextTokens: Int?,
    val runtimeVersion: String = LiteRtLocalProvider.RUNTIME_VERSION,
    val stage: LiteRtFailureStage,
    val exceptionClass: String,
    val exceptionMessage: String?,
    val recordedAtMs: Long = System.currentTimeMillis(),
) {
    fun technicalSummary(): String = buildString {
        append("id=${diagnosticsId.take(8)} stage=$stage model=$modelId runtime=$runtimeVersion backend=$requestedBackend")
        effectiveBackend?.let { append(" effective=$it") }
        append(" bytes=$bytes")
        append(" fingerprint=${artifactSha256?.take(12) ?: "unknown"}")
        append(" context=${contextTokens ?: "model-default"}")
        append(" type=$exceptionClass")
        exceptionMessage?.let { append(" message=${it.take(300)}") }
    }
}

class LiteRtLocalProvider(
    private val context: Context,
    private val inventoryRepository: LocalModelInventoryRepository,
    private val storage: Storage,
    private val nativeResourceCoordinator: NativeResourceCoordinator? = null,
    val modelSession: LocalModelSession = LocalModelSession(context),
) : AiInferenceProvider, NativeResourceOwner {

    override val id: String = ProviderIds.LITERT
    override val displayName: String = "On-device (LiteRT)"
    override val isLocal: Boolean = true
    override val nativeResourceId: String = "litert_local"
    override val nativeResourceKind: NativeResourceKind = NativeResourceKind.HEAVY_LLM

    override suspend fun releaseIfIdle(): Boolean {
        if (!modelSession.isLoaded()) return true
        val releasingModelId = modelSession.loadedModelId
        val currentActivity = modelSession.activity.value
        if (currentActivity is LocalModelActivityState.Generating ||
            currentActivity is LocalModelActivityState.Loading ||
            currentActivity is LocalModelActivityState.Benchmarking
        ) {
            return false
        }
        val unloadResult = modelSession.unloadModel()
        val unloaded = unloadResult.isSuccess
        if (!unloaded) {
            recordGenerationFailure(
                releasingModelId,
                unloadResult.exceptionOrNull() ?: IllegalStateException("LiteRT teardown failed"),
                LiteRtFailureStage.TEARDOWN,
            )
        }
        // The coordinator owns lease bookkeeping. releaseIfIdle() is invoked while it
        // arbitrates an owner, so re-entering coordinator.release() here can deadlock.
        return unloaded
    }

    private val _status = MutableStateFlow(ProviderStatus.UNAVAILABLE)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()

    private val _loadDiagnostics = MutableStateFlow<LiteRtLoadDiagnostics?>(null)
    val loadDiagnostics: StateFlow<LiteRtLoadDiagnostics?> = _loadDiagnostics.asStateFlow()

    private val loadMutex = Mutex()

    fun isGenerationProven(descriptor: LocalModelDescriptor): Boolean =
        storage.getBoolean(generationProofKey(descriptor), false)

    private fun markGenerationProven(descriptor: LocalModelDescriptor) {
        storage.putBoolean(generationProofKey(descriptor), true)
    }

    private fun generationProofKey(descriptor: LocalModelDescriptor): String =
        "litert_generation_proven_${descriptor.sha256 ?: "unknown"}_$RUNTIME_VERSION"

    private val configuredModelId: String?
        get() = storage.getString("active_ai_provider")
            ?.takeIf { it == id }
            ?.let { storage.getString("active_ai_model") }
            ?.trim()
            ?.ifBlank { null }

    override suspend fun getStatus(): ProviderStatus = withContext(Dispatchers.IO) {
        val modelId = configuredModelId
        if (modelId.isNullOrBlank()) {
            val status = ProviderStatus(ProviderStatus.State.UNCONFIGURED, "No on-device LiteRT model selected")
            _status.value = status
            return@withContext status
        }

        val descriptor = inventoryRepository.resolveModel(modelId)
        if (descriptor == null) {
            val status = ProviderStatus(
                ProviderStatus.State.MODEL_MISSING,
                "Model '$modelId' is not installed",
            )
            _status.value = status
            return@withContext status
        }
        if (!descriptor.valid) {
            val status = ProviderStatus(
                ProviderStatus.State.ERROR,
                "${descriptor.displayName} is ${describeValidation(descriptor.validation)} — re-download it",
            )
            _status.value = status
            return@withContext status
        }

        if (modelSession.isLoaded() && modelSession.loadedModelId == modelId) {
            val backend = modelSession.activeBackend ?: LocalComputeBackend.AUTO
            val status = ProviderStatus(
                ProviderStatus.State.READY,
                if (isGenerationProven(descriptor)) {
                    "LiteRT model '$modelId' generation proven on $backend"
                } else {
                    "LiteRT model '$modelId' resident on $backend; generation not yet proven"
                },
                ProviderReadiness.Ready
            )
            _status.value = status
            return@withContext status
        }

        val status = ProviderStatus(
            ProviderStatus.State.STANDBY,
            "Model '$modelId' verified on disk; not loaded",
            ProviderReadiness.ConfiguredCold
        )
        _status.value = status
        status
    }

    private fun describeValidation(validation: LocalModelValidation): String = when (validation) {
        LocalModelValidation.VERIFIED_ARTIFACT -> "verified"
        LocalModelValidation.UNVERIFIED -> "unverified"
        LocalModelValidation.CORRUPT -> "damaged or stale"
        LocalModelValidation.INCOMPATIBLE -> "not usable"
    }

    override suspend fun getCapabilities(): Set<AiCapability> {
        val modelId = configuredModelId ?: return emptySet()
        val descriptor = inventoryRepository.resolveModel(modelId)?.takeIf { it.valid } ?: return emptySet()
        return buildSet {
            add(AiCapability.TEXT)
            add(AiCapability.STREAMING)
            if (descriptor.supportsVision) add(AiCapability.IMAGE)
            if (descriptor.supportsTools) add(AiCapability.TOOLS)
        }
    }

    override suspend fun testConnection(): Boolean = loadModelIfNeeded()

    suspend fun loadModelIfNeeded(): Boolean = loadMutex.withLock {
        val modelId = configuredModelId ?: return@withLock false
        val descriptor = inventoryRepository.resolveModel(modelId) ?: return@withLock false
        if (!descriptor.valid) {
            _status.value = ProviderStatus(
                ProviderStatus.State.ERROR,
                "${descriptor.displayName} is ${describeValidation(descriptor.validation)} — re-download it",
            )
            return@withLock false
        }
        loadLocked(descriptor)
    }

    private suspend fun loadLocked(descriptor: LocalModelDescriptor): Boolean {
        if (modelSession.isLoaded() && modelSession.loadedModelId == descriptor.id) return true

        val acquired = try {
            nativeResourceCoordinator?.acquire(this) ?: true
        } catch (e: Exception) {
            false
        }
        if (!acquired) {
            _status.value = ProviderStatus(
                ProviderStatus.State.READY,
                "Local inference resource is temporarily busy. Retry shortly.",
                ProviderReadiness.ConfiguredCold
            )
            throw AiProviderException(
                AiErrorType.SESSION_CONFLICT,
                "Local inference resource is busy",
                AiFailureDetails(retryAfterMs = 1000L, recoverability = Recoverability.RETRY_AFTER)
            )
        }

        nativeResourceCoordinator?.mark(this, NativeResourceState.LOADING, "Loading LiteRT model: ${descriptor.id}")
        _status.value = ProviderStatus(ProviderStatus.State.LOADING, "Loading ${descriptor.displayName}...")

        val loadConfig = RuntimeLoadConfig(
            contextSize = descriptor.contextWindowTokens,
            computeBackend = LocalComputeBackend.AUTO,
            useModelDefaultContext = descriptor.useModelDefaultContext,
        )

        val result = try {
            modelSession.loadModel(
                modelId = descriptor.id,
                modelPath = descriptor.path,
                config = loadConfig,
            )
        } catch (cancelled: CancellationException) {
            _loadDiagnostics.value = buildDiagnostics(
                descriptor,
                loadConfig,
                cancelled,
                LiteRtFailureStage.CANCEL,
            )
            nativeResourceCoordinator?.release(this)
            _status.value = ProviderStatus(
                ProviderStatus.State.READY,
                "Loading ${descriptor.displayName} was cancelled",
                ProviderReadiness.ConfiguredCold,
            )
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }

        if (result.isSuccess) {
            _loadDiagnostics.value = null
            nativeResourceCoordinator?.mark(this, NativeResourceState.RESIDENT, "LiteRT resident: ${descriptor.id}")
            val backend = modelSession.activeBackend ?: LocalComputeBackend.AUTO
            _status.value = ProviderStatus(
                ProviderStatus.State.READY,
                if (modelSession.fallbackReason != null) {
                    "Loaded ${descriptor.displayName} on $backend (fallback)"
                } else {
                    "Loaded ${descriptor.displayName} on $backend"
                },
                ProviderReadiness.Ready,
            )
            return true
        }

        val failure = result.exceptionOrNull()
        _loadDiagnostics.value = buildDiagnostics(descriptor, loadConfig, failure)
        // The native cause is preserved in diagnostics; the UI only ever sees a user-safe message.
        _status.value = ProviderStatus(
            ProviderStatus.State.ERROR,
            "${descriptor.displayName} could not be loaded.",
        )
        nativeResourceCoordinator?.release(this)
        return false
    }

    private fun buildDiagnostics(
        descriptor: LocalModelDescriptor,
        loadConfig: RuntimeLoadConfig,
        failure: Throwable?,
        stageOverride: LiteRtFailureStage? = null,
    ): LiteRtLoadDiagnostics {
        val loadException = failure as? LiteRtModelLoadException
        val diagnostics = LiteRtLoadDiagnostics(
            modelId = descriptor.id,
            displayName = descriptor.displayName,
            artifactSha256 = descriptor.sha256,
            bytes = descriptor.bytes,
            requestedBackend = loadException?.requestedBackend ?: loadConfig.computeBackend,
            effectiveBackend = modelSession.activeBackend,
            contextTokens = if (loadConfig.useModelDefaultContext) null else loadConfig.contextSize,
            stage = stageOverride ?: when (loadException?.stage) {
                com.halo.providers.litert.runtime.ModelLoadStage.ABI,
                com.halo.providers.litert.runtime.ModelLoadStage.ARTIFACT -> LiteRtFailureStage.INVENTORY
                com.halo.providers.litert.runtime.ModelLoadStage.CONVERSATION -> LiteRtFailureStage.CONVERSATION_CREATE
                com.halo.providers.litert.runtime.ModelLoadStage.GENERATION -> LiteRtFailureStage.GENERATION
                else -> if (
                    loadException?.requestedBackend == LocalComputeBackend.CPU ||
                    loadException?.primaryCause != null
                ) {
                    LiteRtFailureStage.ENGINE_INIT_CPU
                } else {
                    LiteRtFailureStage.ENGINE_INIT_GPU
                }
            },
            exceptionClass = failure?.let { it::class.java.simpleName } ?: "UnknownError",
            exceptionMessage = failure?.message,
        )
        Log.e(
            "HALO_LITERT_LOAD_FAILED",
            "model=${diagnostics.modelId} sha=${diagnostics.artifactSha256 ?: "unknown"} " +
                "bytes=${diagnostics.bytes} backend=${diagnostics.requestedBackend} " +
                "context=${diagnostics.contextTokens ?: "model-default"} " +
                "type=${diagnostics.exceptionClass} message=${diagnostics.exceptionMessage?.take(300)}",
            failure,
        )
        loadException?.primaryCause?.let {
            Log.e("HALO_LITERT_LOAD_FAILED", "primaryCause=${it::class.java.simpleName}: ${it.message?.take(300)}", it)
        }
        return diagnostics
    }

    override suspend fun listModels(): List<AiModel> = withContext(Dispatchers.IO) {
        val state = inventoryRepository.inventoryState.value
        if (state !is LocalModelInventoryState.Ready) return@withContext emptyList()
        state.models.filter(LocalModelDescriptor::valid).map { desc ->
            AiModel(
                id = desc.id,
                displayName = desc.displayName,
                providerId = id,
                inputCapabilities = buildSet {
                    add(AiCapability.TEXT)
                    if (desc.supportsVision) add(AiCapability.IMAGE)
                },
                contextLength = desc.contextWindowTokens.toLong(),
                supportsStreaming = true,
                supportsTools = desc.supportsTools
            )
        }
    }

    /**
     * Qualification probe: resolves the artifact, loads the engine, creates a conversation and
     * proves a non-empty completion. Never run on app launch — only on explicit user request.
     */
    suspend fun verifyModel(modelId: String): ModelVerificationResult = withContext(Dispatchers.IO) {
        val descriptor = inventoryRepository.resolveModel(modelId)
            ?: return@withContext ModelVerificationResult.Failed(
                modelId,
                ModelVerificationStage.DOWNLOAD,
                "Model is not installed.",
            )
        if (!descriptor.valid) {
            return@withContext ModelVerificationResult.Failed(
                modelId,
                ModelVerificationStage.INTEGRITY,
                descriptor.validationDetail ?: "Model file failed integrity verification.",
            )
        }

        val loadStart = System.currentTimeMillis()
        val loaded = loadMutex.withLock { loadLocked(descriptor) }
        if (!loaded) {
            val diagnostics = _loadDiagnostics.value
            return@withContext ModelVerificationResult.Failed(
                modelId,
                ModelVerificationStage.ENGINE_INIT,
                "${descriptor.displayName} could not be loaded.",
                diagnostics?.technicalSummary(),
            )
        }
        val loadMs = System.currentTimeMillis() - loadStart
        val loadBackend = modelSession.activeBackend ?: LocalComputeBackend.AUTO
        val generation = withTimeoutOrNull(VERIFY_TIMEOUT_MS) {
            modelSession.generate(
                RuntimeGenerationConfig(
                    prompt = VERIFY_PROMPT,
                    maxTokens = VERIFY_MAX_TOKENS,
                    temperature = 0.0,
                    priority = com.halo.sdk.provider.InferencePriority.INTERACTIVE,
                    availableTools = emptyList(),
                )
            )
        }

        if (generation == null) {
            recordGenerationFailure(
                modelId,
                java.util.concurrent.TimeoutException("LiteRT verification timed out"),
                LiteRtFailureStage.FIRST_TOKEN,
            )
            return@withContext ModelVerificationResult.Failed(
                modelId,
                ModelVerificationStage.GENERATION,
                "Timed out while waiting for a response from the model.",
            )
        }

        val result = generation.getOrElse { error ->
            recordGenerationFailure(modelId, error, LiteRtFailureStage.GENERATION)
            return@withContext ModelVerificationResult.Failed(
                modelId,
                if (error is LiteRtModelLoadException) ModelVerificationStage.ENGINE_INIT else ModelVerificationStage.GENERATION,
                "The model loaded but could not generate a response.",
                "${error::class.java.simpleName}: ${error.message?.take(300)}",
            )
        }

        val text = result.text.trim()
        if (text.isEmpty()) {
            recordGenerationFailure(
                modelId,
                AiProviderException(AiErrorType.EMPTY_RESPONSE, "LiteRT verification returned empty text"),
                LiteRtFailureStage.FIRST_TOKEN,
            )
            return@withContext ModelVerificationResult.Failed(
                modelId,
                ModelVerificationStage.GENERATION,
                "The model produced an empty response.",
            )
        }

        val backend = modelSession.activeBackend ?: LocalComputeBackend.AUTO

        Log.i(
            "HALO_LITERT_VERIFY",
            "HALO_LITERT_VERIFY_OK model=$modelId backend=$backend loadMs=$loadMs ttftMs=${result.timeToFirstTokenMs}",
        )
        ModelVerificationResult.Verified(
            modelId = modelId,
            backend = backend,
            loadMs = loadMs,
            ttftMs = result.timeToFirstTokenMs,
            sampleText = text.take(80),
            loadBackend = loadBackend,
            generationBackend = backend,
            fallbackReason = modelSession.fallbackReason,
        )
    }

    /**
     * Tools are only ever exposed for models that declare tool support. Qwen 3 0.6B Dynamic INT4
     * does not, so no tool schema may reach its conversation.
     */
    private suspend fun toolsFor(descriptor: LocalModelDescriptor?, requested: List<com.halo.sdk.provider.ToolDefinition>) =
        if (descriptor?.supportsTools == true) requested else emptyList()

    private suspend fun toolDefinitionsFor(requestContext: AiContext): List<com.halo.sdk.provider.ToolDefinition> {
        val modelId = modelSession.loadedModelId ?: configuredModelId
        val descriptor = modelId?.let { inventoryRepository.resolveModel(it) }
        return toolsFor(descriptor, requestContext.availableTools)
    }

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = withContext(Dispatchers.IO) {
        request.validateForTextGeneration()
        request.validateAgainst(getCapabilities())

        if (!loadModelIfNeeded()) {
            throw AiProviderException(
                AiErrorType.LOCAL_MODEL_UNAVAILABLE,
                _loadDiagnostics.value?.let { "${it.displayName} could not be loaded: ${it.stage}" }
                    ?: "Failed to load LiteRT model",
            )
        }

        nativeResourceCoordinator?.mark(this@LiteRtLocalProvider, NativeResourceState.IN_USE, "Inferring")

        val mapped = LiteRtConversationMapper.map(request, context)
        val genConfig = RuntimeGenerationConfig(
            prompt = mapped.outgoingMessage?.content ?: request.text ?: "",
            systemInstruction = mapped.systemInstruction,
            imageBytes = request.image,
            initialMessages = mapped.initialMessages.map { message -> message.toRuntimeMessage() },
            availableTools = toolDefinitionsFor(context),
            maxTokens = request.maxOutputTokens ?: 512,
        )

        return@withContext try {
            val result = modelSession.generate(genConfig).getOrElse { err ->
                if (err is CancellationException) throw err
                recordGenerationFailure(modelSession.loadedModelId, err, LiteRtFailureStage.GENERATION)
                throw AiProviderException(AiErrorType.GENERATION_FAILED, "LiteRT generation failed: ${err.message}", err)
            }

            if (result.text.isBlank()) {
                val error = AiProviderException(AiErrorType.EMPTY_RESPONSE, "LiteRT produced no response text")
                recordGenerationFailure(modelSession.loadedModelId, error, LiteRtFailureStage.FIRST_TOKEN)
                throw error
            }
            inventoryRepository.resolveModel(modelSession.loadedModelId ?: configuredModelId.orEmpty())
                ?.let(::markGenerationProven)
            _loadDiagnostics.value = null

            AiResponse(
                text = result.text,
                providerId = id,
                modelId = modelSession.loadedModelId ?: "litert",
                finishReason = if (result.toolCalls.isNotEmpty()) "tool_calls" else "stop",
                toolCalls = result.toolCalls,
                executionBackend = modelSession.activeBackend?.name,
            )
        } finally {
            nativeResourceCoordinator?.mark(this@LiteRtLocalProvider, NativeResourceState.RESIDENT, "Resident")
        }
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = channelFlow {
        request.validateForTextGeneration()
        request.validateAgainst(getCapabilities())
        send(AiStreamEvent.Started)

        if (!loadModelIfNeeded()) {
            val diagnostics = _loadDiagnostics.value
            send(
                AiStreamEvent.Failed(
                    AiProviderException(
                        AiErrorType.LOCAL_MODEL_UNAVAILABLE,
                        diagnostics?.let { "${it.displayName} could not be loaded." }
                            ?: "Failed to load LiteRT model",
                    )
                )
            )
            return@channelFlow
        }

        nativeResourceCoordinator?.mark(this@LiteRtLocalProvider, NativeResourceState.IN_USE, "Inferring")

        val mapped = LiteRtConversationMapper.map(request, context)
        val genConfig = RuntimeGenerationConfig(
            prompt = mapped.outgoingMessage?.content ?: request.text ?: "",
            systemInstruction = mapped.systemInstruction,
            imageBytes = request.image,
            initialMessages = mapped.initialMessages.map { message -> message.toRuntimeMessage() },
            availableTools = toolDefinitionsFor(context),
            maxTokens = request.maxOutputTokens ?: 512,
        )

        try {
            val result = modelSession.generate(genConfig) { token ->
                send(AiStreamEvent.Token(token))
            }

            result.onSuccess { gen ->
                if (gen.text.isNotBlank()) {
                    inventoryRepository.resolveModel(modelSession.loadedModelId ?: configuredModelId.orEmpty())
                        ?.let(::markGenerationProven)
                    _loadDiagnostics.value = null
                }
                send(
                    AiStreamEvent.Completed(
                        AiResponse(
                            text = gen.text,
                            providerId = id,
                            modelId = modelSession.loadedModelId ?: "litert",
                            finishReason = if (gen.toolCalls.isNotEmpty()) "tool_calls" else "stop",
                            toolCalls = gen.toolCalls,
                            executionBackend = modelSession.activeBackend?.name,
                        )
                    )
                )
            }.onFailure { err ->
                if (err is CancellationException) throw err
                recordGenerationFailure(modelSession.loadedModelId, err, LiteRtFailureStage.GENERATION)
                send(AiStreamEvent.Failed(AiProviderException(AiErrorType.GENERATION_FAILED, err.message ?: "Stream failed", err)))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: AiProviderException) {
            send(AiStreamEvent.Failed(error))
        } catch (error: Throwable) {
            send(AiStreamEvent.Failed(AiProviderException(AiErrorType.GENERATION_FAILED, "LiteRT generation failed", error)))
        } finally {
            nativeResourceCoordinator?.mark(this@LiteRtLocalProvider, NativeResourceState.RESIDENT, "Resident")
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun recordGenerationFailure(
        modelId: String?,
        error: Throwable,
        stage: LiteRtFailureStage,
    ) {
        val descriptor = modelId?.let { inventoryRepository.resolveModel(it) } ?: return
        _loadDiagnostics.value = LiteRtLoadDiagnostics(
            modelId = descriptor.id,
            displayName = descriptor.displayName,
            artifactSha256 = descriptor.sha256,
            bytes = descriptor.bytes,
            requestedBackend = LocalComputeBackend.AUTO,
            effectiveBackend = modelSession.activeBackend,
            contextTokens = descriptor.contextWindowTokens,
            stage = stage,
            exceptionClass = error::class.java.simpleName,
            exceptionMessage = error.message?.take(300),
        )
    }

    private fun com.halo.providers.litert.runtime.LiteRtMappedMessage.toRuntimeMessage() =
        RuntimeConversationMessage(
            role = when (role) {
                "model" -> com.halo.sdk.provider.Role.ASSISTANT
                "tool" -> com.halo.sdk.provider.Role.TOOL
                else -> com.halo.sdk.provider.Role.USER
            },
            content = content,
            toolCalls = toolCalls,
            toolCallId = toolCallId,
        )

    object ProviderIds {
        const val LITERT = "provider.litert.local"
    }

    companion object {
        const val RUNTIME_VERSION = "0.17.1"
        const val VERIFY_PROMPT = "Reply with OK."
        const val VERIFY_MAX_TOKENS = 8
        const val VERIFY_TIMEOUT_MS = 120_000L
    }
}
