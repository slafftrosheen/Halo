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

import com.halo.host.android.AndroidExecutionProcess
import com.halo.host.android.AndroidSpeechProcess
import com.halo.host.android.TextToSpeechProcess
import com.halo.engines.speech.SpeechRecognitionProcess
import com.halo.engines.interpretation.RuleInterpretationProcess
import com.halo.engines.agents.PlannerServiceProcess
import com.halo.engines.memory.WorkingMemoryProcess

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

    val processManager by lazy { ProcessManager(scope) }

    val cognitiveTrace by lazy {
        CognitiveTrace(scope, perceptionBus, cognitiveBus, executionBus).apply { start() }
    }

    val androidSpeechProcess by lazy { AndroidSpeechProcess(applicationContext, perceptionBus) }
    val workingMemoryProcess by lazy { WorkingMemoryProcess(perceptionBus, cognitiveBus, executionBus) }

    val microkernel by lazy {
        // Register new alpha MVP processes
        processManager.register(androidSpeechProcess)
        processManager.register(RuleInterpretationProcess(perceptionBus, cognitiveBus))
        processManager.register(PlannerServiceProcess(cognitiveBus, executionBus))
        processManager.register(workingMemoryProcess)
        processManager.register(AndroidExecutionProcess(executionBus, applicationContext))
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
