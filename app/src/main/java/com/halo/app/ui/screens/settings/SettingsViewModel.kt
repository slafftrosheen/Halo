package com.halo.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.theme.AppearanceMode
import com.halo.app.ui.theme.AppearancePreferences
import com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.runtime.RuntimeSnapshotSource
import java.io.File
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
    val voiceStatus: String = "Not installed",
    val deviceName: String = "Not reported",
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM
)

class SettingsViewModel(
    private val context: Context,
    private val snapshotManager: RuntimeSnapshotSource,
    private val aiProviderRouter: AiProviderRouter,
    private val appearancePreferences: AppearancePreferences
) : ViewModel() {
    private val appContext = context.applicationContext
    private val _navigationEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val navigationEvents = _navigationEvents.asSharedFlow()

    private val speechModelDirectory: File
        get() = File(appContext.filesDir, "speech/${SherpaSpeechModelCatalog.WHISPER_TINY_DIRECTORY}")

    val uiState: StateFlow<SettingsUiState> = combine(
        snapshotManager.snapshot,
        aiProviderRouter.selectedProviderId,
        appearancePreferences.mode
    ) { snapshot, selectedId, appearanceMode ->
        val provider = aiProviderRouter.getAllProviders().firstOrNull { it.id == selectedId }
        val isVoiceReady = SherpaSpeechModelCatalog.isInstalled(
            speechModelDirectory,
            SherpaSpeechModelCatalog.whisperTiny
        )
        SettingsUiState(
            version = "v${snapshot.runtimeVersion} (${snapshot.buildNumber})",
            provider = provider?.displayName ?: "Not configured",
            voiceStatus = if (isVoiceReady) "Ready" else "Not installed",
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

    fun openIntelligence() { _navigationEvents.tryEmit("ai_settings") }
    fun openVoice() { _navigationEvents.tryEmit("voice_settings") }
    fun openDevices() { _navigationEvents.tryEmit("devices") }
    fun openPermissions() { _navigationEvents.tryEmit("permissions") }
}
