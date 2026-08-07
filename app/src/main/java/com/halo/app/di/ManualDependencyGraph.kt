package com.halo.app.di

import android.content.Context
import com.halo.libraries.logging.DefaultHaloLogger
import com.halo.runtime.commands.DefaultCommandBus
import com.halo.runtime.events.DefaultEventBus
import com.halo.runtime.kernel.HaloMicrokernel
import com.halo.runtime.kernel.ProcessManager
import com.halo.runtime.kernel.bus.PerceptionBus
import com.halo.runtime.kernel.bus.CognitiveBus
import com.halo.runtime.kernel.bus.ExecutionBus
import com.halo.runtime.queries.DefaultQueryBus
import com.halo.runtime.registry.DefaultServiceRegistry
import com.halo.runtime.scheduler.DefaultScheduler
import com.halo.app.devtools.RecordingEventBus
import com.halo.app.devtools.CognitiveTrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

import com.halo.host.android.AndroidSpeechProcess
import com.halo.host.android.TextToSpeechProcess
import com.halo.host.android.AndroidWakeWordProcess
import com.halo.engines.interpretation.InterpretationEngine
import com.halo.engines.agents.RuntimeOrchestrator

import com.halo.engines.memory.WorkingMemoryProcess
import com.halo.engines.memory.ReflectionEngine
import com.halo.engines.memory.store.WorkingMemoryStore
import com.halo.engines.memory.store.EpisodicMemoryStore
import com.halo.engines.reasoning.service.GoalManager
import com.halo.engines.planning.PlanningEngine
import com.halo.engines.execution.ExecutionEngine
import com.halo.engines.execution.service.ExecutionScheduler
import com.halo.engines.execution.service.CapabilityRegistry
import com.halo.host.android.capability.AndroidDeviceCapability
import com.halo.host.android.capability.AndroidApplicationCapability
import com.halo.host.android.capability.AndroidMediaCapability
import com.halo.host.android.capability.AndroidNavigationCapability
import com.halo.engines.reasoning.models.ModelDownloadManager
import com.halo.engines.reasoning.providers.NativeLlamaProvider
import com.halo.runtime.observer.DefaultRuntimeObserver
import com.halo.runtime.observer.TimelineRecorder
import com.halo.app.ui.screens.home.HomeViewModel
import com.halo.app.ui.screens.timeline.TimelineViewModel
import com.halo.app.ui.screens.console.ConsoleViewModel
import com.halo.app.ui.screens.settings.SettingsViewModel
import com.halo.runtime.speech.SpeechSessionManager
import com.halo.engines.speech.MockSpeechRecognizer

object ManualDependencyGraph {
    lateinit var applicationContext: Context
    
    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val eventBus by lazy { RecordingEventBus(DefaultEventBus()) }
    val commandBus by lazy { DefaultCommandBus() }
    val queryBus by lazy { DefaultQueryBus() }
    val scheduler by lazy { DefaultScheduler() }
    val registry by lazy { DefaultServiceRegistry() }
    val logger by lazy { DefaultHaloLogger() }

    val perceptionBus by lazy { com.halo.runtime.kernel.bus.DefaultPerceptionBus() }
    val cognitiveBus by lazy { com.halo.runtime.kernel.bus.DefaultCognitiveBus() }
    val executionBus by lazy { com.halo.runtime.kernel.bus.DefaultExecutionBus() }
    val blackboard by lazy { com.halo.runtime.cognition.blackboard.DefaultBlackboard() }

    val processManager by lazy { ProcessManager(scope) }

    val cognitiveTrace by lazy {
        CognitiveTrace(scope, perceptionBus, cognitiveBus, executionBus).apply { start() }
    }

    val androidSpeechProcess by lazy { AndroidSpeechProcess(applicationContext, perceptionBus) }
    val androidWakeWordProcess by lazy { AndroidWakeWordProcess(applicationContext, perceptionBus) }
    val workingMemoryProcess by lazy { WorkingMemoryProcess(perceptionBus, cognitiveBus, executionBus) }
    val modelDownloadManager by lazy { ModelDownloadManager(applicationContext) }
    val nativeLlamaProvider by lazy { NativeLlamaProvider(applicationContext, modelDownloadManager) }

    
    // Alpha Architecture Services
    val goalManager by lazy { GoalManager() }
    
    val capabilityRegistry by lazy { 
        CapabilityRegistry().apply {
            register(AndroidDeviceCapability(applicationContext))
            register(AndroidApplicationCapability(applicationContext))
            register(AndroidMediaCapability(applicationContext))
            register(AndroidNavigationCapability(applicationContext))
        }
    }
    
    val executionScheduler by lazy { ExecutionScheduler(capabilityRegistry) }
    val executionEngine by lazy { ExecutionEngine(executionScheduler) }
    val planningEngine by lazy { PlanningEngine() }
    val interpretationEngine by lazy { InterpretationEngine(blackboard) }
    val reflectionEngine by lazy { ReflectionEngine(EpisodicMemoryStore()) }

    val runtimeOrchestrator by lazy {
        RuntimeOrchestrator(
            interpretationEngine,
            goalManager,
            planningEngine,
            executionEngine,
            reflectionEngine
        )
    }
    
    val speechSessionManager by lazy { SpeechSessionManager(MockSpeechRecognizer(), eventBus, scope, runtimeOrchestrator) }

    val timelineRecorder by lazy { TimelineRecorder(eventBus, scope) }

    val runtimeSnapshotManager by lazy {
        DefaultRuntimeObserver(
            speechSessionManager,
            processManager,
            blackboard,
            timelineRecorder,
            java.time.Clock.systemUTC(),
            scope
        )
    }

    // ViewModels
    val homeViewModel by lazy { HomeViewModel(runtimeSnapshotManager, commandBus) }
    val timelineViewModel by lazy { TimelineViewModel(cognitiveBus) }
    val consoleViewModel by lazy { ConsoleViewModel(runtimeSnapshotManager) }
    val settingsViewModel by lazy { SettingsViewModel() }

    val microkernel by lazy {
        // Register new alpha MVP processes
        processManager.register(androidSpeechProcess)
        processManager.register(androidWakeWordProcess)
        
        // Start speech session manager
        speechSessionManager.start()
        

        processManager.register(workingMemoryProcess)
        processManager.register(TextToSpeechProcess(applicationContext, executionBus))
        
        HaloMicrokernel(
            scope,
            processManager,
            perceptionBus,
            cognitiveBus,
            executionBus
        )
    }
}
