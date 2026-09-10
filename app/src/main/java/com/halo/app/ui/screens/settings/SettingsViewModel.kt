package com.halo.app.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


data class SettingsUiState(
    val version: String = "1.0.0",
    val provider: String = "Not configured",
    val wakeWord: String = "Halo",
    val sensitivity: String = "High",
    val devMode: Boolean = false,
    val logLevel: String = "Debug",
    val isAlwaysListening: Boolean = false,
    val deviceName: String = "Not reported",
    val aiProviders: List<AiProviderUiState> = emptyList(),
    val openRouterKey: String = "",
    val openRouterModel: String = "openrouter/free",
    val availableModels: List<AiModel> = emptyList(),
    val isRefreshingModels: Boolean = false,
    val ollamaEndpoint: String = "http://10.0.2.2:11434",
    val ollamaModel: String = "llama3",
    val llamacppModel: String = "",
    val llamacppContext: Int = 4096,
    val isMemoryEnabled: Boolean = true,
    val isToolsEnabled: Boolean = true,
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
    private val _navigationEvents = MutableSharedFlow<String>()

    val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    init {
        if (storage.getString("openrouter_model").isNullOrBlank()) {
            storage.putString("openrouter_model", "openrouter/free")
        }
        viewModelScope.launch {
            aiProviderRouter.selectedProviderId.collect { providerId ->
                _availableModels.value = emptyList()
                if (providerId != null) {
                    refreshModelsFor(providerId)
                }
            }
        }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        snapshotManager.snapshot,
        aiProviderRouter.selectedProviderId,
        _devMode,
        _availableModels,
        _isRefreshing
    ) { snapshot, selectedId, devMode, models, refreshing ->
        val providers = aiProviderRouter.getAllProviders().map { provider ->
            AiProviderUiState(
                id = provider.id,
                name = provider.displayName,
                status = provider.status.value,
                isSelected = provider.id == selectedId,
                capabilities = provider.getCapabilities()
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
            openRouterModel = storage.getString("openrouter_model") ?: "openrouter/free",
            availableModels = models,
            isRefreshingModels = refreshing,
            ollamaEndpoint = storage.getString("ollama_endpoint") ?: "http://10.0.2.2:11434",
            ollamaModel = storage.getString("ollama_model") ?: "llama3",
            llamacppModel = storage.getString("llamacpp_model") ?: "",
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

    private suspend fun refreshModelsFor(providerId: String) {
        val provider = aiProviderRouter.getAllProviders().find { it.id == providerId } ?: return
        _isRefreshing.value = true
        try {
            _availableModels.value = provider.listModels()
        } catch (error: Exception) {
            Log.w("SettingsViewModel", "Failed to refresh models for $providerId", error)
            _availableModels.value = emptyList()
        } finally {
            _isRefreshing.value = false
        }
    }

    fun refreshModels() {
        val selectedId = aiProviderRouter.selectedProviderId.value ?: return
        viewModelScope.launch { refreshModelsFor(selectedId) }
    }

    fun setOpenRouterKey(key: String) {
        storage.putString("openrouter_api_key", key)
    }

    fun setOpenRouterModel(model: String) {
        val normalized = model.trim()
        if (normalized.isBlank()) return
        storage.putString("openrouter_model", normalized)
        if (aiProviderRouter.selectedProviderId.value == "openrouter") {
            aiProviderRouter.selectModel(normalized)
        }
    }

    fun setOllamaEndpoint(endpoint: String) {
        storage.putString("ollama_endpoint", endpoint.trim().trimEnd('/'))
        _availableModels.value = emptyList()
        if (aiProviderRouter.selectedProviderId.value == "provider.ollama") {
            viewModelScope.launch { refreshModelsFor("provider.ollama") }
        }
    }

    fun setOllamaModel(model: String) {
        val normalized = model.trim()
        if (normalized.isBlank()) return
        storage.putString("ollama_model", normalized)
        if (aiProviderRouter.selectedProviderId.value == "provider.ollama") {
            aiProviderRouter.selectModel(normalized)
        }
    }

    fun setLlamacppModel(model: String) {
        val normalized = model.trim()
        if (normalized.isBlank()) return
        storage.putString("llamacpp_model", normalized)
        if (aiProviderRouter.selectedProviderId.value == "provider.llamacpp.native") {
            aiProviderRouter.selectModel(normalized)
        }
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

    fun setDevMode(enabled: Boolean) {
        _devMode.value = enabled
    }

    fun toggleDeveloperMode() {
        _devMode.update { !it }
    }

    fun selectAiProvider(id: String) {
        Log.d("SettingsViewModel", "selectAiProvider: $id")
        aiProviderRouter.selectProvider(id)
    }

    fun navigateTo(route: String) {
        Log.d("SettingsViewModel", "navigateTo: $route")
        viewModelScope.launch { _navigationEvents.emit(route) }
    }
}
