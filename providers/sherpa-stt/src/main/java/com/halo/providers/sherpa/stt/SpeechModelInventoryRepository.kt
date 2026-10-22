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
    val selectedModel: String = "AUTOMATIC",
    val languageMode: String = "en",
    val models: Map<String, Boolean> = emptyMap(),
    val sileroInstalled: Boolean = false,
    val error: String? = null
)

interface SpeechModelInventoryRepository {
    val state: StateFlow<SpeechModelInventoryState>
    suspend fun refresh(languageMode: String? = null)
    suspend fun install(modelId: String, onProgress: (SpeechModelDownloadProgress) -> Unit): Result<Unit>
    suspend fun remove(modelId: String): Result<Unit>
    suspend fun installWhisperTiny(onProgress: (Float, String) -> Unit): Result<Unit> =
        install(SherpaSpeechModelCatalog.WHISPER_TINY_ID) { onProgress(it.fraction, it.currentFile) }
}

class DefaultSpeechModelInventoryRepository(
    private val speechRoot: () -> File,
    private val dispatchers: HaloDispatchers = productionHaloDispatchers(),
    private val selectedProviderId: () -> String = { "AUTOMATIC" },
    private val activeProviderId: () -> String? = { null },
    private val releaseSileroVad: suspend () -> Unit = {},
    private val allowSileroVadReload: () -> Unit = {}
) : SpeechModelInventoryRepository {
    private val _state = MutableStateFlow(SpeechModelInventoryState())
    override val state: StateFlow<SpeechModelInventoryState> = _state

    override suspend fun refresh(languageMode: String?) {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching {
            withContext(dispatchers.io) {
                val models = SherpaSpeechModelCatalog.recognitionModels.associate { model ->
                    model.id to SherpaSpeechModelCatalog.isInstalled(File(speechRoot(), model.directoryName), model)
                }
                val selected = selectedProviderId()
                val spec = SherpaSpeechModelCatalog.resolve(selected) { models[it.id] == true }
                val language = languageMode ?: _state.value.languageMode
                SpeechModelInventoryState(
                    loading = false,
                    installed = spec != null && spec.supportsLanguage(language),
                    selectedModel = spec?.id ?: selected,
                    totalBytes = SherpaSpeechModelCatalog.recognitionModels.sumOf { model ->
                        model.files.sumOf { File(File(speechRoot(), model.directoryName), it.name).length() }
                    },
                    missingFiles = if (spec != null) emptyList() else {
                        val requested = SherpaSpeechModelCatalog.recognitionModels.firstOrNull { it.providerId == selected }
                        requested?.let { SherpaSpeechModelCatalog.missingFiles(File(speechRoot(), it.directoryName), it) } ?: emptyList()
                    },
                    models = models,
                    sileroInstalled = SherpaSpeechModelCatalog.isInstalled(speechRoot(), SherpaSpeechModelCatalog.sileroVad),
                    languageMode = languageMode ?: _state.value.languageMode
                )
            }
        }.onSuccess { _state.value = it }
            .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
    }

    override suspend fun install(modelId: String, onProgress: (SpeechModelDownloadProgress) -> Unit): Result<Unit> {
        val spec = SherpaSpeechModelCatalog.byId(modelId)
            ?: SherpaSpeechModelCatalog.sileroVad.takeIf { it.id == modelId }
            ?: return Result.failure(IllegalArgumentException("Unknown speech model"))
        val destination = withContext(dispatchers.io) {
            if (modelId == SherpaSpeechModelCatalog.SILERO_VAD_ID) speechRoot()
            else File(speechRoot(), spec.directoryName)
        }
        return SpeechModelInstaller.install(spec, destination, onProgress).also {
            if (it.isSuccess && modelId == SherpaSpeechModelCatalog.SILERO_VAD_ID) allowSileroVadReload()
            refresh()
        }
    }

    override suspend fun remove(modelId: String): Result<Unit> = withContext(dispatchers.io) {
        try {
            if (modelId == SherpaSpeechModelCatalog.SILERO_VAD_ID) {
                releaseSileroVad()
                val root = speechRoot().canonicalFile
                val model = File(root, SherpaSpeechModelCatalog.SILERO_VAD_FILE).canonicalFile
                require(model.parentFile == root) { "Invalid VAD model file" }
                val proof = File(root, "${SherpaSpeechModelCatalog.SILERO_VAD_FILE}.verified").canonicalFile
                require(proof.parentFile == root) { "Invalid VAD verification file" }
                val partial = File(root, "${SherpaSpeechModelCatalog.SILERO_VAD_FILE}.partial").canonicalFile
                require(partial.parentFile == root) { "Invalid VAD partial file" }
                check(!model.exists() || model.delete()) { "Unable to remove VAD model" }
                check(!proof.exists() || proof.delete()) { "Unable to remove VAD verification file" }
                check(!partial.exists() || partial.delete()) { "Unable to remove partial VAD download" }
                refresh()
                return@withContext Result.success(Unit)
            }
            val spec = requireNotNull(SherpaSpeechModelCatalog.byId(modelId))
            val selection = selectedProviderId()
            val automatic = SherpaSpeechModelCatalog.resolve(selection) {
                SherpaSpeechModelCatalog.isInstalled(File(speechRoot(), it.directoryName), it)
            }
            check(selection != spec.providerId && automatic != spec && activeProviderId() != spec.providerId) {
                "Select another speech model before deleting this model."
            }
            val root = speechRoot().canonicalFile
            val directory = File(root, spec.directoryName).canonicalFile
            require(directory.parentFile == root) { "Invalid speech model directory" }
            check(!directory.exists() || directory.deleteRecursively()) { "Unable to remove speech model" }
            refresh()
            Result.success(Unit)
        } catch (error: Exception) {
            if (modelId == SherpaSpeechModelCatalog.SILERO_VAD_ID) runCatching { allowSileroVadReload() }
            Result.failure(error)
        }
    }
}
