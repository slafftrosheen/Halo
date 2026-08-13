package com.halo.app.di

import android.content.Context
import com.halo.engines.memory.WorkingMemoryProcess
import com.halo.host.android.AndroidWakeWordProcess
import com.halo.runtime.kernel.HaloMicrokernel
import com.halo.app.ui.screens.home.HomeViewModel
import com.halo.app.ui.developer.console.ConsoleViewModel
import com.halo.app.ui.screens.settings.SettingsViewModel
import com.halo.app.ui.screens.activity.ActivityViewModel
import com.halo.app.ui.screens.devices.DevicesViewModel
import com.halo.app.ui.developer.protocol.ProtocolLabViewModel
import com.halo.app.ui.developer.inspector.RuntimeInspectorViewModel
import com.halo.sdk.provider.AiProviderRouter

/**
 * Thin façade that wires sub-graphs together. This is the sole composition root.
 * No resolve() calls exist outside this file.
 */
object ManualDependencyGraph {
    lateinit var applicationContext: Context

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    // ─── Sub-graphs ─────────────────────────────────────────────

    val infra by lazy { InfrastructureGraph() }
    val storage by lazy { com.halo.host.android.AndroidStorage(applicationContext) }
    val providerGraph by lazy { ProviderGraph(applicationContext, storage) }
    val hostGraph by lazy { HostGraph(applicationContext, infra, providerGraph, storage) }
    val engineGraph by lazy { EngineGraph(applicationContext, infra, providerGraph) }
    val observerGraph by lazy { ObserverGraph(applicationContext, infra, engineGraph) }

    // ─── Convenience accessors (preserve existing API) ──────────

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

    val wearableHost by lazy {
        android.util.Log.d("ManualDependencyGraph", "Creating wearableHost")
        hostGraph.wearableHost
    }
    val audioPipeline get() = hostGraph.audioPipeline
    val audioPerceptionEngine get() = hostGraph.audioPerceptionEngine
    val heyCyanDriver get() = HostFactory.getHeyCyanDriver(applicationContext)

    val goalManager get() = engineGraph.goalManager
    val capabilityRegistry get() = engineGraph.capabilityRegistry
    val executionScheduler get() = engineGraph.executionScheduler
    val executionEngine get() = engineGraph.executionEngine
    val planningEngine get() = engineGraph.planningEngine
    val interpretationEngine get() = engineGraph.interpretationEngine
    val reasoningEngine get() = engineGraph.reasoningEngine
    val aiEngine get() = engineGraph.aiEngine
    val aiProviderRouter get() = engineGraph.aiProviderRouter
    val reflectionEngine get() = engineGraph.reflectionEngine
    val runtimeOrchestrator get() = engineGraph.runtimeOrchestrator
    val aiHandoffOrchestrator by lazy {
        com.halo.app.runtime.AiHandoffOrchestrator(
            infra.eventBus,
            aiProviderRouter,
            infra.scope
        )
    }
    val speechSessionManager get() = engineGraph.speechSessionManager
    val speechPerceptionBridge get() = engineGraph.speechPerceptionBridge

    val cognitiveSession by lazy {
        com.halo.runtime.cognition.session.CognitiveSession(
            eventBus,
            blackboard,
            aiProviderRouter,
            infra.scope
        )
    }

    val processManager get() = observerGraph.processManager
    val timelineEventMapper get() = observerGraph.timelineEventMapper
    val timelineRecorder get() = observerGraph.timelineRecorder
    val runtimeValidator get() = observerGraph.runtimeValidator
    val traceRecorder get() = observerGraph.traceRecorder
    val healthEvaluator get() = observerGraph.healthEvaluator
    val snapshotAssembler get() = observerGraph.snapshotAssembler
    val runtimeReplayRecorder get() = observerGraph.runtimeReplayRecorder
    val runtimeReplayPlayer get() = observerGraph.runtimeReplayPlayer
    val runtimeSnapshotManager get() = observerGraph.runtimeSnapshotManager

    // ─── Processes ──────────────────────────────────────────────

    val androidWakeWordProcess by lazy {
        AndroidWakeWordProcess(
            applicationContext, perceptionBus, eventBus,
            id = "driver.android.wakeword", name = "Android Wake Word",
            scope = infra.scope
        )
    }

    val workingMemoryProcess by lazy {
        WorkingMemoryProcess(perceptionBus, cognitiveBus, executionBus)
    }

    // ─── ViewModels ─────────────────────────────────────────────

    val homeViewModel by lazy { 
        android.util.Log.d("ManualDependencyGraph", "Creating HomeViewModel")
        HomeViewModel(runtimeSnapshotManager, commandBus, cognitiveSession) 
    }
    val consoleViewModel by lazy { 
        android.util.Log.d("ManualDependencyGraph", "Creating ConsoleViewModel")
        ConsoleViewModel(runtimeSnapshotManager, eventBus, blackboard) 
    }
    val settingsViewModel by lazy { 
        android.util.Log.d("ManualDependencyGraph", "Creating SettingsViewModel")
        SettingsViewModel(runtimeSnapshotManager, aiProviderRouter, storage) 
    }
    val activityViewModel by lazy { 
        android.util.Log.d("ManualDependencyGraph", "Creating ActivityViewModel")
        ActivityViewModel(runtimeSnapshotManager) 
    }
    val devicesViewModel by lazy { 
        android.util.Log.d("ManualDependencyGraph", "Creating DevicesViewModel")
        DevicesViewModel(runtimeSnapshotManager) 
    }
    val protocolLabViewModel by lazy { 
        android.util.Log.d("ManualDependencyGraph", "Creating ProtocolLabViewModel")
        ProtocolLabViewModel(heyCyanDriver) 
    }
    val pairingViewModel by lazy { 
        android.util.Log.d("ManualDependencyGraph", "Creating PairingViewModel")
        com.halo.app.ui.screens.discovery.PairingViewModel(heyCyanDriver) 
    }
    val intelligenceViewModel by lazy {
        android.util.Log.d("ManualDependencyGraph", "Creating IntelligenceViewModel")
        com.halo.app.ui.screens.settings.IntelligenceViewModel(
            runtimeSnapshotManager,
            aiProviderRouter,
            cognitiveSession,
            storage
        )
    }
    val runtimeInspectorViewModel by lazy { 
        android.util.Log.d("ManualDependencyGraph", "Creating RuntimeInspectorViewModel")
        RuntimeInspectorViewModel(blackboard, runtimeSnapshotManager) 
    }

    // ─── Kernel ─────────────────────────────────────────────────

    val microkernel by lazy {
        processManager.register(androidWakeWordProcess)
        processManager.register(workingMemoryProcess)
        processManager.register(
            com.halo.host.android.AudioPerceptionProcess(perceptionBus)
        )
        processManager.register(
            com.halo.engines.interpretation.RuleInterpretationProcess(perceptionBus, cognitiveBus)
        )
        processManager.register(
            com.halo.engines.agents.ReasoningServiceProcess(perceptionBus, cognitiveBus, providerGraph.providers.inference)
        )

        speechSessionManager.start()
        runtimeReplayRecorder.startRecording()

        HaloMicrokernel(
            infra.scope,
            processManager,
            perceptionBus,
            cognitiveBus,
            executionBus
        )
    }

    val engineContext by lazy {
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