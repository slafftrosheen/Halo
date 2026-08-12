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

data class SettingsUiState(
    val version: String = "1.0.0",
    val provider: String = "Llama.cpp",
    val wakeWord: String = "Halo",
    val sensitivity: String = "High",
    val devMode: Boolean = false,
    val logLevel: String = "Debug",
    val isAlwaysListening: Boolean = false
)
class SettingsViewModel(private val snapshotManager: RuntimeSnapshotSource) : ViewModel() {

    private val _devMode = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        snapshotManager.snapshot,
        _devMode
    ) { snapshot, devMode ->
        SettingsUiState(
            version = "v${snapshot.runtimeVersion} (${snapshot.buildNumber})",
            provider = snapshot.providers.firstOrNull()?.name ?: "Native Engine",
            wakeWord = "Halo",
            sensitivity = "High",
            devMode = devMode,
            logLevel = "Debug",
            isAlwaysListening = false
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

    fun navigateTo(route: String) {
        viewModelScope.launch {
            _navigationEvents.emit(route)
        }
    }
}
