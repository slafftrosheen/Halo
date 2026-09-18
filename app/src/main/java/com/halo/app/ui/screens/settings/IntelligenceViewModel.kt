package com.halo.app.ui.screens.settings

import android.util.Log
import com.halo.app.ai.readRuntimeAiContextPolicy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.providers.android.assistant.AndroidAssistantProvider
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.NativeResourceSnapshot
import com.halo.sdk.runtime.Storage
import com.halo.runtime.cognition.session.CognitiveSession
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.halo.providers.llamacpp.ModelDownloadManager
import com.halo.providers.llamacpp.CognitiveModel
import com.halo.providers.llamacpp.DownloadState

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
    val llamacppModel: String = "",
    val llamacppContext: Int = 4096,
    val localModels: List<CognitiveModel> = emptyList(),
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    val activeLocalModelId: String? = null,
    val temperature: Float = 0.7f,
    val policy: AiContextPolicy = AiContextPolicy.CONSERVATIVE,
    val assistantPackage: String? = null,
    val routingPolicy: AiRoutingPolicy = AiRoutingPolicy.AUTOMATIC,
    val activeProviderIsLocal: Boolean? = null,
    val nativeResources: List<NativeResourceSnapshot> = emptyList()
)

private data class IntelligenceSettings(
    val openRouterKey: String = "",
    val openRouterModel: String = "openrouter/free",
    val ollamaEndpoint: String = "",
    val ollamaModel: String = "llama3",
    val llamacppModel: String = "qwen1.5-0.5b",
    val llamacppContext: Int = 4096,
    val temperature: Float = 0.7f,
    val policy: AiContextPolicy = AiContextPolicy.CONSERVATIVE
)

private data class ProviderSelection(
    val providerId: String?,
    val modelId: String?,
    val assistantPackage: String?,
    val routingPolicy: AiRoutingPolicy
)

class IntelligenceViewModel(
    private val snapshotManager: RuntimeSnapshotSource,
    private val aiProviderRouter: AiProviderRouter,
    private val cognitiveSession: CognitiveSession,
    private val storage: Storage,
    private val modelDownloadManager: ModelDownloadManager? = null
) : ViewModel() {
    private val _refreshTrigger = MutableStateFlow(0)
    private val _availableModels = MutableStateFlow<List<AiModel>>(emptyList())
    private val _isRefreshing = MutableStateFlow(false)
    private val _testConnectionResult = MutableSharedFlow<Boolean>()
    val testConnectionResult: SharedFlow<Boolean> = _testConnectionResult.asSharedFlow()
    private val _assistantPackage = MutableStateFlow<String?>(null)
    val assistantPackage: StateFlow<String?> = _assistantPackage.asStateFlow()
    private val _settings = MutableStateFlow(IntelligenceSettings())

    init {
        loadSettings()
        modelDownloadManager?.refreshStates()
        viewModelScope.launch {
            aiProviderRouter.selectedProviderId.collectLatest { providerId ->
                if (providerId != null) {
                    syncStoredModelSelection(providerId)
                    refreshModelsFor(providerId)
                    refreshAssistantInfo(providerId)
                }
            }
        }
        viewModelScope.launch {
            modelDownloadManager?.downloadStates?.collect { _refreshTrigger.update { it + 1 } }
        }
    }

    private fun loadSettings() {
        val storedOpenRouterModel = storage.getString("openrouter_model")?.trim()?.ifBlank { null }
        val openRouterModel = storedOpenRouterModel ?: "openrouter/free"
        if (storedOpenRouterModel == null) storage.putString("openrouter_model", openRouterModel)
        _settings.value = IntelligenceSettings(
            openRouterKey = storage.getString("openrouter_api_key") ?: "",
            openRouterModel = openRouterModel,
            ollamaEndpoint = storage.getString("ollama_endpoint") ?: "",
            ollamaModel = storage.getString("ollama_model") ?: "llama3",
            llamacppModel = storage.getString("llamacpp_model") ?: "qwen1.5-0.5b",
            llamacppContext = storage.getInt("llamacpp_context", 4096),
            temperature = storage.getInt("ai_temperature", 70) / 100f,
            policy = storage.readRuntimeAiContextPolicy()
        )
    }

    private suspend fun syncStoredModelSelection(providerId: String) {
        val settings = _settings.value
        val storedModelId = when (providerId) {
            ProviderIds.OPENROUTER -> settings.openRouterModel.trim().ifBlank { "openrouter/free" }
            ProviderIds.OLLAMA -> settings.ollamaModel.trim().ifBlank { null }
            ProviderIds.LLAMACPP -> modelDownloadManager?.activeModelId?.value ?: settings.llamacppModel.trim().ifBlank { null }
            else -> null
        }
        if (storedModelId != aiProviderRouter.selectedModelId.value) aiProviderRouter.selectModel(storedModelId)
    }

    private suspend fun refreshAssistantInfo(providerId: String) {
        if (providerId != ProviderIds.ANDROID_ASSISTANT) { _assistantPackage.value = null; return }
        val provider = aiProviderRouter.getAllProviders().firstOrNull { it.id == providerId }
        _assistantPackage.value = (provider as? AndroidAssistantProvider)?.assistantPackageName()
    }

    val uiState: StateFlow<IntelligenceUiState> = combine(
        snapshotManager.snapshot,
        combine(
            aiProviderRouter.selectedProviderId,
            aiProviderRouter.selectedModelId,
            _assistantPackage,
            aiProviderRouter.routingPolicy
        ) { pId, mId, pkg, routing -> ProviderSelection(pId, mId, pkg, routing) },
        modelDownloadManager?.downloadStates ?: MutableStateFlow<Map<String, DownloadState>>(emptyMap()),
        combine(modelDownloadManager?.activeModelId ?: MutableStateFlow<String?>(null), _refreshTrigger) { activeId, _ -> activeId },
        _settings
    ) { snapshot, selection, dlStates, activeLocalId, settings ->
        val (selectedProviderId, selectedModelId, assistantPkg) = selection
        val allProviders = aiProviderRouter.getAllProviders()
        val activeProvider = allProviders.find { it.id == selectedProviderId }
        val snapshotProvider = snapshot.providers.find { it.id == selectedProviderId }
        IntelligenceUiState(
            activeProviderId = selectedProviderId ?: "",
            activeProviderName = snapshotProvider?.name ?: activeProvider?.displayName ?: "None",
            activeProviderStatus = activeProvider?.status?.value ?: ProviderStatus.UNAVAILABLE,
            capabilities = activeProvider?.getCapabilities() ?: emptySet(),
            activeModelId = selectedModelId ?: snapshotProvider?.activeModel ?: if (selectedProviderId == ProviderIds.LLAMACPP) activeLocalId ?: settings.llamacppModel else null,
            providers = allProviders.map { routerProv ->
                val snap = snapshot.providers.find { it.id == routerProv.id }
                AiProviderUiState(routerProv.id, snap?.name ?: routerProv.displayName, routerProv.status.value, routerProv.id == selectedProviderId, routerProv.getCapabilities())
            },
            availableModels = _availableModels.value,
            isRefreshingModels = _isRefreshing.value,
            openRouterKey = settings.openRouterKey,
            openRouterModel = settings.openRouterModel,
            ollamaEndpoint = settings.ollamaEndpoint,
            ollamaModel = settings.ollamaModel,
            llamacppModel = settings.llamacppModel,
            llamacppContext = settings.llamacppContext,
            localModels = modelDownloadManager?.getAllModels() ?: emptyList(),
            downloadStates = dlStates,
            activeLocalModelId = activeLocalId ?: settings.llamacppModel,
            temperature = settings.temperature,
            policy = settings.policy,
            assistantPackage = assistantPkg,
            routingPolicy = selection.routingPolicy,
            activeProviderIsLocal = activeProvider?.isLocal,
            nativeResources = snapshot.nativeResources
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IntelligenceUiState())

    fun handoffToAssistant(prompt: String? = null) {
        val provider = aiProviderRouter.getAllProviders().firstOrNull { it.id == ProviderIds.ANDROID_ASSISTANT } as? AndroidAssistantProvider ?: return
        runCatching { provider.handoff(prompt) }.onFailure { error -> Log.w("IntelligenceViewModel", "Assistant handoff failed", error) }
        _refreshTrigger.update { it + 1 }
    }
    fun downloadModel(modelId: String) { modelDownloadManager?.downloadModel(modelId) }
    fun downloadCustomUrl(url: String, name: String) {
        val normalizedUrl = url.trim(); val normalizedName = name.trim()
        if (normalizedUrl.isBlank() || normalizedName.isBlank()) return
        modelDownloadManager?.downloadCustomUrl(normalizedUrl, normalizedName); _refreshTrigger.update { it + 1 }
    }
    fun setActiveLocalModel(modelId: String) {
        if (modelDownloadManager?.getAllModels()?.any { it.id == modelId } != true) return
        modelDownloadManager?.setActiveModel(modelId); updateSettings { it.copy(llamacppModel = modelId) }; persist { storage.putString("llamacpp_model", modelId) }
        if (aiProviderRouter.selectedProviderId.value == ProviderIds.LLAMACPP) aiProviderRouter.selectModel(modelId)
        _refreshTrigger.update { it + 1 }
    }

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    fun selectProvider(id: String) {
        if (aiProviderRouter.getAllProviders().none { it.id == id }) return
        if (id == ProviderIds.ANDROID_ASSISTANT) {
            handoffToAssistant()
            return
        }
        aiProviderRouter.selectProvider(id)
        _availableModels.value = emptyList()
        _refreshTrigger.update { it + 1 }
        viewModelScope.launch { syncStoredModelSelection(id); refreshModelsFor(id); refreshAssistantInfo(id) }
    }

    fun selectModel(modelId: String?) {
        val normalized = modelId?.trim()?.ifBlank { null }; val providerId = aiProviderRouter.selectedProviderId.value
        if (normalized != null) {
            val known = _availableModels.value.any { it.id == normalized } || (providerId == ProviderIds.LLAMACPP && modelDownloadManager?.getAllModels()?.any { it.id == normalized } == true)
            if (!known) return
        }
        when (providerId) {
            ProviderIds.LLAMACPP -> if (normalized != null) { modelDownloadManager?.setActiveModel(normalized); updateSettings { it.copy(llamacppModel = normalized) }; persist { storage.putString("llamacpp_model", normalized) } }
            ProviderIds.OLLAMA -> if (normalized != null) { updateSettings { it.copy(ollamaModel = normalized) }; persist { storage.putString("ollama_model", normalized) } }
            ProviderIds.OPENROUTER -> if (normalized != null) { updateSettings { it.copy(openRouterModel = normalized) }; persist { storage.putString("openrouter_model", normalized) } }
        }
        aiProviderRouter.selectModel(normalized); _refreshTrigger.update { it + 1 }
    }
    fun refreshModels() { aiProviderRouter.selectedProviderId.value?.let { id -> viewModelScope.launch { refreshModelsFor(id) } } }

    private suspend fun refreshModelsFor(providerId: String) {
        val provider = aiProviderRouter.getAllProviders().find { it.id == providerId } ?: return
        _isRefreshing.value = true
        try {
            val models = if (provider is AiInferenceProvider) provider.listModels() else emptyList()
            _availableModels.value = models
            val settings = _settings.value
            val storedModelId = when (providerId) {
                ProviderIds.OPENROUTER -> settings.openRouterModel.trim().ifBlank { null }
                ProviderIds.OLLAMA -> settings.ollamaModel.trim().ifBlank { null }
                ProviderIds.LLAMACPP -> modelDownloadManager?.activeModelId?.value
                ProviderIds.ANDROID_ASSISTANT -> "android-assistant/handoff"
                else -> null
            }
            if (storedModelId != null && models.any { it.id == storedModelId } && aiProviderRouter.selectedModelId.value != storedModelId) aiProviderRouter.selectModel(storedModelId)
            else if (storedModelId == null && aiProviderRouter.selectedModelId.value == null && models.size == 1) aiProviderRouter.selectModel(models.first().id)
            if (providerId == ProviderIds.OLLAMA && settings.ollamaModel.isBlank() && models.isNotEmpty()) {
                val modelId = models.first().id; updateSettings { it.copy(ollamaModel = modelId) }; persist { storage.putString("ollama_model", modelId) }; aiProviderRouter.selectModel(modelId)
            }
        } catch (error: Exception) {
            Log.w("IntelligenceViewModel", "Failed to refresh models for $providerId", error); _availableModels.value = emptyList()
        } finally { _isRefreshing.value = false; _refreshTrigger.update { it + 1 } }
    }

    fun testConnection() {
        viewModelScope.launch {
            val selectedId = aiProviderRouter.selectedProviderId.value ?: return@launch
            val provider = aiProviderRouter.getAllProviders().find { it.id == selectedId } ?: return@launch
            try { _testConnectionResult.emit(provider.testConnection()); provider.getStatus(); refreshModelsFor(selectedId) }
            catch (error: Exception) { Log.w("IntelligenceViewModel", "Provider test failed: $selectedId", error); _testConnectionResult.emit(false); _refreshTrigger.update { it + 1 } }
        }
    }
    fun setOpenRouterKey(key: String) { updateSettings { it.copy(openRouterKey = key) }; persist { storage.putString("openrouter_api_key", key) } }
    fun setOpenRouterModel(model: String) { val n = model.trim(); if (n.isBlank()) return; updateSettings { it.copy(openRouterModel = n) }; persist { storage.putString("openrouter_model", n) }; if (aiProviderRouter.selectedProviderId.value == ProviderIds.OPENROUTER) aiProviderRouter.selectModel(n); _refreshTrigger.update { it + 1 } }
    fun setOllamaEndpoint(endpoint: String) { val n = endpoint.trim().trimEnd('/'); updateSettings { it.copy(ollamaEndpoint = n) }; persist { storage.putString("ollama_endpoint", n) }; _availableModels.value = emptyList(); _refreshTrigger.update { it + 1 } }
    fun setOllamaModel(model: String) { val n = model.trim(); if (n.isBlank()) return; updateSettings { it.copy(ollamaModel = n) }; persist { storage.putString("ollama_model", n) }; if (aiProviderRouter.selectedProviderId.value == ProviderIds.OLLAMA) aiProviderRouter.selectModel(n); _refreshTrigger.update { it + 1 } }
    fun setLlamacppModel(model: String) { val n = model.trim(); if (n.isBlank() || modelDownloadManager?.getAllModels()?.any { it.id == n } != true) return; updateSettings { it.copy(llamacppModel = n) }; persist { storage.putString("llamacpp_model", n) }; if (aiProviderRouter.selectedProviderId.value == ProviderIds.LLAMACPP) { modelDownloadManager?.setActiveModel(n); aiProviderRouter.selectModel(n) }; _refreshTrigger.update { it + 1 } }
    fun setLlamacppContext(size: Int) { val n = size.coerceAtLeast(1); updateSettings { it.copy(llamacppContext = n) }; persist { storage.putInt("llamacpp_context", n) }; _refreshTrigger.update { it + 1 } }
    fun setToolsEnabled(enabled: Boolean) {
        updatePolicy(tools = enabled)
    }
    fun setTemperature(temp: Float) { val n = temp.coerceIn(0f, 2f); updateSettings { it.copy(temperature = n) }; persist { storage.putInt("ai_temperature", (n * 100).toInt()) }; _refreshTrigger.update { it + 1 } }

    fun updatePolicy(conversation: Boolean? = null, deviceState: Boolean? = null, recentEvents: Boolean? = null, memories: Boolean? = null, tools: Boolean? = null, location: Boolean? = null, images: Boolean? = null, audio: Boolean? = null, video: Boolean? = null) {
        val current = _settings.value.policy
        val updated = current.copy(includeConversation = conversation ?: current.includeConversation, includeDeviceState = deviceState ?: current.includeDeviceState, includeRecentEvents = recentEvents ?: current.includeRecentEvents, includeMemories = memories ?: current.includeMemories, includeAvailableTools = tools ?: current.includeAvailableTools, includeLocation = location ?: current.includeLocation, includeImages = images ?: current.includeImages, includeAudio = audio ?: current.includeAudio, includeVideo = video ?: current.includeVideo)
        updateSettings { it.copy(policy = updated) }
        conversation?.let { storage.putBoolean("policy_conversation", it) }; deviceState?.let { storage.putBoolean("policy_device_state", it) }; recentEvents?.let { storage.putBoolean("policy_recent_events", it) }; memories?.let { storage.putBoolean("policy_memories", it) }; tools?.let { storage.putBoolean("policy_tools", it) }; location?.let { storage.putBoolean("policy_location", it) }; images?.let { storage.putBoolean("policy_images", it) }; audio?.let { storage.putBoolean("policy_audio", it) }; video?.let { storage.putBoolean("policy_video", it) }
        _refreshTrigger.update { it + 1 }
    }
    private inline fun updateSettings(transform: (IntelligenceSettings) -> IntelligenceSettings) { _settings.update(transform) }
    private fun persist(write: () -> Unit) { write() }
    fun navigateTo(route: String) { viewModelScope.launch { _navigationEvents.emit(route) } }
}
