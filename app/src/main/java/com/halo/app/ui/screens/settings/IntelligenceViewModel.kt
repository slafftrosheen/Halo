package com.halo.app.ui.screens.settings

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ai.readRuntimeAiContextPolicy
import com.halo.app.ai.writeRuntimeAiContextPolicy
import com.halo.providers.litert.LiteRtLoadDiagnostics
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.LiteRtToolProbeResult
import com.halo.providers.litert.models.LiteRtModelManager
import com.halo.providers.litert.models.LiteRtAcquisitionDiagnostics
import com.halo.providers.litert.models.DownloadState
import com.halo.providers.litert.models.LiteRtCatalogModel
import com.halo.providers.litert.models.LiteRtModelFailureRecord
import com.halo.providers.litert.models.LiteRtStoragePreflightResult
import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.models.ModelVerificationResult
import com.halo.providers.litert.runtime.LocalModelBenchmarkResult
import com.halo.providers.litert.runtime.LocalModelMatrixRow
import com.halo.providers.litert.runtime.LocalModelSmokeTestResult
import com.halo.providers.gemini.GeminiAuthenticationMode
import com.halo.providers.gemini.GeminiProvider
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.HaloDispatchers
import com.halo.sdk.runtime.NativeResourceSnapshot
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.productionHaloDispatchers
import com.halo.runtime.cognition.session.CognitiveSession
import com.halo.runtime.observer.InferenceAggregate
import com.halo.runtime.observer.InferenceLedger
import com.halo.runtime.observer.ToolAggregate
import com.halo.engines.ai.catalog.DefaultAiModelCatalogRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class IntelligenceUiState(
    val activeProviderId: String = "",
    val activeProviderName: String = "None",
    val activeProviderStatus: ProviderStatus = ProviderStatus.UNAVAILABLE,
    val capabilities: Set<AiCapability> = emptySet(),
    val activeModelId: String? = null,
    val lastResolvedModelId: String? = null,
    val providers: List<AiProviderUiState> = emptyList(),
    val availableModels: List<AiModel> = emptyList(),
    val catalogEntries: List<AiModelCatalogEntry> = emptyList(),
    val catalogErrors: Map<String, String> = emptyMap(),
    val isRefreshingModels: Boolean = false,
    val openRouterKey: String = "",
    val openRouterModel: String = "openrouter/free",
    val openAiKey: String = "",
    val openAiModel: String = "",
    val anthropicKey: String = "",
    val anthropicModel: String = "",
    val geminiApiKey: String = "",
    val geminiProjectId: String = "",
    val geminiModel: String = "",
    val geminiAuthenticationMode: GeminiAuthenticationMode = GeminiAuthenticationMode.NONE,
    val ollamaEndpoint: String = "",
    val ollamaModel: String = "",
    val litertModel: String = "",
    val litertContext: Int = 4096,
    val localModels: List<LocalModelDescriptor> = emptyList(),
    val catalogModels: List<LiteRtCatalogModel> = emptyList(),
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    val downloadErrors: Map<String, String> = emptyMap(),
    val activeLocalModelId: String? = null,
    val verifications: Map<String, ModelVerificationResult> = emptyMap(),
    val generationProvenIds: Set<String> = emptySet(),
    val verifyingModelId: String? = null,
    // Development matrix surface: per-model developer backend, health memory and runner results.
    val developerBackends: Map<String, LocalComputeBackend> = emptyMap(),
    val requestedBackends: Map<String, LocalComputeBackend> = emptyMap(),
    val modelFailureRecords: Map<String, LiteRtModelFailureRecord> = emptyMap(),
    val smokeTests: Map<String, LocalModelSmokeTestResult> = emptyMap(),
    val benchmarkResults: Map<String, LocalModelBenchmarkResult> = emptyMap(),
    val toolProbes: Map<String, LiteRtToolProbeResult> = emptyMap(),
    val matrixRows: List<LocalModelMatrixRow> = emptyList(),
    val runningMatrix: Boolean = false,
    val busyModelId: String? = null,
    /** Currently resident (loaded) model, if any. Resident != selected. */
    val residentModelId: String? = null,
    val residentBackend: LocalComputeBackend? = null,
    val residentContextTokens: Int? = null,
    val litertLoadDiagnostics: LiteRtLoadDiagnostics? = null,
    val litertAcquisitionDiagnostics: LiteRtAcquisitionDiagnostics? = null,
    val temperature: Float = 0.7f,
    val policy: AiContextPolicy = AiContextPolicy.CONSERVATIVE,
    val routingPolicy: AiRoutingPolicy = AiRoutingPolicy.AUTOMATIC,
    val activeProviderIsLocal: Boolean? = null,
    val nativeResources: List<NativeResourceSnapshot> = emptyList(),
    val legacyGgufCount: Int = 0,
    val legacyGgufBytes: Long = 0L,
    val inferenceRequests: Long = 0L,
    val inferenceInputTokens: Long = 0L,
    val inferenceOutputTokens: Long = 0L,
    val inferenceFailures: Long = 0L,
    val inferenceAverageDurationMs: Long? = null,
    val routerResolutions: Long = 0L,
    val toolOutcomes: Long = 0L,
    val toolFailures: Long = 0L,
    val toolUnknownOutcomes: Long = 0L,
    val toolAverageDurationMs: Long? = null,
)

private data class IntelligenceSettings(
    val openRouterKey: String = "",
    val openRouterModel: String = "openrouter/free",
    val openAiKey: String = "",
    val openAiModel: String = "",
    val anthropicKey: String = "",
    val anthropicModel: String = "",
    val geminiApiKey: String = "",
    val geminiProjectId: String = "",
    val geminiModel: String = "",
    val ollamaEndpoint: String = "",
    val ollamaModel: String = "llama3",
    val litertModel: String = "",
    val litertContext: Int = 4096,
    val temperature: Float = 0.7f,
    val policy: AiContextPolicy = AiContextPolicy.CONSERVATIVE
)

private data class ProviderSelection(
    val providerId: String?,
    val modelId: String?,
    val routingPolicy: AiRoutingPolicy,
    val diagnostics: List<AiProviderDiagnostic>,
    val catalog: AiModelCatalogState,
)

private data class LocalVerificationInputs(
    val results: Map<String, ModelVerificationResult> = emptyMap(),
    val verifyingModelId: String? = null,
    val diagnostics: LiteRtLoadDiagnostics? = null,
    val acquisitionDiagnostics: LiteRtAcquisitionDiagnostics? = null,
    val modelMemory: Map<String, LiteRtModelFailureRecord> = emptyMap(),
)

/** Development-only UI inputs, kept in one flow so the main combine stays within arity limits. */
private data class LocalDevInputs(
    val developerBackends: Map<String, LocalComputeBackend> = emptyMap(),
    val smokeTests: Map<String, LocalModelSmokeTestResult> = emptyMap(),
    val benchmarkResults: Map<String, LocalModelBenchmarkResult> = emptyMap(),
    val toolProbes: Map<String, LiteRtToolProbeResult> = emptyMap(),
    val matrixRows: List<LocalModelMatrixRow> = emptyList(),
    val runningMatrix: Boolean = false,
    val busyModelId: String? = null,
)

private data class IntelligencePresentationInputs(
    val settings: IntelligenceSettings,
    val lastResolvedModelId: String?,
    val inference: InferenceAggregate,
    val tools: ToolAggregate,
    val verification: LocalVerificationInputs = LocalVerificationInputs(),
)

class IntelligenceViewModel(
    private val snapshotManager: RuntimeSnapshotSource,
    private val aiProviderRouter: AiProviderRouter,
    private val cognitiveSession: CognitiveSession,
    private val storage: Storage,
    private val modelManager: LiteRtModelManager? = null,
    private val dispatchers: HaloDispatchers = productionHaloDispatchers(),
    private val inferenceLedger: InferenceLedger? = null,
) : ViewModel() {
    private val catalogRepository = DefaultAiModelCatalogRepository(
        aiProviderRouter.getAllProviders().filterIsInstance<AiInferenceProvider>()
    )
    private val _refreshTrigger = MutableStateFlow(0)
    private val _availableModels = MutableStateFlow<List<AiModel>>(emptyList())
    private val _isRefreshing = MutableStateFlow(false)
    private val _testConnectionResult = MutableSharedFlow<Boolean>()
    val testConnectionResult: SharedFlow<Boolean> = _testConnectionResult.asSharedFlow()
    private val _settings = MutableStateFlow(IntelligenceSettings())
    private val _lastResolvedModelId = MutableStateFlow<String?>(null)
    private val _verifications = MutableStateFlow<Map<String, ModelVerificationResult>>(emptyMap())
    private val _verifyingModelId = MutableStateFlow<String?>(null)

    /** The on-device provider owns load failures and the qualification probe. */
    private val localProvider: LiteRtLocalProvider?
        get() = aiProviderRouter.getAllProviders().filterIsInstance<LiteRtLocalProvider>().firstOrNull()
    private val geminiProvider: GeminiProvider?
        get() = aiProviderRouter.getAllProviders().filterIsInstance<GeminiProvider>().firstOrNull()

    private val devInputs = MutableStateFlow(LocalDevInputs())

    private val localVerificationInputs: Flow<LocalVerificationInputs> = combine(
        _verifications,
        _verifyingModelId,
        localProvider?.loadDiagnostics ?: MutableStateFlow<LiteRtLoadDiagnostics?>(null),
        modelManager?.acquisitionDiagnostics ?: MutableStateFlow<LiteRtAcquisitionDiagnostics?>(null),
        localProvider?.modelMemory ?: MutableStateFlow<Map<String, LiteRtModelFailureRecord>>(emptyMap()),
    ) { results, verifying, diagnostics, acquisitionDiagnostics, modelMemory ->
        LocalVerificationInputs(results, verifying, diagnostics, acquisitionDiagnostics, modelMemory)
    }

    init {
        viewModelScope.launch { loadSettings() }
        viewModelScope.launch {
            aiProviderRouter.selectedProviderId.collectLatest { providerId ->
                if (providerId != null) {
                    runCatching { aiProviderRouter.diagnostics() }
                    refreshModelsFor(providerId)
                }
            }
        }
        viewModelScope.launch {
            modelManager?.downloadStates?.collect { _refreshTrigger.update { it + 1 } }
        }
        viewModelScope.launch {
            cognitiveSession.state.collect { state ->
                if (state is CognitiveSessionState.Completed) {
                    _lastResolvedModelId.value = state.response.resolvedModelId
                }
            }
        }
    }

    private suspend fun loadSettings() {
        _settings.value = withContext(dispatchers.io) {
            val activeProvider = aiProviderRouter.selectedProviderId.value
            val activeModel = aiProviderRouter.selectedModelId.value
            IntelligenceSettings(
                openRouterKey = storage.getString("openrouter_api_key") ?: "",
                openRouterModel = activeModel.takeIf { activeProvider == ProviderIds.OPENROUTER } ?: "openrouter/free",
                openAiKey = storage.getString("openai_api_key") ?: "",
                openAiModel = activeModel.takeIf { activeProvider == ProviderIds.OPENAI }.orEmpty(),
                anthropicKey = storage.getString("anthropic_api_key") ?: "",
                anthropicModel = activeModel.takeIf { activeProvider == ProviderIds.ANTHROPIC }.orEmpty(),
                geminiApiKey = storage.getString("gemini_api_key") ?: "",
                geminiProjectId = storage.getString("gemini_project_id") ?: "",
                geminiModel = activeModel.takeIf { activeProvider == ProviderIds.GEMINI }.orEmpty(),
                ollamaEndpoint = storage.getString("ollama_endpoint") ?: "",
                ollamaModel = activeModel.takeIf { activeProvider == ProviderIds.OLLAMA }.orEmpty(),
                litertModel = activeModel.takeIf { activeProvider == ProviderIds.LITERT }.orEmpty(),
                litertContext = storage.getInt("litert_context", 4096),
                temperature = storage.getInt("ai_temperature", 70) / 100f,
                policy = storage.readRuntimeAiContextPolicy()
            )
        }
    }

    private val presentationInputs: Flow<IntelligencePresentationInputs> = combine(
        _settings,
        _lastResolvedModelId,
        inferenceLedger?.aggregate ?: MutableStateFlow(InferenceAggregate()),
        inferenceLedger?.toolAggregate ?: MutableStateFlow(ToolAggregate()),
        localVerificationInputs,
    ) { settings, resolved, inference, tools, verification ->
        IntelligencePresentationInputs(settings, resolved, inference, tools, verification)
    }

    val uiState: StateFlow<IntelligenceUiState> = combine(
        snapshotManager.snapshot,
        combine(
            aiProviderRouter.selectedProviderId,
            aiProviderRouter.selectedModelId,
            aiProviderRouter.routingPolicy,
            aiProviderRouter.diagnosticsFlow,
            catalogRepository.state
        ) { pId, mId, routing, diagnostics, catalog ->
            ProviderSelection(pId, mId, routing, diagnostics, catalog)
        },
        modelManager?.downloadStates ?: MutableStateFlow<Map<String, DownloadState>>(emptyMap()),
        modelManager?.legacyGgufFiles ?: MutableStateFlow(emptyList()),
        combine(presentationInputs, devInputs) { presentation, dev -> presentation to dev }
    ) { snapshot, selection, dlStates, ggufs, uiInputs ->
        val presentation = uiInputs.first
        val dev = uiInputs.second
        val settings = presentation.settings
        val lastResolvedModelId = presentation.lastResolvedModelId
        val inference = presentation.inference
        val tools = presentation.tools
        val verification = presentation.verification
        val (selectedProviderId, selectedModelId) = selection
        val requestedBackends = modelManager?.getAllCatalogModels().orEmpty()
            .associate { catalogModel ->
                catalogModel.id to (localProvider?.requestedBackend(catalogModel.id)
                    ?: catalogModel.runtimeProfile.preferredBackend)
            }
        val allProviders = aiProviderRouter.getAllProviders()
        val activeProvider = allProviders.find { it.id == selectedProviderId }
        // Presentation consumes the router's cached diagnostics. Provider status/capability calls
        // may touch credentials, files, or the network and must never execute in Compose mapping.
        val diagnosticsById = selection.diagnostics.associateBy { it.providerId }
        val activeDiagnostic = selectedProviderId?.let(diagnosticsById::get)
        val activeProviderStatus = activeDiagnostic?.let { ProviderStatus(it.state, it.message) }
            ?: ProviderStatus.UNAVAILABLE

        val localModels = (modelManager?.inventoryState?.value as? com.halo.providers.litert.models.LocalModelInventoryState.Ready)?.models.orEmpty()
        val installedIds = localModels.mapTo(hashSetOf()) { it.id }
        val downloadableEntries = modelManager?.getAllCatalogModels().orEmpty()
            .filterNot { it.id in installedIds }
            .map { downloadable ->
                AiModelCatalogEntry(
                    model = AiModel(
                        id = downloadable.id,
                        displayName = downloadable.displayName,
                        providerId = ProviderIds.LITERT,
                        inputCapabilities = buildSet {
                            add(AiCapability.TEXT)
                            if (downloadable.supportsVision) add(AiCapability.IMAGE)
                        },
                        contextLength = downloadable.contextWindowTokens.toLong(),
                        supportsStreaming = true,
                        supportsTools = downloadable.supportsTools,
                    ),
                    runtime = AiModelRuntimeMetadata(
                        ref = AiModelRef(ProviderIds.LITERT, downloadable.id),
                        location = AiExecutionLocation.ON_DEVICE,
                        installed = false,
                        resident = false,
                        requiresNetwork = false,
                        requiresCredential = false,
                        costClass = AiCostClass.LOCAL,
                    ),
                    source = ModelCatalogueSource.LOCAL_DOWNLOADABLE,
                )
            }
        val catalogEntries = (selection.catalog.models + downloadableEntries)
            .distinctBy { it.runtime.ref }

        IntelligenceUiState(
            activeProviderId = selectedProviderId ?: "",
            activeProviderName = activeProvider?.displayName ?: "None",
            activeProviderStatus = activeProviderStatus,
            capabilities = activeDiagnostic?.capabilities ?: emptySet(),
            activeModelId = selectedModelId,
            lastResolvedModelId = lastResolvedModelId,
            providers = allProviders.map { p ->
                val diagnostic = diagnosticsById[p.id]
                val status = diagnostic?.let { ProviderStatus(it.state, it.message) }
                    ?: ProviderStatus.UNAVAILABLE
                AiProviderUiState(
                    id = p.id,
                    name = p.displayName,
                    status = status,
                    isSelected = p.id == selectedProviderId,
                    capabilities = diagnostic?.capabilities ?: emptySet()
                )
            },
            availableModels = catalogEntries.map { it.model },
            catalogEntries = catalogEntries,
            catalogErrors = selection.catalog.providerErrors,
            isRefreshingModels = selection.catalog.refreshingProviders.isNotEmpty(),
            openRouterKey = settings.openRouterKey,
            openRouterModel = settings.openRouterModel,
            openAiKey = settings.openAiKey,
            openAiModel = settings.openAiModel,
            anthropicKey = settings.anthropicKey,
            anthropicModel = settings.anthropicModel,
            geminiApiKey = settings.geminiApiKey,
            geminiProjectId = settings.geminiProjectId,
            geminiModel = settings.geminiModel,
            geminiAuthenticationMode = geminiProvider?.authenticationMode ?: GeminiAuthenticationMode.NONE,
            ollamaEndpoint = settings.ollamaEndpoint,
            ollamaModel = settings.ollamaModel,
            litertModel = settings.litertModel,
            litertContext = settings.litertContext,
            localModels = localModels,
            catalogModels = modelManager?.getAllCatalogModels().orEmpty(),
            downloadStates = dlStates,
            verifications = verification.results,
            generationProvenIds = localModels.filter { localProvider?.isGenerationProven(it) == true }.mapTo(mutableSetOf()) { it.id },
            verifyingModelId = verification.verifyingModelId,
            developerBackends = dev.developerBackends,
            requestedBackends = requestedBackends,
            modelFailureRecords = verification.modelMemory,
            smokeTests = dev.smokeTests,
            benchmarkResults = dev.benchmarkResults,
            toolProbes = dev.toolProbes,
            matrixRows = dev.matrixRows,
            runningMatrix = dev.runningMatrix,
            busyModelId = dev.busyModelId,
            residentModelId = localProvider?.modelSession?.takeIf { it.isLoaded() }?.loadedModelId,
            residentBackend = localProvider?.modelSession?.takeIf { it.isLoaded() }?.activeBackend,
            residentContextTokens = localProvider?.modelSession?.takeIf { it.isLoaded() }?.effectiveContextSize,
            litertLoadDiagnostics = verification.diagnostics,
            litertAcquisitionDiagnostics = verification.acquisitionDiagnostics,
            temperature = settings.temperature,
            policy = settings.policy,
            routingPolicy = selection.routingPolicy,
            activeProviderIsLocal = activeProvider?.isLocal,
            nativeResources = snapshot.nativeResources,
            legacyGgufCount = ggufs.size,
            legacyGgufBytes = ggufs.sumOf { it.length() },
            inferenceRequests = inference.requests,
            inferenceInputTokens = inference.inputTokens,
            inferenceOutputTokens = inference.outputTokens,
            inferenceFailures = inference.failures,
            inferenceAverageDurationMs = inference.totalDurationMs
                .takeIf { inference.requests > 0 }
                ?.div(inference.requests),
            routerResolutions = inference.routerResolutions.values.sum(),
            toolOutcomes = tools.outcomes,
            toolFailures = tools.failures,
            toolUnknownOutcomes = tools.unknownOutcomes,
            toolAverageDurationMs = tools.totalDurationMs
                .takeIf { tools.outcomes > 0 }
                ?.div(tools.outcomes),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IntelligenceUiState())

    fun selectProvider(providerId: String) {
        aiProviderRouter.selectProvider(providerId)
    }

    /**
     * Selects a model for the *currently selected* provider. Persistence stays in the router:
     * [selectTarget] is the single writer for `active_ai_provider` / `active_ai_model`.
     */
    fun selectModel(modelId: String) {
        val providerId = aiProviderRouter.selectedProviderId.value ?: return
        when (providerId) {
            ProviderIds.LITERT -> selectLocalModel(modelId)
            ProviderIds.OLLAMA -> selectTarget(AiModelRef(ProviderIds.OLLAMA, modelId))
            ProviderIds.OPENROUTER -> selectTarget(AiModelRef(ProviderIds.OPENROUTER, modelId))
            ProviderIds.OPENAI -> selectTarget(AiModelRef(ProviderIds.OPENAI, modelId))
            ProviderIds.ANTHROPIC -> selectTarget(AiModelRef(ProviderIds.ANTHROPIC, modelId))
            ProviderIds.GEMINI -> selectTarget(AiModelRef(ProviderIds.GEMINI, modelId))
        }
    }

    /**
     * Atomically selects the on-device LiteRT provider *and* the local model, independent of
     * whatever provider happened to be selected before. The LiteRT-only Model Browser must use
     * this instead of [selectModel], which would otherwise bind a LiteRT model ID to OpenRouter
     * or Ollama.
     */
    fun selectLocalModel(modelId: String) {
        updateSettings { it.copy(litertModel = modelId) }
        aiProviderRouter.selectTarget(AiModelRef(ProviderIds.LITERT, modelId))
    }

    fun selectTarget(ref: AiModelRef) {
        when (ref.providerId) {
            ProviderIds.LITERT -> updateSettings { it.copy(litertModel = ref.modelId) }
            ProviderIds.OLLAMA -> updateSettings { it.copy(ollamaModel = ref.modelId) }
            ProviderIds.OPENROUTER -> updateSettings { it.copy(openRouterModel = ref.modelId) }
            ProviderIds.OPENAI -> updateSettings { it.copy(openAiModel = ref.modelId) }
            ProviderIds.ANTHROPIC -> updateSettings { it.copy(anthropicModel = ref.modelId) }
            ProviderIds.GEMINI -> updateSettings { it.copy(geminiModel = ref.modelId) }
        }
        aiProviderRouter.selectTarget(ref)
    }

    /** Runs the explicit qualification probe. Never triggered automatically on app launch. */
    fun verifyModel(modelId: String) {
        val provider = localProvider ?: return
        if (_verifyingModelId.value != null) return
        _verifyingModelId.value = modelId
        viewModelScope.launch {
            try {
                val result = withContext(dispatchers.io) { provider.verifyModel(modelId) }
                _verifications.value = _verifications.value + (modelId to result)
            } finally {
                _verifyingModelId.value = null
            }
        }
    }

    fun cancelDownload(modelId: String) {
        modelManager?.cancelDownload(modelId)
    }

    fun activateCatalogEntry(entry: AiModelCatalogEntry) {
        if (entry.source == ModelCatalogueSource.LOCAL_DOWNLOADABLE) {
            modelManager?.getAllCatalogModels()?.firstOrNull { it.id == entry.model.id }
                ?.let(::downloadCatalogModel)
            return
        }
        selectTarget(entry.runtime.ref)
    }

    fun testConnection() {
        viewModelScope.launch {
            val currentId = aiProviderRouter.selectedProviderId.value ?: return@launch
            val provider = aiProviderRouter.getAllProviders().find { it.id == currentId }
            val ok = runCatching { provider?.testConnection() == true }.getOrDefault(false)
            _testConnectionResult.emit(ok)
        }
    }

    fun refreshModels() {
        val providerId = aiProviderRouter.selectedProviderId.value ?: return
        refreshModelsFor(providerId)
    }

    fun setOpenRouterKey(key: String) {
        val normalized = key.trim()
        updateSettings { it.copy(openRouterKey = normalized) }
        persistAndRefresh { storage.putString("openrouter_api_key", normalized) }
    }

    fun setOpenAiKey(key: String) {
        val normalized = key.trim()
        updateSettings { it.copy(openAiKey = normalized) }
        persistAndRefresh { storage.putString("openai_api_key", normalized) }
    }

    fun setAnthropicKey(key: String) {
        val normalized = key.trim()
        updateSettings { it.copy(anthropicKey = normalized) }
        persistAndRefresh { storage.putString("anthropic_api_key", normalized) }
    }

    fun setGeminiConfiguration(projectId: String, apiKey: String) {
        val normalizedProject = projectId.trim()
        val normalizedKey = apiKey.trim()
        updateSettings {
            it.copy(geminiProjectId = normalizedProject, geminiApiKey = normalizedKey)
        }
        persistAndRefresh {
            storage.putString("gemini_project_id", normalizedProject)
            storage.putString("gemini_api_key", normalizedKey)
        }
    }

    fun acceptGeminiOAuthToken(token: String, projectId: String) {
        val normalizedProject = projectId.trim()
        updateSettings { it.copy(geminiProjectId = normalizedProject) }
        geminiProvider?.acceptOAuthAccessToken(token, normalizedProject)
        _refreshTrigger.update { it + 1 }
        persistAndRefresh { storage.putString("gemini_project_id", normalizedProject) }
    }

    fun disconnectGeminiOAuth() {
        geminiProvider?.clearOAuthSession()
        _refreshTrigger.update { it + 1 }
        refreshModelsFor(ProviderIds.GEMINI)
    }

    fun setOllamaEndpoint(endpoint: String) {
        val normalized = endpoint.trim().trimEnd('/')
        updateSettings { it.copy(ollamaEndpoint = normalized) }
        persistAndRefresh { storage.putString("ollama_endpoint", normalized) }
    }

    fun setToolsEnabled(enabled: Boolean) {
        updatePolicy { it.copy(includeAvailableTools = enabled) }
    }

    fun setImagesEnabled(enabled: Boolean) {
        updatePolicy { it.copy(includeImages = enabled) }
    }

    fun setWorkspaceEnabled(enabled: Boolean) {
        updatePolicy { it.copy(includeWorkspace = enabled) }
    }

    fun setCloudWorkspaceEnabled(enabled: Boolean) {
        updatePolicy { it.copy(allowCloudWorkspace = enabled) }
    }

    fun setCloudScreenEnabled(enabled: Boolean) {
        updatePolicy { it.copy(allowCloudScreenContext = enabled) }
    }

    fun setCloudImagesEnabled(enabled: Boolean) {
        updatePolicy { it.copy(allowCloudImages = enabled) }
    }

    fun downloadCatalogModel(model: LiteRtCatalogModel) {
        unloadIfResident(model.id)
        modelManager?.downloadCatalogModel(model)
        _refreshTrigger.update { it + 1 }
    }

    /** Resumes an interrupted transfer from the stored partial (HTTP Range when the server allows). */
    fun resumeCatalogDownload(model: LiteRtCatalogModel) {
        unloadIfResident(model.id)
        modelManager?.resumeDownload(model)
        _refreshTrigger.update { it + 1 }
    }

    /** Explicit "discard download": drops the partial artifact and its pinned metadata. */
    fun discardPartialDownload(modelId: String) {
        modelManager?.discardPartial(modelId)
        _refreshTrigger.update { it + 1 }
    }

    fun storagePreflight(model: LiteRtCatalogModel): LiteRtStoragePreflightResult? =
        modelManager?.storagePreflight(model)

    /** Development per-model execution preference: AUTO, GPU or CPU. Persisted by model id. */
    fun setDeveloperBackend(modelId: String, backend: LocalComputeBackend) {
        localProvider?.setDeveloperBackend(modelId, backend)
        devInputs.value = devInputs.value.copy(
            developerBackends = devInputs.value.developerBackends + (modelId to backend),
        )
        _refreshTrigger.update { it + 1 }
    }

    /** Two-prompt smoke test: proves repeated generation without a process restart. */
    fun smokeTestModel(modelId: String) = runModelTask(modelId) { provider ->
        val result = provider.smokeTestModel(modelId)
        devInputs.value = devInputs.value.copy(smokeTests = devInputs.value.smokeTests + (modelId to result))
    }

    fun benchmarkModel(modelId: String) = runModelTask(modelId) { provider ->
        provider.benchmarkModel(modelId).onSuccess { result ->
            devInputs.value = devInputs.value.copy(
                benchmarkResults = devInputs.value.benchmarkResults + (modelId to result),
            )
        }
    }

    /** Optional capability probe: never marks the model broken when the artifact cannot tool-call. */
    fun probeModelTools(modelId: String) = runModelTask(modelId) { provider ->
        val result = provider.probeToolCalling(modelId)
        devInputs.value = devInputs.value.copy(toolProbes = devInputs.value.toolProbes + (modelId to result))
    }

    fun unloadLocalModel() {
        val provider = localProvider ?: return
        viewModelScope.launch(dispatchers.io) { provider.unloadLocalModel() }
    }

    /** Development-only: loads, smoke-prompts and unloads every installed model, serially. */
    fun runModelMatrix() {
        val provider = localProvider ?: return
        if (devInputs.value.runningMatrix) return
        devInputs.value = devInputs.value.copy(runningMatrix = true)
        viewModelScope.launch {
            try {
                val rows = withContext(dispatchers.io) { provider.runModelMatrix() }
                devInputs.value = devInputs.value.copy(matrixRows = rows)
            } finally {
                devInputs.value = devInputs.value.copy(runningMatrix = false)
            }
        }
    }

    private fun unloadIfResident(modelId: String) {
        localProvider?.let { provider ->
            if (provider.modelSession.loadedModelId == modelId) {
                // Switching or re-downloading must not leave the engine pinned to stale bytes.
                viewModelScope.launch(dispatchers.io) { provider.modelSession.unloadModel() }
            }
        }
    }

    private fun runModelTask(modelId: String, block: suspend (LiteRtLocalProvider) -> Unit) {
        val provider = localProvider ?: return
        if (devInputs.value.busyModelId != null) return
        devInputs.value = devInputs.value.copy(busyModelId = modelId)
        viewModelScope.launch {
            try {
                withContext(dispatchers.io) { block(provider) }
            } finally {
                devInputs.value = devInputs.value.copy(busyModelId = null)
                _refreshTrigger.update { it + 1 }
            }
        }
    }

    fun importModelUri(uri: Uri) {
        viewModelScope.launch {
            modelManager?.importModelFromUri(uri)
            _refreshTrigger.update { it + 1 }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelManager?.deleteModel(modelId)
            _verifications.value = _verifications.value - modelId
            _refreshTrigger.update { it + 1 }
        }
    }

    fun deleteLegacyGgufFiles() {
        viewModelScope.launch {
            modelManager?.deleteLegacyGgufFiles()
            _refreshTrigger.update { it + 1 }
        }
    }

    private fun updatePolicy(transform: (AiContextPolicy) -> AiContextPolicy) {
        val next = transform(_settings.value.policy)
        updateSettings { it.copy(policy = next) }
        persist { storage.writeRuntimeAiContextPolicy(next) }
    }

    private fun refreshModelsFor(providerId: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            catalogRepository.refresh()
            _isRefreshing.value = false
        }
    }

    private fun updateSettings(transform: (IntelligenceSettings) -> IntelligenceSettings) {
        _settings.value = transform(_settings.value)
    }

    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch(dispatchers.io) { block() }
    }

    private fun persistAndRefresh(block: suspend () -> Unit) {
        viewModelScope.launch {
            withContext(dispatchers.io) { block() }
            _isRefreshing.value = true
            try {
                catalogRepository.refresh()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
