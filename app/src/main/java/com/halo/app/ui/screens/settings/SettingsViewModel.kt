package com.halo.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** System-level settings only. Provider/model configuration lives in Intelligence. */
data class SettingsUiState(
    val version: String = "1.0.0",
    val provider: String = "Not configured",
    val deviceName: String = "Not reported",
    val devMode: Boolean = false
)

class SettingsViewModel(
    private val snapshotManager: RuntimeSnapshotSource,
    private val aiProviderRouter: AiProviderRouter,
    private val storage: Storage
) : ViewModel() {
    private val _devMode = MutableStateFlow(false)

    private val _navigationEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val navigationEvents = _navigationEvents.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _devMode.value = storage.getBoolean("developer_mode", false)
        }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        snapshotManager.snapshot,
        aiProviderRouter.selectedProviderId,
        _devMode
    ) { snapshot, selectedId, devMode ->
        val provider = aiProviderRouter.getAllProviders().firstOrNull { it.id == selectedId }
        SettingsUiState(
            version = "v${snapshot.runtimeVersion} (${snapshot.buildNumber})",
            provider = provider?.displayName ?: "Not configured",
            deviceName = snapshot.deviceContext.name ?: "Not reported",
            devMode = devMode
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsUiState()
    )

    fun setDevMode(enabled: Boolean) {
        _devMode.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            storage.putBoolean("developer_mode", enabled)
        }
    }

    fun openIntelligence() { _navigationEvents.tryEmit("ai_settings") }
    fun openDevices() { _navigationEvents.tryEmit("devices") }
    fun openPermissions() { _navigationEvents.tryEmit("permissions") }
    fun openProtocolLab() { _navigationEvents.tryEmit("protocol_lab") }
    fun openInspector() { _navigationEvents.tryEmit("inspector") }
    fun openConsole() { _navigationEvents.tryEmit("console") }
}
