package com.halo.app.di

import com.halo.libraries.logging.DefaultHaloLogger
import com.halo.libraries.testing.FakeDriver
import com.halo.libraries.testing.FakeProvider
import com.halo.libraries.testing.FakeRuntimeContext
import com.halo.runtime.commands.DefaultCommandBus
import com.halo.runtime.events.DefaultEventBus
import com.halo.runtime.kernel.HaloRuntime
import com.halo.runtime.queries.DefaultQueryBus
import com.halo.runtime.registry.DefaultServiceRegistry
import com.halo.runtime.scheduler.DefaultScheduler
import com.halo.engines.diagnostics.DiagnosticsEngine
import com.halo.engines.memory.DefaultMemoryEngine
import com.halo.runtime.intents.IntentEngine
import com.halo.runtime.plugins.PluginManager
import com.halo.runtime.plugins.PluginRegistry
import com.halo.runtime.plugins.PluginContext
import com.halo.app.devtools.RecordingEventBus
import com.halo.runtime.cognition.WorkingMemory
import com.halo.sdk.runtime.RuntimeClock
import com.halo.runtime.plugins.PermissionManager

// speech imports
import com.halo.engines.speech.AudioPerceptionEngine
import com.halo.engines.speech.EnergyVoiceActivityDetector
import com.halo.engines.speech.DefaultAudioPreprocessor
import com.halo.engines.speech.MockSpeechRecognizer
import com.halo.host.android.audio.AndroidAudioSource

object ManualDependencyGraph {
    val eventBus by lazy { RecordingEventBus(DefaultEventBus()) }
    val commandBus by lazy { DefaultCommandBus() }
    val queryBus by lazy { DefaultQueryBus() }
    val scheduler by lazy { DefaultScheduler() }
    val registry by lazy {
        val r = DefaultServiceRegistry()
        r.register(com.halo.runtime.services.Services.EventBus, eventBus)
        r.register(com.halo.runtime.services.Services.CommandBus, commandBus)
        r.register(com.halo.runtime.services.Services.Driver, driver)
        r.register(com.halo.runtime.services.Services.Provider, provider)
        r
    }
    val logger by lazy { DefaultHaloLogger() }
    val context by lazy {
        FakeRuntimeContext(
            eventBus = eventBus,
            commandBus = commandBus,
            queryBus = queryBus,
            scheduler = scheduler,
            registry = registry,
            logger = logger
        )
    }
    val driver by lazy { FakeDriver("heycyan.mock") }
    val provider by lazy { FakeProvider("llamacpp.mock") }

    val aiEngine by lazy { com.halo.engines.ai.AIEngine() }
    val memoryEngine by lazy { DefaultMemoryEngine() }
    val intentEngine by lazy { IntentEngine() }
    val diagnosticsEngine by lazy { DiagnosticsEngine() }
    
    val reasoningEngine by lazy { com.halo.engines.reasoning.ReasoningEngine() }
    val interpretationEngine by lazy { com.halo.engines.interpretation.InterpretationEngine() }

    val audioPerceptionEngine: com.halo.sdk.runtime.Engine by lazy {
        AudioPerceptionEngine(
            AndroidAudioSource(),
            EnergyVoiceActivityDetector(),
            DefaultAudioPreprocessor(),
            MockSpeechRecognizer()
        )
    }

    val runtime by lazy {
        val pluginRegistry = PluginRegistry()
        val pluginContext = PluginContext(
            runtime = context,
            scheduler = scheduler,
            logger = logger,
            eventBus = eventBus,
            commandBus = commandBus,
            clock = object : RuntimeClock { override fun now() = java.time.Instant.now(); override fun millis() = System.currentTimeMillis() },
            workingMemory = WorkingMemory(),
            permissionManager = object : PermissionManager { override fun hasPermission(p: String) = true }
        )
        val pluginManager = PluginManager(pluginRegistry, pluginContext)
        registry.register(com.halo.runtime.services.Services.PluginManager, pluginManager)

        HaloRuntime(
            engines = listOf(
                aiEngine, memoryEngine, intentEngine, diagnosticsEngine, audioPerceptionEngine, interpretationEngine, reasoningEngine
            ),
            runtimeContext = context
        )
    }
    val runtimeMonitor by lazy {
        com.halo.app.devtools.RuntimeMonitor(
            runtime = runtime,
            engines = listOf(
                aiEngine, memoryEngine, intentEngine, diagnosticsEngine, audioPerceptionEngine, interpretationEngine, reasoningEngine
            ),
            eventBus = eventBus
        ).apply { start() }
    }
}
