package com.halo.app.di

import com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog
import java.io.File

/**
 * Owns Android-host audio and speech input for the phone-first runtime.
 * Physical wearable audio is attached later by the Devices flow.
 */
class HostGraph(
    private val context: android.content.Context,
    private val infra: InfrastructureGraph,
    private val providerGraph: ProviderGraph,
    private val storage: com.halo.sdk.runtime.Storage
) {

    private val speechModelDirectory by lazy {
        File(context.filesDir, "speech")
    }

    private val vad by lazy {
        val sileroInstalled = SherpaSpeechModelCatalog.isInstalled(
            speechModelDirectory,
            SherpaSpeechModelCatalog.sileroVad
        )
        if (sileroInstalled) {
            runCatching {
                SherpaSileroVad(
                    modelFile = File(speechModelDirectory, SherpaSpeechModelCatalog.SILERO_VAD_FILE)
                )
            }.getOrNull()
        } else {
            null
        }
    }

    /** Android microphone is the canonical input while Halo is phone-only. */
    val audioPipeline by lazy {
        com.halo.engines.speech.AudioPipeline(
            vad = vad ?: com.halo.engines.speech.EnergyVAD(threshold = 1000),
            wakeWordDetector = providerGraph.providers.wakeWord,
            sttProvider = providerGraph.providers.speechRecognition,
            eventBus = infra.eventBus,
            scope = infra.scope
        )
    }

    val audioPerceptionEngine by lazy {
        com.halo.engines.speech.AudioPerceptionEngine(
            audioSource = com.halo.host.android.audio.AndroidAudioSource(),
            preprocessor = com.halo.engines.speech.DefaultAudioPreprocessor(),
            audioPipeline = audioPipeline,
            eventBus = infra.eventBus,
            commandBus = infra.commandBus
        )
    }
}
