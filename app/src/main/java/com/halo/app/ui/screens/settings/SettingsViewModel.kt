package com.halo.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.provider.AiProviderUiState
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Legacy settings state retained only for system-level settings.
 * Provider/model configuration lives in IntelligenceViewModel.
 */
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
    val uiState: StateFlow<SettingsUiState> = _devMode.asStateFlow().let { dev ->
        kotlinx.coroutines.flow.combine(snapshotManager.snapshot, aiProviderRouter.selectedProviderId, dev) { snapshot, selectedId, devMode ->
            val provider = aiProviderRouter.getAllProviders().firstOrNull { it.id == selectedId }
            SettingsUiState(
                version = "v${snapshot.runtimeVersion} (${snapshot.buildNumber})",
                provider = provider?.displayName ?: "Not configured",
                deviceName = snapshot.deviceContext.name ?: "Not reported",
                devMode = devMode
            )
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), SettingsUiState())
    }

    fun setDevMode(enabled: Boolean) {
        _devMode.value = enabled
    }

    fun navigateTo(route: String) {
        // Retained as a compatibility shim for existing shell callers.
        // System settings routes should remain explicit in AppShell.
    }
}
