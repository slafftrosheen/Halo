package com.halo.providers.sherpa.stt

import com.halo.sdk.runtime.HaloDispatchers
import com.halo.sdk.runtime.productionHaloDispatchers
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

data class SpeechModelInventoryState(
    val loading: Boolean = true,
    val installed: Boolean = false,
    val totalBytes: Long = 0L,
    val missingFiles: List<String> = emptyList(),
    val selectedModel: String = SherpaSpeechModelCatalog.WHISPER_TINY_DIRECTORY,
    val languageMode: String = "en",
    val error: String? = null
)

interface SpeechModelInventoryRepository {
    val state: StateFlow<SpeechModelInventoryState>
    suspend fun refresh(languageMode: String? = null)
    suspend fun installWhisperTiny(onProgress: (Float, String) -> Unit): Result<Unit>
}

class DefaultSpeechModelInventoryRepository(
    private val speechRoot: () -> File,
    private val dispatchers: HaloDispatchers = productionHaloDispatchers()
) : SpeechModelInventoryRepository {
    private val _state = MutableStateFlow(SpeechModelInventoryState())
    override val state: StateFlow<SpeechModelInventoryState> = _state

    override suspend fun refresh(languageMode: String?) {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching {
            withContext(dispatchers.io) {
                val directory = File(speechRoot(), SherpaSpeechModelCatalog.WHISPER_TINY_DIRECTORY)
                val spec = SherpaSpeechModelCatalog.whisperTiny
                SpeechModelInventoryState(
                    loading = false,
                    installed = SherpaSpeechModelCatalog.isInstalled(directory, spec),
                    totalBytes = if (directory.isDirectory) {
                        directory.walkTopDown().filter(File::isFile).sumOf(File::length)
                    } else 0L,
                    missingFiles = SherpaSpeechModelCatalog.missingFiles(directory, spec),
                    languageMode = languageMode ?: _state.value.languageMode
                )
            }
        }.onSuccess { _state.value = it }
            .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
    }

    override suspend fun installWhisperTiny(onProgress: (Float, String) -> Unit): Result<Unit> {
        val destination = withContext(dispatchers.io) {
            File(speechRoot(), SherpaSpeechModelCatalog.WHISPER_TINY_DIRECTORY)
        }
        return WhisperModelDownloader.downloadAndInstall(destination, onProgress)
            .also { refresh() }
    }
}
