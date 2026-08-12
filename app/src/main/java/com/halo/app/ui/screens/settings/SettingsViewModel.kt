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

import com.halo.engines.ai.AiProviderRouter
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiProvider

data class AiProviderUiState(
    val id: String,
    val name: String,
    val isAvailable: Boolean,
    val isSelected: Boolean,
    val capabilities: Set<AiCapability>
)

data class SettingsUiState(
    val version: String = "1.0.0",
    val provider: String = "Llama.cpp",
    val wakeWord: String = "Halo",
    val sensitivity: String = "High",
    val devMode: Boolean = false,
    val logLevel: String = "Debug",
    val isAlwaysListening: Boolean = false,
    val deviceName: String = "Not reported",
    val aiProviders: List<AiProviderUiState> = emptyList()
)
class SettingsViewModel(
    private val snapshotManager: RuntimeSnapshotSource,
    private val aiProviderRouter: AiProviderRouter
) : ViewModel() {

    private val _devMode = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        snapshotManager.snapshot,
        aiProviderRouter.selectedProviderId,
        _devMode
    ) { snapshot, selectedId, devMode ->
        val providers = aiProviderRouter.getAllProviders().map {
            AiProviderUiState(
                id = it.id,
                name = it.name,
                isAvailable = it.isAvailable(),
                isSelected = it.id == selectedId,
                capabilities = it.capabilities
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
            aiProviders = providers
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

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
