package com.halo.app.di

/**
 * Owns the WearableHost, AudioPipeline, and AudioPerceptionEngine.
 * Wires the host → audio source → pipeline → engine chain.
 */
class HostGraph(
    private val infra: InfrastructureGraph,
    private val providerGraph: ProviderGraph
) {

    val wearableHost by lazy {
        com.halo.host.android.AndroidWearableHost(
            audioOutput = com.halo.host.android.AndroidWearableAudioOutput()
        )
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
