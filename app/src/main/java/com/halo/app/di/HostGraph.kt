package com.halo.app.di

import android.media.AudioManager
import com.halo.drivers.heycyan.HeyCyanAudioOutput
import com.halo.drivers.heycyan.HeyCyanHost
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog
import com.halo.app.audio.AndroidInteractionAudioManager
import com.halo.app.audio.ForegroundAudioSource
import com.halo.host.android.audio.AndroidAudioSource
import com.halo.host.android.audio.AndroidCommunicationAudioRouter
import com.halo.sdk.host.WearableHost
import com.halo.sdk.runtime.NativeMemoryPressure
import java.io.File
import kotlinx.coroutines.launch

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

    val speechActivityEstimator by lazy {
        SherpaOrEnergySpeechActivityEstimator(
            modelFile = { File(speechModelDirectory, SherpaSpeechModelCatalog.SILERO_VAD_FILE) })
    }
    private val vad by lazy {
        ReloadableSileroVad { File(speechModelDirectory, SherpaSpeechModelCatalog.SILERO_VAD_FILE) }
    }

    /** Android microphone is the canonical input while Halo is phone-only. */
    val audioPipeline by lazy {
        com.halo.engines.speech.AudioPipeline(
            vad = vad,
            speechActivityEstimator = speechActivityEstimator,
            wakeWordDetector = providerGraph.providers.wakeWord,
            sttProvider = providerGraph.providers.speechRecognition,
            eventBus = infra.eventBus,
            scope = infra.scope
        )
    }

    val androidCommunicationAudioRouter by lazy {
        AndroidCommunicationAudioRouter(context.getSystemService(AudioManager::class.java))
    }

    val androidInteractionAudioManager by lazy {
        AndroidInteractionAudioManager(
            audioRouter = androidCommunicationAudioRouter,
            eventBus = infra.eventBus,
            scope = infra.scope
        )
    }

    val audioPerceptionEngine by lazy {
        com.halo.engines.speech.AudioPerceptionEngine(
            audioSource = ForegroundAudioSource(context,
                AndroidAudioSource(context) { androidInteractionAudioManager.expectedInputDeviceId },
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
    var captureCoordinator: com.halo.app.capture.HeyCyanCaptureCoordinator? = null
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
                    providerGraph.preloadLocalModel()
                },
            ).start()
            captureCoordinator = com.halo.app.capture.HeyCyanCaptureCoordinator(context, driver,
                ManualDependencyGraph.captures, infra.eventBus, infra.scope,
                preloadLocalModel = { infra.scope.launch { providerGraph.preloadLocalModel() } }).also { it.start() }
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

    /**
     * RUNTIME-017: this host advertises its semantic capabilities through the
     * portable catalogue. Android mechanics (intents, permissions) stay inside
     * the publisher adapter.
     */
    val capabilityPublisher: com.halo.app.di.AndroidCapabilityPublisher by lazy {
        AndroidCapabilityPublisher(context)
    }
}
