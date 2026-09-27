package com.halo.app.ui.screens.settings

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ai.readRuntimeAiContextPolicy
import com.halo.app.ai.writeRuntimeAiContextPolicy
import com.halo.providers.litert.models.LiteRtModelManager
import com.halo.providers.litert.models.DownloadState
import com.halo.providers.litert.models.LiteRtCatalogModel
import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.HaloDispatchers
import com.halo.sdk.runtime.NativeResourceSnapshot
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.productionHaloDispatchers
import com.halo.runtime.cognition.session.CognitiveSession
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class IntelligenceUiState(
    val activeProviderId: String = "",
    val activeProviderName: String = "None",
    val activeProviderStatus: ProviderStatus = ProviderStatus.UNAVAILABLE,
    val capabilities: Set<AiCapability> = emptySet(),
    val activeModelId: String? = null,
    val providers: List<AiProviderUiState> = emptyList(),
    val availableModels: List<AiModel> = emptyList(),
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
    val temperature: Float = 0.7f,
    val policy: AiContextPolicy = AiContextPolicy.CONSERVATIVE,
    val routingPolicy: AiRoutingPolicy = AiRoutingPolicy.AUTOMATIC,
    val activeProviderIsLocal: Boolean? = null,
    val nativeResources: List<NativeResourceSnapshot> = emptyList(),
    val legacyGgufCount: Int = 0,
    val legacyGgufBytes: Long = 0L,
)

private data class IntelligenceSettings(
    val openRouterKey: String = "",
    val openRouterModel: String = "openrouter/free",
    val ollamaEndpoint: String = "",
    val ollamaModel: String = "llama3",
    val litertModel: String = "gemma-3-1b-it-int4",
    val litertContext: Int = 4096,
    val temperature: Float = 0.7f,
    val policy: AiContextPolicy = AiContextPolicy.CONSERVATIVE
)

private data class ProviderSelection(
    val providerId: String?,
    val modelId: String?,
    val routingPolicy: AiRoutingPolicy,
    val diagnostics: List<AiProviderDiagnostic>
)

class IntelligenceViewModel(
    private val snapshotManager: RuntimeSnapshotSource,
    private val aiProviderRouter: AiProviderRouter,
    private val cognitiveSession: CognitiveSession,
    private val storage: Storage,
    private val modelManager: LiteRtModelManager? = null,
    private val dispatchers: HaloDispatchers = productionHaloDispatchers()
) : ViewModel() {
    private val _refreshTrigger = MutableStateFlow(0)
    private val _availableModels = MutableStateFlow<List<AiModel>>(emptyList())
    private val _isRefreshing = MutableStateFlow(false)
    private val _testConnectionResult = MutableSharedFlow<Boolean>()
    val testConnectionResult: SharedFlow<Boolean> = _testConnectionResult.asSharedFlow()
    private val _settings = MutableStateFlow(IntelligenceSettings())

    init {
        viewModelScope.launch { loadSettings() }
        viewModelScope.launch {
            aiProviderRouter.selectedProviderId.collectLatest { providerId ->
                if (providerId != null) {
                    runCatching { aiProviderRouter.diagnostics() }
                    syncStoredModelSelection(providerId)
                    refreshModelsFor(providerId)
                }
            }
        }
        viewModelScope.launch {
            modelManager?.downloadStates?.collect { _refreshTrigger.update { it + 1 } }
        }
    }

    private suspend fun loadSettings() {
        _settings.value = withContext(dispatchers.io) {
            val storedOpenRouterModel = storage.getString("openrouter_model")?.trim()?.ifBlank { null }
            val openRouterModel = storedOpenRouterModel ?: "openrouter/free"
            if (storedOpenRouterModel == null) storage.putString("openrouter_model", openRouterModel)
            IntelligenceSettings(
                openRouterKey = storage.getString("openrouter_api_key") ?: "",
                openRouterModel = openRouterModel,
                ollamaEndpoint = storage.getString("ollama_endpoint") ?: "",
                ollamaModel = storage.getString("ollama_model") ?: "llama3",
                litertModel = storage.getString("litert_model") ?: "gemma-3-1b-it-int4",
                litertContext = storage.getInt("litert_context", 4096),
                temperature = storage.getInt("ai_temperature", 70) / 100f,
                policy = storage.readRuntimeAiContextPolicy()
            )
        }
    }

    private suspend fun syncStoredModelSelection(providerId: String) {
        val settings = _settings.value
        val storedModelId = when (providerId) {
            ProviderIds.OPENROUTER -> settings.openRouterModel.trim().ifBlank { "openrouter/free" }
            ProviderIds.OLLAMA -> settings.ollamaModel.trim().ifBlank { null }
            ProviderIds.LITERT, ProviderIds.LLAMACPP -> settings.litertModel.trim().ifBlank { null }
            else -> null
        }
        if (storedModelId != aiProviderRouter.selectedModelId.value) aiProviderRouter.selectModel(storedModelId)
    }

    val uiState: StateFlow<IntelligenceUiState> = combine(
        snapshotManager.snapshot,
        combine(
            aiProviderRouter.selectedProviderId,
            aiProviderRouter.selectedModelId,
            aiProviderRouter.routingPolicy,
            aiProviderRouter.diagnosticsFlow
        ) { pId, mId, routing, diagnostics ->
            ProviderSelection(pId, mId, routing, diagnostics)
        },
        modelManager?.downloadStates ?: MutableStateFlow<Map<String, DownloadState>>(emptyMap()),
        modelManager?.legacyGgufFiles ?: MutableStateFlow(emptyList()),
        _settings
    ) { snapshot, selection, dlStates, ggufs, settings ->
        val (selectedProviderId, selectedModelId) = selection
        val allProviders = aiProviderRouter.getAllProviders()
        val activeProvider = allProviders.find { it.id == selectedProviderId }
        val activeProviderStatus = selectedProviderId?.let { id ->
            runCatching { activeProvider?.getStatus() }.getOrNull()
        } ?: ProviderStatus.UNAVAILABLE

        val localModels = (modelManager?.inventoryState?.value as? com.halo.providers.litert.models.LocalModelInventoryState.Ready)?.models.orEmpty()

        IntelligenceUiState(
            activeProviderId = selectedProviderId ?: "",
            activeProviderName = activeProvider?.displayName ?: "None",
            activeProviderStatus = activeProviderStatus,
            capabilities = activeProvider?.getCapabilities() ?: emptySet(),
            activeModelId = selectedModelId,
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
            availableModels = _availableModels.value,
            isRefreshingModels = _isRefreshing.value,
            openRouterKey = settings.openRouterKey,
            openRouterModel = settings.openRouterModel,
            ollamaEndpoint = settings.ollamaEndpoint,
            ollamaModel = settings.ollamaModel,
            litertModel = settings.litertModel,
            litertContext = settings.litertContext,
            localModels = localModels,
            catalogModels = modelManager?.getAllCatalogModels().orEmpty(),
            downloadStates = dlStates,
            temperature = settings.temperature,
            policy = settings.policy,
            routingPolicy = selection.routingPolicy,
            activeProviderIsLocal = activeProvider?.isLocal,
            nativeResources = snapshot.nativeResources,
            legacyGgufCount = ggufs.size,
            legacyGgufBytes = ggufs.sumOf { it.length() }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IntelligenceUiState())

    fun selectProvider(providerId: String) {
        aiProviderRouter.selectProvider(providerId)
        viewModelScope.launch { syncStoredModelSelection(providerId) }
    }

    fun selectModel(modelId: String) {
        val providerId = aiProviderRouter.selectedProviderId.value ?: return
        when (providerId) {
            ProviderIds.LITERT, ProviderIds.LLAMACPP -> {
                updateSettings { it.copy(litertModel = modelId) }
                persist { storage.putString("litert_model", modelId) }
            }
            ProviderIds.OLLAMA -> {
                updateSettings { it.copy(ollamaModel = modelId) }
                persist { storage.putString("ollama_model", modelId) }
            }
            ProviderIds.OPENROUTER -> {
                updateSettings { it.copy(openRouterModel = modelId) }
                persist { storage.putString("openrouter_model", modelId) }
            }
        }
        aiProviderRouter.selectModel(modelId)
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

    fun importModelUri(uri: Uri, displayName: String = "Imported Model") {
        viewModelScope.launch {
            modelManager?.importModelFromUri(uri, displayName)
            _refreshTrigger.update { it + 1 }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelManager?.deleteModel(modelId)
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
            val provider = aiProviderRouter.getAllProviders().find { it.id == providerId } as? AiInferenceProvider
            val models = runCatching { provider?.listModels() }.getOrNull().orEmpty()
            _availableModels.value = models
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
