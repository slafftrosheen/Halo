package com.halo.providers.litert

import com.halo.providers.litert.runtime.*
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
import com.halo.providers.litert.runtime.LiteRtBackendQualification
import com.halo.providers.litert.runtime.LiteRtBackendQualificationPolicy
import com.halo.providers.litert.runtime.LiteRtBackendQualificationStore
import com.halo.providers.litert.runtime.LiteRtBackendResolution
import com.halo.providers.litert.runtime.LiteRtControlProbeResult
import com.halo.providers.litert.runtime.LiteRtControlProbeRunner
import com.halo.providers.litert.runtime.LiteRtConversationMapper
import com.halo.providers.litert.runtime.LiteRtEngineConfigFactory
import com.halo.providers.litert.runtime.LiteRtGpuStabilityProbeResult
import com.halo.providers.litert.runtime.LiteRtModelLoadException
import com.halo.providers.litert.runtime.LiteRtNativeAttemptJournal
import com.halo.providers.litert.runtime.LiteRtNativeToolCallValidator
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
import com.halo.sdk.provider.AiReasoningMode
import com.halo.sdk.provider.LOCAL_INTERACTIVE_REASONING_TOKEN_BUDGET
import com.halo.sdk.provider.ToolUseMode
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiGenerationMetrics
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.InferencePriority
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
import com.halo.sdk.runtime.RuntimeWorkloadClass
import com.halo.sdk.runtime.ResidencyPreference
import com.halo.sdk.runtime.WorkloadResourcePolicy
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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

/**
 * Result of the optional development tool-calling probe. Never marks a model broken.
 *
 * MINING-P0-001 tightened: never stores raw argument values or generated text. Only structural
 * facts, counts and digests are retained, so the probe can be exported and shown safely.
 */
data class LiteRtToolProbeResult(
    val modelId: String,
    val nativeToolCallObserved: Boolean,
    val toolName: String?,
    val argumentsFieldCount: Int?,
    /** Digest over sorted argument *key names* only: never over values. */
    val argumentsSha256Prefix: String?,
    val outputNonEmpty: Boolean,
    val outputMatchedExpected: Boolean,
    val callIdComplete: Boolean,
    val argumentsStructurallyComplete: Boolean,
    val pairedResponseCount: Int,
    val qualification: LiteRtToolQualification,
    val qualificationKey: String?,
    val failureStage: String?,
    val failureDetail: String?,
    val probedAtMs: Long = System.currentTimeMillis(),
) {
    fun summaryLine(): String = buildString {
        append("model=$modelId toolCall=$nativeToolCallObserved")
        toolName?.let { append(" tool=$it") }
        argumentsFieldCount?.let { append(" argsFields=$it") }
        argumentsSha256Prefix?.let { append(" argsSha=$it") }
        append(" qualification=${qualification.label}")
        append(" outputNonEmpty=$outputNonEmpty")
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
    /** MINING-P0-001 artifact truth, independently visible from verified/resident/proven. */
    val validation: LocalModelValidation? = null,
    val artifactFingerprintPrefix: String? = null,
    val toolQualification: LiteRtToolQualification = LiteRtToolQualification.NOT_APPLICABLE,
    /**
     * DOGFOOD-005 accelerator qualification for this artifact on this device/build/runtime.
     * Independent of verified/resident/proven: installed != verified != resident !=
     * generation-proven != accelerator-qualified.
     */
    val gpuQualification: LiteRtBackendQualification = LiteRtBackendQualification.UNKNOWN,
    /** DOGFOOD-005 backend the AUTO gate would resolve to right now for this model. */
    val resolvedBackend: LocalComputeBackend? = null,
)

/**
 * MINING-P0-001 content-free local-LiteRT facts for diagnostics export.
 *
 * Never contains prompts, generated text, user utterances, tool argument values, credentials or
 * tokens: only states, counts, digests and stage names.
 */
data class LiteRtProviderDiagnostics(
    val modelId: String,
    val displayName: String,
    val installed: Boolean,
    val artifactState: String,
    val artifactFingerprintPrefix: String?,
    val resident: Boolean,
    val requestedBackend: LocalComputeBackend,
    val effectiveBackend: LocalComputeBackend?,
    val requestedContextTokens: Int?,
    val effectiveContextTokens: Int?,
    val generationProven: Boolean,
    val toolQualification: LiteRtToolQualification,
    val qualificationKey: String?,
    val configuredToolCount: Int,
    val toolChannelObservation: String?,
    val lastFailureStage: String?,
    val runtimeVersion: String,
) {
    fun exportLines(): List<String> = buildList {
        add("model=$modelId")
        add("display=$displayName")
        add("artifact=$artifactState (installed=$installed)")
        add("artifactShaPrefix=${artifactFingerprintPrefix ?: "unknown"}")
        add("resident=$resident selected=true")
        add("backend requested=${LiteRtEngineConfigFactory.backendName(requestedBackend)} " +
            "effective=${effectiveBackend?.let(LiteRtEngineConfigFactory::backendName) ?: "none"}")
        add("context requested=${requestedContextTokens ?: "model-default"} " +
            "effective=${effectiveContextTokens ?: "model-default"}")
        add("generationProven=$generationProven")
        add("lastFailureStage=${lastFailureStage ?: "none"}")
        add("runtimeVersion=$runtimeVersion")
        add("toolChannel qualification=${toolQualification.name} key=${qualificationKey ?: "none"}")
        add("toolChannel configuredToolCount=$configuredToolCount")
        toolChannelObservation?.let { add("toolChannel observation=$it") }
    }
}

class LiteRtLocalProvider(
    private val context: Context,
    private val inventoryRepository: LocalModelInventoryRepository,
    private val storage: Storage,
    private val nativeResourceCoordinator: NativeResourceCoordinator? = null,
    /**
     * DOGFOOD-005 durable, content-free journal of the in-flight native attempt. Survives hard
     * native crashes so the next launch can attribute the death to a backend/model. Exposed for
     * the app-level previous-process-exit reconciler.
     */
    val nativeAttemptJournal: LiteRtNativeAttemptJournal = LiteRtNativeAttemptJournal(storage),
    val modelSession: LocalModelSession = LocalModelSession(context, nativeAttemptJournal = nativeAttemptJournal),
    /** Pinned catalog used to resolve per-artifact runtime profiles. */
    private val catalog: List<LiteRtCatalogModel> = LiteRtModelCatalog.BUILT_IN_MODELS,
    preferences: LiteRtModelPreferences? = null,
    failureMemory: LiteRtModelFailureMemory? = null,
) : AiInferenceProvider, NativeResourceOwner, com.halo.sdk.provider.ToolProtocolReporting, com.halo.sdk.provider.ModelInputCapabilityReporting, com.halo.sdk.provider.ModelReadinessReporting, com.halo.sdk.provider.ProviderConfigurationReporting, com.halo.sdk.provider.LocalInferenceExecutionReporting {

    override val configurationDescriptor: com.halo.sdk.provider.ProviderConfigurationDescriptor get() =
        com.halo.sdk.provider.ProviderConfigurationDescriptor(id, displayName, com.halo.sdk.provider.AiExecutionLocation.ON_DEVICE,
            emptyList(), "LITERT_NATIVE", "LOCAL")

    override val id: String = ProviderIds.LITERT
    override val displayName: String = "On-device (LiteRT)"
    override val isLocal: Boolean = true
    override val nativeResourceId: String = "litert_local"
    override val nativeResourceKind: NativeResourceKind = NativeResourceKind.HEAVY_LLM
    @Volatile private var workloadResidency: ResidencyPreference? = null
    override val defaultResourcePolicy get() = WorkloadResourcePolicy(
        workloadClass = RuntimeWorkloadClass.TEXT_CHAT,
        exclusive = true,
        residencyPreference = workloadResidency ?: ResidencyPreference.WARM_EJECTABLE,
        idleGraceMillis = if(workloadResidency==ResidencyPreference.ON_DEMAND) 0L else null,
    )

    val preferences: LiteRtModelPreferences = preferences ?: LiteRtModelPreferences(storage)
    val failureMemory: LiteRtModelFailureMemory = failureMemory ?: LiteRtModelFailureMemory(storage)

    /**
     * DOGFOOD-005 per-(artifact + runtime version + device/build + GPU) qualification state.
     * AUTO only ever selects a GPU that is explicitly QUALIFIED through the stability probe.
     */
    val backendQualificationStore: LiteRtBackendQualificationStore =
        LiteRtBackendQualificationStore(storage)
    private val nativeRecoveryMutex = Mutex()
    private var nativeRecoveryComplete = false

    private suspend fun reconcilePreviousNativeAttempt() = withContext(Dispatchers.IO) {
        nativeRecoveryMutex.withLock {
            if (nativeRecoveryComplete) return@withLock
            val pending = nativeAttemptJournal.pendingAttempt()
            if (pending != null && pending.appSessionId != nativeAttemptJournal.appSessionId) {
                val exit = if (android.os.Build.VERSION.SDK_INT >= 30) runCatching {
                    val processName = android.app.Application.getProcessName()
                    context.getSystemService(android.app.ActivityManager::class.java)
                        ?.getHistoricalProcessExitReasons(context.packageName, 0, 16)
                        ?.filter { it.processName == processName && it.timestamp >= pending.startedAtEpochMs }
                        ?.maxByOrNull { it.timestamp }
                        ?.let { com.halo.providers.litert.runtime.LiteRtProcessExit.normalize(it.reason) }
                }.getOrNull() else null
                if (pending.backend == LocalComputeBackend.GPU) {
                    val decision = backendQualificationStore.recordUncleanExit(
                        pending.artifactSha256, exit ?: com.halo.providers.litert.runtime.LiteRtProcessExitReason.UNKNOWN,
                    )
                    Log.w(TAG, "${LiteRtBackendQualificationStore.EVENT_UNCLEAN_ATTEMPT_FOUND} " +
                        "backend=GPU stage=${pending.stage} exit=$exit outcome=${decision.outcome}")
                }
                nativeAttemptJournal.clear()
            }
            nativeRecoveryComplete = true
        }
    }

    /** Observable per-model health for the development Model Browser. */
    val modelMemory: StateFlow<Map<String, LiteRtModelFailureRecord>> get() = failureMemory.records

    @Volatile private var activeComputeJob: kotlinx.coroutines.Job? = null
    override suspend fun preemptForVoiceInput(): Boolean {
        val job = activeComputeJob ?: return true
        if (!job.isActive) return true
        job.cancel(CancellationException("Voice ASR preempted obsolete inference"))
        job.join()
        return true
    }

    override suspend fun releaseIfIdle(): Boolean {
        if (!loadMutex.tryLock()) return false
        try {
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
        } finally {
            loadMutex.unlock()
        }
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
     * MINING-P0-001 native tool proof is keyed by artifact + LiteRT-LM runtime version + tool-schema
     * contract version, not by model id. The same bytes against another LiteRT version may behave
     * differently, so stale proof must not survive an incompatible runtime/schema change.
     */
    private val nativeToolProvenKeys =
        java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Qualification keys with a probe in flight right now. */
    private val toolProbeInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Last explicit probe failure per qualification key, as (stage, bounded detail). */
    private val lastToolProbeFailures =
        java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

    /** Structured, content-free conversation diagnostics for the last generation. */
    private val _conversationDiagnostics = MutableStateFlow<LiteRtConversationDiagnostics?>(null)
    val conversationDiagnostics: StateFlow<LiteRtConversationDiagnostics?> =
        _conversationDiagnostics.asStateFlow()

    private val generationCounter = java.util.concurrent.atomic.AtomicLong(0L)

    private fun LocalModelDescriptor.artifactFingerprint(): String =
        sha256 ?: "id:$id:bytes:$bytes"

    /** `<sha256>:litertlm-<version>:<schema>`; the unit native proof is stored against. */
    private fun LocalModelDescriptor.qualificationKey(): String =
        LiteRtToolQualificationKey.of(artifactFingerprint(), RUNTIME_VERSION)

    @Volatile
    private var lastLoggedSelection: String? = null

    // -------------------------------------------------------------------------------------
    // Catalog / preferences
    // -------------------------------------------------------------------------------------

    fun catalogModel(modelId: String): LiteRtCatalogModel? =
        (inventoryRepository as? com.halo.providers.litert.models.LiteRtModelManager)?.catalogModel(modelId)
            ?: catalog.firstOrNull { it.id == modelId }

    private fun catalogModelFor(descriptor: LocalModelDescriptor): LiteRtCatalogModel? =
        catalogModel(descriptor.id)

    /** Backend Halo will ask for, after developer override and catalog profile resolution. */
    fun requestedBackend(modelId: String): LocalComputeBackend =
        catalogModel(modelId)?.let(preferences::requestedBackend) ?: LocalComputeBackend.AUTO

    fun setDeveloperBackend(modelId: String, backend: LocalComputeBackend) {
        preferences.setDeveloperBackend(modelId, backend)
        Log.i(TAG, "$EVENT_BACKEND_OVERRIDE model=$modelId backend=${backend.name}")
    }

    fun clearDeveloperBackend(modelId: String) {
        preferences.clearDeveloperBackend(modelId)
    }

    /** Developer context experiment: never starts a load or changes the selected provider. */
    fun contextOverride(modelId: String): Int? = preferences.contextOverride(modelId)
    // Bound only after a verified artifact is actually loaded and native preflight has run.
    // Unknown support stays unavailable; a catalog/family label is never native API evidence.
    private val nativeThinkingByArtifact = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private fun rememberNativeControls(descriptor: LocalModelDescriptor) {
        val sha = descriptor.sha256?.takeIf { descriptor.verified } ?: return
        if (!modelSession.isLoaded() || modelSession.loadedArtifactSha256 != sha) return
        com.halo.providers.litert.runtime.LiteRtModelPreflight.thinkingSupport(
            descriptor.path, descriptor.bytes, descriptor.modifiedAt)?.let { nativeThinkingByArtifact[sha] = it }
    }
    fun nativeThinkingSupported(modelId: String): Boolean {
        val descriptor = (inventoryRepository.inventoryState.value as? LocalModelInventoryState.Ready)?.models?.firstOrNull { it.id == modelId }
            ?: return false
        return descriptor.verified && descriptor.sha256?.let { nativeThinkingByArtifact[it] } == true
    }
    fun userTuning(modelId: String): com.halo.providers.litert.models.LiteRtUserTuning = preferences.tuning(modelId)
    fun setUserTuning(modelId: String, tuning: com.halo.providers.litert.models.LiteRtUserTuning?): Boolean {
        if (catalogModel(modelId) == null) return false
        if (modelSession.isLoaded() && modelSession.loadedModelId == modelId) return false
        if (tuning?.thinkingMode == com.halo.providers.litert.models.LiteRtThinkingMode.ON && !nativeThinkingSupported(modelId)) return false
        preferences.setTuning(modelId, tuning)
        return true
    }

    fun setDeveloperContext(modelId: String, tokens: Int?): Boolean {
        val model = catalogModel(modelId) ?: return false
        if (!com.halo.providers.litert.runtime.LiteRtContextPresetPolicy.isAllowed(tokens, model.contextWindowTokens))
            return false
        if (modelSession.isLoaded() && modelSession.loadedModelId == modelId) return false
        preferences.setContextOverride(modelId, tokens)
        Log.i(TAG, "HALO_LITERT_CONTEXT_PRESET model=$modelId tokens=${tokens ?: "PROFILE"}")
        return true
    }

    fun loadConfigFor(descriptor: LocalModelDescriptor): RuntimeLoadConfig =
        preferences.buildLoadConfig(catalogModelFor(descriptor), descriptor)

    override fun localExecutionBackend(modelId: String): String? =
        modelSession.activeBackend?.name?.takeIf { modelSession.loadedModelId == modelId }

    override fun localExecutionIdentity(modelId: String): String? {
        val descriptor = (inventoryRepository.inventoryState.value as? LocalModelInventoryState.Ready)
            ?.models?.firstOrNull { it.id == modelId && it.valid } ?: return null
        val resident = modelSession.takeIf { it.isLoaded() && it.loadedModelId == modelId &&
            (it.loadedArtifactSha256 == null || it.loadedArtifactSha256 == descriptor.sha256) }
        val config = resident?.activeConfig ?: loadConfigFor(descriptor)
        val backend = resident?.activeBackend ?: backendResolutionForDescriptor(descriptor).effective
        val context = resident?.effectiveContextSize ?: config.contextSize.takeUnless { config.useModelDefaultContext }
        return listOf(resident?.loadedArtifactSha256 ?: descriptor.sha256, RUNTIME_VERSION, backend.name, context, config.cpuThreads,
            resident?.activeGpuLayers ?: if (backend == LocalComputeBackend.CPU) 0 else config.gpuLayers,
            config.visionEnabled, config.audioEnabled).joinToString("|")
    }

    fun isGenerationProven(descriptor: LocalModelDescriptor): Boolean =
        failureMemory.isGenerationProven(descriptor.id, descriptor.sha256)

    // -------------------------------------------------------------------------------------
    // DOGFOOD-005 backend qualification
    // -------------------------------------------------------------------------------------

    /**
     * Applies the qualification gate to a requested backend and emits the bounded
     * `HALO_LITERT_BACKEND_RESOLUTION` event. Pure policy: see [LiteRtBackendQualificationPolicy].
     */
    private fun measuredTotalRamBytes(): Long = runCatching {
        val manager = context.getSystemService(android.app.ActivityManager::class.java)
        android.app.ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }.totalMem
    }.getOrDefault(0L)

    private fun memoryGatedResolution(
        descriptor: LocalModelDescriptor,
        requested: LocalComputeBackend,
    ): LiteRtBackendResolution {
        val explicitGpu = preferences.developerBackend(descriptor.id) == LocalComputeBackend.GPU
        val qualified = LiteRtBackendQualificationPolicy.resolve(
            requested, backendQualificationStore.qualificationFor(descriptor.sha256), explicitGpu)
        return com.halo.providers.litert.runtime.LiteRtMobileBackendPolicy.apply(
            qualified, measuredTotalRamBytes(), explicitGpu)
    }

    private suspend fun resolveBackend(
        descriptor: LocalModelDescriptor,
        requested: LocalComputeBackend,
    ): LiteRtBackendResolution {
        reconcilePreviousNativeAttempt()
        val qualification = backendQualificationStore.qualificationFor(descriptor.sha256)
        val resolution = memoryGatedResolution(descriptor, requested)
        Log.i(
            TAG,
            "${LiteRtBackendQualificationStore.EVENT_BACKEND_RESOLUTION} model=${descriptor.id} " +
                "sha=${descriptor.sha256?.take(12) ?: "unknown"} requested=${requested.name} " +
                "effective=${resolution.effective.name} gpu=${qualification.name} reason=${resolution.reason}",
        )
        return resolution
    }

    /** GPU qualification of the installed artifact of [modelId]; UNKNOWN when not installed. */
    suspend fun gpuQualificationForModel(modelId: String): LiteRtBackendQualification =
        inventoryRepository.resolveModel(modelId)?.let { backendQualificationStore.qualificationFor(it.sha256) }
            ?: LiteRtBackendQualification.UNKNOWN

    /** UI-safe one-liner, e.g. `GPU: unqualified` / `GPU: quarantined (native crash ...)`. */
    suspend fun gpuQualificationSummaryForModel(modelId: String): String =
        inventoryRepository.resolveModel(modelId)
            ?.let { backendQualificationStore.summaryLine(it.sha256) }
            ?: "GPU: not installed"

    /** Resolution that would apply right now if this model were loaded with its stored request. */
    /** Read existing inventory/backend truth without resolving or scanning inventory. */
    fun backendResolutionForDescriptor(descriptor: LocalModelDescriptor): LiteRtBackendResolution =
        memoryGatedResolution(descriptor, loadConfigFor(descriptor).computeBackend)

    suspend fun backendResolutionForModel(modelId: String): LiteRtBackendResolution? {
        val descriptor = inventoryRepository.resolveModel(modelId) ?: return null
        val requested = loadConfigFor(descriptor).computeBackend
        return resolveBackend(descriptor, requested)
    }

    /**
     * DOGFOOD-005 GPU stability probe: [LiteRtControlProbeRunner.LITE_RT_GPU_STABILITY_PROBE_RUNS]
     * complete consecutive GPU sequences through the existing control-probe path. A pass is the
     * only way an accelerator becomes QUALIFIED; a fail leaves it unqualified (no penalty).
     */
    suspend fun gpuStabilityProbe(modelId: String): LiteRtGpuStabilityProbeResult = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            reconcilePreviousNativeAttempt()
            backendQualificationStore.markVerifying(inventoryRepository.resolveModel(modelId)?.sha256)
            val result = LiteRtControlProbeRunner(
                context = context,
                inventoryRepository = inventoryRepository,
                loadConfigFor = { descriptor -> loadConfigFor(descriptor) },
                nativeResourceSnapshot = { nativeResourceCoordinator?.snapshot?.value.orEmpty() },
                residentModelId = { if (modelSession.isLoaded()) modelSession.loadedModelId else null },
                nativeAttemptJournal = nativeAttemptJournal,
            ).runGpuStabilityProbe(modelId)

            val sha = inventoryRepository.resolveModel(modelId)?.sha256
            when {
                result.passed -> {
                    backendQualificationStore.markQualified(sha)
                    Log.i(
                        TAG,
                        "${LiteRtBackendQualificationStore.EVENT_GPU_QUALIFIED} model=$modelId " +
                            "sha=${sha?.take(12) ?: "unknown"} sequences=${result.completedGenerations}",
                    )
                }

                result.completedGenerations == 0 ->
                    backendQualificationStore.markUnknown(sha)

                else ->
                    // The GPU was exercised and failed (or fell back to CPU): it is not penalized
                    // as unsafe — it is simply unproven, and AUTO stays on CPU.
                    backendQualificationStore.markUnknown(sha)
            }
            result
        }
    }

    /** Developer action: forget every qualification verdict for this artifact. */
    suspend fun clearGpuQualification(modelId: String) {
        val sha = inventoryRepository.resolveModel(modelId)?.sha256
        backendQualificationStore.clear(sha)
        Log.i(
            TAG,
            "${LiteRtBackendQualificationStore.EVENT_GPU_QUALIFICATION_CLEARED} model=$modelId " +
                "sha=${sha?.take(12) ?: "unknown"}",
        )
    }

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

    override suspend fun getStatus(): ProviderStatus = modelStatus(configuredModelId, publish = true)

    override suspend fun statusForModel(modelId: String): ProviderStatus = modelStatus(modelId, publish = false)

    private suspend fun modelStatus(modelId: String?, publish: Boolean): ProviderStatus = withContext(Dispatchers.IO) {
        if (modelId.isNullOrBlank()) {
            val status = ProviderStatus(
                ProviderStatus.State.UNCONFIGURED,
                "No on-device LiteRT model selected",
                ProviderReadiness.Unconfigured,
            )
            if (publish) _status.value = status
            return@withContext status
        }

        val descriptor = inventoryRepository.resolveModel(modelId)
        if (descriptor == null) {
            val status = ProviderStatus(
                ProviderStatus.State.MODEL_MISSING,
                "Model '$modelId' is not installed",
                ProviderReadiness.Failed("Model is not installed"),
            )
            if (publish) _status.value = status
            return@withContext status
        }
        if (!descriptor.valid) {
            val status = ProviderStatus(
                ProviderStatus.State.ERROR,
                "${descriptor.displayName} is ${describeValidation(descriptor.validation)} — re-download it",
                ProviderReadiness.Failed(descriptor.validationDetail ?: "artifact is not usable"),
            )
            if (publish) _status.value = status
            return@withContext status
        }

        if (!conversationalArtifactAllowed(descriptor)) {
            val explanation = com.halo.providers.litert.models.LiteRtGenerationAdmission
                .explanation(catalogModel(modelId))
            val status = ProviderStatus(ProviderStatus.State.ERROR, explanation,
                ProviderReadiness.Failed(explanation))
            if (publish) _status.value = status
            return@withContext status
        }
        if (publish) logSelectionIfChanged(modelId, requestedBackend(modelId))

        when (val activity = modelSession.activity.value) {
            is LocalModelActivityState.Generating, is LocalModelActivityState.Benchmarking
                -> if (modelSession.loadedModelId == modelId) {
                val status = ProviderStatus(
                    ProviderStatus.State.BUSY,
                    "LiteRT model '$modelId' is busy inferring",
                    ProviderReadiness.Ready,
                )
                if (publish) _status.value = status
                return@withContext status
            }
            is LocalModelActivityState.Loading -> if (activity.modelId == modelId) {
                val status = ProviderStatus(
                    ProviderStatus.State.LOADING,
                    "Loading ${descriptor.displayName}...",
                    ProviderReadiness.Loading,
                )
                if (publish) _status.value = status
                return@withContext status
            }
            else -> Unit
        }

        val residentRequest = loadConfigFor(descriptor)
        val effectiveRequest = residentRequest.copy(
            computeBackend = backendResolutionForDescriptor(descriptor).effective)
        if (modelSession.isResidentFor(modelId, descriptor.path, descriptor.sha256, effectiveRequest)) {
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
            if (publish) _status.value = status
            return@withContext status
        }

        // Installed and verified but cold: still a valid execution target. A router request must
        // load it, not be told the provider is not ready.
        val status = ProviderStatus(
            ProviderStatus.State.STANDBY,
            "Model '$modelId' verified on disk; not loaded (cold)",
            ProviderReadiness.ConfiguredCold,
        )
        if (publish) _status.value = status
        status
    }

    private fun describeValidation(validation: LocalModelValidation): String = when (validation) {
        LocalModelValidation.VERIFIED_ARTIFACT -> "verified"
        LocalModelValidation.UNVERIFIED -> "unverified"
        LocalModelValidation.CORRUPT -> "damaged or stale"
        LocalModelValidation.INCOMPATIBLE -> "not usable"
    }

    override suspend fun getCapabilities(): Set<AiCapability> {
        val models = (inventoryRepository.inventoryState.value as? LocalModelInventoryState.Ready)?.models.orEmpty()
        return models.filter { it.valid && conversationalArtifactAllowed(it) }
            .flatMap { capabilitiesForModel(it.id) }.toSet()
    }

    override suspend fun capabilitiesForModel(modelId: String): Set<AiCapability> {
        val descriptor = inventoryRepository.resolveModel(modelId)?.takeIf {
            it.valid && conversationalArtifactAllowed(it)
        } ?: return emptySet()
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
    override suspend fun toolCallProtocol(): ToolCallProtocol =
        configuredModelId?.let { toolCallProtocol(it) } ?: ToolCallProtocol.NONE

    override suspend fun toolCallProtocol(modelId: String): ToolCallProtocol = withContext(Dispatchers.IO) {
        val descriptor = inventoryRepository.resolveModel(modelId) ?: return@withContext ToolCallProtocol.NONE
        if (!descriptor.valid) return@withContext ToolCallProtocol.NONE
        toolCallProtocolFor(descriptor)
    }

    /**
     * MINING-P0-001 a catalog boolean is DECLARATION, not proof.
     *
     * NATIVE is claimed only when Halo observed a real native tool call for this exact
     * artifact/runtime/schema key. A declared-native but unproven artifact honestly falls back to
     * the prompt-mediated envelope loop instead of pretending to be native.
     */
    private fun toolCallProtocolFor(descriptor: LocalModelDescriptor): ToolCallProtocol {
        if (!descriptor.supportsText) return ToolCallProtocol.NONE
        if (nativeToolProven(descriptor)) return ToolCallProtocol.NATIVE
        val preference = catalogModelFor(descriptor)?.toolCallPreference
            ?: LiteRtToolCallPreference.HALO_PROMPT
        return when (preference) {
            // Declared native, unproven: Halo can still complete a tool loop via the prompt
            // envelope, which is what the router actually cares about.
            LiteRtToolCallPreference.NATIVE -> ToolCallProtocol.HALO_PROMPT
            LiteRtToolCallPreference.HALO_PROMPT -> ToolCallProtocol.HALO_PROMPT
            LiteRtToolCallPreference.NONE ->
                if (descriptor.supportsTools) ToolCallProtocol.HALO_PROMPT else ToolCallProtocol.NONE
        }
    }

    private fun nativeToolProven(descriptor: LocalModelDescriptor): Boolean =
        nativeToolProvenKeys.contains(descriptor.qualificationKey())

    /**
     * Honest qualification state for one artifact. See [LiteRtToolQualification]: every state maps
     * to something Halo actually observed or declared, never to an assumption.
     */
    fun toolQualificationFor(
        descriptor: LocalModelDescriptor,
        includeProbing: Boolean = true,
    ): LiteRtToolQualification {
        if (!descriptor.supportsText) return LiteRtToolQualification.NOT_APPLICABLE
        val key = descriptor.qualificationKey()
        if (nativeToolProvenKeys.contains(key)) return LiteRtToolQualification.NATIVE_PROVEN
        if (includeProbing && toolProbeInFlight.contains(key)) return LiteRtToolQualification.PROBING
        if (lastToolProbeFailures.containsKey(key)) return LiteRtToolQualification.FAILED_LAST_PROBE
        val preference = catalogModelFor(descriptor)?.toolCallPreference
            ?: LiteRtToolCallPreference.HALO_PROMPT
        val declaresNative = descriptor.supportsTools || preference == LiteRtToolCallPreference.NATIVE
        if (declaresNative) return LiteRtToolQualification.DECLARED
        return when (toolCallProtocolFor(descriptor)) {
            ToolCallProtocol.HALO_PROMPT -> LiteRtToolQualification.PROMPT_MEDIATED
            ToolCallProtocol.NATIVE -> LiteRtToolQualification.NATIVE_PROVEN
            ToolCallProtocol.NONE -> if (preference == LiteRtToolCallPreference.NONE) {
                LiteRtToolQualification.NOT_APPLICABLE
            } else {
                LiteRtToolQualification.PROBE_REQUIRED
            }
        }
    }

    override suspend fun testConnection(): Boolean = loadModelIfNeeded()

    suspend fun loadModelIfNeeded(requestedModelId: String? = null,request: AiRequest? = null): Boolean {
        if(request?.priority==InferencePriority.BACKGROUND) {
            if(!loadMutex.tryLock()) return false
            return try { loadModelIfNeededLocked(requestedModelId,request) } finally { loadMutex.unlock() }
        }
        return loadMutex.withLock { loadModelIfNeededLocked(requestedModelId,request) }
    }

    /** User-initiated AI Lab action. No generation, role mutation, download or hidden GPU choice. */
    suspend fun initializeForWorkloadLab(modelId: String,fresh: Boolean): Pair<Long,String?> = withContext(Dispatchers.IO) {
        check(loadMutex.tryLock()) { "NATIVE_RESOURCE_BUSY" }
        try {
            val descriptor=inventoryRepository.verifyPhysicalArtifact(modelId)?.takeIf { it.verified }
                ?: error("ARTIFACT_NOT_VERIFIED")
            check(!rejectNonGeneration(descriptor)) { "TASK_NOT_GENERATIVE" }
            if(fresh) { modelSession.unloadModel().getOrThrow(); nativeResourceCoordinator?.release(this@LiteRtLocalProvider) }
            workloadResidency=ResidencyPreference.WARM_EJECTABLE
            val started=System.nanoTime()
            check(loadLocked(descriptor,loadConfigFor(descriptor).copy(computeBackend=LocalComputeBackend.CPU))) { "INITIALIZATION_FAILED" }
            ((System.nanoTime()-started)/1_000_000) to modelSession.activeBackend?.name
        } finally { loadMutex.unlock() }
    }

    private fun conversationalArtifactAllowed(descriptor: LocalModelDescriptor): Boolean =
        com.halo.providers.litert.models.LiteRtGenerationAdmission.permitted(
            catalogModel(descriptor.id), java.io.File(descriptor.path).name)

    private fun rejectNonGeneration(descriptor: LocalModelDescriptor): Boolean {
        if (conversationalArtifactAllowed(descriptor)) return false
        val explanation = com.halo.providers.litert.models.LiteRtGenerationAdmission
            .explanation(catalogModel(descriptor.id))
        _status.value = ProviderStatus(ProviderStatus.State.ERROR, explanation,
            ProviderReadiness.Failed(explanation))
        return true
    }

    private suspend fun loadModelIfNeededLocked(requestedModelId: String?, request: AiRequest? = null): Boolean {
        val modelId = requestedModelId ?: configuredModelId ?: return false
        // An unchanged *already verified* inventory row is authoritative for
        // ordinary cold starts. Rehashing every large model on every prompt
        // delays load and can race the startup inventory scan. The explicit
        // Verify action still uses verifyPhysicalArtifact unconditionally.
        val known = inventoryRepository.resolveModel(modelId)
        val descriptor = known?.takeIf { candidate ->
            val artifact = java.io.File(candidate.path)
            candidate.valid && artifact.isFile && artifact.length() == candidate.bytes &&
                artifact.lastModified() == candidate.modifiedAt
        } ?: inventoryRepository.verifyPhysicalArtifact(modelId)
        if (descriptor == null) return false
        if (!descriptor.valid) {
            _status.value = ProviderStatus(
                ProviderStatus.State.ERROR,
                "${descriptor.displayName} is ${describeValidation(descriptor.validation)} — re-download it",
            )
            return false
        }
        if (rejectNonGeneration(descriptor)) return false
        // Even manual imports must not enter native code for the upstream, explicitly
        // non-runnable Bonsai INT1 proof-of-concept. Its format has no LiteRT INT1 kernel.
        // Exact verified artifact fingerprint only; executable ternary Bonsai is unaffected.
        if (com.halo.providers.litert.models.LiteRtCommunityModelGuide.nonRunnableFingerprint(descriptor.sha256)) {
            _status.value = ProviderStatus(ProviderStatus.State.ERROR,
                "This Bonsai 1-bit proof-of-concept has no LiteRT execution kernel. Choose a ternary Bonsai build.")
            return false
        }
        val profile=request?.localExecutionProfile
        val base=loadConfigFor(descriptor)
        val requestedBackend=when(profile?.backend) {
            com.halo.sdk.provider.WorkloadBackend.CPU -> LocalComputeBackend.CPU
            com.halo.sdk.provider.WorkloadBackend.GPU -> LocalComputeBackend.GPU
            null,com.halo.sdk.provider.WorkloadBackend.AUTO -> base.computeBackend
            else -> throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY,"WORKLOAD_BACKEND_UNSUPPORTED")
        }
        if(profile?.backend==com.halo.sdk.provider.WorkloadBackend.GPU && resolveBackend(descriptor,requestedBackend).effective!=LocalComputeBackend.GPU)
            throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY,"WORKLOAD_GPU_UNAVAILABLE_NO_CPU_FALLBACK")
        workloadResidency=profile?.residency
        val config = base.copy(computeBackend=requestedBackend,
            contextSize=profile?.contextTokens?.let { minOf(it,descriptor.contextWindowTokens.takeIf { size -> size>0 } ?: it) } ?: base.contextSize,
            useModelDefaultContext=if(profile!=null) false else base.useModelDefaultContext,
            visionEnabled = request?.image != null, audioEnabled = request?.audio != null)
        if (config.visionEnabled && resolveBackend(descriptor, config.computeBackend).effective != LocalComputeBackend.GPU) {
            throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY,
                "This model's image encoder requires GPU. Verify GPU stability in On-device models before using images.")
        }
        val loaded=loadLocked(descriptor, config,request?.priority)
        if(loaded && profile?.backend==com.halo.sdk.provider.WorkloadBackend.GPU && modelSession.activeBackend!=LocalComputeBackend.GPU)
            throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY,"WORKLOAD_GPU_EFFECTIVE_BACKEND_MISMATCH")
        return loaded
    }

    private suspend fun loadLocked(descriptor: LocalModelDescriptor,
        requestedConfig: RuntimeLoadConfig = loadConfigFor(descriptor),priority: InferencePriority? = null): Boolean {
        if (rejectNonGeneration(descriptor)) return false
        if (com.halo.providers.litert.models.LiteRtCommunityModelGuide.nonRunnableFingerprint(descriptor.sha256)) {
            _status.value = ProviderStatus(ProviderStatus.State.ERROR,
                "The Bonsai INT1 proof-of-concept has no LiteRT execution kernel.")
            return false
        }
        // The selected backend/context may have changed while this model stayed
        // resident. Model ID alone is not sufficient evidence to reuse the engine.
        val desiredConfig = requestedConfig
        val preferredBackend = resolveBackend(descriptor, requestedConfig.computeBackend).effective
        val desiredEffectiveConfig = desiredConfig.copy(computeBackend = preferredBackend)
        if (modelSession.isResidentFor(descriptor.id, descriptor.path, descriptor.sha256, desiredEffectiveConfig)) {
            rememberNativeControls(descriptor)
            return true
        }

        val acquired = try {
            nativeResourceCoordinator?.acquire(this,priorityOverride=if(priority==InferencePriority.BACKGROUND)
                com.halo.sdk.runtime.resource.ResourcePriority.BACKGROUND else null) ?: true
        } catch (cancelled: CancellationException) {
            throw cancelled
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

        activeComputeJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
        nativeResourceCoordinator?.mark(this, NativeResourceState.LOADING, "Loading LiteRT model: ${descriptor.id}")
        _status.value = ProviderStatus(ProviderStatus.State.LOADING, "Loading ${descriptor.displayName}...")

        // DOGFOOD-005 backend gate: AUTO never selects an unqualified GPU. The requested backend
        // stays observable (diagnostics, resolution log); the SDK only ever sees the effective one.
        val resolution = resolveBackend(descriptor, requestedConfig.computeBackend)
        val loadConfig = if (resolution.effective != requestedConfig.computeBackend) {
            requestedConfig.copy(computeBackend = resolution.effective)
        } else {
            requestedConfig
        }
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
                artifactSha256 = descriptor.sha256,
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
            rememberNativeControls(descriptor)
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
        } catch (cancelled: CancellationException) {
            throw cancelled
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
        activeComputeJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
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
        state.models.filter { it.valid && conversationalArtifactAllowed(it) }.map { desc ->
            AiModel(
                id = desc.id,
                displayName = desc.displayName,
                providerId = id,
                executionLocation = com.halo.sdk.provider.AiExecutionLocation.ON_DEVICE,
                capabilityEvidence = buildMap {
                    val declared = if (LiteRtModelCatalog.bySha256(desc.sha256) != null)
                        com.halo.sdk.provider.CapabilityEvidence.CATALOG_PIN else com.halo.sdk.provider.CapabilityEvidence.HEURISTIC
                    if (desc.supportsVision) put(AiCapability.IMAGE,
                        if (desc.sha256?.let { visionEvidence[VisionQualificationKey.of(it, RUNTIME_VERSION)]?.passed } == true)
                            com.halo.sdk.provider.CapabilityEvidence.HALO_PROBE_SUCCESS else declared)
                    if (desc.supportsAudio) put(AiCapability.AUDIO, declared)
                },
                inputCapabilities = buildSet {
                    add(AiCapability.TEXT)
                    if (desc.supportsVision) add(AiCapability.IMAGE)
                    if (desc.supportsAudio) add(AiCapability.AUDIO)
                },
                contextLength = desc.contextWindowTokens.toLong(),
                supportsStreaming = true,
                // True when Halo can complete a tool loop, native or prompt-mediated.
                supportsTools = toolCallProtocolFor(desc).supportsToolLoop,
                supportsThinking = desc.supportsThinking
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
            validation = descriptor?.validation,
            artifactFingerprintPrefix = descriptor?.sha256?.take(12),
            toolQualification = descriptor?.let(::toolQualificationFor) ?: LiteRtToolQualification.NOT_APPLICABLE,
        )
    }

    private val _roleQualifications = kotlinx.coroutines.flow.MutableStateFlow<Map<String, LocalModelRoleQualification>>(emptyMap())
    val roleQualifications: kotlinx.coroutines.flow.StateFlow<Map<String, LocalModelRoleQualification>> = _roleQualifications

    /** Explicit role diagnostics reserve the existing session before touching residency. */
    suspend fun runRoleQualification(
        modelId: String? = null,
        agentic: suspend (com.halo.providers.litert.models.LocalModelDescriptor, suspend (AiRequest, AiContext) -> AiResponse) -> LocalAgenticQualification,
        onRow: suspend (LocalModelRoleQualification) -> Unit = {},
        admissionAllowed: () -> Boolean = { true },
    ): List<LocalModelRoleQualification> = withContext(Dispatchers.IO) {
        val runner = LocalModelRoleQualificationRunner(modelSession, inventoryRepository, admissionAllowed) { nativeResourceCoordinator?.budget?.value?.pressure }
        val publish: suspend (LocalModelRoleQualification) -> Unit = { row ->
            _roleQualifications.value = _roleQualifications.value + (row.modelId to row)
            onRow(row)
        }
        val callback: suspend (com.halo.providers.litert.models.LocalModelDescriptor, String) -> LocalAgenticQualification = { descriptor, owner ->
            val initialBackend = modelSession.activeBackend
            var changed = false
            var generationFailed = false
            val result = agentic(descriptor) { request, context ->
                try { diagnosticGenerate(descriptor, owner, request, context) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { generationFailed = true; throw error }
                finally { if (modelSession.activeBackend != initialBackend) changed = true }
            }
            when {
                changed -> result.copy(verdict = LocalRoleProbeVerdict.FAIL, failure = LocalRoleProbeFailure.BACKEND_CHANGED)
                generationFailed -> result.copy(verdict = LocalRoleProbeVerdict.FAIL, failure = LocalRoleProbeFailure.GENERATION_FAILED)
                else -> result
            }
        }
        // A non-generative artifact must not enter FAST/CHAT/AGENTIC probes, including
        // explicit model-ID calls that bypass Model Browser's disabled action buttons.
        if (modelId != null) {
            val selected = inventoryRepository.resolveModel(modelId)
            if (selected != null && !conversationalArtifactAllowed(selected)) {
                val now = System.currentTimeMillis()
                val row = LocalModelRoleQualification(modelId, selected.sha256.orEmpty(), selected.bytes,
                    null, null, null, null, null, null,
                    failure = LocalRoleProbeFailure.UNSUPPORTED_MODEL_TASK,
                    startedAtMs = now, completedAtMs = now)
                publish(row)
                return@withContext listOf(row)
            }
        }

        // tryLock refuses an active turn; diagnostics must never wait and then preempt it.
        if (!loadMutex.tryLock()) {
            val ids = modelId?.let(::listOf) ?: (inventoryRepository.inventoryState.value as? LocalModelInventoryState.Ready)
                ?.models.orEmpty().filter { it.verified && it.valid && conversationalArtifactAllowed(it) }.map { it.id }
            return@withContext ids.map { id -> LocalModelRoleQualification(id, "", 0, null, null, null, null, null, null,
                failure = LocalRoleProbeFailure.RESOURCE_BUSY, startedAtMs = System.currentTimeMillis(), completedAtMs = System.currentTimeMillis()) }.also { rows -> rows.forEach { publish(it) } }
        }
        try {
            val config: suspend (com.halo.providers.litert.models.LocalModelDescriptor) -> RuntimeLoadConfig = { descriptor ->
                val requested = loadConfigFor(descriptor)
                requested.copy(computeBackend = resolveBackend(descriptor, requested.computeBackend).effective)
            }
            val vision: (com.halo.providers.litert.models.LocalModelDescriptor) -> LocalVisionQualification = { descriptor ->
                if (visionQualificationFor(descriptor) == VisionQualification.PROVEN)
                    LocalVisionQualification(LocalRoleProbeVerdict.PASS, "VISION_LAB_PROVEN")
                else LocalVisionQualification(LocalRoleProbeVerdict.NOT_APPLICABLE)
            }
            val acquire: suspend () -> Unit = {
                if (nativeResourceCoordinator?.acquire(this@LiteRtLocalProvider, diagnostic = true) == false)
                    throw AiProviderException(AiErrorType.SESSION_CONFLICT, "Diagnostic resource busy")
                activeComputeJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
                nativeResourceCoordinator?.mark(this@LiteRtLocalProvider, NativeResourceState.IN_USE, "Role qualification")
            }
            val release: suspend () -> Unit = { activeComputeJob = null; nativeResourceCoordinator?.release(this@LiteRtLocalProvider); Unit }
            if (modelId != null) listOf(runner.runSuite(modelId, config, callback, acquire, release, ::loadConfigFor, vision)).also { it.forEach { row -> publish(row) } }
            else runner.runRoleMatrix(
                (inventoryRepository.inventoryState.value as? LocalModelInventoryState.Ready)
                    ?.models.orEmpty().filter { it.valid && conversationalArtifactAllowed(it) },
                config, callback, acquire, release, ::loadConfigFor, vision, publish)
        } catch (cancelled: CancellationException) {
            if (modelId != null) {
                val descriptor = (inventoryRepository.inventoryState.value as? LocalModelInventoryState.Ready)?.models?.firstOrNull { it.id == modelId }
                val now = System.currentTimeMillis()
                publish(LocalModelRoleQualification(modelId, descriptor?.sha256.orEmpty(), descriptor?.bytes ?: 0,
                    null, null, null, null, null, null, failure = LocalRoleProbeFailure.CANCELLED, startedAtMs = now, completedAtMs = now))
            }
            throw cancelled
        } finally { loadMutex.unlock() }
    }

    /** Reuses the provider's mapper and tool parser, without production observation or settings writes. */
    private suspend fun diagnosticGenerate(
        descriptor: com.halo.providers.litert.models.LocalModelDescriptor, owner: String,
        request: AiRequest, context: AiContext,
    ): AiResponse {
        require(request.modelId == descriptor.id && modelSession.loadedModelId == descriptor.id)
        val mapped = LiteRtConversationMapper.map(request, context)
        val protocol = toolCallProtocolFor(descriptor)
        val tools = if (request.toolUseMode == ToolUseMode.NONE || protocol == ToolCallProtocol.NONE) emptyList() else toolDefinitionsFor(context)
        val promptProtocol = protocol == ToolCallProtocol.HALO_PROMPT
        val config = generationConfig(descriptor, mapped.outgoingMessage?.content.orEmpty(),
            systemInstructionFor(mapped.systemInstruction, tools, promptProtocol), request.image,
            mapped.initialMessages.map { it.toRuntimeMessage() }, if (promptProtocol) emptyList() else tools,
            request.maxOutputTokens, 0.0, request.reasoningMode, request.reasoningTokenBudget, request.audio)
        val generated = modelSession.generate(config, benchmarkRequestId = owner).getOrThrow()
        val effective = applyPromptProtocol(generated, promptProtocol, tools)
        return AiResponse(text = effective.text, providerId = id, modelId = descriptor.id,
            toolCalls = effective.toolCalls, executionBackend = modelSession.activeBackend?.name, coldStart = generated.coldStart)
    }

    /** Loads, generates "HALO_OK", then generates a second prompt without a process restart. */
    suspend fun smokeTestModel(modelId: String): LocalModelSmokeTestResult = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            val descriptor = inventoryRepository.resolveModel(modelId)
            require(descriptor != null && conversationalArtifactAllowed(descriptor)) {
                "This local artifact is not a conversational LiteRT generator"
            }
            developmentRunner().smokeTest(modelId) { installed -> loadConfigFor(installed) }
        }
    }

    /** Development-only serial matrix over every installed model. Never keeps two resident. */
    suspend fun runModelMatrix(): List<LocalModelMatrixRow> = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            val state = inventoryRepository.inventoryState.value
            val installed = (state as? LocalModelInventoryState.Ready)?.models
                ?.filter { it.valid && conversationalArtifactAllowed(it) }
                .orEmpty()
            Log.i(TAG, "$EVENT_SWITCH action=matrix models=${installed.size}")
            developmentRunner().runMatrix(
                descriptors = installed,
                loadConfigFor = { descriptor -> loadConfigFor(descriptor) },
            )
        }
    }

    suspend fun compareInstalledModels(modelIds: List<String>, prompt: String,
        onResult: (InstalledModelComparison) -> Unit): List<InstalledModelComparison> = withContext(Dispatchers.IO) {
        require(modelIds.size == 2 && modelIds.distinct().size == 2 && prompt.isNotBlank() && prompt.length <= 4096)
        val artifacts = modelIds.map { id -> requireNotNull(inventoryRepository.verifyPhysicalArtifact(id)).also {
            require(it.verified && conversationalArtifactAllowed(it) && isGenerationProven(it)) { "Comparison requires verified installed generation artifacts with current generation proof" }
        } }
        val results = mutableListOf<InstalledModelComparison>()
        for (artifact in artifacts) {
            val fresh = requireNotNull(inventoryRepository.verifyPhysicalArtifact(artifact.id))
            require(fresh.verified && fresh.sha256 == artifact.sha256 && fresh.bytes == artifact.bytes) { "Installed inventory changed" }
            run {
                val tuning = preferences.tuning(artifact.id)
                val result = kotlinx.coroutines.withTimeout(120_000) {
                    benchmarkModel(artifact.id, prompt, minOf(tuning.maxOutputTokens ?: 256, 256), LocalComputeBackend.CPU, forceFreshEngine = true).getOrThrow()
                }
                val row = InstalledModelComparison(artifact.id, requireNotNull(artifact.sha256), System.currentTimeMillis(), tuning,
                    result.responseText.filter { it == '\n' || !it.isISOControl() }.take(512), result.loadDurationMs,
                    result.timeToFirstTokenMs, result.estimatedTokensPerSecond, result.effectiveContextTokens, result.effectiveBackend.name)
                results += row
                onResult(row)
            }
        }
        results
    }

    suspend fun benchmarkModel(
        modelId: String,
        prompt: String = LocalModelBenchmarkRunner.DEFAULT_PROMPT,
        maxOutputTokens: Int = LocalModelBenchmarkRunner.DEFAULT_MAX_OUTPUT_TOKENS,
        backendOverride: LocalComputeBackend? = null,
        forceFreshEngine: Boolean = false,
    ): Result<LocalModelBenchmarkResult> = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            val descriptor = inventoryRepository.resolveModel(modelId)
                ?: return@withLock Result.failure(com.halo.providers.litert.runtime.LocalModelBenchmarkFailure(
                    com.halo.providers.litert.runtime.BenchmarkFailureCode.ARTIFACT_MISMATCH))
            require(conversationalArtifactAllowed(descriptor)) {
                "This local artifact is not a conversational LiteRT generator"
            }
            val requested = loadConfigFor(descriptor).let { if (backendOverride == null) it else it.copy(computeBackend = backendOverride) }
            LocalModelBenchmarkRunner(modelSession, inventoryRepository).runBenchmark(
                modelId = modelId,
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                requestedBackend = requested.computeBackend,
                freshEngine = forceFreshEngine,
                generationConfigFor = { installed ->
                    rememberNativeControls(installed)
                    generationConfig(installed, prompt, null, null, emptyList(), emptyList(), maxOutputTokens)
                },
                loadConfigFor = { installed ->
                    val resolution = resolveBackend(installed, requested.computeBackend)
                    requested.copy(computeBackend = if (resolution.effective == LocalComputeBackend.GPU &&
                        requested.computeBackend == LocalComputeBackend.AUTO) LocalComputeBackend.AUTO else resolution.effective)
                },
            )
        }
    }

    /**
     * Optional development capability probe for native tool calling.
     *
     * LiteRT-LM has model-family level tool support, but a family is not an artifact: Halo asks the
     * artifact to call one harmless synthetic tool and records what actually happened. A failed
     * probe never marks the model broken and never disables normal text generation.
     */
    private val visionEvidence = java.util.concurrent.ConcurrentHashMap<String, VisionProbeResult>()

    suspend fun visionQualificationForModel(modelId: String): VisionQualification? =
        inventoryRepository.resolveModel(modelId)?.let(::visionQualificationFor)

    suspend fun visionQualificationKeyForModel(modelId: String): String? =
        inventoryRepository.resolveModel(modelId)?.sha256?.let { VisionQualificationKey.of(it, RUNTIME_VERSION) }

    fun visionQualificationFor(descriptor: LocalModelDescriptor): VisionQualification? {
        if (!descriptor.supportsVision) return null
        val fingerprint = descriptor.sha256 ?: return VisionQualification.DECLARED
        val evidence = visionEvidence[VisionQualificationKey.of(fingerprint, RUNTIME_VERSION)]
        return when {
            evidence == null -> VisionQualification.PROBE_REQUIRED
            evidence.passed -> VisionQualification.PROVEN
            else -> VisionQualification.FAILED
        }
    }

    /** Explicit user action only; reuses normal model residency/admission and never has tools. */
    suspend fun probeVision(modelId: String): VisionProbeResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val descriptor = inventoryRepository.resolveModel(modelId)
        val fingerprint = descriptor?.sha256
        val key = fingerprint?.let { VisionQualificationKey.of(it, RUNTIME_VERSION) }
        if (descriptor == null || !descriptor.valid || !descriptor.supportsVision || key == null) {
            return@withContext VisionProbeResult(key, false, null, 0, false, "ARTIFACT_OR_CAPABILITY")
        }
        val result = try {
            val response = kotlinx.coroutines.withTimeout(30_000) {
                generate(AiRequest(text = "Read the text and number in this image.", image = SyntheticVisionImage.create(),
                    mimeTypes = listOf("image/png"), modelId = modelId, maxOutputTokens = 64), AiContext.EMPTY)
            }
            val matched = Regex("\\bHALO\\b", RegexOption.IGNORE_CASE).containsMatchIn(response.text) && Regex("\\b42\\b").containsMatchIn(response.text)
            VisionProbeResult(key, matched && response.toolCalls.isEmpty(), response.executionBackend,
                System.currentTimeMillis() - started, matched, if (matched && response.toolCalls.isEmpty()) null else "EXPECTED_CONTENT")
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
            VisionProbeResult(key, false, null, System.currentTimeMillis() - started, false, "TIMEOUT")
        } catch (_: Exception) {
            VisionProbeResult(key, false, null, System.currentTimeMillis() - started, false, "GENERATION")
        }
        visionEvidence[key] = result
        result
    }

    suspend fun probeToolCalling(modelId: String): LiteRtToolProbeResult = withContext(Dispatchers.IO) {
        loadMutex.withLock locked@ {
            val descriptor = inventoryRepository.resolveModel(modelId)
                ?: return@locked toolProbeFailure(modelId, null, "LOAD", "Model is not installed")
            if (!descriptor.valid) {
                return@locked toolProbeFailure(modelId, descriptor, "ARTIFACT", descriptor.validationDetail)
            }

            val key = descriptor.qualificationKey()
            if (!toolProbeInFlight.add(key)) {
                return@locked toolProbeFailure(modelId, descriptor, "BUSY", "A tool probe is already running")
            }
            try {
                val requestId = UUID.randomUUID().toString()
                val loadOk = loadLocked(descriptor)
                if (!loadOk) {
                    val detail = _loadDiagnostics.value?.technicalSummary()
                    lastToolProbeFailures[key] = "ENGINE_INIT" to (detail ?: "Local model could not be loaded")
                    return@locked toolProbeFailure(modelId, descriptor, "ENGINE_INIT", detail)
                }
                if (modelSession.tryBeginBenchmark(requestId) !is com.halo.sdk.provider.InferenceAdmission.Granted) {
                    return@locked toolProbeFailure(modelId, descriptor, "BUSY", "Session busy")
                }
                try {
                    val result = modelSession.generate(
                        RuntimeGenerationConfig(
                            prompt = TOOL_PROBE_PROMPT,
                            thinkingEnabled = false.takeIf { descriptor.supportsThinking },
                            maxTokens = TOOL_PROBE_MAX_TOKENS,
                            temperature = 0.0,
                            availableTools = listOf(HALO_PROBE_TOOL),
                        ),
                        benchmarkRequestId = requestId,
                    )
                    val generated = result.getOrElse { error ->
                        val stage = failureStage(error)
                        lastToolProbeFailures[key] = stage to (error.message?.take(200) ?: "generation failed")
                        return@locked toolProbeFailure(modelId, descriptor, stage, error.message?.take(200))
                    }

                    val call = generated.toolCalls.firstOrNull { it.functionName == HALO_PROBE_TOOL.name }
                    val structurallyUsable = call != null &&
                        LiteRtNativeToolCallValidator.rejectionFor(call.functionName, call.arguments) == null
                    val observation = modelSession.lastToolChannelObservation
                    Log.i(
                        TAG,
                        "$EVENT_TOOL_PROBE model=$modelId nativeToolCall=${call != null} " +
                            "usable=$structurallyUsable sha=${descriptor.sha256?.take(12) ?: "unknown"} " +
                            "argsFields=${call?.arguments?.size ?: 0}",
                    )
                    if (structurallyUsable) {
                        // Proof is recorded against artifact + runtime + schema, never against model id.
                        nativeToolProvenKeys.add(key)
                        lastToolProbeFailures.remove(key)
                    } else if (call != null) {
                        lastToolProbeFailures[key] = "ARGUMENTS" to "Emitted call had incomplete arguments"
                    }
                    LiteRtToolProbeResult(
                        modelId = modelId,
                        nativeToolCallObserved = call != null,
                        toolName = call?.functionName,
                        argumentsFieldCount = call?.arguments?.size,
                        argumentsSha256Prefix = call?.arguments?.keys
                            ?.sorted()
                            ?.joinToString(",")
                            ?.let(::digestPrefix),
                        outputNonEmpty = generated.text.isNotBlank(),
                        outputMatchedExpected = structurallyUsable,
                        callIdComplete = observation?.callIdComplete ?: (call?.functionName != null),
                        argumentsStructurallyComplete = observation?.argumentsStructurallyComplete
                            ?: structurallyUsable,
                        pairedResponseCount = observation?.pairedResponseCount ?: 0,
                        qualification = toolQualificationFor(descriptor, includeProbing = false),
                        qualificationKey = key,
                        failureStage = null,
                        failureDetail = null,
                    )
                } finally {
                    modelSession.endBenchmark(requestId)
                }
            } finally {
                toolProbeInFlight.remove(key)
            }
        }
    }

    private fun toolProbeFailure(
        modelId: String,
        descriptor: LocalModelDescriptor?,
        stage: String,
        detail: String?,
    ): LiteRtToolProbeResult = LiteRtToolProbeResult(
        modelId = modelId,
        nativeToolCallObserved = false,
        toolName = null,
        argumentsFieldCount = null,
        argumentsSha256Prefix = null,
        outputNonEmpty = false,
        outputMatchedExpected = false,
        callIdComplete = false,
        argumentsStructurallyComplete = false,
        pairedResponseCount = 0,
        qualification = descriptor?.let { toolQualificationFor(it, includeProbing = false) }
            ?: LiteRtToolQualification.NOT_APPLICABLE,
        qualificationKey = descriptor?.qualificationKey(),
        failureStage = stage,
        failureDetail = detail,
    )

    /**
     * MINING-P0-001 content-free tool-channel truth for one artifact: qualification state,
     * fingerprint prefix, runtime/schema versions, structural call validity and probe history.
     * Never exposes argument values or generated text.
     */
    suspend fun toolChannelDiagnostics(modelId: String): LiteRtToolChannelDiagnostics = withContext(Dispatchers.IO) {
        val descriptor = inventoryRepository.resolveModel(modelId)
        val observation = modelSession.lastToolChannelObservation
        val failure = descriptor?.let { lastToolProbeFailures[it.qualificationKey()] }
        LiteRtToolChannelDiagnostics(
            modelId = modelId,
            qualification = descriptor?.let(::toolQualificationFor) ?: LiteRtToolQualification.NOT_APPLICABLE,
            qualificationKey = descriptor?.qualificationKey(),
            artifactFingerprintPrefix = descriptor?.sha256?.take(12),
            runtimeVersion = RUNTIME_VERSION,
            schemaContractVersion = LiteRtToolQualificationKey.SCHEMA_CONTRACT_VERSION,
            configuredToolCount = _conversationDiagnostics.value?.toolSchemaCount
                ?: if (descriptor != null && toolCallProtocolFor(descriptor).supportsToolLoop) 1 else 0,
            nativeToolCallObserved = observation?.observedNativeCall == true,
            callIdComplete = observation?.callIdComplete ?: true,
            argumentsStructurallyComplete = observation?.argumentsStructurallyComplete ?: true,
            pairedResponseCount = observation?.pairedResponseCount ?: 0,
            lastProbeAtMs = failure?.let { System.currentTimeMillis() },
            lastProbeFailureStage = failure?.first,
            lastProbeFailureDetail = failure?.second,
        )
    }

    /**
     * MINING-P0-001 explicit raw LiteRT SDK control probe.
     *
     * Bypasses the provider router, cognition, memory, tools, the agent loop and speech so a field
     * failure can be attributed to a layer. Still honours artifact verification, ABI preflight and
     * native-resource arbitration. Never runs at startup or when Settings opens.
     */
    suspend fun runControlProbe(modelId: String): LiteRtControlProbeResult = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            LiteRtControlProbeRunner(
                context = context,
                inventoryRepository = inventoryRepository,
                loadConfigFor = { descriptor -> loadConfigFor(descriptor) },
                nativeResourceSnapshot = { nativeResourceCoordinator?.snapshot?.value.orEmpty() },
                residentModelId = { if (modelSession.isLoaded()) modelSession.loadedModelId else null },
                nativeAttemptJournal = nativeAttemptJournal,
            ).run(modelId)
        }
    }

    /**
     * Non-suspend, content-free snapshot for DiagnosticsExport. Reads already-published state only;
     * never triggers a probe, a load or any inference.
     */
    fun diagnosticsSnapshot(): LiteRtProviderDiagnostics? {
        val modelId = configuredModelId ?: return null
        val descriptor = (inventoryRepository.inventoryState.value as? LocalModelInventoryState.Ready)
            ?.models
            ?.firstOrNull { it.id == modelId }
        val observation = modelSession.lastToolChannelObservation
        return LiteRtProviderDiagnostics(
            modelId = modelId,
            displayName = descriptor?.displayName ?: modelId,
            installed = descriptor != null,
            artifactState = descriptor?.validation?.name ?: "NOT_INSTALLED",
            artifactFingerprintPrefix = descriptor?.sha256?.take(12),
            resident = modelSession.isLoaded() && modelSession.loadedModelId == modelId,
            requestedBackend = requestedBackend(modelId),
            effectiveBackend = modelSession.activeBackend,
            requestedContextTokens = modelSession.requestedContextSize,
            effectiveContextTokens = modelSession.effectiveContextSize,
            generationProven = descriptor != null && isGenerationProven(descriptor),
            toolQualification = descriptor?.let { toolQualificationFor(it) }
                ?: LiteRtToolQualification.NOT_APPLICABLE,
            qualificationKey = descriptor?.qualificationKey(),
            configuredToolCount = _conversationDiagnostics.value?.toolSchemaCount
                ?: if (descriptor != null && toolCallProtocolFor(descriptor).supportsToolLoop) 1 else 0,
            toolChannelObservation = observation?.summaryLine(),
            lastFailureStage = _loadDiagnostics.value?.stage?.name,
            runtimeVersion = RUNTIME_VERSION,
        )
    }

    /** Bounded digest prefix over a label list (argument key names / schema names), never values. */
    private fun digestPrefix(text: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(12)

    private fun failureStage(error: Throwable): String = when ((error as? LiteRtModelLoadException)?.stage) {
        ModelLoadStage.CONVERSATION -> "CONVERSATION"
        ModelLoadStage.FIRST_TOKEN -> "FIRST_TOKEN"
        ModelLoadStage.GENERATION -> "GENERATION"
        ModelLoadStage.ENGINE_INIT -> "ENGINE_INIT"
        else -> error::class.java.simpleName
    }

    suspend fun unloadLocalModel(): Boolean = withContext(Dispatchers.IO) {
        loadMutex.withLock locked@ {
            val modelId = modelSession.loadedModelId
            val ok = modelSession.unloadModel().isSuccess
            if (ok) nativeResourceCoordinator?.release(this@LiteRtLocalProvider)
            Log.i(TAG, "$EVENT_UNLOAD model=$modelId success=$ok")
            ok
        }
    }

    private fun developmentRunner() = LocalModelDevelopmentRunner(modelSession, inventoryRepository)

    // -------------------------------------------------------------------------------------
    // Qualification probe
    // -------------------------------------------------------------------------------------

    /**
     * Qualification probe: resolves the artifact, loads the engine, creates a conversation and
     * proves a non-empty completion. Never run on app launch — only on explicit user request.
     */
    suspend fun verifyModel(modelId: String, loadOverride: RuntimeLoadConfig? = null): ModelVerificationResult = withContext(Dispatchers.IO) {
        loadMutex.withLock locked@ {
            val descriptor = inventoryRepository.verifyPhysicalArtifact(modelId)
                ?: return@locked ModelVerificationResult.Failed(
                    modelId,
                    ModelVerificationStage.DOWNLOAD,
                    "Model is not installed.",
                )
            if (!descriptor.valid) {
                return@locked ModelVerificationResult.Failed(
                    modelId,
                    ModelVerificationStage.INTEGRITY,
                    descriptor.validationDetail ?: "Model file failed integrity verification.",
                )
            }

            if (rejectNonGeneration(descriptor))
                return@locked ModelVerificationResult.Failed(modelId, ModelVerificationStage.INTEGRITY,
                    "This model does not perform conversational text generation.")
            val loadStart = System.currentTimeMillis()
            val loaded = loadLocked(descriptor, loadOverride ?: loadConfigFor(descriptor))
            if (!loaded) {
                val diagnostics = _loadDiagnostics.value
                return@locked ModelVerificationResult.Failed(
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
                        thinkingEnabled = false.takeIf { descriptor.supportsThinking },
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
                return@locked ModelVerificationResult.Failed(
                    modelId,
                    ModelVerificationStage.GENERATION,
                    "Timed out while waiting for a response from the model.",
                )
            }

            val result = generation.getOrElse { error ->
                recordGenerationFailure(modelId, error, stageFor(error))
                return@locked ModelVerificationResult.Failed(
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
                return@locked ModelVerificationResult.Failed(
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

    override val supportsTemperatureOverride: Boolean get() = true

    private fun generationConfig(
        descriptor: LocalModelDescriptor?,
        mappedPrompt: String,
        systemInstruction: String?,
        imageBytes: ByteArray?,
        initialMessages: List<RuntimeConversationMessage>,
        availableTools: List<ToolDefinition>,
        requestMaxTokens: Int?,
        requestTemperature: Double? = null,
        reasoningMode: AiReasoningMode = AiReasoningMode.DEFAULT,
        reasoningTokenBudget: Int? = null,
        audioBytes: ByteArray? = null,
    ): RuntimeGenerationConfig {
        val defaults = preferences.generationDefaults(
            descriptor?.let(::catalogModelFor),
            requestMaxTokens,
        )
        descriptor?.let(::rememberNativeControls)
        val tuning = descriptor?.let { preferences.tuning(it.id) }
        val nativeThinking = descriptor?.sha256?.let { nativeThinkingByArtifact[it] } == true
        return RuntimeGenerationConfig(
            thinkingEnabled = if (!nativeThinking) null else when (reasoningMode) {
                AiReasoningMode.DEFAULT -> when (tuning?.thinkingMode) {
                    com.halo.providers.litert.models.LiteRtThinkingMode.ON -> true
                    com.halo.providers.litert.models.LiteRtThinkingMode.OFF -> false
                    else -> null
                }
                AiReasoningMode.DISABLED -> false
                AiReasoningMode.ENABLED -> true
            },
            thinkingTokenBudget = if (nativeThinking && (reasoningMode == AiReasoningMode.ENABLED ||
                reasoningMode == AiReasoningMode.DEFAULT && tuning?.thinkingMode == com.halo.providers.litert.models.LiteRtThinkingMode.ON))
                (reasoningTokenBudget ?: tuning?.thinkingTokenBudget)?.coerceIn(0, LOCAL_INTERACTIVE_REASONING_TOKEN_BUDGET) ?: LOCAL_INTERACTIVE_REASONING_TOKEN_BUDGET else null,
            prompt = mappedPrompt,
            systemInstruction = systemInstruction,
            imageBytes = imageBytes,
            audioBytes = audioBytes,
            initialMessages = initialMessages,
            availableTools = availableTools,
            maxTokens = defaults.maxOutputTokens,
            temperature = requestTemperature?.takeIf { it.isFinite() && it in 0.0..2.0 } ?: defaults.temperature,
            topP = defaults.topP,
            topK = if(requestTemperature == 0.0) 1 else defaults.topK,
        )
    }

    private fun generationMetrics(result: RuntimeGenerationResult, startedAtMs: Long, generationCompletedAtMs: Long): AiGenerationMetrics {
        val completed = System.currentTimeMillis()
        return AiGenerationMetrics(startedAtMs = startedAtMs,
            firstTokenAtMs = (generationCompletedAtMs - result.durationMs + result.timeToFirstTokenMs).coerceIn(startedAtMs, completed),
            completedAtMs = completed, outputTokens = result.tokenCount)
    }

    /** Recurrent hybrid state can outlive individual Conversation instances inside a warm Engine.
     * Release that Engine after each user turn until the exact build proves clean isolation.
     * This is model-family policy, never an Android-wide model cache disable.
     */
    private fun residentModelNeedsIsolation(): Boolean {
        val modelId = modelSession.loadedModelId ?: return false
        val model = catalogModel(modelId)
        val filename = model?.filename ?: modelSession.loadedModelPath.orEmpty()
        return com.halo.providers.litert.models.LiteRtCommunityModelGuide
            .guide(model?.repoId.orEmpty(), filename)?.needsConversationIsolation == true
    }

    /** Verification and developer probes may also leave a hybrid Engine warm. Never
     * let the first real user turn inherit their recurrent state. Called inside loadMutex.
     */
    private suspend fun discardHybridResidencyBeforeUserTurn() {
        if (!residentModelNeedsIsolation()) return
        val staleModel = modelSession.loadedModelId
        Log.i(TAG, "HALO_LITERT_ENGINE_ISOLATION model=$staleModel policy=CLEAR_BEFORE_USER_TURN")
        val result = try { modelSession.unloadModel() }
        finally { nativeResourceCoordinator?.release(this@LiteRtLocalProvider) }
        if (result.isFailure) throw AiProviderException(AiErrorType.SESSION_CONFLICT,
            "Hybrid model could not clear its previous native state", result.exceptionOrNull())
    }

    private suspend fun finishInteractiveTurn() = withContext(NonCancellable) {
        val modelId = modelSession.loadedModelId
        if (residentModelNeedsIsolation()) {
            Log.i(TAG, "HALO_LITERT_ENGINE_ISOLATION model=${modelId ?: "unknown"} policy=FRESH_ENGINE_NEXT_TURN")
            try {
                val result = modelSession.unloadModel()
                if (result.isFailure)
                    Log.w(TAG, "Could not fully unload isolated LiteRT engine", result.exceptionOrNull())
            } finally {
                nativeResourceCoordinator?.release(this@LiteRtLocalProvider)
            }
        } else {
            nativeResourceCoordinator?.mark(this@LiteRtLocalProvider, NativeResourceState.RESIDENT, "Resident")
        }
    }

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = withContext(Dispatchers.IO) {
        loadMutex.withLock locked@ {
            val turnStartedAtMs = System.currentTimeMillis()
            request.validateForTextGeneration()
            request.validateAgainst((request.modelId ?: configuredModelId)?.let { capabilitiesForModel(it) }.orEmpty())

            discardHybridResidencyBeforeUserTurn()
            if (!loadModelIfNeededLocked(request.modelId, request)) {
                throw AiProviderException(
                    AiErrorType.LOCAL_MODEL_UNAVAILABLE,
                    _loadDiagnostics.value?.let { "${it.displayName} could not be loaded: ${it.stage}" }
                        ?: "Failed to load LiteRT model",
                )
            }

            acquireExecutionLease("Inferring")
            return@locked try {
                val descriptor = descriptorForGeneration()
                val mapped = LiteRtConversationMapper.map(request, context)
                val protocol = descriptor?.let(::toolCallProtocolFor) ?: ToolCallProtocol.NONE
                val requestedTools = if (request.toolUseMode == ToolUseMode.NONE || protocol == ToolCallProtocol.NONE) emptyList() else toolDefinitionsFor(context)
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
                    requestTemperature = request.temperatureOverride,
                    reasoningMode = request.reasoningMode,
                    reasoningTokenBudget = request.reasoningTokenBudget,
                    audioBytes = request.audio,
                )

                val result = modelSession.generate(genConfig).getOrElse { err ->
                    if (err is CancellationException) throw err
                    recordGenerationFailure(modelSession.loadedModelId, err, stageFor(err))
                    throw AiProviderException(AiErrorType.GENERATION_FAILED, "LiteRT generation failed: ${err.message}", err)
                }

                val generationCompletedAtMs = System.currentTimeMillis()
                val effective = applyPromptProtocol(result, usePromptProtocol, requestedTools)

                if (effective.text.isBlank() && effective.toolCalls.isEmpty()) {
                    val error = AiProviderException(AiErrorType.EMPTY_RESPONSE, "LiteRT produced no response text")
                    recordGenerationFailure(modelSession.loadedModelId, error, LiteRtFailureStage.FIRST_TOKEN)
                    throw error
                }
                recordReasoning(result.reasoningText)
                descriptorForGeneration()?.let(::markGenerationProven)
                recordConversationDiagnostics(
                    descriptor = descriptor,
                    genConfig = genConfig,
                    generatedText = effective.text,
                    usePromptProtocol = usePromptProtocol,
                    requestedTools = requestedTools,
                    firstTokenMs = result.timeToFirstTokenMs,
                )
                _loadDiagnostics.value = null

                AiResponse(
                    text = effective.text,
                    providerId = id,
                    modelId = modelSession.loadedModelId ?: "litert",
                    finishReason = if (effective.toolCalls.isNotEmpty()) "tool_calls" else "stop",
                    toolCalls = effective.toolCalls,
                    executionBackend = modelSession.activeBackend?.name,
                    coldStart = result.coldStart,
                    metrics = generationMetrics(result, turnStartedAtMs, generationCompletedAtMs),
                )
            } finally {
                finishInteractiveTurn()
            }
        }
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = channelFlow {
        if ((request.modelId ?: configuredModelId)?.let { preferences.tuning(it).streaming } == false) {
            send(AiStreamEvent.Started)
            try { send(AiStreamEvent.Completed(generate(request.copy(publishStreamingTokens = false), context))) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { send(AiStreamEvent.Failed(error)) }
            return@channelFlow
        }
        loadMutex.withLock locked@ {
            val turnStartedAtMs = System.currentTimeMillis()
            request.validateForTextGeneration()
            request.validateAgainst((request.modelId ?: configuredModelId)?.let { capabilitiesForModel(it) }.orEmpty())
            send(AiStreamEvent.Started)

            val loaded = try {
                discardHybridResidencyBeforeUserTurn()
                loadModelIfNeededLocked(request.modelId, request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: AiProviderException) {
                // Resource contention during cold load is a recoverable stream
                // outcome, not an uncaught channelFlow exception.
                send(AiStreamEvent.Failed(error))
                return@locked
            } catch (error: Exception) {
                send(AiStreamEvent.Failed(AiProviderException(
                    AiErrorType.LOCAL_MODEL_UNAVAILABLE, "LiteRT model could not be loaded", error)))
                return@locked
            }
            if (!loaded) {
                val diagnostics = _loadDiagnostics.value
                send(AiStreamEvent.Failed(AiProviderException(
                    AiErrorType.LOCAL_MODEL_UNAVAILABLE,
                    diagnostics?.let { "${it.displayName} could not be loaded at ${it.stage}." }
                        ?: "Failed to load LiteRT model")))
                return@locked
            }

            acquireExecutionLease("Inferring")
            try {

                val descriptor = descriptorForGeneration()
                val mapped = LiteRtConversationMapper.map(request, context)
                val protocol = descriptor?.let(::toolCallProtocolFor) ?: ToolCallProtocol.NONE
                val requestedTools = if (request.toolUseMode == ToolUseMode.NONE || protocol == ToolCallProtocol.NONE) emptyList() else toolDefinitionsFor(context)
                val usePromptProtocol = protocol == ToolCallProtocol.HALO_PROMPT
                val genConfig = generationConfig(
                    descriptor = descriptor,
                    mappedPrompt = mapped.outgoingMessage?.content.orEmpty(),
                    systemInstruction = systemInstructionFor(mapped.systemInstruction, requestedTools, usePromptProtocol),
                    imageBytes = request.image,
                    initialMessages = mapped.initialMessages.map { message -> message.toRuntimeMessage() },
                    availableTools = if (usePromptProtocol) emptyList() else requestedTools,
                    requestMaxTokens = request.maxOutputTokens,
                    requestTemperature = request.temperatureOverride,
                    reasoningMode = request.reasoningMode,
                    reasoningTokenBudget = request.reasoningTokenBudget,
                    audioBytes = request.audio,
                )

                val result = modelSession.generate(genConfig) { token ->
                    send(AiStreamEvent.Token(token))
                }

                result.onSuccess { gen ->
                    val generationCompletedAtMs = System.currentTimeMillis()
                    val effective = applyPromptProtocol(gen, usePromptProtocol, requestedTools)
                    if (effective.text.isNotBlank() || effective.toolCalls.isNotEmpty()) {
                        recordReasoning(gen.reasoningText)
                        descriptorForGeneration()?.let(::markGenerationProven)
                        recordConversationDiagnostics(
                            descriptor = descriptor,
                            genConfig = genConfig,
                            generatedText = effective.text,
                            usePromptProtocol = usePromptProtocol,
                            requestedTools = requestedTools,
                            firstTokenMs = gen.timeToFirstTokenMs,
                        )
                        _loadDiagnostics.value = null
                    }
                    if (effective.text.isBlank() && effective.toolCalls.isEmpty()) {
                        val error = AiProviderException(AiErrorType.EMPTY_RESPONSE, "LiteRT produced no response text")
                        recordGenerationFailure(modelSession.loadedModelId, error, LiteRtFailureStage.FIRST_TOKEN)
                        send(AiStreamEvent.Failed(error))
                        return@onSuccess
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
                                coldStart = gen.coldStart,
                                metrics = generationMetrics(gen, turnStartedAtMs, generationCompletedAtMs),
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
                finishInteractiveTurn()
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * MINING-P0-001 structured, content-free conversation snapshot plus a bounded prompt-echo
     * observation.
     *
     * Observation only: this batch does NOT terminate generation. The later P1 bounded-agent batch
     * owns breaker policy. The matched sequence itself is never logged or retained; only the fact
     * and the threshold are emitted.
     */
    private fun recordConversationDiagnostics(
        descriptor: LocalModelDescriptor?,
        genConfig: RuntimeGenerationConfig,
        generatedText: String,
        usePromptProtocol: Boolean,
        requestedTools: List<ToolDefinition>,
        firstTokenMs: Long?,
    ) {
        val generation = generationCounter.incrementAndGet()
        val preface = genConfig.systemInstruction
        val echo = LiteRtPromptEcho.suspected(generatedText, preface)
        if (echo) {
            Log.i(
                TAG,
                "$EVENT_PROMPT_ECHO generation=$generation " +
                    "matchedChars>=${LiteRtPromptEcho.MIN_MATCHED_CHARS}",
            )
        }
        val schemaNames = genConfig.availableTools.map { it.name }.sorted()
        _conversationDiagnostics.value = LiteRtConversationDiagnostics(
            generationId = generation.toString(),
            conversationGeneration = generation,
            createdAtMs = System.currentTimeMillis(),
            systemInstructionMode = if (preface.isNullOrBlank()) {
                LiteRtSystemInstructionMode.NONE
            } else {
                LiteRtSystemInstructionMode.SDK_SYSTEM_INSTRUCTION
            },
            systemInstructionChars = preface?.length ?: 0,
            systemInstructionFingerprint = preface?.takeIf { it.isNotBlank() }?.let(::digestPrefix),
            // The prompt-mediated tool preface is folded into the SDK system instruction, not a
            // separate channel Halo pretends to have.
            toolPrefaceFolded = usePromptProtocol && requestedTools.isNotEmpty(),
            initialMessageCount = genConfig.initialMessages.size,
            toolSchemaCount = genConfig.availableTools.size,
            toolSchemaFingerprint = schemaNames.takeIf { it.isNotEmpty() }?.joinToString(",")?.let(::digestPrefix),
            requestedBackend = descriptor?.id?.let(::requestedBackend),
            effectiveBackend = modelSession.activeBackend,
            requestedContextTokens = modelSession.requestedContextSize,
            effectiveContextTokens = modelSession.effectiveContextSize,
            conversationCreateDurationMs = modelSession.conversationCreateDurationMs,
            firstTokenDurationMs = firstTokenMs,
            lastRebuildReason = modelSession.fallbackReason,
            promptEchoSuspected = echo,
            thinkingEnabled = genConfig.thinkingEnabled,
            thinkingTokenBudget = genConfig.thinkingTokenBudget,
        )
    }

    private fun recordReasoning(reasoningText: String?) {
        val preview = reasoningText?.take(200)?.replace('\n', ' ')
        if (preview.isNullOrBlank()) return
        // Diagnostic evidence only: internal reasoning must never reach Android TTS.
        _lastReasoningPreview.value = preview
        Log.i(TAG, "$EVENT_THINKING reasoningChars=${reasoningText.length}")
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

        const val RUNTIME_VERSION = "0.18.0"
        const val VERIFY_PROMPT = "Reply with OK."
        // The native output budget includes hidden reasoning. Eight tokens can stop
        // a thinking artifact before its answer even when loading/generation work.
        const val VERIFY_MAX_TOKENS = 256
        const val VERIFY_TIMEOUT_MS = 120_000L

        /** Harmless synthetic probe prompt: one bounded tool call, no prose. Never stored. */
        const val TOOL_PROBE_PROMPT =
            "Call the halo_probe tool with value=\"ok\". Do not answer in prose."
        const val TOOL_PROBE_MAX_TOKENS = 64

        const val EVENT_PROMPT_ECHO = "HALO_LITERT_PROMPT_ECHO"

        const val EVENT_SELECT = "HALO_LITERT_SELECT"
        const val EVENT_BACKEND_OVERRIDE = "HALO_LITERT_BACKEND_OVERRIDE"
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
