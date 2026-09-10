package com.halo.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** System-level settings only. Provider/model configuration belongs to Intelligence. */
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
    private val _devMode = MutableStateFlow(storage.getBoolean("developer_mode", false))

    val uiState: StateFlow<SettingsUiState> = kotlinx.coroutines.flow.combine(
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
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        SettingsUiState(devMode = _devMode.value)
    )

    fun setDevMode(enabled: Boolean) {
        storage.putBoolean("developer_mode", enabled)
        _devMode.value = enabled
    }
}
