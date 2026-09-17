package com.halo.app.di

import android.content.Context
import com.halo.engines.memory.WorkingMemoryProcess
import com.halo.runtime.kernel.HaloMicrokernel
import com.halo.app.ui.screens.home.HomeViewModel
import com.halo.app.ui.screens.settings.SettingsViewModel
import com.halo.app.ui.screens.activity.ActivityViewModel
import com.halo.app.ui.screens.devices.DevicesViewModel
import com.halo.app.RuntimeStartupTrace
import com.halo.app.ui.screens.discovery.PairingViewModel
import com.halo.app.ui.screens.settings.VoiceSettingsViewModel
import com.halo.runtime.cognition.session.CognitiveSession

/** Thin façade that wires sub-graphs together. This is the sole composition root. */
object ManualDependencyGraph {
    lateinit var applicationContext: Context

    fun init(context: Context) {
        RuntimeStartupTrace.measure("graph.init") { applicationContext = context.applicationContext }
    }

    val infra by lazy { RuntimeStartupTrace.measure("graph.infra") { InfrastructureGraph() } }
    val storage by lazy { RuntimeStartupTrace.measure("graph.storage") { com.halo.host.android.AndroidStorage(applicationContext) } }
    val providerGraph by lazy { RuntimeStartupTrace.measure("graph.providers") { ProviderGraph(applicationContext, storage) } }
    val hostGraph by lazy { RuntimeStartupTrace.measure("graph.host") { HostGraph(applicationContext, infra, providerGraph, storage) } }
    val engineGraph by lazy { RuntimeStartupTrace.measure("graph.engines") { EngineGraph(applicationContext, infra, providerGraph) } }
    val observerGraph by lazy { RuntimeStartupTrace.measure("graph.observers") { ObserverGraph(applicationContext, infra, engineGraph) } }

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

    val conversationStore by lazy { com.halo.runtime.cognition.conversation.InMemoryConversationStore() }
    val toolRegistry by lazy { com.halo.runtime.cognition.tools.ToolRegistry(listOf(com.halo.runtime.cognition.tools.EchoToolExecutor(), com.halo.runtime.cognition.tools.CurrentTimeToolExecutor())) }
    val contextAssembler by lazy { com.halo.runtime.cognition.context.DefaultContextAssembler(conversationStore, blackboard, toolRegistry) }
    val toolAuthorizer by lazy { com.halo.runtime.cognition.tools.InMemoryToolAuthorizer(toolRegistry) }
    val cognitiveSession by lazy { CognitiveSession(eventBus, aiProviderRouter, conversationStore, contextAssembler, toolRegistry, toolAuthorizer, infra.scope, blackboard) }

    val processManager get() = observerGraph.processManager
    val timelineEventMapper get() = observerGraph.timelineEventMapper
    val timelineRecorder get() = observerGraph.timelineRecorder
    val runtimeValidator get() = observerGraph.runtimeValidator
    val traceRecorder get() = observerGraph.traceRecorder
    val healthEvaluator get() = observerGraph.healthEvaluator
    val snapshotAssembler get() = observerGraph.snapshotAssembler

    val workingMemoryProcess by lazy { WorkingMemoryProcess(perceptionBus, cognitiveBus, executionBus) }

    val deviceRegistry by lazy { com.halo.sdk.runtime.embodiment.PersistentDeviceRegistry(storage) }
    val deviceSessionManager by lazy { com.halo.sdk.runtime.embodiment.DeviceSessionManager(deviceRegistry) }
    val buddyBotHost by lazy { com.halo.app.embodiment.BuddyBotHost(deviceSessionManager, infra.eventBus, storage, infra.scope) }

    val homeViewModel by lazy { RuntimeStartupTrace.measure("viewmodel.home") { HomeViewModel(runtimeSnapshotManager, commandBus, cognitiveSession, aiProviderRouter) } }
    val settingsViewModel by lazy { SettingsViewModel(applicationContext, runtimeSnapshotManager, aiProviderRouter) }
    val voiceSettingsViewModel by lazy { VoiceSettingsViewModel(applicationContext, storage, providerGraph) }
    val activityViewModel by lazy { ActivityViewModel(runtimeSnapshotManager) }
    val devicesViewModel by lazy { DevicesViewModel(deviceRegistry, deviceSessionManager, eventBus, runtimeSnapshotManager, { heyCyanDriver }, buddyBotHost) }
    val pairingViewModel by lazy { PairingViewModel({ heyCyanDriver }) }
    val intelligenceViewModel by lazy { com.halo.app.ui.screens.settings.IntelligenceViewModel(runtimeSnapshotManager, aiProviderRouter, cognitiveSession, storage, modelDownloadManager) }

    val runtimeSnapshotManager get() = observerGraph.runtimeSnapshotManager

    private val androidSpeakerDelegate = lazy { com.halo.host.android.audio.AndroidSpeaker(applicationContext, infra.eventBus) }
    val androidSpeaker: com.halo.host.android.audio.AndroidSpeaker get() = androidSpeakerDelegate.value
    fun startAndroidSpeaker() { androidSpeakerDelegate.value.start() }
    fun shutdownAndroidSpeaker() { if (androidSpeakerDelegate.isInitialized()) androidSpeakerDelegate.value.shutdown() }

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