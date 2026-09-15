package com.halo.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.runtime.RuntimeSnapshotSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** System-level settings only. Provider/model configuration lives in Intelligence. */
data class SettingsUiState(
    val version: String = "1.0.0",
    val provider: String = "Not configured",
    val deviceName: String = "Not reported"
)

class SettingsViewModel(
    private val snapshotManager: RuntimeSnapshotSource,
    private val aiProviderRouter: AiProviderRouter
) : ViewModel() {
    private val _navigationEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val navigationEvents = _navigationEvents.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        snapshotManager.snapshot,
        aiProviderRouter.selectedProviderId
    ) { snapshot, selectedId ->
        val provider = aiProviderRouter.getAllProviders().firstOrNull { it.id == selectedId }
        SettingsUiState(
            version = "v${snapshot.runtimeVersion} (${snapshot.buildNumber})",
            provider = provider?.displayName ?: "Not configured",
            deviceName = snapshot.deviceContext.name ?: "Not reported"
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsUiState()
    )

    fun openIntelligence() { _navigationEvents.tryEmit("ai_settings") }
    fun openDevices() { _navigationEvents.tryEmit("devices") }
    fun openPermissions() { _navigationEvents.tryEmit("permissions") }
}
