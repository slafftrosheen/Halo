package com.halo.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.theme.AppearanceMode
import com.halo.app.ui.theme.AppearancePreferences
import com.halo.providers.sherpa.stt.SpeechModelInventoryRepository
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.runtime.RuntimeSnapshotSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** System-level settings only. Provider/model configuration lives in Intelligence. */
data class SettingsUiState(
    val version: String = "1.0.0",
    val provider: String = "Not configured",
    val voiceStatus: String = "Not installed",
    val deviceName: String = "Not reported",
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM
)

class SettingsViewModel(
    private val snapshotManager: RuntimeSnapshotSource,
    private val aiProviderRouter: AiProviderRouter,
    private val appearancePreferences: AppearancePreferences,
    private val speechInventory: SpeechModelInventoryRepository
) : ViewModel() {
    private val _navigationEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val navigationEvents = _navigationEvents.asSharedFlow()

    init {
        viewModelScope.launch { speechInventory.refresh() }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        snapshotManager.snapshot,
        aiProviderRouter.selectedProviderId,
        appearancePreferences.mode,
        speechInventory.state
    ) { snapshot, selectedId, appearanceMode, speechState ->
        val provider = aiProviderRouter.getAllProviders().firstOrNull { it.id == selectedId }
        SettingsUiState(
            version = "v${snapshot.runtimeVersion} (${snapshot.buildNumber})",
            provider = provider?.displayName ?: "Not configured",
            voiceStatus = if (speechState.installed) "Ready" else if (speechState.loading) "Checking…" else "Not installed",
            deviceName = snapshot.deviceContext.name ?: "Not reported",
            appearanceMode = appearanceMode
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsUiState(appearanceMode = appearancePreferences.mode.value)
    )

    fun setAppearanceMode(mode: AppearanceMode) {
        appearancePreferences.setMode(mode)
    }

    fun openAutomation() { _navigationEvents.tryEmit("automation") }
    fun openIntelligence() { _navigationEvents.tryEmit("ai_settings") }
    fun openVoice() { _navigationEvents.tryEmit("voice_settings") }
    fun openSkills() { _navigationEvents.tryEmit("skills") }
    fun openDevices() { _navigationEvents.tryEmit("devices") }
    fun openPermissions() { _navigationEvents.tryEmit("permissions") }
    fun openStorageBackup() { _navigationEvents.tryEmit("storage_backup") }
    fun openRuntimeStatus() { _navigationEvents.tryEmit("runtime_status") }
    fun openAndroidAssistant() { _navigationEvents.tryEmit("android_assistant") }
}
