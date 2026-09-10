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
    val openRouterModel: String = "",
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
        modelDownloadManager?.refreshStates()
    }

    val uiState: StateFlow<IntelligenceUiState> = combine(
        snapshotManager.snapshot,
        aiProviderRouter.selectedProviderId,
        aiProviderRouter.selectedModelId,
        modelDownloadManager?.downloadStates ?: MutableStateFlow(emptyMap()),
        modelDownloadManager?.activeModelId ?: MutableStateFlow(null),
        _refreshTrigger
    ) { snapshot, selectedProviderId, selectedModelId, dlStates, activeLocalId, _ ->
        val allProviders = aiProviderRouter.getAllProviders()
        val activeProvider = allProviders.find { it.id == selectedProviderId }
        val snapshotProvider = snapshot.providers.find { it.id == selectedProviderId }

        IntelligenceUiState(
            activeProviderId = selectedProviderId ?: "",
            activeProviderName = snapshotProvider?.name ?: activeProvider?.displayName ?: "None",
            activeProviderStatus = activeProvider?.status?.value ?: ProviderStatus.UNAVAILABLE,
            capabilities = activeProvider?.getCapabilities() ?: emptySet(),
            activeModelId = snapshotProvider?.activeModel ?: selectedModelId,
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
            openRouterModel = storage.getString("openrouter_model") ?: "openrouter/auto",
            ollamaEndpoint = storage.getString("ollama_endpoint") ?: "http://10.0.2.2:11434",
            ollamaModel = storage.getString("ollama_model") ?: "llama3",
            llamacppModel = storage.getString("llamacpp_model") ?: "qwen1.5-0.5b",
            llamacppContext = storage.getInt("llamacpp_context", 4096),
            localModels = modelDownloadManager?.getAllModels() ?: emptyList(),
            downloadStates = dlStates,
            activeLocalModelId = activeLocalId ?: storage.getString("llamacpp_model"),
            isMemoryEnabled = storage.getBoolean("ai_memory_enabled", true),
            isToolsEnabled = storage.getBoolean("ai_tools_enabled", true),
            isAgentEnabled = storage.getBoolean("ai_agent_enabled", false),
            temperature = storage.getInt("ai_temperature", 70) / 100f,
            policy = loadPolicy()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = IntelligenceUiState()
    )

    fun downloadModel(modelId: String) {
        Log.d("IntelligenceViewModel", "downloadModel: $modelId")
        modelDownloadManager?.downloadModel(modelId)
    }

    fun downloadCustomUrl(url: String, name: String) {
        Log.d("IntelligenceViewModel", "downloadCustomUrl: $url")
        modelDownloadManager?.downloadCustomUrl(url, name)
    }

    fun setActiveLocalModel(modelId: String) {
        Log.d("IntelligenceViewModel", "setActiveLocalModel: $modelId")
        modelDownloadManager?.setActiveModel(modelId)
        setLlamacppModel(modelId)
    }

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    fun selectProvider(id: String) {
        Log.d("IntelligenceViewModel", "selectProvider: $id")
        aiProviderRouter.selectProvider(id)
    }

    fun selectModel(modelId: String?) {
        Log.d("IntelligenceViewModel", "selectModel: $modelId")
        aiProviderRouter.selectModel(modelId)
    }

    fun refreshModels() {
        Log.d("IntelligenceViewModel", "refreshModels")
        viewModelScope.launch {
            val selectedId = aiProviderRouter.selectedProviderId.value ?: return@launch
            val provider = aiProviderRouter.getAllProviders().find { it.id == selectedId } ?: return@launch

            _isRefreshing.value = true
            try {
                _availableModels.value = provider.listModels()
            } finally {
                _isRefreshing.value = false
                _refreshTrigger.update { it + 1 }
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            val selectedId = aiProviderRouter.selectedProviderId.value ?: return@launch
            val provider = aiProviderRouter.getAllProviders().find { it.id == selectedId } ?: return@launch
            try {
                _testConnectionResult.emit(provider.testConnection())
                provider.getStatus()
                _refreshTrigger.update { it + 1 }
            } catch (e: Exception) {
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
        storage.putString("openrouter_model", model)
        _refreshTrigger.update { it + 1 }
    }

    fun setOllamaEndpoint(endpoint: String) {
        storage.putString("ollama_endpoint", endpoint.trimEnd('/'))
        _refreshTrigger.update { it + 1 }
    }

    fun setOllamaModel(model: String) {
        storage.putString("ollama_model", model)
        _refreshTrigger.update { it + 1 }
    }

    fun setLlamacppModel(model: String) {
        storage.putString("llamacpp_model", model)
        _refreshTrigger.update { it + 1 }
    }

    fun setLlamacppContext(size: Int) {
        storage.putInt("llamacpp_context", size)
        _refreshTrigger.update { it + 1 }
    }

    fun setMemoryEnabled(enabled: Boolean) {
        Log.d("IntelligenceViewModel", "setMemoryEnabled: $enabled")
        storage.putBoolean("ai_memory_enabled", enabled)
        _refreshTrigger.update { it + 1 }
    }

    fun setToolsEnabled(enabled: Boolean) {
        Log.d("IntelligenceViewModel", "setToolsEnabled: $enabled")
        storage.putBoolean("ai_tools_enabled", enabled)
        _refreshTrigger.update { it + 1 }
    }

    fun setAgentEnabled(enabled: Boolean) {
        Log.d("IntelligenceViewModel", "setAgentEnabled: $enabled")
        storage.putBoolean("ai_agent_enabled", enabled)
        _refreshTrigger.update { it + 1 }
    }

    fun setTemperature(temp: Float) {
        storage.putInt("ai_temperature", (temp * 100).toInt())
        _refreshTrigger.update { it + 1 }
    }

    private fun loadPolicy(): AiContextPolicy {
        return AiContextPolicy(
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
    }

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
        Log.d("IntelligenceViewModel", "updatePolicy")
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
        Log.d("IntelligenceViewModel", "navigateTo: $route")
        viewModelScope.launch {
            _navigationEvents.emit(route)
        }
    }
}
