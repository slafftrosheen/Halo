package com.halo.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.di.ProviderGraph
import com.halo.providers.sherpa.stt.SpeechModelInventoryRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class LocaleOption(
    val displayName: String,
    val tag: String
)

data class VoiceSettingsUiState(
    val isInstalled: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val currentFileDownloading: String? = null,
    val downloadError: String? = null,
    val missingFiles: List<String> = emptyList(),
    val installedSizeBytes: Long = 0L,
    val selectedLocale: String = "en",
    val availableLocales: List<LocaleOption> = listOf(
        LocaleOption("English", "en"),
        LocaleOption("Spanish", "es"),
        LocaleOption("French", "fr"),
        LocaleOption("German", "de"),
        LocaleOption("Chinese", "zh"),
        LocaleOption("Japanese", "ja"),
        LocaleOption("Portuguese", "pt"),
        LocaleOption("Russian", "ru"),
        LocaleOption("Italian", "it"),
        LocaleOption("Detect language automatically (Advanced)", "auto")
    )
)

class VoiceSettingsViewModel(
    private val speechInventory: SpeechModelInventoryRepository,
    private val languageSettings: SpeechLanguageSettingsRepository,
    private val providerGraph: ProviderGraph? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceSettingsUiState())
    val uiState: StateFlow<VoiceSettingsUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val navigationEvents = _navigationEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            languageSettings.loadAndMigrate()
            speechInventory.refresh(languageSettings.languageMode.value)
            combine(speechInventory.state, languageSettings.languageMode) { inventory, language ->
                inventory to language
            }.collect { (inventory, language) ->
                _uiState.value = _uiState.value.copy(
                    isInstalled = inventory.installed,
                    missingFiles = inventory.missingFiles,
                    installedSizeBytes = inventory.totalBytes,
                    selectedLocale = language,
                    downloadError = inventory.error
                )
            }
        }
    }

    fun refreshState() {
        viewModelScope.launch { speechInventory.refresh(languageSettings.languageMode.value) }
    }

    fun installWhisperTiny() {
        if (_uiState.value.isDownloading) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                downloadProgress = 0f,
                currentFileDownloading = "Starting...",
                downloadError = null
            )

            val result = speechInventory.installWhisperTiny { progress, currentFile ->
                _uiState.value = _uiState.value.copy(
                    downloadProgress = progress,
                    currentFileDownloading = currentFile
                )
            }

            result.fold(
                onSuccess = {
                    refreshState()
                    _uiState.value = _uiState.value.copy(isDownloading = false)
                    providerGraph?.reloadSpeechProvider()
                },
                onFailure = { error ->
                    refreshState()
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        downloadError = error.message ?: "Download failed"
                    )
                }
            )
        }
    }

    fun selectLocale(tag: String) {
        viewModelScope.launch {
            languageSettings.select(tag)
            speechInventory.refresh(tag)
            providerGraph?.reloadSpeechProvider()
        }
    }

    fun goBack() {
        _navigationEvents.tryEmit("back")
    }
}
