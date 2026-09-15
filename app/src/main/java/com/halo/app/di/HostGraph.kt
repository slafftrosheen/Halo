package com.halo.app.di

import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog
import java.io.File

/**
 * Owns the WearableHost, physical driver, audio pipeline, and audio perception
 * chain for the current host instance.
 */
class HostGraph(
    private val context: android.content.Context,
    private val infra: InfrastructureGraph,
    private val providerGraph: ProviderGraph,
    private val storage: com.halo.sdk.runtime.Storage
) {

    val heyCyanDriver: RealHeyCyanDriver by lazy {
        RealHeyCyanDriver(context, infra.eventBus)
    }

    private val hostFactory by lazy {
        HostFactory(
            context = context,
            storage = storage,
            heyCyanDriver = heyCyanDriver,
            sessionManager = ManualDependencyGraph.deviceSessionManager,
            eventBus = infra.eventBus
        )
    }

    val wearableHost: com.halo.sdk.host.WearableHost by lazy {
        hostFactory.create(HostType.HEYCYAN)
    }

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

    val audioPipeline by lazy {
        com.halo.engines.speech.AudioPipeline(
            // Neural VAD is opt-in through an explicitly installed model. Keep
            // deterministic energy VAD as the safe fallback for fresh installs.
            vad = vad ?: com.halo.engines.speech.EnergyVAD(threshold = 1000),
            wakeWordDetector = providerGraph.providers.wakeWord,
            sttProvider = providerGraph.providers.speechRecognition,
            eventBus = infra.eventBus,
            scope = infra.scope
        )
    }

    val audioPerceptionEngine by lazy {
        com.halo.engines.speech.AudioPerceptionEngine(
            audioSource = HybridAudioSource(wearableHost),
            preprocessor = com.halo.engines.speech.DefaultAudioPreprocessor(),
            audioPipeline = audioPipeline,
            eventBus = infra.eventBus,
            commandBus = infra.commandBus
        )
    }
}
