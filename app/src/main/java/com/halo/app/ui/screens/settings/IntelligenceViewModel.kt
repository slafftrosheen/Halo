package com.halo.app.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.RuntimeSnapshotSource
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
    val isMemoryEnabled: Boolean = true,
    val isToolsEnabled: Boolean = true,
    val isAgentEnabled: Boolean = false,
    val temperature: Float = 0.7f,
    val policy: AiContextPolicy = AiContextPolicy.CONSERVATIVE
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

    init {
        if (storage.getString("openrouter_model").isNullOrBlank()) {
            storage.putString("openrouter_model", "openrouter/free")
        }
        modelDownloadManager?.refreshStates()

        viewModelScope.launch {
            aiProviderRouter.selectedProviderId.collectLatest { providerId ->
                if (providerId != null) {
                    syncStoredModelSelection(providerId)
                    refreshModelsFor(providerId)
                }
            }
        }
        viewModelScope.launch {
            modelDownloadManager?.downloadStates?.collect {
                _refreshTrigger.update { it + 1 }
            }
        }
    }

    private suspend fun syncStoredModelSelection(providerId: String) {
        val storedModelId = when (providerId) {
            "openrouter" -> storage.getString("openrouter_model")?.trim()?.ifBlank { "openrouter/free" }
            "provider.ollama" -> storage.getString("ollama_model")?.trim()?.ifBlank { null }
            "provider.llamacpp.native" -> modelDownloadManager?.activeModelId?.value
                ?: storage.getString("llamacpp_model")?.trim()?.ifBlank { null }
            "android.system.assistant" -> "android-assistant/handoff"
            else -> null
        }

        if (storedModelId != aiProviderRouter.selectedModelId.value) {
            aiProviderRouter.selectModel(storedModelId)
        }
    }

    val uiState: StateFlow<IntelligenceUiState> = combine(
        snapshotManager.snapshot,
        aiProviderRouter.selectedProviderId,
        aiProviderRouter.selectedModelId,
        modelDownloadManager?.downloadStates ?: MutableStateFlow<Map<String, DownloadState>>(emptyMap()),
        combine(
            modelDownloadManager?.activeModelId ?: MutableStateFlow<String?>(null),
            _refreshTrigger
        ) { activeId, _ -> activeId }
    ) { snapshot, selectedProviderId, selectedModelId, dlStates, activeLocalId ->
        val allProviders = aiProviderRouter.getAllProviders()
        val activeProvider = allProviders.find { it.id == selectedProviderId }
        val snapshotProvider = snapshot.providers.find { it.id == selectedProviderId }
        val storedLocalModel = storage.getString("llamacpp_model")
        IntelligenceUiState(
            activeProviderId = selectedProviderId ?: "",
            activeProviderName = snapshotProvider?.name ?: activeProvider?.displayName ?: "None",
            activeProviderStatus = activeProvider?.status?.value ?: ProviderStatus.UNAVAILABLE,
            capabilities = activeProvider?.getCapabilities() ?: emptySet(),
            activeModelId = selectedModelId
                ?: snapshotProvider?.activeModel
                ?: if (selectedProviderId == "provider.llamacpp.native") activeLocalId ?: storedLocalModel else null,
            providers = allProviders.map { routerProv ->
                val snap = snapshot.providers.find { it.id == routerProv.id }
                AiProviderUiState(
                    id = routerProv.id,
                    name = snap?.name ?: routerProv.displayName,
                    status = routerProv.status.value,
                    isSelected = routerProv.id == selectedProviderId,
                    capabilities = routerProv.getCapabilities()
                )
            },
            availableModels = _availableModels.value,
            isRefreshingModels = _isRefreshing.value,
            openRouterKey = storage.getString("openrouter_api_key") ?: "",
            openRouterModel = storage.getString("openrouter_model") ?: "openrouter/free",
            ollamaEndpoint = storage.getString("ollama_endpoint") ?: "",
            ollamaModel = storage.getString("ollama_model") ?: "llama3",
            llamacppModel = storedLocalModel ?: "qwen1.5-0.5b",
            llamacppContext = storage.getInt("llamacpp_context", 4096),
            localModels = modelDownloadManager?.getAllModels() ?: emptyList(),
            downloadStates = dlStates,
            activeLocalModelId = activeLocalId ?: storedLocalModel,
            isMemoryEnabled = storage.getBoolean("ai_memory_enabled", true),
            isToolsEnabled = storage.getBoolean("ai_tools_enabled", true),
            isAgentEnabled = storage.getBoolean("ai_agent_enabled", false),
            temperature = storage.getInt("ai_temperature", 70) / 100f,
            policy = loadPolicy()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IntelligenceUiState())

    fun downloadModel(modelId: String) {
        modelDownloadManager?.downloadModel(modelId)
    }

    fun downloadCustomUrl(url: String, name: String) {
        val normalizedUrl = url.trim()
        val normalizedName = name.trim()
        if (normalizedUrl.isBlank() || normalizedName.isBlank()) return
        modelDownloadManager?.downloadCustomUrl(normalizedUrl, normalizedName)
        _refreshTrigger.update { it + 1 }
    }

    fun setActiveLocalModel(modelId: String) {
        val known = modelDownloadManager?.getAllModels()?.any { it.id == modelId } == true
        if (!known) return
        modelDownloadManager?.setActiveModel(modelId)
        storage.putString("llamacpp_model", modelId)
        if (aiProviderRouter.selectedProviderId.value == "provider.llamacpp.native") {
            aiProviderRouter.selectModel(modelId)
        }
        _refreshTrigger.update { it + 1 }
    }

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    fun selectProvider(id: String) {
        if (aiProviderRouter.getAllProviders().none { it.id == id }) return
        aiProviderRouter.selectProvider(id)
        _availableModels.value = emptyList()
        _refreshTrigger.update { it + 1 }
        viewModelScope.launch {
            syncStoredModelSelection(id)
            refreshModelsFor(id)
        }
    }

    fun selectModel(modelId: String?) {
        val normalized = modelId?.trim()?.ifBlank { null }
        val providerId = aiProviderRouter.selectedProviderId.value
        if (normalized != null) {
            val known = _availableModels.value.any { it.id == normalized } ||
                (providerId == "provider.llamacpp.native" && modelDownloadManager?.getAllModels()?.any { it.id == normalized } == true)
            if (!known) return
        }
        when (providerId) {
            "provider.llamacpp.native" -> if (normalized != null) {
                modelDownloadManager?.setActiveModel(normalized)
                storage.putString("llamacpp_model", normalized)
            }
            "provider.ollama" -> if (normalized != null) storage.putString("ollama_model", normalized)
            "openrouter" -> if (normalized != null) storage.putString("openrouter_model", normalized)
        }
        aiProviderRouter.selectModel(normalized)
        _refreshTrigger.update { it + 1 }
    }

    fun refreshModels() {
        val selectedId = aiProviderRouter.selectedProviderId.value ?: return
        viewModelScope.launch { refreshModelsFor(selectedId) }
    }

    private suspend fun refreshModelsFor(providerId: String) {
        val provider = aiProviderRouter.getAllProviders().find { it.id == providerId } ?: return
        _isRefreshing.value = true
        try {
            val models = provider.listModels()
            _availableModels.value = models

            val storedModelId = when (providerId) {
                "openrouter" -> storage.getString("openrouter_model")?.trim()?.ifBlank { null }
                "provider.ollama" -> storage.getString("ollama_model")?.trim()?.ifBlank { null }
                "provider.llamacpp.native" -> modelDownloadManager?.activeModelId?.value
                "android.system.assistant" -> "android-assistant/handoff"
                else -> null
            }

            if (storedModelId != null && models.any { it.id == storedModelId } &&
                aiProviderRouter.selectedModelId.value != storedModelId
            ) {
                aiProviderRouter.selectModel(storedModelId)
            } else if (storedModelId == null && aiProviderRouter.selectedModelId.value == null && models.size == 1) {
                aiProviderRouter.selectModel(models.first().id)
            }

            if (providerId == "provider.ollama" && storage.getString("ollama_model").isNullOrBlank() && models.isNotEmpty()) {
                storage.putString("ollama_model", models.first().id)
                aiProviderRouter.selectModel(models.first().id)
            }
        } catch (error: Exception) {
            Log.w("IntelligenceViewModel", "Failed to refresh models for $providerId", error)
            _availableModels.value = emptyList()
        } finally {
            _isRefreshing.value = false
            _refreshTrigger.update { it + 1 }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            val selectedId = aiProviderRouter.selectedProviderId.value ?: return@launch
            val provider = aiProviderRouter.getAllProviders().find { it.id == selectedId } ?: return@launch
            try {
                _testConnectionResult.emit(provider.testConnection())
                provider.getStatus()
                refreshModelsFor(selectedId)
            } catch (error: Exception) {
                Log.w("IntelligenceViewModel", "Provider test failed: $selectedId", error)
                _testConnectionResult.emit(false)
                _refreshTrigger.update { it + 1 }
            }
        }
    }

    fun setOpenRouterKey(key: String) {
        storage.putString("openrouter_api_key", key)
        _refreshTrigger.update { it + 1 }
    }

    fun setOpenRouterModel(model: String) {
        val normalized = model.trim()
        if (normalized.isBlank()) return
        storage.putString("openrouter_model", normalized)
        if (aiProviderRouter.selectedProviderId.value == "openrouter") aiProviderRouter.selectModel(normalized)
        _refreshTrigger.update { it + 1 }
    }

    fun setOllamaEndpoint(endpoint: String) {
        val normalized = endpoint.trim().trimEnd('/')
        storage.putString("ollama_endpoint", normalized)
        _availableModels.value = emptyList()
        _refreshTrigger.update { it + 1 }
    }

    fun setOllamaModel(model: String) {
        val normalized = model.trim()
        if (normalized.isBlank()) return
        storage.putString("ollama_model", normalized)
        if (aiProviderRouter.selectedProviderId.value == "provider.ollama") aiProviderRouter.selectModel(normalized)
        _refreshTrigger.update { it + 1 }
    }

    fun setLlamacppModel(model: String) {
        val normalized = model.trim()
        if (normalized.isBlank()) return
        if (modelDownloadManager?.getAllModels()?.any { it.id == normalized } != true) return
        storage.putString("llamacpp_model", normalized)
        if (aiProviderRouter.selectedProviderId.value == "provider.llamacpp.native") {
            modelDownloadManager?.setActiveModel(normalized)
            aiProviderRouter.selectModel(normalized)
        }
        _refreshTrigger.update { it + 1 }
    }

    fun setLlamacppContext(size: Int) {
        storage.putInt("llamacpp_context", size)
        _refreshTrigger.update { it + 1 }
    }

    fun setMemoryEnabled(enabled: Boolean) {
        storage.putBoolean("ai_memory_enabled", enabled)
        _refreshTrigger.update { it + 1 }
    }

    fun setToolsEnabled(enabled: Boolean) {
        storage.putBoolean("ai_tools_enabled", enabled)
        _refreshTrigger.update { it + 1 }
    }

    fun setAgentEnabled(enabled: Boolean) {
        storage.putBoolean("ai_agent_enabled", enabled)
        _refreshTrigger.update { it + 1 }
    }

    fun setTemperature(temp: Float) {
        storage.putInt("ai_temperature", (temp * 100).toInt())
        _refreshTrigger.update { it + 1 }
    }

    private fun loadPolicy() = AiContextPolicy(
        includeConversation = storage.getBoolean("policy_conversation", true),
        includeDeviceState = storage.getBoolean("policy_device_state", true),
        includeRecentEvents = storage.getBoolean("policy_recent_events", false),
        includeMemories = storage.getBoolean("policy_memories", false),
        includeAvailableTools = storage.getBoolean("policy_tools", true),
        includeLocation = storage.getBoolean("policy_location", false),
        includeImages = storage.getBoolean("policy_images", true),
        includeAudio = storage.getBoolean("policy_audio", false),
        includeVideo = storage.getBoolean("policy_video", false)
    )

    fun updatePolicy(
        conversation: Boolean? = null,
        deviceState: Boolean? = null,
        recentEvents: Boolean? = null,
        memories: Boolean? = null,
        tools: Boolean? = null,
        location: Boolean? = null,
        images: Boolean? = null,
        audio: Boolean? = null,
        video: Boolean? = null
    ) {
        conversation?.let { storage.putBoolean("policy_conversation", it) }
        deviceState?.let { storage.putBoolean("policy_device_state", it) }
        recentEvents?.let { storage.putBoolean("policy_recent_events", it) }
        memories?.let { storage.putBoolean("policy_memories", it) }
        tools?.let { storage.putBoolean("policy_tools", it) }
        location?.let { storage.putBoolean("policy_location", it) }
        images?.let { storage.putBoolean("policy_images", it) }
        audio?.let { storage.putBoolean("policy_audio", it) }
        video?.let { storage.putBoolean("policy_video", it) }
        _refreshTrigger.update { it + 1 }
    }

    fun navigateTo(route: String) {
        viewModelScope.launch { _navigationEvents.emit(route) }
    }
}
