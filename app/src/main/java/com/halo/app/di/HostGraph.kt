package com.halo.app.di

import com.halo.drivers.heycyan.HeyCyanAudioOutput
import com.halo.drivers.heycyan.HeyCyanHost
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.providers.llamacpp.NativeLlamaProvider
import com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog
import com.halo.sdk.host.WearableHost
import com.halo.sdk.runtime.NativeMemoryPressure
import java.io.File

/**
 * Owns Android-host audio and speech input for the phone-first runtime.
 * Physical wearable adapters remain lazy. They are constructed only by an
 * explicit device flow or when a persisted autoConnect=true record opts that
 * device kind into process-level recovery.
 */
class HostGraph(
    private val context: android.content.Context,
    private val infra: InfrastructureGraph,
    private val providerGraph: ProviderGraph,
    private val storage: com.halo.sdk.runtime.Storage,
    private val deviceSessionManager: com.halo.sdk.runtime.embodiment.DeviceSessionManager
) {

    private val speechModelDirectory by lazy {
        com.halo.app.storage.haloStorageLayout(context).speechModels
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
            speechActivityEstimator = SherpaOrEnergySpeechActivityEstimator(
                modelFile = { File(speechModelDirectory, SherpaSpeechModelCatalog.SILERO_VAD_FILE) }
            ),
            wakeWordDetector = providerGraph.providers.wakeWord,
            sttProvider = providerGraph.providers.speechRecognition,
            eventBus = infra.eventBus,
            scope = infra.scope
        )
    }

    val audioPerceptionEngine by lazy {
        com.halo.engines.speech.AudioPerceptionEngine(
            audioSource = com.halo.app.audio.ForegroundAudioSource(context,
                com.halo.host.android.audio.AndroidAudioSource(context),
                com.halo.app.audio.HaloAudioForegroundService.Kind.PHONE_INPUT),
            preprocessor = com.halo.engines.speech.DefaultAudioPreprocessor(),
            audioPipeline = audioPipeline,
            eventBus = infra.eventBus,
            commandBus = infra.commandBus
        )
    }

    /**
     * Physical HeyCyan support is deliberately lazy. It is resolved by Devices
     * flows or by DeviceAutoConnectCoordinator when a saved glasses record has
     * autoConnect enabled.
     */
    var captureCoordinator: com.halo.app.capture.WearableCaptureCoordinator? = null
        private set

    val heyCyanDriver: RealHeyCyanDriver by lazy {
        RealHeyCyanDriver(context, infra.eventBus).also { driver ->
            WearableSpeechInputBridge(
                context,
                driver,
                audioPerceptionEngine,
                infra.eventBus,
                infra.scope,
                cancelSpeechOutput = {
                    ManualDependencyGraph.cognitiveSession.cancel()
                    ManualDependencyGraph.haloSpeechOutputCoordinator.stop("Barge-in")
                },
                prepareInference = {
                    val inference = providerGraph.providers.inference as? NativeLlamaProvider
                    val selectedProvider = storage.getString("active_ai_provider")
                    val budget = providerGraph.nativeResourceCoordinator.budget.value
                    val nativeSelected = inference != null &&
                        (selectedProvider.isNullOrBlank() || selectedProvider == inference.id)
                    if (
                        nativeSelected &&
                        budget.allowConcurrentHeavyweights &&
                        budget.pressure == NativeMemoryPressure.NORMAL
                    ) {
                        inference?.loadModelIfNeeded()
                    }
                }
            ).start()
            captureCoordinator = com.halo.app.capture.WearableCaptureCoordinator(context, driver,
                ManualDependencyGraph.captures, infra.eventBus, infra.scope).also { it.start() }
        }
    }

    val heyCyanHost: HeyCyanHost by lazy {
        HeyCyanHost(
            driver = heyCyanDriver,
            audioOutput = HeyCyanAudioOutput(heyCyanDriver),
            storage = storage,
            sessionManager = deviceSessionManager,
            eventBus = infra.eventBus
        )
    }

    val wearableHost: WearableHost by lazy {
        heyCyanHost
    }
}
