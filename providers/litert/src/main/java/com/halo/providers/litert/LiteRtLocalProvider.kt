package com.halo.providers.litert

import android.content.Context
import android.util.Log
import com.halo.providers.litert.models.LiteRtCatalogModel
import com.halo.providers.litert.models.LiteRtModelCatalog
import com.halo.providers.litert.models.LiteRtModelFailureMemory
import com.halo.providers.litert.models.LiteRtModelFailureRecord
import com.halo.providers.litert.models.LiteRtModelPreferences
import com.halo.providers.litert.models.LiteRtToolCallPreference
import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.models.LocalModelInventoryRepository
import com.halo.providers.litert.models.LocalModelInventoryState
import com.halo.providers.litert.models.LocalModelValidation
import com.halo.providers.litert.models.ModelVerificationResult
import com.halo.providers.litert.models.ModelVerificationStage
import com.halo.providers.litert.runtime.HaloPromptToolProtocol
import com.halo.providers.litert.runtime.LiteRtConversationMapper
import com.halo.providers.litert.runtime.LiteRtModelLoadException
import com.halo.providers.litert.runtime.LocalModelBenchmarkResult
import com.halo.providers.litert.runtime.LocalModelBenchmarkRunner
import com.halo.providers.litert.runtime.LocalModelDevelopmentRunner
import com.halo.providers.litert.runtime.LocalModelMatrixRow
import com.halo.providers.litert.runtime.LocalModelSession
import com.halo.providers.litert.runtime.LocalModelSmokeTestResult
import com.halo.providers.litert.runtime.ModelLoadStage
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
import com.halo.sdk.provider.RuntimeConversationMessage
import com.halo.sdk.provider.RuntimeGenerationConfig
import com.halo.sdk.provider.RuntimeGenerationResult
import com.halo.sdk.provider.RuntimeLoadConfig
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolCallProtocol
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.provider.ToolRisk
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

/** Result of the optional development tool-calling probe. Never marks a model broken. */
data class LiteRtToolProbeResult(
    val modelId: String,
    val nativeToolCallObserved: Boolean,
    val toolName: String?,
    val arguments: Map<String, Any>?,
    val responseText: String,
    val failureStage: String?,
    val failureDetail: String?,
) {
    fun summaryLine(): String = buildString {
        append("model=$modelId toolCall=$nativeToolCallObserved")
        toolName?.let { append(" tool=$it") }
        arguments?.let { append(" args=$it") }
        failureStage?.let { append(" stage=$it") }
    }
}

/**
 * Truthful, independently visible facts about one artifact/backend combination.
 *
 * Examples of conflation this type exists to prevent: selected != resident,
 * installed != verified, verified != generation-proven.
 */
data class LiteRtModelRuntimeStatus(
    val modelId: String,
    val displayName: String,
    val installed: Boolean,
    val verified: Boolean,
    val selected: Boolean,
    val resident: Boolean,
    val generationProven: Boolean,
    val requestedBackend: LocalComputeBackend,
    val effectiveBackend: LocalComputeBackend?,
    val effectiveContextTokens: Int?,
    val failureRecord: LiteRtModelFailureRecord?,
)

class LiteRtLocalProvider(
    private val context: Context,
    private val inventoryRepository: LocalModelInventoryRepository,
    private val storage: Storage,
    private val nativeResourceCoordinator: NativeResourceCoordinator? = null,
    val modelSession: LocalModelSession = LocalModelSession(context),
    /** Pinned catalog used to resolve per-artifact runtime profiles. */
    private val catalog: List<LiteRtCatalogModel> = LiteRtModelCatalog.BUILT_IN_MODELS,
    preferences: LiteRtModelPreferences? = null,
    failureMemory: LiteRtModelFailureMemory? = null,
) : AiInferenceProvider, NativeResourceOwner, com.halo.sdk.provider.ToolProtocolReporting {

    override val id: String = ProviderIds.LITERT
    override val displayName: String = "On-device (LiteRT)"
    override val isLocal: Boolean = true
    override val nativeResourceId: String = "litert_local"
    override val nativeResourceKind: NativeResourceKind = NativeResourceKind.HEAVY_LLM

    val preferences: LiteRtModelPreferences = preferences ?: LiteRtModelPreferences(storage)
    val failureMemory: LiteRtModelFailureMemory = failureMemory ?: LiteRtModelFailureMemory(storage)

    /** Observable per-model health for the development Model Browser. */
    val modelMemory: StateFlow<Map<String, LiteRtModelFailureRecord>> get() = failureMemory.records

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

    /** Raw reasoning observed from the last generation; diagnostic only, never spoken. */
    private val _lastReasoningPreview = MutableStateFlow<String?>(null)
    val lastReasoningPreview: StateFlow<String?> = _lastReasoningPreview.asStateFlow()

    private val loadMutex = Mutex()

    /**
     * Artifact fingerprints for which Halo observed a real native tool call. Keyed by
     * fingerprint so a failed or succeeded probe for one revision can never be inherited by
     * another, and so a GPU failure on one model cannot poison a different one.
     */
    private val nativeToolProvenFingerprints =
        java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun LocalModelDescriptor.artifactFingerprint(): String =
        sha256 ?: "id:$id:bytes:$bytes"

    @Volatile
    private var lastLoggedSelection: String? = null

    // -------------------------------------------------------------------------------------
    // Catalog / preferences
    // -------------------------------------------------------------------------------------

    fun catalogModel(modelId: String): LiteRtCatalogModel? = catalog.firstOrNull { it.id == modelId }

    private fun catalogModelFor(descriptor: LocalModelDescriptor): LiteRtCatalogModel? =
        catalogModel(descriptor.id)

    /** Backend Halo will ask for, after developer override and catalog profile resolution. */
    fun requestedBackend(modelId: String): LocalComputeBackend =
        catalogModel(modelId)?.let(preferences::requestedBackend) ?: LocalComputeBackend.AUTO

    fun setDeveloperBackend(modelId: String, backend: LocalComputeBackend) {
        preferences.setDeveloperBackend(modelId, backend)
        Log.i(TAG, "$EVENT_SELECT model=$modelId requestedBackend=${backend.name} source=developer")
    }

    fun clearDeveloperBackend(modelId: String) {
        preferences.clearDeveloperBackend(modelId)
    }

    fun loadConfigFor(descriptor: LocalModelDescriptor): RuntimeLoadConfig =
        preferences.buildLoadConfig(catalogModelFor(descriptor), descriptor)

    fun isGenerationProven(descriptor: LocalModelDescriptor): Boolean =
        failureMemory.isGenerationProven(descriptor.id, descriptor.sha256)

    // -------------------------------------------------------------------------------------
    // Status / readiness
    // -------------------------------------------------------------------------------------

    private val configuredModelId: String?
        get() = storage.getString("active_ai_provider")
            ?.takeIf { it == id }
            ?.let { storage.getString("active_ai_model") }
            ?.trim()
            ?.ifBlank { null }

    private fun logSelectionIfChanged(modelId: String, backend: LocalComputeBackend) {
        if (lastLoggedSelection == modelId) return
        lastLoggedSelection = modelId
        val catalogModel = catalogModel(modelId)
        Log.i(
            TAG,
            "$EVENT_SELECT model=$modelId sha=${catalogModel?.sha256?.take(12) ?: "unknown"} " +
                "requestedBackend=${backend.name} access=${catalogModel?.access?.name ?: "unmanaged"} " +
                "maturity=${catalogModel?.maturity?.name ?: "unknown"}",
        )
    }

    override suspend fun getStatus(): ProviderStatus = withContext(Dispatchers.IO) {
        val modelId = configuredModelId
        if (modelId.isNullOrBlank()) {
            val status = ProviderStatus(
                ProviderStatus.State.UNCONFIGURED,
                "No on-device LiteRT model selected",
                ProviderReadiness.Unconfigured,
            )
            _status.value = status
            return@withContext status
        }

        val descriptor = inventoryRepository.resolveModel(modelId)
        if (descriptor == null) {
            val status = ProviderStatus(
                ProviderStatus.State.MODEL_MISSING,
                "Model '$modelId' is not installed",
                ProviderReadiness.Failed("Model is not installed"),
            )
            _status.value = status
            return@withContext status
        }
        if (!descriptor.valid) {
            val status = ProviderStatus(
                ProviderStatus.State.ERROR,
                "${descriptor.displayName} is ${describeValidation(descriptor.validation)} — re-download it",
                ProviderReadiness.Failed(descriptor.validationDetail ?: "artifact is not usable"),
            )
            _status.value = status
            return@withContext status
        }

        logSelectionIfChanged(modelId, requestedBackend(modelId))

        when (modelSession.activity.value) {
            is LocalModelActivityState.Generating, is LocalModelActivityState.Benchmarking -> {
                val status = ProviderStatus(
                    ProviderStatus.State.BUSY,
                    "LiteRT model '$modelId' is busy inferring",
                    ProviderReadiness.Ready,
                )
                _status.value = status
                return@withContext status
            }
            is LocalModelActivityState.Loading -> {
                val status = ProviderStatus(
                    ProviderStatus.State.LOADING,
                    "Loading ${descriptor.displayName}...",
                    ProviderReadiness.Loading,
                )
                _status.value = status
                return@withContext status
            }
            else -> Unit
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
                ProviderReadiness.Ready,
            )
            _status.value = status
            return@withContext status
        }

        // Installed and verified but cold: still a valid execution target. A router request must
        // load it, not be told the provider is not ready.
        val status = ProviderStatus(
            ProviderStatus.State.STANDBY,
            "Model '$modelId' verified on disk; not loaded (cold)",
            ProviderReadiness.ConfiguredCold,
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
            if (descriptor.supportsAudio) add(AiCapability.AUDIO)
            /*
             * AGENTIC-001: TOOLS describes whether Halo can complete a tool loop with this
             * artifact, which is true for a prompt-mediated loop as well. The native flag
             * stays artifact-scoped and is only set by an observed probe.
             */
            if (toolCallProtocolFor(descriptor).supportsToolLoop) add(AiCapability.TOOLS)
        }
    }

    /**
     * How Halo can complete a tool loop with this artifact. Native calling is claimed only
     * when Halo's own probe observed a real tool call for this exact fingerprint.
     */
    override suspend fun toolCallProtocol(): ToolCallProtocol = withContext(Dispatchers.IO) {
        val modelId = configuredModelId ?: return@withContext ToolCallProtocol.NONE
        val descriptor = inventoryRepository.resolveModel(modelId) ?: return@withContext ToolCallProtocol.NONE
        if (!descriptor.valid) return@withContext ToolCallProtocol.NONE
        toolCallProtocolFor(descriptor)
    }

    private fun toolCallProtocolFor(descriptor: LocalModelDescriptor): ToolCallProtocol {
        if (!descriptor.supportsText) return ToolCallProtocol.NONE
        if (descriptor.supportsTools || nativeToolProven(descriptor)) return ToolCallProtocol.NATIVE
        val preference = catalogModelFor(descriptor)?.toolCallPreference
            ?: LiteRtToolCallPreference.HALO_PROMPT
        return when (preference) {
            LiteRtToolCallPreference.NATIVE -> ToolCallProtocol.NATIVE
            LiteRtToolCallPreference.HALO_PROMPT -> ToolCallProtocol.HALO_PROMPT
            LiteRtToolCallPreference.NONE -> ToolCallProtocol.NONE
        }
    }

    private fun nativeToolProven(descriptor: LocalModelDescriptor): Boolean =
        nativeToolProvenFingerprints.contains(descriptor.artifactFingerprint())

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

        val loadConfig = loadConfigFor(descriptor)
        Log.i(
            TAG,
            "$EVENT_LOAD_START model=${descriptor.id} sha=${descriptor.sha256?.take(12) ?: "unknown"} " +
                "requestedBackend=${loadConfig.computeBackend.name} " +
                "context=${if (loadConfig.useModelDefaultContext) "model-default" else loadConfig.contextSize.toString()}",
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
        val diagnostics = buildDiagnostics(descriptor, loadConfig, failure)
        _loadDiagnostics.value = diagnostics
        failureMemory.recordLoadFailure(
            modelId = descriptor.id,
            artifactFingerprint = descriptor.sha256,
            stage = diagnostics.stage.name,
            backend = loadConfig.computeBackend,
            message = failure?.message,
        )
        // The native cause is preserved in diagnostics; the UI only ever sees a user-safe message.
        _status.value = ProviderStatus(
            ProviderStatus.State.ERROR,
            "${descriptor.displayName} could not be loaded.",
        )
        nativeResourceCoordinator?.release(this)
        return false
    }

    /** Acquires the execution lease for engine work that genuinely needs exclusive compute. */
    private suspend fun acquireExecutionLease(operation: String) {
        val acquired = try {
            nativeResourceCoordinator?.acquire(this) ?: true
        } catch (e: Exception) {
            false
        }
        if (!acquired) {
            throw AiProviderException(
                AiErrorType.SESSION_CONFLICT,
                "Local inference resource is busy",
                AiFailureDetails(retryAfterMs = 1000L, recoverability = Recoverability.RETRY_AFTER),
            )
        }
        nativeResourceCoordinator?.mark(this, NativeResourceState.IN_USE, operation)
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
            contextTokens = modelSession.requestedContextSize
                ?: loadConfig.contextSize.takeUnless { loadConfig.useModelDefaultContext },
            stage = stageOverride ?: resolveFailureStage(loadException),
            exceptionClass = failure?.let { it::class.java.simpleName } ?: "UnknownError",
            exceptionMessage = failure?.message,
        )
        Log.e(
            TAG,
            "${EVENT_LOAD_FAILED} model=${diagnostics.modelId} " +
                "sha=${diagnostics.artifactSha256?.take(12) ?: "unknown"} " +
                "bytes=${diagnostics.bytes} backend=${diagnostics.requestedBackend} " +
                "context=${diagnostics.contextTokens ?: "model-default"} " +
                "type=${diagnostics.exceptionClass} message=${diagnostics.exceptionMessage?.take(300)}",
            failure,
        )
        loadException?.primaryCause?.let {
            Log.e(TAG, "${EVENT_LOAD_FAILED} primaryCause=${it::class.java.simpleName}: ${it.message?.take(300)}", it)
        }
        return diagnostics
    }

    /** Maps a native load failure onto the independently visible Halo failure stage. */
    private fun resolveFailureStage(loadException: LiteRtModelLoadException?): LiteRtFailureStage =
        when (loadException?.stage) {
            ModelLoadStage.ABI,
            ModelLoadStage.ARTIFACT -> LiteRtFailureStage.INVENTORY
            ModelLoadStage.CONVERSATION -> LiteRtFailureStage.CONVERSATION_CREATE
            ModelLoadStage.FIRST_TOKEN -> LiteRtFailureStage.FIRST_TOKEN
            ModelLoadStage.GENERATION -> LiteRtFailureStage.GENERATION
            ModelLoadStage.ENGINE_INIT, null -> engineInitStage(loadException)
        }

    private fun engineInitStage(loadException: LiteRtModelLoadException?): LiteRtFailureStage =
        if (loadException?.requestedBackend == LocalComputeBackend.CPU || loadException?.primaryCause != null) {
            LiteRtFailureStage.ENGINE_INIT_CPU
        } else {
            LiteRtFailureStage.ENGINE_INIT_GPU
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
                    if (desc.supportsAudio) add(AiCapability.AUDIO)
                },
                contextLength = desc.contextWindowTokens.toLong(),
                supportsStreaming = true,
                // True when Halo can complete a tool loop, native or prompt-mediated.
                supportsTools = toolCallProtocolFor(desc).supportsToolLoop
            )
        }
    }

    // -------------------------------------------------------------------------------------
    // Development helpers
    // -------------------------------------------------------------------------------------

    /** Independent view of installed/verified/selected/resident/generation-proven for one model. */
    suspend fun modelStatus(modelId: String): LiteRtModelRuntimeStatus = withContext(Dispatchers.IO) {
        val descriptor = inventoryRepository.resolveModel(modelId)
        val selectedModelId = configuredModelId
        LiteRtModelRuntimeStatus(
            modelId = modelId,
            displayName = descriptor?.displayName ?: modelId,
            installed = descriptor != null,
            verified = descriptor?.verified == true,
            selected = selectedModelId == modelId,
            resident = modelSession.isLoaded() && modelSession.loadedModelId == modelId,
            generationProven = descriptor != null && isGenerationProven(descriptor),
            requestedBackend = requestedBackend(modelId),
            effectiveBackend = if (modelSession.loadedModelId == modelId) modelSession.activeBackend else null,
            effectiveContextTokens = if (modelSession.loadedModelId == modelId) modelSession.effectiveContextSize else null,
            failureRecord = descriptor?.let { failureMemory.record(modelId, it.sha256) },
        )
    }

    /** Loads, generates "HALO_OK", then generates a second prompt without a process restart. */
    suspend fun smokeTestModel(modelId: String): LocalModelSmokeTestResult = withContext(Dispatchers.IO) {
        developmentRunner().smokeTest(modelId) { descriptor -> loadConfigFor(descriptor) }
    }

    /** Development-only serial matrix over every installed model. Never keeps two resident. */
    suspend fun runModelMatrix(): List<LocalModelMatrixRow> = withContext(Dispatchers.IO) {
        val state = inventoryRepository.inventoryState.value
        val installed = (state as? LocalModelInventoryState.Ready)?.models
            ?.filter { it.valid }
            .orEmpty()
        Log.i(TAG, "$EVENT_SWITCH action=matrix models=${installed.size}")
        developmentRunner().runMatrix(
            descriptors = installed,
            loadConfigFor = { descriptor -> loadConfigFor(descriptor) },
        )
    }

    suspend fun benchmarkModel(
        modelId: String,
        prompt: String = LocalModelBenchmarkRunner.DEFAULT_PROMPT,
        maxOutputTokens: Int = LocalModelBenchmarkRunner.DEFAULT_MAX_OUTPUT_TOKENS,
    ): Result<LocalModelBenchmarkResult> = withContext(Dispatchers.IO) {
        LocalModelBenchmarkRunner(modelSession, inventoryRepository).runBenchmark(
            modelId = modelId,
            prompt = prompt,
            maxOutputTokens = maxOutputTokens,
            loadConfigFor = { descriptor -> loadConfigFor(descriptor) },
        )
    }

    /**
     * Optional development capability probe for native tool calling.
     *
     * LiteRT-LM has model-family level tool support, but a family is not an artifact: Halo asks the
     * artifact to call one harmless synthetic tool and records what actually happened. A failed
     * probe never marks the model broken and never disables normal text generation.
     */
    suspend fun probeToolCalling(modelId: String): LiteRtToolProbeResult = withContext(Dispatchers.IO) {
        val descriptor = inventoryRepository.resolveModel(modelId)
            ?: return@withContext LiteRtToolProbeResult(
                modelId, false, null, null, "", "LOAD", "Model is not installed",
            )
        if (!descriptor.valid) {
            return@withContext LiteRtToolProbeResult(
                modelId, false, null, null, "", "ARTIFACT", descriptor.validationDetail,
            )
        }

        val requestId = UUID.randomUUID().toString()
        val loadOk = loadMutex.withLock { loadLocked(descriptor) }
        if (!loadOk) {
            return@withContext LiteRtToolProbeResult(
                modelId, false, null, null, "", "ENGINE_INIT", _loadDiagnostics.value?.technicalSummary(),
            )
        }
        if (modelSession.tryBeginBenchmark(requestId) is com.halo.sdk.provider.InferenceAdmission.Granted) {
            try {
                val result = modelSession.generate(
                    RuntimeGenerationConfig(
                        prompt = "Call the halo_probe tool with value=\"ok\". Do not answer in prose.",
                        maxTokens = 64,
                        temperature = 0.0,
                        availableTools = listOf(HALO_PROBE_TOOL),
                    ),
                    benchmarkRequestId = requestId,
                )
                val generated = result.getOrElse { error ->
                    return@withContext LiteRtToolProbeResult(
                        modelId, false, null, null, "", failureStage(error), error.message?.take(200),
                    )
                }
                val call = generated.toolCalls.firstOrNull { it.functionName == HALO_PROBE_TOOL.name }
                Log.i(
                    TAG,
                    "$EVENT_TOOL_PROBE model=$modelId nativeToolCall=${call != null} " +
                        "sha=${descriptor.sha256?.take(12) ?: "unknown"}",
                )
                if (call != null) nativeToolProvenFingerprints.add(descriptor.artifactFingerprint())
                LiteRtToolProbeResult(
                    modelId = modelId,
                    nativeToolCallObserved = call != null,
                    toolName = call?.functionName,
                    arguments = call?.arguments,
                    responseText = generated.text,
                    failureStage = null,
                    failureDetail = null,
                )
            } finally {
                modelSession.endBenchmark(requestId)
            }
        } else {
            LiteRtToolProbeResult(modelId, false, null, null, "", "BUSY", "Session busy")
        }
    }

    private fun failureStage(error: Throwable): String = when ((error as? LiteRtModelLoadException)?.stage) {
        ModelLoadStage.CONVERSATION -> "CONVERSATION"
        ModelLoadStage.FIRST_TOKEN -> "FIRST_TOKEN"
        ModelLoadStage.GENERATION -> "GENERATION"
        ModelLoadStage.ENGINE_INIT -> "ENGINE_INIT"
        else -> error::class.java.simpleName
    }

    suspend fun unloadLocalModel(): Boolean = withContext(Dispatchers.IO) {
        val modelId = modelSession.loadedModelId
        val ok = modelSession.unloadModel().isSuccess
        Log.i(TAG, "$EVENT_UNLOAD model=$modelId success=$ok")
        ok
    }

    private fun developmentRunner() = LocalModelDevelopmentRunner(modelSession, inventoryRepository)

    // -------------------------------------------------------------------------------------
    // Qualification probe
    // -------------------------------------------------------------------------------------

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
                when (diagnostics?.stage) {
                    LiteRtFailureStage.CONVERSATION_CREATE -> ModelVerificationStage.CONVERSATION
                    else -> ModelVerificationStage.ENGINE_INIT
                },
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
            recordGenerationFailure(modelId, error, stageFor(error))
            return@withContext ModelVerificationResult.Failed(
                modelId,
                when ((error as? LiteRtModelLoadException)?.stage) {
                    ModelLoadStage.CONVERSATION -> ModelVerificationStage.CONVERSATION
                    ModelLoadStage.ENGINE_INIT -> ModelVerificationStage.ENGINE_INIT
                    else -> ModelVerificationStage.GENERATION
                },
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
        failureMemory.recordSuccess(modelId, descriptor.sha256, backend, modelSession.effectiveContextSize)

        Log.i(
            "HALO_LITERT_VERIFY",
            "HALO_LITERT_VERIFY_OK model=$modelId sha=${descriptor.sha256?.take(12) ?: "unknown"} " +
                "backend=$backend loadMs=$loadMs ttftMs=${result.timeToFirstTokenMs}",
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

    // -------------------------------------------------------------------------------------
    // Generation
    // -------------------------------------------------------------------------------------

    /**
     * Tools are only ever exposed for models that declare tool support. Qwen 3 0.6B Dynamic INT4
     * does not, so no tool schema may reach its conversation.
     */
    private fun toolsFor(descriptor: LocalModelDescriptor?, requested: List<ToolDefinition>) =
        if (descriptor != null && toolCallProtocolFor(descriptor).supportsToolLoop) requested else emptyList()

    private suspend fun toolDefinitionsFor(requestContext: AiContext): List<ToolDefinition> {
        val modelId = modelSession.loadedModelId ?: configuredModelId
        val descriptor = modelId?.let { inventoryRepository.resolveModel(it) }
        return toolsFor(descriptor, requestContext.availableTools)
    }

    private suspend fun descriptorForGeneration(): LocalModelDescriptor? =
        (modelSession.loadedModelId ?: configuredModelId)?.let { inventoryRepository.resolveModel(it) }

    /**
     * AGENTIC-001 prompt-mediated loop, applied at the provider adapter boundary.
     *
     * The model's text is the only channel, so one bounded envelope is parsed out of it.
     * Never a guess: [HaloPromptToolProtocol] refuses prose, malformed JSON, partial output
     * and any action name that was not exposed for this turn.
     */
    private data class EffectiveGeneration(val text: String, val toolCalls: List<ToolCall>)

    private fun applyPromptProtocol(
        result: RuntimeGenerationResult,
        usePromptProtocol: Boolean,
        tools: List<ToolDefinition>,
    ): EffectiveGeneration {
        if (!usePromptProtocol || tools.isEmpty()) return EffectiveGeneration(result.text, result.toolCalls)
        val parsed = HaloPromptToolProtocol.parse(result.text, tools)
        parsed.diagnostic?.let { reason ->
            Log.i(
                TAG,
                "HALO_LITERT_TOOL_PROTOCOL action=parse_result calls=${parsed.toolCalls.size} dropped=$reason" +
                    " model=${modelSession.loadedModelId ?: "litert"}",
            )
        }
        if (parsed.toolCalls.isEmpty()) return EffectiveGeneration(result.text, result.toolCalls)
        return EffectiveGeneration(parsed.visibleText, result.toolCalls + parsed.toolCalls)
    }

    private fun systemInstructionFor(
        base: String?,
        tools: List<ToolDefinition>,
        usePromptProtocol: Boolean,
    ): String? {
        if (!usePromptProtocol || tools.isEmpty()) return base
        val instruction = HaloPromptToolProtocol.instruction(tools)
        if (instruction.isBlank()) return base
        return listOfNotNull(base?.takeIf { it.isNotBlank() }, instruction)
            .joinToString("\n\n")
    }

    private fun generationConfig(
        descriptor: LocalModelDescriptor?,
        mappedPrompt: String,
        systemInstruction: String?,
        imageBytes: ByteArray?,
        initialMessages: List<RuntimeConversationMessage>,
        availableTools: List<ToolDefinition>,
        requestMaxTokens: Int?,
    ): RuntimeGenerationConfig {
        val defaults = preferences.generationDefaults(
            descriptor?.let(::catalogModelFor),
            requestMaxTokens,
        )
        return RuntimeGenerationConfig(
            prompt = mappedPrompt,
            systemInstruction = systemInstruction,
            imageBytes = imageBytes,
            initialMessages = initialMessages,
            availableTools = availableTools,
            maxTokens = defaults.maxOutputTokens,
            temperature = defaults.temperature,
            topP = defaults.topP,
            topK = defaults.topK,
        )
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

        acquireExecutionLease("Inferring")

        val descriptor = descriptorForGeneration()
        val mapped = LiteRtConversationMapper.map(request, context)
        val requestedTools = toolDefinitionsFor(context)
        val protocol = descriptor?.let(::toolCallProtocolFor) ?: ToolCallProtocol.NONE
        val usePromptProtocol = protocol == ToolCallProtocol.HALO_PROMPT
        val genConfig = generationConfig(
            descriptor = descriptor,
            mappedPrompt = mapped.outgoingMessage?.content.orEmpty(),
            systemInstruction = systemInstructionFor(mapped.systemInstruction, requestedTools, usePromptProtocol),
            imageBytes = request.image,
            initialMessages = mapped.initialMessages.map { message -> message.toRuntimeMessage() },
            // The prompt protocol advertises actions as text; passing them as native tools
            // too would invite double execution and claim a channel the artifact lacks.
            availableTools = if (usePromptProtocol) emptyList() else requestedTools,
            requestMaxTokens = request.maxOutputTokens,
        )

        return@withContext try {
            val result = modelSession.generate(genConfig).getOrElse { err ->
                if (err is CancellationException) throw err
                recordGenerationFailure(modelSession.loadedModelId, err, stageFor(err))
                throw AiProviderException(AiErrorType.GENERATION_FAILED, "LiteRT generation failed: ${err.message}", err)
            }

            val effective = applyPromptProtocol(result, usePromptProtocol, requestedTools)

            if (effective.text.isBlank() && effective.toolCalls.isEmpty()) {
                val error = AiProviderException(AiErrorType.EMPTY_RESPONSE, "LiteRT produced no response text")
                recordGenerationFailure(modelSession.loadedModelId, error, LiteRtFailureStage.FIRST_TOKEN)
                throw error
            }
            recordReasoning(result.reasoningText)
            descriptorForGeneration()?.let(::markGenerationProven)
            _loadDiagnostics.value = null

            AiResponse(
                text = effective.text,
                providerId = id,
                modelId = modelSession.loadedModelId ?: "litert",
                finishReason = if (effective.toolCalls.isNotEmpty()) "tool_calls" else "stop",
                toolCalls = effective.toolCalls,
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

        acquireExecutionLease("Inferring")

        val descriptor = descriptorForGeneration()
        val mapped = LiteRtConversationMapper.map(request, context)
        val requestedTools = toolDefinitionsFor(context)
        val protocol = descriptor?.let(::toolCallProtocolFor) ?: ToolCallProtocol.NONE
        val usePromptProtocol = protocol == ToolCallProtocol.HALO_PROMPT
        val genConfig = generationConfig(
            descriptor = descriptor,
            mappedPrompt = mapped.outgoingMessage?.content.orEmpty(),
            systemInstruction = systemInstructionFor(mapped.systemInstruction, requestedTools, usePromptProtocol),
            imageBytes = request.image,
            initialMessages = mapped.initialMessages.map { message -> message.toRuntimeMessage() },
            availableTools = if (usePromptProtocol) emptyList() else requestedTools,
            requestMaxTokens = request.maxOutputTokens,
        )

        try {
            val result = modelSession.generate(genConfig) { token ->
                send(AiStreamEvent.Token(token))
            }

            result.onSuccess { gen ->
                val effective = applyPromptProtocol(gen, usePromptProtocol, requestedTools)
                if (effective.text.isNotBlank() || effective.toolCalls.isNotEmpty()) {
                    recordReasoning(gen.reasoningText)
                    descriptorForGeneration()?.let(::markGenerationProven)
                    _loadDiagnostics.value = null
                }
                send(
                    AiStreamEvent.Completed(
                        AiResponse(
                            text = effective.text,
                            providerId = id,
                            modelId = modelSession.loadedModelId ?: "litert",
                            finishReason = if (effective.toolCalls.isNotEmpty()) "tool_calls" else "stop",
                            toolCalls = effective.toolCalls,
                            executionBackend = modelSession.activeBackend?.name,
                        )
                    )
                )
            }.onFailure { err ->
                if (err is CancellationException) throw err
                recordGenerationFailure(modelSession.loadedModelId, err, stageFor(err))
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

    private fun recordReasoning(reasoningText: String?) {
        val preview = reasoningText?.take(200)?.replace('\n', ' ')
        if (preview.isNullOrBlank()) return
        // Diagnostic evidence only: internal reasoning must never reach Android TTS.
        _lastReasoningPreview.value = preview
        Log.i(TAG, "$EVENT_THINKING chars=${reasoningText.length} preview=$preview")
    }

    private fun stageFor(error: Throwable): LiteRtFailureStage =
        when ((error as? LiteRtModelLoadException)?.stage) {
            ModelLoadStage.CONVERSATION -> LiteRtFailureStage.CONVERSATION_CREATE
            ModelLoadStage.FIRST_TOKEN -> LiteRtFailureStage.FIRST_TOKEN
            ModelLoadStage.ENGINE_INIT -> LiteRtFailureStage.ENGINE_INIT_GPU
            else -> LiteRtFailureStage.GENERATION
        }

    private fun markGenerationProven(descriptor: LocalModelDescriptor) {
        failureMemory.recordSuccess(
            modelId = descriptor.id,
            artifactFingerprint = descriptor.sha256,
            backend = modelSession.activeBackend,
            contextTokens = modelSession.effectiveContextSize,
        )
    }

    private suspend fun recordGenerationFailure(
        modelId: String?,
        error: Throwable,
        stage: LiteRtFailureStage,
    ) {
        val descriptor = modelId?.let { inventoryRepository.resolveModel(it) }
        val backend = modelSession.activeBackend
        if (descriptor != null) {
            failureMemory.recordGenerationFailure(
                modelId = descriptor.id,
                artifactFingerprint = descriptor.sha256,
                stage = stage.name,
                backend = backend,
                message = error.message,
            )
            _loadDiagnostics.value = LiteRtLoadDiagnostics(
                modelId = descriptor.id,
                displayName = descriptor.displayName,
                artifactSha256 = descriptor.sha256,
                bytes = descriptor.bytes,
                requestedBackend = requestedBackend(descriptor.id),
                effectiveBackend = backend,
                contextTokens = modelSession.effectiveContextSize,
                stage = stage,
                exceptionClass = error::class.java.simpleName,
                exceptionMessage = error.message?.take(300),
            )
        }
        Log.e(
            TAG,
            "$EVENT_GENERATION_FAILED model=${modelId ?: "unknown"} " +
                "sha=${descriptor?.sha256?.take(12) ?: "unknown"} backend=${backend ?: LocalComputeBackend.AUTO} " +
                "stage=$stage type=${error::class.java.simpleName} message=${error.message?.take(300)}",
            error,
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
        private const val TAG = "LiteRtLocalProvider"

        const val RUNTIME_VERSION = "0.17.1"
        const val VERIFY_PROMPT = "Reply with OK."
        const val VERIFY_MAX_TOKENS = 8
        const val VERIFY_TIMEOUT_MS = 120_000L

        const val EVENT_SELECT = "HALO_LITERT_SELECT"
        const val EVENT_LOAD_START = "HALO_LITERT_LOAD_START"
        const val EVENT_LOAD_FAILED = "HALO_LITERT_LOAD_FAILED"
        const val EVENT_GENERATION_FAILED = "HALO_LITERT_GENERATION_FAILED"
        const val EVENT_SWITCH = "HALO_LITERT_SWITCH"
        const val EVENT_UNLOAD = "HALO_LITERT_UNLOAD"
        const val EVENT_THINKING = "HALO_LITERT_THINKING"
        const val EVENT_TOOL_PROBE = "HALO_LITERT_TOOL_PROBE"

        /** Harmless synthetic tool used only to probe native tool-call support. */
        val HALO_PROBE_TOOL = ToolDefinition(
            id = "halo_probe",
            name = "halo_probe",
            description = "Development probe. Echo a short value back.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "value" to mapOf("type" to "string", "description" to "A short value to echo."),
                ),
                "required" to listOf("value"),
            ),
            risk = ToolRisk.SAFE,
        )
    }
}
