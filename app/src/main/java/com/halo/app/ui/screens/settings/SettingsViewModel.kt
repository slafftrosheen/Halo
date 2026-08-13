package com.halo.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import com.halo.sdk.runtime.RuntimeSnapshotSource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiProvider
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.AiModel
import com.halo.sdk.runtime.Storage

data class SettingsUiState(
    val version: String = "1.0.0",
    val provider: String = "Llama.cpp",
    val wakeWord: String = "Halo",
    val sensitivity: String = "High",
    val devMode: Boolean = false,
    val logLevel: String = "Debug",
    val isAlwaysListening: Boolean = false,
    val deviceName: String = "Not reported",
    val aiProviders: List<AiProviderUiState> = emptyList(),
    val openRouterKey: String = "",
    val openRouterModel: String = "",
    val availableModels: List<AiModel> = emptyList(),
    val isRefreshingModels: Boolean = false,
    val ollamaEndpoint: String = "",
    val ollamaModel: String = "",
    val llamacppModel: String = "",
    val llamacppContext: Int = 4096,
    val isMemoryEnabled: Boolean = false,
    val isToolsEnabled: Boolean = false,
    val isAgentEnabled: Boolean = false,
    val temperature: Float = 0.7f
)

class SettingsViewModel(
    private val snapshotManager: RuntimeSnapshotSource,
    private val aiProviderRouter: AiProviderRouter,
    private val storage: Storage
) : ViewModel() {

    private val _devMode = MutableStateFlow(false)
    private val _availableModels = MutableStateFlow<List<AiModel>>(emptyList())
    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        snapshotManager.snapshot,
        aiProviderRouter.selectedProviderId,
        _devMode,
        _availableModels,
        _isRefreshing
    ) { snapshot, selectedId, devMode, models, refreshing ->
        val providers = mutableListOf<AiProviderUiState>()
        for (it in aiProviderRouter.getAllProviders()) {
            providers.add(
                AiProviderUiState(
                    id = it.id,
                    name = it.displayName,
                    status = it.getStatus(),
                    isSelected = it.id == selectedId,
                    capabilities = it.getCapabilities()
                )
            )
        }

        SettingsUiState(
            version = "v${snapshot.runtimeVersion} (${snapshot.buildNumber})",
            provider = providers.find { it.isSelected }?.name ?: "Not configured",
            wakeWord = "Halo",
            sensitivity = "High",
            devMode = devMode,
            logLevel = "Debug",
            isAlwaysListening = false,
            deviceName = snapshot.deviceContext.name ?: "Not reported",
            aiProviders = providers,
            openRouterKey = storage.getString("openrouter_api_key") ?: "",
            openRouterModel = storage.getString("openrouter_model") ?: "openrouter/auto",
            availableModels = models,
            isRefreshingModels = refreshing,
            ollamaEndpoint = storage.getString("ollama_endpoint") ?: "http://10.0.2.2:11434",
            ollamaModel = storage.getString("ollama_model") ?: "llama3",
            llamacppModel = storage.getString("llamacpp_model") ?: "qwen2-0.5b",
            llamacppContext = storage.getInt("llamacpp_context", 4096),
            isMemoryEnabled = storage.getBoolean("ai_memory_enabled", true),
            isToolsEnabled = storage.getBoolean("ai_tools_enabled", true),
            isAgentEnabled = storage.getBoolean("ai_agent_enabled", false),
            temperature = storage.getInt("ai_temperature", 70) / 100f
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

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

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    fun setDevMode(enabled: Boolean) {
        _devMode.value = enabled
    }

    fun toggleDeveloperMode() {
        _devMode.value = !_devMode.value
    }

    fun selectAiProvider(id: String) {
        aiProviderRouter.selectProvider(id)
    }

    fun navigateTo(route: String) {
        viewModelScope.launch {
            _navigationEvents.emit(route)
        }
    }
}
