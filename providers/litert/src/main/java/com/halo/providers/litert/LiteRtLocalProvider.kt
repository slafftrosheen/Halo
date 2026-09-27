package com.halo.providers.litert

import android.content.Context
import android.util.Log
import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.models.LocalModelInventoryRepository
import com.halo.providers.litert.models.LocalModelInventoryState
import com.halo.providers.litert.runtime.HaloLiteRtToolSchemaAdapter
import com.halo.providers.litert.runtime.LiteRtConversationMapper
import com.halo.providers.litert.runtime.LocalModelSession
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.LocalModelActivityState
import com.halo.sdk.provider.ProviderReadiness
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.RuntimeGenerationConfig
import com.halo.sdk.provider.RuntimeLoadConfig
import com.halo.sdk.provider.validateAgainst
import com.halo.sdk.provider.validateForTextGeneration
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.sdk.runtime.NativeResourceKind
import com.halo.sdk.runtime.NativeResourceOwner
import com.halo.sdk.runtime.NativeResourceState
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
        val currentActivity = modelSession.activity.value
        if (currentActivity is LocalModelActivityState.Generating ||
            currentActivity is LocalModelActivityState.Loading ||
            currentActivity is LocalModelActivityState.Benchmarking
        ) {
            return false
        }
        val unloaded = modelSession.unloadModel().isSuccess
        if (unloaded) {
            nativeResourceCoordinator?.release(this)
        }
        return unloaded
    }

    private val _status = MutableStateFlow(ProviderStatus.UNAVAILABLE)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()

    private val loadMutex = Mutex()

    private val configuredModelId: String?
        get() = storage.getString("litert_model")
            ?.trim()
            ?.ifBlank { null }
            ?: storage.getString("active_ai_model")
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
        if (descriptor == null || !descriptor.valid) {
            val status = ProviderStatus(ProviderStatus.State.MODEL_MISSING, "Model '$modelId' is not installed")
            _status.value = status
            return@withContext status
        }

        if (modelSession.isLoaded() && modelSession.loadedModelId == modelId) {
            val backend = modelSession.activeBackend ?: LocalComputeBackend.AUTO
            val status = ProviderStatus(
                ProviderStatus.State.READY,
                "LiteRT model '$modelId' loaded on $backend",
                ProviderReadiness.Ready
            )
            _status.value = status
            return@withContext status
        }

        val status = ProviderStatus(
            ProviderStatus.State.READY,
            "Model '$modelId' installed and ready to load",
            ProviderReadiness.ConfiguredCold
        )
        _status.value = status
        status
    }

    override suspend fun getCapabilities(): Set<AiCapability> {
        val modelId = configuredModelId ?: return setOf(AiCapability.TEXT, AiCapability.STREAMING)
        val descriptor = inventoryRepository.resolveModel(modelId) ?: return setOf(AiCapability.TEXT, AiCapability.STREAMING)
        return buildSet {
            add(AiCapability.TEXT)
            add(AiCapability.STREAMING)
            if (descriptor.supportsVision) add(AiCapability.IMAGE)
            if (descriptor.supportsTools) add(AiCapability.TOOLS)
        }
    }

    override suspend fun testConnection(): Boolean = loadModelIfNeeded()

    suspend fun loadModelIfNeeded(): Boolean = loadMutex.withLock {
        val modelId = configuredModelId ?: return false
        val descriptor = inventoryRepository.resolveModel(modelId) ?: return false

        if (modelSession.isLoaded() && modelSession.loadedModelId == modelId) return true

        check(nativeResourceCoordinator?.acquire(this) ?: true) {
            "Local inference resource is busy"
        }

        nativeResourceCoordinator?.mark(this, NativeResourceState.LOADING, "Loading LiteRT model: $modelId")
        _status.value = ProviderStatus(ProviderStatus.State.LOADING, "Loading $modelId...")

        return try {
            val loadConfig = RuntimeLoadConfig(
                contextSize = descriptor.contextWindowTokens,
                computeBackend = LocalComputeBackend.AUTO,
            )
            val result = modelSession.loadModel(
                modelId = descriptor.id,
                modelPath = descriptor.path,
                config = loadConfig
            )
            if (result.isSuccess) {
                nativeResourceCoordinator?.mark(this, NativeResourceState.RESIDENT, "LiteRT resident: $modelId")
                _status.value = ProviderStatus(ProviderStatus.State.READY, "Loaded $modelId", ProviderReadiness.Ready)
                true
            } else {
                _status.value = ProviderStatus(ProviderStatus.State.ERROR, "Failed to load $modelId")
                nativeResourceCoordinator?.release(this)
                false
            }
        } catch (e: Exception) {
            _status.value = ProviderStatus(ProviderStatus.State.ERROR, "Load error: ${e.message}")
            nativeResourceCoordinator?.release(this)
            false
        }
    }

    override suspend fun listModels(): List<AiModel> = withContext(Dispatchers.IO) {
        val state = inventoryRepository.inventoryState.value
        if (state !is LocalModelInventoryState.Ready) return@withContext emptyList()
        state.models.map { desc ->
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

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = withContext(Dispatchers.IO) {
        request.validateForTextGeneration()
        request.validateAgainst(getCapabilities())

        if (!loadModelIfNeeded()) {
            throw AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Failed to load LiteRT model")
        }

        nativeResourceCoordinator?.mark(this@LiteRtLocalProvider, NativeResourceState.IN_USE, "Inferring")

        val mapped = LiteRtConversationMapper.map(request, context)
        val genConfig = RuntimeGenerationConfig(
            prompt = mapped.outgoingMessage?.content ?: request.text ?: "",
            systemInstruction = mapped.systemInstruction
        )

        return@withContext try {
            val result = modelSession.generate(genConfig).getOrElse { err ->
                throw AiProviderException(AiErrorType.GENERATION_FAILED, "LiteRT generation failed: ${err.message}", err)
            }

            val parsedCalls = HaloLiteRtToolSchemaAdapter.parseToolCallsFromResponseText(result.text, request.modelId ?: "litert")

            AiResponse(
                text = result.text,
                providerId = id,
                modelId = modelSession.loadedModelId ?: "litert",
                finishReason = if (parsedCalls.isNotEmpty()) "tool_calls" else "stop",
                toolCalls = parsedCalls
            )
        } finally {
            nativeResourceCoordinator?.mark(this@LiteRtLocalProvider, NativeResourceState.RESIDENT, "Resident")
        }
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
        request.validateForTextGeneration()
        request.validateAgainst(getCapabilities())
        emit(AiStreamEvent.Started)

        if (!loadModelIfNeeded()) {
            emit(AiStreamEvent.Failed(AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Failed to load LiteRT model")))
            return@flow
        }

        nativeResourceCoordinator?.mark(this@LiteRtLocalProvider, NativeResourceState.IN_USE, "Inferring")

        val mapped = LiteRtConversationMapper.map(request, context)
        val genConfig = RuntimeGenerationConfig(
            prompt = mapped.outgoingMessage?.content ?: request.text ?: "",
            systemInstruction = mapped.systemInstruction
        )

        try {
            val result = modelSession.generate(genConfig) { token ->
                emit(AiStreamEvent.Token(token))
            }

            result.onSuccess { gen ->
                val parsedCalls = HaloLiteRtToolSchemaAdapter.parseToolCallsFromResponseText(gen.text, request.modelId ?: "litert")
                emit(
                    AiStreamEvent.Completed(
                        AiResponse(
                            text = gen.text,
                            providerId = id,
                            modelId = modelSession.loadedModelId ?: "litert",
                            finishReason = if (parsedCalls.isNotEmpty()) "tool_calls" else "stop",
                            toolCalls = parsedCalls
                        )
                    )
                )
            }.onFailure { err ->
                emit(AiStreamEvent.Failed(AiProviderException(AiErrorType.GENERATION_FAILED, err.message ?: "Stream failed", err)))
            }
        } finally {
            nativeResourceCoordinator?.mark(this@LiteRtLocalProvider, NativeResourceState.RESIDENT, "Resident")
        }
    }.flowOn(Dispatchers.IO)

    object ProviderIds {
        const val LITERT = "provider.litert.local"
    }
}
