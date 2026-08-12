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

data class SettingsUiState(
    val version: String = "1.0.0",
    val provider: String = "Llama.cpp",
    val wakeWord: String = "Halo",
    val sensitivity: String = "High",
    val devMode: Boolean = false,
    val logLevel: String = "Debug",
    val isAlwaysListening: Boolean = false
)

class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    fun setDevMode(enabled: Boolean) {
        _uiState.update { it.copy(devMode = enabled) }
    }

    fun toggleDeveloperMode() {
        _uiState.update { it.copy(devMode = !it.devMode) }
    }

    fun navigateTo(route: String) {
        viewModelScope.launch {
            _navigationEvents.emit(route)
        }
    }
}
