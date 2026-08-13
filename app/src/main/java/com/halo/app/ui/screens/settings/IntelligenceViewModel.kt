package com.halo.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.Storage
import com.halo.runtime.cognition.session.CognitiveSession
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    private val storage: Storage
) : ViewModel() {

    private val _availableModels = MutableStateFlow<List<AiModel>>(emptyList())
    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<IntelligenceUiState> = combine(
        aiProviderRouter.selectedProviderId,
        aiProviderRouter.selectedModelId,
        _availableModels,
        _isRefreshing
    ) { selectedProviderId, selectedModelId, models, refreshing ->
        val allProviders = aiProviderRouter.getAllProviders()
        val activeProvider = allProviders.find { it.id == selectedProviderId }
        
        IntelligenceUiState(
            activeProviderId = selectedProviderId ?: "",
            activeProviderName = activeProvider?.displayName ?: "None",
            activeProviderStatus = activeProvider?.status?.value ?: ProviderStatus.UNAVAILABLE,
            capabilities = activeProvider?.getCapabilities() ?: emptySet(),
            activeModelId = selectedModelId,
            providers = allProviders.map {
                AiProviderUiState(
                    id = it.id,
                    name = it.displayName,
                    status = it.status.value,
                    isSelected = it.id == selectedProviderId,
                    capabilities = it.getCapabilities()
                )
            },
            availableModels = models,
            isRefreshingModels = refreshing,
            openRouterKey = storage.getString("openrouter_api_key") ?: "",
            openRouterModel = storage.getString("openrouter_model") ?: "openrouter/auto",
            ollamaEndpoint = storage.getString("ollama_endpoint") ?: "http://10.0.2.2:11434",
            ollamaModel = storage.getString("ollama_model") ?: "llama3",
            llamacppModel = storage.getString("llamacpp_model") ?: "qwen2-0.5b",
            llamacppContext = storage.getInt("llamacpp_context", 4096),
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

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    fun selectProvider(id: String) {
        aiProviderRouter.selectProvider(id)
    }

    fun selectModel(modelId: String?) {
        aiProviderRouter.selectModel(modelId)
    }

    fun refreshModels() {
        viewModelScope.launch {
            val selectedId = aiProviderRouter.selectedProviderId.value ?: return@launch
            val provider = aiProviderRouter.getAllProviders().find { it.id == selectedId } ?: return@launch
            
            _isRefreshing.value = true
            try {
                _availableModels.value = provider.listModels()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun setOpenRouterKey(key: String) {
        storage.putString("openrouter_api_key", key)
    }

    fun setOpenRouterModel(model: String) {
        storage.putString("openrouter_model", model)
    }

    fun setOllamaEndpoint(endpoint: String) {
        storage.putString("ollama_endpoint", endpoint)
    }

    fun setOllamaModel(model: String) {
        storage.putString("ollama_model", model)
    }

    fun setLlamacppModel(model: String) {
        storage.putString("llamacpp_model", model)
    }

    fun setLlamacppContext(size: Int) {
        storage.putInt("llamacpp_context", size)
    }

    fun setMemoryEnabled(enabled: Boolean) {
        storage.putBoolean("ai_memory_enabled", enabled)
    }

    fun setToolsEnabled(enabled: Boolean) {
        storage.putBoolean("ai_tools_enabled", enabled)
    }

    fun setAgentEnabled(enabled: Boolean) {
        storage.putBoolean("ai_agent_enabled", enabled)
    }

    fun setTemperature(temp: Float) {
        storage.putInt("ai_temperature", (temp * 100).toInt())
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
        conversation?.let { storage.putBoolean("policy_conversation", it) }
        deviceState?.let { storage.putBoolean("policy_device_state", it) }
        recentEvents?.let { storage.putBoolean("policy_recent_events", it) }
        memories?.let { storage.putBoolean("policy_memories", it) }
        tools?.let { storage.putBoolean("policy_tools", it) }
        location?.let { storage.putBoolean("policy_location", it) }
        images?.let { storage.putBoolean("policy_images", it) }
        audio?.let { storage.putBoolean("policy_audio", it) }
        video?.let { storage.putBoolean("policy_video", it) }
        
        // Trigger state refresh if needed, but since combine observes storage changes
        // we might need a signal or just rely on the fact that storage.putBoolean 
        // doesn't trigger flows.
        // Actually, for Alpha, we'll just manually refresh or use a Trigger flow.
    }

    fun navigateTo(route: String) {
        viewModelScope.launch {
            _navigationEvents.emit(route)
        }
    }
}
