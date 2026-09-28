package com.halo.app.ui.screens.settings

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ai.readRuntimeAiContextPolicy
import com.halo.app.ai.writeRuntimeAiContextPolicy
import com.halo.providers.litert.LiteRtLoadDiagnostics
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.LiteRtModelManager
import com.halo.providers.litert.models.DownloadState
import com.halo.providers.litert.models.LiteRtCatalogModel
import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.models.ModelVerificationResult
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
    val litertLoadDiagnostics: LiteRtLoadDiagnostics? = null,
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

    private val localVerificationInputs: Flow<LocalVerificationInputs> = combine(
        _verifications,
        _verifyingModelId,
        localProvider?.loadDiagnostics ?: MutableStateFlow<LiteRtLoadDiagnostics?>(null),
    ) { results, verifying, diagnostics ->
        LocalVerificationInputs(results, verifying, diagnostics)
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
                ollamaEndpoint = storage.getString("ollama_endpoint") ?: "",
                ollamaModel = activeModel.takeIf { activeProvider == ProviderIds.OLLAMA }.orEmpty(),
                litertModel = activeModel.takeIf { activeProvider == ProviderIds.LITERT }.orEmpty(),
                litertContext = storage.getInt("litert_context", 4096),
                temperature = storage.getInt("ai_temperature", 70) / 100f,
                policy = storage.readRuntimeAiContextPolicy()
            )
        }
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
        combine(
            _settings,
            _lastResolvedModelId,
            inferenceLedger?.aggregate ?: MutableStateFlow(InferenceAggregate()),
            inferenceLedger?.toolAggregate ?: MutableStateFlow(ToolAggregate()),
            localVerificationInputs,
        ) { settings, resolved, inference, tools, verification ->
            IntelligencePresentationInputs(settings, resolved, inference, tools, verification)
        }
    ) { snapshot, selection, dlStates, ggufs, presentation ->
        val settings = presentation.settings
        val lastResolvedModelId = presentation.lastResolvedModelId
        val inference = presentation.inference
        val tools = presentation.tools
        val verification = presentation.verification
        val (selectedProviderId, selectedModelId) = selection
        val allProviders = aiProviderRouter.getAllProviders()
        val activeProvider = allProviders.find { it.id == selectedProviderId }
        val activeProviderStatus = selectedProviderId?.let { id ->
            runCatching { activeProvider?.getStatus() }.getOrNull()
        } ?: ProviderStatus.UNAVAILABLE

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
            capabilities = activeProvider?.getCapabilities() ?: emptySet(),
            activeModelId = selectedModelId,
            lastResolvedModelId = lastResolvedModelId,
            providers = allProviders.map { p ->
                val status = runCatching { p.getStatus() }.getOrDefault(ProviderStatus.UNAVAILABLE)
                val caps = runCatching { p.getCapabilities() }.getOrDefault(emptySet())
                AiProviderUiState(
                    id = p.id,
                    name = p.displayName,
                    status = status,
                    isSelected = p.id == selectedProviderId,
                    capabilities = caps
                )
            },
            availableModels = catalogEntries.map { it.model },
            catalogEntries = catalogEntries,
            catalogErrors = selection.catalog.providerErrors,
            isRefreshingModels = selection.catalog.refreshingProviders.isNotEmpty(),
            openRouterKey = settings.openRouterKey,
            openRouterModel = settings.openRouterModel,
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
            litertLoadDiagnostics = verification.diagnostics,
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
        persist { storage.putString("openrouter_api_key", normalized) }
        refreshModelsFor(ProviderIds.OPENROUTER)
    }

    fun setOllamaEndpoint(endpoint: String) {
        val normalized = endpoint.trim().trimEnd('/')
        updateSettings { it.copy(ollamaEndpoint = normalized) }
        persist { storage.putString("ollama_endpoint", normalized) }
        refreshModelsFor(ProviderIds.OLLAMA)
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
        localProvider?.let { provider ->
            if (provider.modelSession.loadedModelId == model.id) {
                // Re-downloading a resident model must not leave the engine pinned to stale bytes.
                viewModelScope.launch(dispatchers.io) { provider.modelSession.unloadModel() }
            }
        }
        modelManager?.downloadCatalogModel(model)
        _refreshTrigger.update { it + 1 }
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
}
