package com.halo.app.di

import android.content.Context
import com.halo.engines.memory.WorkingMemoryProcess
import com.halo.host.android.AndroidWakeWordProcess
import com.halo.runtime.kernel.HaloMicrokernel
import com.halo.app.ui.screens.home.HomeViewModel
import com.halo.app.ui.screens.timeline.TimelineViewModel
import com.halo.app.ui.screens.console.ConsoleViewModel
import com.halo.app.ui.screens.settings.SettingsViewModel
import com.halo.app.ui.screens.activity.ActivityViewModel
import com.halo.app.ui.screens.devices.DevicesViewModel

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
    val providerGraph by lazy { ProviderGraph(applicationContext) }
    val hostGraph by lazy { HostGraph(applicationContext, infra, providerGraph) }
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

    val wearableHost get() = hostGraph.wearableHost
    val audioPipeline get() = hostGraph.audioPipeline
    val audioPerceptionEngine get() = hostGraph.audioPerceptionEngine

    val goalManager get() = engineGraph.goalManager
    val capabilityRegistry get() = engineGraph.capabilityRegistry
    val executionScheduler get() = engineGraph.executionScheduler
    val executionEngine get() = engineGraph.executionEngine
    val planningEngine get() = engineGraph.planningEngine
    val interpretationEngine get() = engineGraph.interpretationEngine
    val reasoningEngine get() = engineGraph.reasoningEngine
    val reflectionEngine get() = engineGraph.reflectionEngine
    val runtimeOrchestrator get() = engineGraph.runtimeOrchestrator
    val speechSessionManager get() = engineGraph.speechSessionManager
    val speechPerceptionBridge get() = engineGraph.speechPerceptionBridge

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

    val homeViewModel by lazy { HomeViewModel(runtimeSnapshotManager, commandBus) }
    val timelineViewModel by lazy { TimelineViewModel(cognitiveBus) }
    val consoleViewModel by lazy { ConsoleViewModel(runtimeSnapshotManager) }
    val settingsViewModel by lazy { SettingsViewModel() }
    val activityViewModel by lazy { ActivityViewModel() }
    val devicesViewModel by lazy { DevicesViewModel() }

    // ─── Kernel ─────────────────────────────────────────────────

    val microkernel by lazy {
        processManager.register(androidWakeWordProcess)
        speechSessionManager.start()
        runtimeReplayRecorder.startRecording()
        processManager.register(workingMemoryProcess)

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