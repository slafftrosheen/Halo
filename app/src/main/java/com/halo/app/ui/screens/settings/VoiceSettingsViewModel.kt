package com.halo.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.di.ProviderGraph
import com.halo.providers.sherpa.stt.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LocaleOption(val displayName: String, val tag: String)

data class VoiceSettingsUiState(
    val models: Map<String, Boolean> = emptyMap(),
    val selectedProviderId: String = "AUTOMATIC",
    val selectedModelName: String = "Automatic",
    val residentModelId: String? = null,
    val sileroInstalled: Boolean = false,
    val downloadingModelId: String? = null,
    val downloadedBytes: Long = 0,
    val expectedBytes: Long = 0,
    val verifying: Boolean = false,
    val failedModelId: String? = null,
    val isTesting: Boolean = false,
    val testRecording: Boolean = false,
    val testResult: String? = null,
    val isInstalled: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val currentFileDownloading: String? = null,
    val downloadError: String? = null,
    val missingFiles: List<String> = emptyList(),
    val installedSizeBytes: Long = 0L,
    val selectedLocale: String = "en",
    val wearablePhoneFallback: Boolean = false,
    val availableLocales: List<LocaleOption> = listOf(
        LocaleOption("Automatic", "auto"), LocaleOption("Latviešu", "lv"),
        LocaleOption("English", "en"), LocaleOption("Русский", "ru"), LocaleOption("Deutsch", "de"),
        LocaleOption("Spanish", "es"), LocaleOption("French", "fr"), LocaleOption("Chinese", "zh"),
        LocaleOption("Japanese", "ja"), LocaleOption("Portuguese", "pt"), LocaleOption("Italian", "it")
    )
)

class VoiceSettingsViewModel(
    private val speechInventory: SpeechModelInventoryRepository,
    private val languageSettings: SpeechLanguageSettingsRepository,
    private val providerGraph: ProviderGraph? = null,
    private val storage: Storage? = null,
    val synthesisRegistry: com.halo.app.ai.SpeechSynthesisRegistry? = null,
    private val probe: SpeechRecognitionProbe? = null,
    val voiceExperience: com.halo.app.audio.VoiceExperienceSettingsRepository? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(VoiceSettingsUiState(
        wearablePhoneFallback = storage?.getBoolean("wearable_phone_fallback", false) ?: false))
    val uiState: StateFlow<VoiceSettingsUiState> = _uiState.asStateFlow()
    private val _navigationEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val navigationEvents = _navigationEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            languageSettings.loadAndMigrate()
            reloadable()?.refresh()?.join()
            speechInventory.refresh(languageSettings.languageMode.value)
            combine(speechInventory.state, languageSettings.languageMode) { inventory, language -> inventory to language }
                .collect { (inventory, language) ->
                    _uiState.value = _uiState.value.copy(models = inventory.models, sileroInstalled = inventory.sileroInstalled,
                        selectedProviderId = reloadable()?.selectedProviderId ?: "AUTOMATIC",
                        selectedModelName = reloadable()?.metadata?.displayName ?: "Automatic",
                        residentModelId = reloadable()?.residentModelId,
                        isInstalled = reloadable()?.availability?.value ?: inventory.installed, missingFiles = inventory.missingFiles,
                        installedSizeBytes = inventory.totalBytes, selectedLocale = language)
                }
        }
    }
    private var testJob: kotlinx.coroutines.Job? = null
    private fun reloadable() = providerGraph?.providers?.speechRecognition as? ReloadableSpeechRecognitionProvider
    fun cloudSynthesisFallback(): Boolean = storage?.getBoolean(com.halo.app.ai.SpeechSynthesisRegistry.FALLBACK, true) ?: true
    fun setCloudSynthesisFallback(enabled: Boolean) = storage?.putBoolean(com.halo.app.ai.SpeechSynthesisRegistry.FALLBACK, enabled)
    fun refreshState() { viewModelScope.launch { speechInventory.refresh(languageSettings.languageMode.value) } }

    fun selectModel(providerId: String) {
        if (_uiState.value.isTesting || _uiState.value.isDownloading) return
        val spec = SherpaSpeechModelCatalog.recognitionModels.firstOrNull { it.providerId == providerId }
        if (spec != null && (!spec.supportsLanguage(_uiState.value.selectedLocale) || _uiState.value.models[spec.id] != true)) return
        reloadable()?.selectModel(providerId)
        _uiState.value = _uiState.value.copy(selectedProviderId = providerId)
        viewModelScope.launch { reloadable()?.refresh()?.join(); refreshState() }
    }
    fun removeModel(modelId: String) {
        val spec = SherpaSpeechModelCatalog.byId(modelId) ?: return
        val state = _uiState.value
        if (state.selectedProviderId == spec.providerId || reloadable()?.metadata?.id == spec.providerId || state.isTesting || state.isDownloading) return
        viewModelScope.launch {
            speechInventory.remove(modelId).exceptionOrNull()?.let { _uiState.value = _uiState.value.copy(downloadError = it.message) }
        }
    }
    fun installModel(modelId: String) {
        if (_uiState.value.isDownloading || _uiState.value.isTesting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDownloading = true, downloadingModelId = modelId,
                downloadProgress = 0f, downloadError = null, verifying = false, failedModelId = null)
            try {
                val result = speechInventory.install(modelId) { progress ->
                    _uiState.value = _uiState.value.copy(downloadProgress = progress.fraction,
                        currentFileDownloading = progress.currentFile, downloadedBytes = progress.downloadedBytes,
                        expectedBytes = progress.totalBytes, verifying = progress.verifying)
                }
                result.exceptionOrNull()?.let { _uiState.value = _uiState.value.copy(downloadError = it.message, failedModelId = modelId) }
                reloadable()?.refresh()?.join()
                refreshState()
            } finally { _uiState.value = _uiState.value.copy(isDownloading = false, downloadingModelId = null) }
        }
    }
    fun startTest() {
        if (_uiState.value.isTesting || _uiState.value.isDownloading) return
        _uiState.value = _uiState.value.copy(isTesting = true, testRecording = true, testResult = null)
        testJob = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                _uiState.value = _uiState.value.copy(testResult = probe?.run { recording ->
                    _uiState.value = _uiState.value.copy(testRecording = recording)
                } ?: "Speech probe unavailable")
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { _uiState.value = _uiState.value.copy(testResult = error.message) }
            finally {
                testJob = null
                _uiState.value = _uiState.value.copy(isTesting = false, testRecording = false)
                refreshState()
            }
        }.also { it.start() }
    }
    fun finishTest() { probe?.finishCapture() }
    fun cancelTest() { testJob?.cancel(); probe?.cancel() }
    fun selectLocale(tag: String) {
        if (_uiState.value.isTesting) return
        viewModelScope.launch {
            languageSettings.select(tag)
            reloadable()?.refresh()?.join()
            speechInventory.refresh(tag)
        }
    }
    val conversation = voiceExperience?.state ?: MutableStateFlow(com.halo.app.audio.VoiceExperienceSettings())
    fun updateConversation(value: com.halo.app.audio.VoiceExperienceSettings) { voiceExperience?.update(value) }
    fun setWearablePhoneFallback(enabled: Boolean) {
        voiceExperience?.let { it.update(it.state.value.copy(wearablePhoneFallback = enabled)) }
        storage?.putBoolean("wearable_phone_fallback", enabled)
        _uiState.value = _uiState.value.copy(wearablePhoneFallback = enabled)
    }
    fun goBack() { _navigationEvents.tryEmit("back") }
    override fun onCleared() { cancelTest(); super.onCleared() }
}
