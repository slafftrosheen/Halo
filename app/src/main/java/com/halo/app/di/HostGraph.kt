package com.halo.app.di

import com.halo.drivers.heycyan.RealHeyCyanDriver

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

    /** Single driver instance shared by host, capabilities, and UI tooling. */
    val heyCyanDriver: RealHeyCyanDriver by lazy {
        RealHeyCyanDriver(context, infra.eventBus)
    }

    private val hostFactory by lazy {
        HostFactory(
            context = context,
            storage = storage,
            heyCyanDriver = heyCyanDriver
        )
    }

    val wearableHost: com.halo.sdk.host.WearableHost by lazy {
        hostFactory.create(HostType.HEYCYAN)
    }

    val audioPipeline by lazy {
        com.halo.engines.speech.AudioPipeline(
            vad = com.halo.engines.speech.EnergyVAD(threshold = 1000),
            wakeWordDetector = providerGraph.providers.wakeWord,
            sttProvider = providerGraph.providers.speechRecognition,
            eventBus = infra.eventBus,
            scope = infra.scope
        )
    }

    val audioPerceptionEngine by lazy {
        com.halo.engines.speech.AudioPerceptionEngine(
            audioSource = wearableHost.audioSource,
            preprocessor = com.halo.engines.speech.DefaultAudioPreprocessor(),
            audioPipeline = audioPipeline,
            eventBus = infra.eventBus
        )
    }
}
