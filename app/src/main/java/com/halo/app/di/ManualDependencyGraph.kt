package com.halo.app.di

import android.content.Context
import com.halo.app.HaloRuntime
import com.halo.app.embodiment.DeviceAutoConnectCoordinator
import com.halo.host.android.system.AndroidPlatformStateMonitor
import com.halo.app.ai.readRuntimeAiContextPolicy
import com.halo.app.RuntimeStartupTrace
import com.halo.app.audio.HaloSpeechOutputCoordinator
import com.halo.app.audio.SpeechOutputRouteController
import com.halo.app.data.AppDatabase
import com.halo.app.data.WorkspaceContextStore
import com.halo.app.data.agent.RoomAgentRunRepository
import com.halo.app.data.memory.BoundedMemoryContextSource
import com.halo.app.data.memory.RoomMemoryRepository
import com.halo.app.data.notebook.RoomNotebookRepository
import com.halo.app.data.workspace.RoomWorkspaceRepository
import com.halo.app.data.workspace.SendIntentHandler
import com.halo.app.di.tools.StorageStatusToolExecutor
import com.halo.app.di.tools.WorkspaceToolExecutor
import com.halo.app.notebook.NotebookUseCases
import com.halo.app.ui.screens.activity.ActivityViewModel
import com.halo.app.ui.screens.devices.DevicesViewModel
import com.halo.app.ui.screens.discovery.PairingViewModel
import com.halo.app.ui.screens.home.HomeViewModel
import com.halo.app.ui.screens.notebook.NotebookViewModel
import com.halo.app.ui.screens.settings.SettingsViewModel
import com.halo.app.ui.screens.settings.VoiceSettingsViewModel
import com.halo.app.ui.theme.AppearancePreferences
import com.halo.providers.sherpa.stt.ReloadableSpeechRecognitionProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.halo.engines.memory.WorkingMemoryProcess
import com.halo.providers.android.tts.AndroidTTSProvider
import com.halo.runtime.cognition.session.CognitiveSession
import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import com.halo.runtime.cognition.tools.CurrentTimeToolExecutor
import com.halo.runtime.kernel.HaloMicrokernel

/** Thin façade that wires sub-graphs together. This is the sole composition root. */
object ManualDependencyGraph {
    lateinit var applicationContext: Context

    fun init(context: Context) {
        RuntimeStartupTrace.measure("graph.init") { applicationContext = context.applicationContext }
    }

    val infra by lazy { RuntimeStartupTrace.measure("graph.infra") { InfrastructureGraph() } }
    val storageLayout by lazy { com.halo.app.storage.haloStorageLayout(applicationContext).also { it.ensureDirectories() } }
    val secretStore by lazy { com.halo.app.security.AndroidKeystoreSecretStore(storageLayout) }
    private val preferenceStorage by lazy { com.halo.host.android.AndroidStorage(applicationContext) }
    val storage by lazy { RuntimeStartupTrace.measure("graph.storage") { com.halo.app.security.SecretBackedStorage(preferenceStorage, secretStore) } }
    val providerGraph by lazy { RuntimeStartupTrace.measure("graph.providers") { ProviderGraph(applicationContext, storage) } }
    val deviceRegistry by lazy { com.halo.sdk.runtime.embodiment.PersistentDeviceRegistry(storage) }
    val deviceSessionManager by lazy { com.halo.sdk.runtime.embodiment.DeviceSessionManager(deviceRegistry) }
    val platformStateMonitor by lazy { AndroidPlatformStateMonitor(applicationContext) }
    val hostGraph by lazy {
        RuntimeStartupTrace.measure("graph.host") {
            HostGraph(
                applicationContext,
                infra,
                providerGraph,
                storage,
                deviceSessionManager
            )
        }
    }
    val engineGraph by lazy { RuntimeStartupTrace.measure("graph.engines") { EngineGraph(applicationContext, infra, providerGraph) } }
    val observerGraph by lazy { RuntimeStartupTrace.measure("graph.observers") { ObserverGraph(applicationContext, infra, engineGraph) } }
    val appDatabase by lazy { RuntimeStartupTrace.measure("graph.database") { AppDatabase.create(applicationContext) } }
    val notebookRepository by lazy { RoomNotebookRepository(appDatabase.notebookDao()) }
    val notebookUseCases by lazy { NotebookUseCases(notebookRepository) }
    val captures by lazy { com.halo.app.capture.LocalCaptureStore(applicationContext, appDatabase, notebookRepository, eventBus) }
    val appearancePreferences by lazy { AppearancePreferences(applicationContext) }

    /** One runtime instance per Android process; Activities are only views over it. */
    val runtime by lazy {
        RuntimeStartupTrace.measure("graph.runtime") {
            HaloRuntime(ManualDependencyGraph)
        }
    }

    val eventBus get() = infra.eventBus
    val commandBus get() = infra.commandBus
    val queryBus get() = infra.queryBus
    val scheduler get() = infra.scheduler
    val registry get() = infra.registry
    val logger get() = infra.logger
    val perceptionBus get() = infra.perceptionBus
    val cognitiveBus get() = infra.cognitiveBus
    val executionBus get() = infra.executionBus
    val blackboard get() = infra.blackboard
    val cognitiveTrace get() = infra.cognitiveTrace

    val modelDownloadManager get() = providerGraph.modelDownloadManager
    val providers get() = providerGraph.providers
    val speechRecognitionAvailability: StateFlow<Boolean> by lazy {
        (providers.speechRecognition as? ReloadableSpeechRecognitionProvider)?.availability
            ?: MutableStateFlow(true)
    }

    /** Physical hosts remain lazy and are only touched by explicit device flows. */
    val wearableHost by lazy { RuntimeStartupTrace.measure("host.wearableHost") { hostGraph.wearableHost } }
    val audioPipeline get() = hostGraph.audioPipeline
    val audioPerceptionEngine get() = hostGraph.audioPerceptionEngine
    val heyCyanDriver by lazy { RuntimeStartupTrace.measure("host.heyCyanDriver") { hostGraph.heyCyanDriver } }

    val goalManager get() = engineGraph.goalManager
    val capabilityRegistry get() = engineGraph.capabilityRegistry
    val executionScheduler get() = engineGraph.executionScheduler
    val executionEngine get() = engineGraph.executionEngine
    val planningEngine get() = engineGraph.planningEngine
    val interpretationEngine get() = engineGraph.interpretationEngine
    val commandResponseProcess get() = engineGraph.commandResponseProcess
    val reasoningEngine get() = engineGraph.reasoningEngine
    val aiEngine get() = engineGraph.aiEngine
    val aiProviderRouter get() = engineGraph.aiProviderRouter
    val reflectionEngine get() = engineGraph.reflectionEngine
    val speechSessionManager get() = engineGraph.speechSessionManager
    val speechPerceptionBridge get() = engineGraph.speechPerceptionBridge

    val conversationStore by lazy { com.halo.app.data.RoomConversationStore(appDatabase, infra.scope) }
    val workspaceRepository by lazy { RoomWorkspaceRepository(appDatabase.notebookDao()) }
    val memoryRepository by lazy { RoomMemoryRepository(appDatabase) }
    val memoryContextSource by lazy { BoundedMemoryContextSource(memoryRepository) }
    val skillRegistry by lazy { DefaultSkillRegistry(storage) }
    val agentRunRepository by lazy { RoomAgentRunRepository(appDatabase) }
    val toolRegistry by lazy {
        com.halo.runtime.cognition.tools.ToolRegistry(
            listOf(
                AndroidCapabilityTools(applicationContext, eventBus),
                CurrentTimeToolExecutor(),
                StorageStatusToolExecutor(storageUsageRepository, storageAccessRepository),
                WorkspaceToolExecutor(notebookRepository, workspaceRepository, workspaceContext),
                com.halo.app.di.tools.MemoryToolExecutor(memoryRepository, memoryContextSource),
                com.halo.app.di.tools.HostTools(applicationContext),
            )
        )
    }
    val workspaceContext by lazy { WorkspaceContextStore(appDatabase, notebookRepository, storage) }
    val contextAssembler by lazy { com.halo.runtime.cognition.context.DefaultContextAssembler(conversationStore, blackboard, toolRegistry, workspaceContext) }
    val toolAuthorizer by lazy { com.halo.runtime.cognition.tools.PolicyToolAuthorizer() }
    val cognitiveSession by lazy {
        CognitiveSession(
            eventBus = eventBus,
            providerRouter = aiProviderRouter,
            conversationStore = conversationStore,
            contextAssembler = contextAssembler,
            toolRegistry = toolRegistry,
            toolAuthorizer = toolAuthorizer,
            scope = infra.scope,
            blackboard = blackboard,
            defaultPolicyProvider = { storage.readRuntimeAiContextPolicy() }
        )
    }

    val processManager get() = observerGraph.processManager
    val timelineEventMapper get() = observerGraph.timelineEventMapper
    val timelineRecorder get() = observerGraph.timelineRecorder
    val runtimeValidator get() = observerGraph.runtimeValidator
    val traceRecorder get() = observerGraph.traceRecorder
    val healthEvaluator get() = observerGraph.healthEvaluator
    val snapshotAssembler get() = observerGraph.snapshotAssembler

    val workingMemoryProcess by lazy { WorkingMemoryProcess(perceptionBus, cognitiveBus, executionBus) }

    val buddyBotHost by lazy {
        com.halo.app.embodiment.BuddyBotHost(
            deviceSessionManager,
            infra.eventBus,
            storage,
            infra.scope
        )
    }

    val deviceAutoConnectCoordinator by lazy {
        DeviceAutoConnectCoordinator(
            registry = deviceRegistry,
            sessionManager = deviceSessionManager,
            heyCyanHostProvider = { hostGraph.heyCyanHost },
            buddyBotHostProvider = { buddyBotHost },
            bluetoothEnabled = platformStateMonitor.bluetoothEnabled,
            networkRevision = platformStateMonitor.networkRevision,
            scope = infra.scope
        )
    }

    val homeViewModel by lazy {
        RuntimeStartupTrace.measure("viewmodel.home") {
            HomeViewModel(
                snapshotManager = runtimeSnapshotManager,
                commandBus = commandBus,
                eventBus = eventBus,
                cognitiveSession = cognitiveSession,
                stopSpeechOutput = { haloSpeechOutputCoordinator.stop("User stop") },
                aiProviderRouter = aiProviderRouter,
                speechAvailability = speechRecognitionAvailability,
                microphonePermission = platformStateMonitor.microphonePermissionGranted,
                networkRevision = platformStateMonitor.networkRevision,
                speechInput = audioPerceptionEngine.inputStatus,
                notebook = notebookRepository, conversations = conversationStore, database = appDatabase, workspace = workspaceContext, captures = captures
            )
        }
    }
    val settingsRepository by lazy { com.halo.app.data.settings.HaloSettingsRepository(applicationContext) }
    val storageAccessRepository by lazy { com.halo.app.backup.AndroidStorageAccessRepository(applicationContext, settingsRepository) }
    val brainSnapshotSource by lazy { com.halo.app.backup.RoomBrainSnapshotSource(applicationContext, appDatabase, storage) }
    val moshi by lazy { com.squareup.moshi.Moshi.Builder().build() }
    val brainExportRepository by lazy { com.halo.app.backup.ZipBrainExporter(brainSnapshotSource, appDatabase, storageLayout, moshi) }
    val brainImportRepository by lazy {
        com.halo.app.backup.StagedBrainImporter(applicationContext, storageLayout, appDatabase, brainExportRepository, storage, moshi, restoreReconciler)
    }
    val restoreReconciler by lazy { com.halo.app.backup.RestoreReconciler(applicationContext, appDatabase, settingsRepository) }
    val vaultRepository by lazy {
        com.halo.app.backup.AndroidHaloVaultRepository(applicationContext, storageAccessRepository, brainExportRepository, brainImportRepository, appDatabase, settingsRepository)
    }
    val storageUsageRepository by lazy { com.halo.app.storage.AndroidStorageUsageRepository(applicationContext, storageLayout) }
    val workspaceFileRepository by lazy { com.halo.app.data.workspace.RoomWorkspaceFileRepository(applicationContext, appDatabase) }
    val sendIntentHandler by lazy { SendIntentHandler(notebookRepository, workspaceFileRepository) }
    val storageBackupViewModel by lazy {
        com.halo.app.ui.screens.settings.StorageBackupViewModel(
            applicationContext,
            storageAccessRepository,
            vaultRepository,
            brainExportRepository,
            brainImportRepository,
            storageUsageRepository,
            settingsRepository,
            storageLayout,
            workspaceFileRepository,
            canClearModels = {
                com.halo.sdk.runtime.NativeResourceKind.HEAVY_LLM !in providerGraph.nativeResourceCoordinator.activeKinds()
            },
            onModelsCleared = { modelDownloadManager.invalidate("model downloads cleared") },
            onAppearanceRestored = {
                appearancePreferences.reloadFromDisk()
            },
        )
    }
    val speechLanguageSettingsRepository by lazy {
        com.halo.app.ui.screens.settings.SpeechLanguageSettingsRepository(settingsRepository)
    }
    val settingsViewModel by lazy {
        SettingsViewModel(runtimeSnapshotManager, aiProviderRouter, appearancePreferences, providerGraph.speechModelInventory)
    }
    val voiceSettingsViewModel by lazy {
        VoiceSettingsViewModel(providerGraph.speechModelInventory, speechLanguageSettingsRepository, providerGraph)
    }
    val activityViewModel by lazy { ActivityViewModel(runtimeSnapshotManager) }
    val notebookViewModel by lazy { NotebookViewModel(notebookUseCases, workspaceContext, conversationStore) }
    val devicesViewModel by lazy {
        DevicesViewModel(
            deviceRegistry = deviceRegistry,
            sessionManager = deviceSessionManager,
            eventBus = eventBus,
            snapshotSource = runtimeSnapshotManager,
            heyCyanDriverProvider = { heyCyanDriver },
            buddyBotHost = buddyBotHost,
            autoConnectCoordinator = deviceAutoConnectCoordinator,
            captures = { hostGraph.captureCoordinator },
            audioPairingCoordinator = heyCyanAudioPairingCoordinator
        )
    }
    val pairingViewModel by lazy {
        PairingViewModel(
            driverProvider = { heyCyanDriver },
            deviceRegistry = deviceRegistry,
            context = applicationContext,
            bluetoothEnabled = platformStateMonitor.bluetoothEnabled
        )
    }
    val intelligenceViewModel by lazy { com.halo.app.ui.screens.settings.IntelligenceViewModel(runtimeSnapshotManager, aiProviderRouter, cognitiveSession, storage, modelDownloadManager) }

    val runtimeSnapshotManager get() = observerGraph.runtimeSnapshotManager

    private val assistantSpeechAudioAttributes by lazy {
        android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }

    val bluetoothAudioRouteMonitor by lazy {
        com.halo.host.android.audio.AndroidBluetoothAudioRouteMonitor(
            applicationContext,
            assistantSpeechAudioAttributes
        )
    }

    val heyCyanAudioPairingCoordinator by lazy {
        com.halo.app.audio.HeyCyanAudioPairingCoordinator(
            applicationContext,
            storage,
            bluetoothAudioRouteMonitor
        )
    }

    val speechOutputRouteController by lazy {
        SpeechOutputRouteController(
            applicationContext,
            heyCyanDriver,
            bluetoothAudioRouteMonitor,
            heyCyanAudioPairingCoordinator
        )
    }

    val androidTtsProvider by lazy {
        AndroidTTSProvider(applicationContext, assistantSpeechAudioAttributes)
    }

    val routedSpeechPlaybackProvider by lazy {
        com.halo.app.audio.RoutedSpeechPlaybackProvider(
            applicationContext,
            androidTtsProvider,
            assistantSpeechAudioAttributes
        )
    }

    private val speechOutputCoordinatorDelegate = lazy {
        HaloSpeechOutputCoordinator(
            eventBus = infra.eventBus,
            routeController = speechOutputRouteController,
            ttsProvider = androidTtsProvider,
            routedSpeechPlaybackProvider = routedSpeechPlaybackProvider,
            nativeResourceCoordinator = providerGraph.nativeResourceCoordinator
        )
    }
    val haloSpeechOutputCoordinator: HaloSpeechOutputCoordinator get() = speechOutputCoordinatorDelegate.value
    fun startAndroidSpeaker() { speechOutputCoordinatorDelegate.value.start() }
    fun shutdownAndroidSpeaker() { if (speechOutputCoordinatorDelegate.isInitialized()) speechOutputCoordinatorDelegate.value.shutdown() }

    val microkernel by lazy {
        RuntimeStartupTrace.measure("graph.microkernel") {
            // Only explicit, phone-safe runtime processes are registered during boot.
            processManager.register(workingMemoryProcess)
            processManager.register(com.halo.engines.interpretation.RuleInterpretationProcess(perceptionBus, cognitiveBus))
            processManager.register(com.halo.engines.agents.ReasoningServiceProcess(perceptionBus, cognitiveBus, aiProviderRouter))
            HaloMicrokernel(infra.scope, processManager, perceptionBus, cognitiveBus, executionBus)
        }
    }

    val engineContext by lazy {
        RuntimeStartupTrace.measure("graph.engineContext") {
            com.halo.sdk.runtime.EngineContext(
                runtime = object : com.halo.sdk.runtime.RuntimeContext {
                    override val eventBus = infra.eventBus
                    override val commandBus = infra.commandBus
                    override val queryBus = infra.queryBus
                    override val registry = infra.registry
                    override val logger = infra.logger
                    override val scheduler = infra.scheduler
                    override val clock = infra.clock
                    override val powerMode get() = infra.powerMode
                    override val metrics = infra.metrics
                },
                registry = infra.registry
            )
        }
    }
}
