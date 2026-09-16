package com.halo.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.di.ProviderGraph
import com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog
import com.halo.providers.sherpa.stt.WhisperModelDownloader
import com.halo.sdk.runtime.Storage
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val selectedLocale: String = "auto",
    val availableLocales: List<LocaleOption> = listOf(
        LocaleOption("Auto (Default)", "auto"),
        LocaleOption("English", "en"),
        LocaleOption("Spanish", "es"),
        LocaleOption("French", "fr"),
        LocaleOption("German", "de"),
        LocaleOption("Chinese", "zh"),
        LocaleOption("Japanese", "ja"),
        LocaleOption("Portuguese", "pt"),
        LocaleOption("Russian", "ru"),
        LocaleOption("Italian", "it")
    )
)

class VoiceSettingsViewModel(
    private val context: Context,
    private val storage: Storage,
    private val providerGraph: ProviderGraph? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceSettingsUiState())
    val uiState: StateFlow<VoiceSettingsUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val navigationEvents = _navigationEvents.asSharedFlow()

    private val speechModelDirectory: File
        get() = File(context.filesDir, "speech/${SherpaSpeechModelCatalog.WHISPER_TINY_DIRECTORY}")

    init {
        refreshState()
    }

    fun refreshState() {
        val spec = SherpaSpeechModelCatalog.whisperTiny
        val installed = SherpaSpeechModelCatalog.isInstalled(speechModelDirectory, spec)
        val missing = SherpaSpeechModelCatalog.missingFiles(speechModelDirectory, spec)
        val installedSize = if (speechModelDirectory.exists()) {
            speechModelDirectory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else 0L

        val savedLocale = storage.getString("speech_locale") ?: "auto"

        _uiState.value = _uiState.value.copy(
            isInstalled = installed,
            missingFiles = missing,
            installedSizeBytes = installedSize,
            selectedLocale = savedLocale
        )
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

            val result = WhisperModelDownloader.downloadAndInstall(
                destinationDirectory = speechModelDirectory
            ) { progress, currentFile ->
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
        storage.putString("speech_locale", tag)
        _uiState.value = _uiState.value.copy(selectedLocale = tag)
        providerGraph?.reloadSpeechProvider()
    }

    fun goBack() {
        _navigationEvents.tryEmit("back")
    }
}
