package com.halo.app.di

import android.content.Context
import android.net.Uri
import com.halo.providers.sherpa.stt.SherpaModelInstaller
import com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog
import java.io.File

/**
 * Android boundary for explicit local Sherpa model setup.
 *
 * No network access and no automatic downloads are performed here. UI code can
 * launch the system document picker and pass the selected file Uri to this class.
 */
class SherpaSpeechModelSetup(private val context: Context) {
    enum class ModelId {
        WHISPER_TINY,
        SILERO_VAD,
        KWS_ENGLISH
    }

    data class ModelStatus(
        val id: ModelId,
        val installed: Boolean,
        val missingFiles: List<String>
    )

    private val rootDirectory = File(context.filesDir, "speech")

    fun status(id: ModelId): ModelStatus {
        val (directory, spec) = model(id)
        return ModelStatus(
            id = id,
            installed = SherpaModelInstaller.isModelReady(directory, spec),
            missingFiles = SherpaSpeechModelCatalog.missingFiles(directory, spec)
        )
    }

    fun importFile(id: ModelId, expectedFilename: String, uri: Uri): SherpaModelInstaller.Result {
        val (directory, spec) = model(id)
        val fileSpec = spec.files.firstOrNull { it.name == expectedFilename }
            ?: return SherpaModelInstaller.Result.Rejected(
                "${expectedFilename} is not part of ${spec.id}"
            )

        val input = context.contentResolver.openInputStream(uri)
            ?: return SherpaModelInstaller.Result.Rejected("Unable to open selected model file")

        return SherpaModelInstaller.installFile(
            input = input,
            destinationDirectory = directory,
            fileSpec = fileSpec
        )
    }

    private fun model(id: ModelId): Pair<File, SherpaSpeechModelCatalog.ModelSpec> = when (id) {
        ModelId.WHISPER_TINY ->
            File(rootDirectory, SherpaSpeechModelCatalog.WHISPER_TINY_DIRECTORY) to
                SherpaSpeechModelCatalog.whisperTiny
        ModelId.SILERO_VAD ->
            rootDirectory to SherpaSpeechModelCatalog.sileroVad
        ModelId.KWS_ENGLISH ->
            File(rootDirectory, SherpaSpeechModelCatalog.KWS_ENGLISH_DIRECTORY) to
                SherpaSpeechModelCatalog.kwsEnglish
    }
}
