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
import com.halo.app.data.settings.SharedPreferencesDigestDeliverySettingsRepository
import com.halo.app.data.workspace.RoomWorkspaceFileRepository
import com.halo.app.data.workspace.RoomWorkspaceRepository
import com.halo.app.data.workspace.SendIntentHandler
import com.halo.app.di.tools.HostTools
import com.halo.app.di.tools.StorageStatusToolExecutor
import com.halo.app.di.tools.WorkspaceToolExecutor
import com.halo.app.embodiment.RuntimeDeviceCapabilityBridge
import com.halo.app.models.FileModelCatalogCache
import com.halo.app.models.HuggingFaceGgufCatalog

import com.halo.app.notebook.NotebookUseCases
import com.halo.app.notebook.WorkspaceUseCases
import com.halo.app.notification.NotificationDigestTool
import com.halo.app.storage.haloStorageLayout
import com.halo.app.ui.screens.activity.ActivityViewModel
import com.halo.app.ui.screens.devices.DevicesViewModel
import com.halo.app.ui.screens.discovery.PairingViewModel
import com.halo.app.ui.screens.home.HomeViewModel
import com.halo.app.ui.screens.notebook.NotebookViewModel
import com.halo.app.ui.screens.settings.SettingsViewModel
import com.halo.app.ui.screens.settings.VoiceSettingsViewModel
import com.halo.app.ui.state.RuntimeUiProjection
import com.halo.app.ui.theme.AppearancePreferences
import com.halo.providers.sherpa.stt.ReloadableSpeechRecognitionProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.halo.engines.memory.WorkingMemoryProcess
import com.halo.providers.android.tts.AndroidTTSProvider
import com.halo.runtime.cognition.agent.recovery.AgentCheckpointAssembler
import com.halo.runtime.cognition.agent.recovery.DefaultAgentRecoveryPlanner
import com.halo.runtime.cognition.attention.DeliveryChannel
import com.halo.runtime.cognition.context.AssistantContextRepository
import com.halo.runtime.cognition.context.CognitiveContextPlanner
import com.halo.runtime.cognition.context.DefaultContextAcquisitionCoordinator
import com.halo.runtime.cognition.context.DefaultContextAssembler
import com.halo.runtime.cognition.context.InMemoryCognitiveModeRepository
import com.halo.runtime.cognition.device.DefaultDeviceCapabilityResolver
import com.halo.runtime.cognition.device.DefaultResourcePlanner
import com.halo.runtime.cognition.session.CognitiveSession
import com.halo.runtime.cognition.skills.BundledSkillInstructionSource
import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import com.halo.runtime.cognition.tools.CapabilityCatalogue
import com.halo.runtime.cognition.tools.CurrentTimeToolExecutor
import com.halo.runtime.cognition.tools.DefaultToolExposureGate
import com.halo.runtime.cognition.tools.ToolAvailabilityResolver
import com.halo.runtime.cognition.tools.ToolDiscoveryService
import com.halo.runtime.cognition.tools.ToolExposurePlanner
import com.halo.runtime.cognition.tools.ToolResourceCoordinator
import com.halo.runtime.cognition.turn.CognitiveTurnExecutor
import com.halo.runtime.cognition.turn.DefaultActiveTurnCoordinator
import com.halo.runtime.cognition.turn.DefaultAgentRecoveryExecutor
import com.halo.runtime.cognition.turn.DefaultCognitiveResponseCommitter
import com.halo.runtime.cognition.turn.DefaultCognitiveTurnPreparer
import com.halo.runtime.cognition.turn.DefaultProviderTurnExecutor
import com.halo.runtime.cognition.turn.DefaultToolTurnExecutor
import com.halo.runtime.cognition.workload.DefaultAiWorkloadPlanner
import com.halo.runtime.kernel.HaloMicrokernel

/** Thin façade that wires sub-graphs together. This is the sole composition root. */
object ManualDependencyGraph {
    lateinit var applicationContext: Context

    fun init(context: Context) {
        RuntimeStartupTrace.measure("graph.init") { applicationContext = context.applicationContext }
    }

    val infra by lazy { RuntimeStartupTrace.measure("graph.infra") { InfrastructureGraph() } }
    val storageLayout by lazy { haloStorageLayout(applicationContext) }
    val secretStore by lazy { com.halo.app.security.AndroidKeystoreSecretStore(storageLayout) }
    private val preferenceStorage by lazy { com.halo.host.android.AndroidStorage(applicationContext) }
    val storage by lazy { RuntimeStartupTrace.measure("graph.storage") { com.halo.app.security.SecretBackedStorage(preferenceStorage, secretStore) } }
    val providerGraph by lazy { RuntimeStartupTrace.measure("graph.providers") { ProviderGraph(applicationContext, storage, infra) } }
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
    val engineGraph by lazy { RuntimeStartupTrace.measure("graph.engines") { EngineGraph(applicationContext, infra, providerGraph, agentRunCoordinator, clarificationCoordinator, agentRunStore) } }
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
    val workspaceSearchEngine by lazy {
        com.halo.app.data.search.AppSearchWorkspaceSearchEngine(applicationContext, appDatabase.notebookDao())
    }
    val workspaceUseCases by lazy {
        WorkspaceUseCases(workspaceRepository, workspaceSearchEngine, workspaceContext,
            conversationStore, workspaceFileRepository, memoryRepository, notebookUseCases)
    }
    val memoryRepository by lazy { RoomMemoryRepository(appDatabase) }
    val memoryContextSource by lazy { BoundedMemoryContextSource(memoryRepository) }
    val skillRegistry by lazy {
        val permissions = AndroidSkillPermissionChecker(applicationContext)
        DefaultSkillRegistry(
            storage,
            initialBuiltIns = BuiltInSkillManifestLoader(applicationContext).load(),
            availabilityResolver = com.halo.runtime.cognition.skills.SkillAvailabilityResolver(
                permissionGranted = permissions::granted,
                registeredToolNames = {
                    toolRegistry.definitions().flatMap { listOf(it.id, it.name) }.toSet()
                }
            )
        )
    }
    val attentionContextProvider by lazy { com.halo.app.attention.AndroidAttentionHost(applicationContext, eventBus, infra.scope) }
    val attentionPolicyEngine by lazy { com.halo.runtime.cognition.attention.DefaultAttentionPolicyEngine() }
    val attentionQueue by lazy { com.halo.runtime.cognition.attention.InMemoryAttentionQueue() }
    val attentionDeliveryCoordinator by lazy {
        com.halo.runtime.cognition.attention.DefaultAttentionDeliveryCoordinator(attentionQueue,
            listOf(attentionContextProvider), attentionContextProvider::surfaceSilently)
    }
    val attentionController by lazy {
        com.halo.runtime.cognition.attention.AttentionController(attentionContextProvider,
            attentionPolicyEngine, attentionQueue, attentionDeliveryCoordinator, infra.scope)
    }
    val agentRunStore by lazy { RoomAgentRunRepository(appDatabase) }
    val agentRunRepository get() = agentRunStore
    val agentRunCoordinator by lazy {
        com.halo.runtime.cognition.agent.DefaultAgentRunCoordinator(
            agentRunStore, infra.scope,
            checkpointAssembler = com.halo.runtime.cognition.agent.recovery.AgentCheckpointAssembler(),
            recoveryPlanner = com.halo.runtime.cognition.agent.recovery.DefaultAgentRecoveryPlanner(),
        )
    }
    val clarificationCoordinator by lazy { com.halo.runtime.cognition.clarification.DefaultClarificationCoordinator() }

    /*
     * RUNTIME-019: portable interaction surfaces. The store is an in-memory
     * projection; the durable journal stays the only source of truth. The
     * coordinator derives surfaces from run state, the response handler
     * resolves them atomically, and resumption delegates to the session.
     */
    val interactionSurfaceStore: com.halo.runtime.cognition.interaction.InteractionSurfaceStore by lazy {
        com.halo.runtime.cognition.interaction.InMemoryInteractionSurfaceStore()
    }
    val interactionSurfaceCoordinator: com.halo.runtime.cognition.interaction.InteractionSurfaceCoordinator by lazy {
        com.halo.runtime.cognition.interaction.DefaultInteractionSurfaceCoordinator(
            agentRunStore = agentRunStore,
            surfaceStore = interactionSurfaceStore,
            surfaceFactory = com.halo.runtime.cognition.interaction.DefaultInteractionSurfaceFactory(),
            scope = infra.scope,
        )
    }
    val runResumptionCoordinator: com.halo.runtime.cognition.interaction.RunResumptionCoordinator by lazy {
        com.halo.runtime.cognition.interaction.DefaultRunResumptionCoordinator(
            agentRunStore = agentRunStore,
            resume = { runId -> cognitiveSession.resume(runId) },
        )
    }
    val interactionResponseHandler: com.halo.runtime.cognition.interaction.InteractionResponseHandler by lazy {
        com.halo.runtime.cognition.interaction.DefaultInteractionResponseHandler(
            surfaceStore = interactionSurfaceStore,
            runStore = agentRunStore,
            resumeRun = { runId -> runResumptionCoordinator.requestResume(runId) },
            cancelRun = { runId ->
                agentRunCoordinator.cancel(runId, "Cancelled from interaction surface")
                true
            },
            coordinator = interactionSurfaceCoordinator,
        )
    }
    val devicePresenceRepository by lazy { com.halo.runtime.cognition.device.InMemoryDevicePresenceRepository() }
    val devicePresenceBridge by lazy { com.halo.app.embodiment.DevicePresenceBridge(devicePresenceRepository, deviceRegistry, deviceSessionManager, infra.scope) }
    val interactionRoutePolicy by lazy { com.halo.runtime.cognition.device.DefaultInteractionRoutePolicy() }
    val notificationPolicies by lazy { com.halo.app.data.notification.NotificationPolicyManager(applicationContext) }
    val notificationRepository by lazy { com.halo.app.data.notification.RoomNotificationRepository(appDatabase.notificationDao(), notificationPolicies, applicationContext.packageName) }
    val notificationBridge by lazy { com.halo.app.notification.NotificationIngestionBridge(applicationContext, notificationRepository, infra.scope) }
    val digestDeliverySettings by lazy { SharedPreferencesDigestDeliverySettingsRepository(applicationContext, infra.scope) }
    val notificationDigestTool by lazy {
        NotificationDigestTool(notificationRepository, attentionController, notificationBridge::isAccessGranted) {
            val settings = digestDeliverySettings.state.value
            when (settings.channel) {
                "android" -> DeliveryChannel.AndroidNotification
                "wearable" -> settings.wearableId.takeIf { it.isNotBlank() }?.let {
                    if (attentionContextProvider.snapshot().foregroundState != com.halo.runtime.cognition.attention.ForegroundState.HALO_FOREGROUND) startAndroidSpeaker()
                    com.halo.runtime.cognition.attention.DeliveryChannel.WearableSpeech(it)
                }
                else -> null
            }
        }
    }
    // Same executor and registry contract, without initializing unrelated workspace/device tools in a worker.
    val automationToolRegistry by lazy { com.halo.runtime.cognition.tools.ToolRegistry(listOf(notificationDigestTool)) }
    val routineRepository by lazy { com.halo.app.data.routine.RoomRoutineRepository(appDatabase.routineDao()) }
    val routineExecutor by lazy { com.halo.app.automation.RoutineExecutor(appDatabase, routineRepository,
        agentRunCoordinator, agentRunStore, automationToolRegistry, condition = { condition ->
            when (condition) {
                is com.halo.runtime.cognition.routine.RoutineCondition.DeviceAvailable -> devicePresenceRepository.getPresence(condition.deviceId)?.state in
                    setOf(com.halo.runtime.cognition.device.DevicePresenceState.CONNECTED, com.halo.runtime.cognition.device.DevicePresenceState.READY)
                is com.halo.runtime.cognition.routine.RoutineCondition.NetworkAvailable -> {
                    val manager = applicationContext.getSystemService(android.net.ConnectivityManager::class.java)
                    val available = manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                    available == condition.required
                }
                is com.halo.runtime.cognition.routine.RoutineCondition.ProviderAvailable -> aiProviderRouter.getAllProviders()
                    .firstOrNull { it.id == condition.providerId }?.status?.value?.state == com.halo.sdk.provider.ProviderStatus.State.READY
                else -> false
            }
        }, currentlyAllowed = { tool ->
            tool == com.halo.app.notification.NotificationDigestTool.ID &&
                storage.getBoolean("skill_enabled_halo.notifications", true) &&
                (storage.getString("skill_approval_halo.notifications") ?: "ALWAYS_ALLOW") == "ALWAYS_ALLOW"
        }) }
    val routineScheduler by lazy { com.halo.app.automation.AndroidRoutineScheduler(applicationContext, routineRepository, routineExecutor::validate) }
    val routinePresenceObserver by lazy { com.halo.app.automation.RoutinePresenceObserver(devicePresenceRepository, routineRepository, routineExecutor) }
    val routinesViewModel by lazy { com.halo.app.ui.screens.settings.RoutinesViewModel(routineRepository, routineScheduler, routineExecutor) }
    /** RUNTIME-017 / RUNTIME-020.1: portable semantic capability catalogue. */
    val capabilityCatalogue by lazy {
        CapabilityCatalogue().also { catalogue ->
            hostGraph.capabilityPublisher.publishInto(catalogue)
        }
    }

    val runtimeDeviceCapabilityBridge by lazy {
        RuntimeDeviceCapabilityBridge(
            registry = deviceRegistry,
            sessions = deviceSessionManager,
            catalogue = capabilityCatalogue,
            scope = infra.scope
        )
    }

    val toolAvailabilityResolver by lazy {
        ToolAvailabilityResolver(
            registry = toolRegistry,
            skillRegistry = skillRegistry,
            catalogueSource = capabilityCatalogue
        )
    }

    val toolExposurePlanner by lazy {
        ToolExposurePlanner(
            registry = toolRegistry,
            availabilityResolver = toolAvailabilityResolver
        )
    }

    val runtimeUiProjection by lazy {
        RuntimeUiProjection(
            runStore = agentRunStore,
            resourceLeaseManager = infra.resourceLeaseManager,
            catalogueSource = capabilityCatalogue,
            scope = infra.scope
        )
    }

    val toolDiscovery: ToolDiscoveryService by lazy {
        ToolDiscoveryService(
            registry = toolRegistry,
            availabilityResolver = toolAvailabilityResolver
        )
    }

    val toolDiscoveryExecutor by lazy {
        // Resolve the discovery service only when the meta-tool actually executes.
        // Eager resolution here creates toolRegistry -> executor -> discovery -> toolRegistry recursion.
        com.halo.runtime.cognition.tools.ToolDiscoveryExecutor { toolDiscovery }
    }

    val toolRegistry by lazy {
        val foreground = {
            attentionContextProvider.snapshot().foregroundState == com.halo.runtime.cognition.attention.ForegroundState.HALO_FOREGROUND
        }
        val currentTimeExecutor = CurrentTimeToolExecutor()
        val notificationExecutor = notificationDigestTool
        val storageExecutor = StorageStatusToolExecutor(storageUsageRepository, storageAccessRepository)
        val fileExecutor = com.halo.app.di.tools.FileToolExecutor(applicationContext, workspaceFileRepository, workspaceRepository)
        val workspaceExecutor = WorkspaceToolExecutor(notebookRepository, workspaceRepository, workspaceContext, assistantContextRepository)
        val memoryExecutor = com.halo.app.di.tools.MemoryToolExecutor(memoryRepository, memoryContextSource)
        val hostExecutor = com.halo.app.di.tools.HostTools(applicationContext, foreground)
        com.halo.runtime.cognition.tools.ToolRegistry(
            listOf(
                currentTimeExecutor,
                notificationExecutor,
                storageExecutor,
                fileExecutor,
                workspaceExecutor,
                memoryExecutor,
                hostExecutor,
                // Discovery meta-tool is internal: it expands schemas without widening authorization.
                toolDiscoveryExecutor,
            ),
            descriptors = com.halo.app.di.tools.DescriptorCatalog.buildDescriptors(
                com.halo.app.di.tools.HaloToolDescriptors.ExecutorKind.CURRENT_TIME to currentTimeExecutor,
                com.halo.app.di.tools.HaloToolDescriptors.ExecutorKind.NOTIFICATION_DIGEST to notificationExecutor,
                com.halo.app.di.tools.HaloToolDescriptors.ExecutorKind.STORAGE_STATUS to storageExecutor,
                com.halo.app.di.tools.HaloToolDescriptors.ExecutorKind.FILE to fileExecutor,
                com.halo.app.di.tools.HaloToolDescriptors.ExecutorKind.WORKSPACE to workspaceExecutor,
                com.halo.app.di.tools.HaloToolDescriptors.ExecutorKind.MEMORY to memoryExecutor,
                com.halo.app.di.tools.HaloToolDescriptors.ExecutorKind.NAVIGATION to hostExecutor,
                com.halo.app.di.tools.HaloToolDescriptors.ExecutorKind.SYSTEM to hostExecutor,
                com.halo.app.di.tools.HaloToolDescriptors.ExecutorKind.APPLICATION to hostExecutor,
                com.halo.app.di.tools.HaloToolDescriptors.ExecutorKind.DEVICE to hostExecutor,
                com.halo.app.di.tools.HaloToolDescriptors.ExecutorKind.ALARM to hostExecutor,
                com.halo.app.di.tools.HaloToolDescriptors.ExecutorKind.COMMUNICATION to hostExecutor,
                com.halo.app.di.tools.HaloToolDescriptors.ExecutorKind.MEDIA to hostExecutor,
                com.halo.app.di.tools.HaloToolDescriptors.ExecutorKind.ACCESSIBILITY to hostExecutor,
            ) + listOf(
                com.halo.app.di.tools.HaloToolDescriptors.discoveryMeta(
                    com.halo.runtime.cognition.tools.ToolDiscoveryService.discoveryToolDefinition()
                ),
            )
        )
    }
    val workspaceContext by lazy { WorkspaceContextStore(appDatabase, notebookRepository, storage) }
    val assistantContextRepository by lazy {
        AssistantContextRepository(
            eventBus = eventBus,
            scope = infra.scope,
        )
    }
    val toolExposureGate by lazy { DefaultToolExposureGate(skillRegistry) }
    val skillInstructionSource by lazy { BundledSkillInstructionSource() }
    val cognitiveContextPlanner by lazy { CognitiveContextPlanner() }
    val cognitiveModeRepository by lazy { InMemoryCognitiveModeRepository() }

    val contextAssembler by lazy {
        DefaultContextAssembler(
            conversationStore, blackboard, toolRegistry, workspaceContext, skillRegistry, assistantContextRepository,
            discoveryProvider = { toolDiscovery },
            memoryContextSource = memoryContextSource,
            toolExposureGate = toolExposureGate,
        )
    }
    val toolAuthorizationCoordinator by lazy {
        com.halo.app.di.tools.InteractiveToolAuthorization {
            attentionContextProvider.snapshot().foregroundState == com.halo.runtime.cognition.attention.ForegroundState.HALO_FOREGROUND
        }
    }

    /** RUNTIME-018: durable approval resolution from the UI sheet. */
    val toolAuthorizer by lazy {
        com.halo.runtime.cognition.tools.PolicyToolAuthorizer(
            coordinator = toolAuthorizationCoordinator,
            defaultPolicyProvider = skillRegistry::approvalPolicyForTool,
            authorizationGroupProvider = skillRegistry::authorizationGroupForTool,
            descriptorProvider = { toolName -> toolRegistry.descriptor(toolName) }
        )
    }
    val deviceCapabilityResolver by lazy {
        DefaultDeviceCapabilityResolver()
    }
    val resourcePlanner by lazy {
        DefaultResourcePlanner()
    }
    val toolResourceCoordinator by lazy {
        ToolResourceCoordinator(
            capabilityResolver = deviceCapabilityResolver,
            resourcePlanner = resourcePlanner,
            resourceLeaseManager = infra.resourceLeaseManager,
            runtimeDeviceSource = runtimeDeviceCapabilityBridge,
        )
    }
    val visualContextSource by lazy {
        com.halo.app.capture.RuntimeVisualContextSource(
            deviceRegistry = deviceRegistry,
            heyCyanDriverProvider = { heyCyanDriver },
            resourceLeaseManager = infra.resourceLeaseManager,
        )
    }
    val contextAcquisitionCoordinator by lazy {
        DefaultContextAcquisitionCoordinator(
            assistantContexts = assistantContextRepository,
            workspaceContext = workspaceContext,
            blackboard = blackboard,
            visualContextSource = visualContextSource,
        )
    }
    val activeTurnCoordinator by lazy {
        DefaultActiveTurnCoordinator(eventBus)
    }
    val providerTurnExecutor by lazy {
        DefaultProviderTurnExecutor(eventBus)
    }
    val toolTurnExecutor by lazy {
        DefaultToolTurnExecutor(
            toolRegistry = toolRegistry,
            toolAuthorizer = toolAuthorizer,
            toolResourceCoordinator = toolResourceCoordinator,
            agentRunCoordinator = agentRunCoordinator,
            agentRunStore = agentRunStore,
            eventBus = eventBus,
        )
    }
    val cognitiveTurnPreparer by lazy {
        com.halo.runtime.cognition.turn.DefaultCognitiveTurnPreparer(
            workloadPlanner = DefaultAiWorkloadPlanner(),
            providerRouter = aiProviderRouter,
            contextAcquisitionCoordinator = contextAcquisitionCoordinator,
            contextAssembler = contextAssembler,
            agentRunStore = agentRunStore,
        )
    }
    val cognitiveResponseCommitter by lazy {
        DefaultCognitiveResponseCommitter(
            conversationStore = conversationStore,
            agentRunCoordinator = agentRunCoordinator,
            eventBus = eventBus,
        )
    }
    val cognitiveTurnExecutor by lazy {
        com.halo.runtime.cognition.turn.CognitiveTurnExecutor(
            preparer = cognitiveTurnPreparer,
            providerExecutor = providerTurnExecutor,
            toolExecutor = toolTurnExecutor,
            agentRunCoordinator = agentRunCoordinator,
            agentRunStore = agentRunStore,
            eventBus = eventBus,
        )
    }
    val agentRecoveryExecutor by lazy {
        com.halo.runtime.cognition.turn.DefaultAgentRecoveryExecutor(
            agentRunStore = agentRunStore,
            agentRunCoordinator = agentRunCoordinator,
            toolRegistry = toolRegistry,
            eventBus = eventBus,
            recoveryPlanner = DefaultAgentRecoveryPlanner(),
        )
    }
    val cognitiveSession by lazy {
        CognitiveSession(
            eventBus = eventBus,
            conversationStore = conversationStore,
            scope = infra.scope,
            blackboard = blackboard,
            defaultPolicyProvider = { storage.readRuntimeAiContextPolicy() },
            activeTurnCoordinator = activeTurnCoordinator,
            agentRecoveryExecutor = agentRecoveryExecutor,
            turnExecutor = cognitiveTurnExecutor,
            responseCommitter = cognitiveResponseCommitter,
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
                runtimeProjection = runtimeUiProjection,
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
    val workspaceFileRepository by lazy { RoomWorkspaceFileRepository(applicationContext, appDatabase, storageLayout.managedWorkspaceFiles) }
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
        VoiceSettingsViewModel(providerGraph.speechModelInventory, speechLanguageSettingsRepository, providerGraph, storage)
    }
    val activityViewModel by lazy { ActivityViewModel(runtimeSnapshotManager, agentRunStore = agentRunStore) }
    val notebookViewModel by lazy { NotebookViewModel(workspaceUseCases, notebookUseCases) }
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
            audioPairingCoordinator = heyCyanAudioPairingCoordinator,
            capabilityCatalogue = capabilityCatalogue,
            resourceLeaseManager = infra.resourceLeaseManager,
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
    val modelCatalogCache by lazy { FileModelCatalogCache(applicationContext) }
    val modelCatalog by lazy { HuggingFaceGgufCatalog(applicationContext, modelCatalogCache) }
    val intelligenceViewModel by lazy { com.halo.app.ui.screens.settings.IntelligenceViewModel(
        runtimeSnapshotManager, aiProviderRouter, cognitiveSession, storage, modelDownloadManager, modelCatalog) }

    val runtimeSnapshotManager get() = observerGraph.runtimeSnapshotManager

    private val assistantSpeechAudioAttributes by lazy {
        android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
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
            com.halo.app.audio.HeyCyanWearableAudioRouteAdapter(heyCyanDriver),
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
            context = applicationContext,
            speechAudioAttributes = assistantSpeechAudioAttributes,
            storage = storage,
            eventBus = infra.eventBus,
            routeController = speechOutputRouteController,
            ttsProvider = androidTtsProvider,
            routedSpeechPlaybackProvider = routedSpeechPlaybackProvider,
            nativeResourceCoordinator = providerGraph.nativeResourceCoordinator,
            interactionRoutePolicy = interactionRoutePolicy,
            resourceLeaseManager = infra.resourceLeaseManager,
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
