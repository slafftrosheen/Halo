package com.halo.app.di

import android.content.Context
import com.halo.engines.interpretation.InterpretationEngine
import com.halo.engines.agents.RuntimeOrchestrator
import com.halo.engines.memory.ReflectionEngine
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
import com.halo.runtime.speech.SpeechSessionManager

/**
 * Owns all cognitive engines: interpretation, reasoning, planning, execution, reflection.
 * Also owns the RuntimeOrchestrator that coordinates the Alpha Loop.
 */
class EngineGraph(
    private val applicationContext: Context,
    private val infra: InfrastructureGraph,
    private val providerGraph: ProviderGraph
) {

    val goalManager by lazy { GoalManager() }

    val capabilityRegistry by lazy {
        CapabilityRegistry().apply {
            register(AndroidDeviceCapability(applicationContext))
            register(AndroidApplicationCapability(applicationContext))
            register(AndroidMediaCapability(applicationContext))
            register(AndroidNavigationCapability(applicationContext))
            register(
                com.halo.engines.execution.capability.SpeechCapability(
                    providerGraph.providers.speechSynthesis
                )
            )
        }
    }

    val executionScheduler by lazy { ExecutionScheduler(capabilityRegistry) }
    val executionEngine by lazy { ExecutionEngine(executionScheduler) }
    val planningEngine by lazy { PlanningEngine() }

    val interpretationEngine by lazy {
        InterpretationEngine(infra.blackboard, infra.eventBus)
    }

    val reflectionEngine by lazy { ReflectionEngine(EpisodicMemoryStore()) }

    val runtimeOrchestrator by lazy {
        RuntimeOrchestrator(
            interpretationEngine = interpretationEngine,
            goalManager = goalManager,
            planningEngine = planningEngine,
            executionEngine = executionEngine,
            reflectionEngine = reflectionEngine,
            eventBus = infra.eventBus
        ).apply { start() }
    }

    val speechSessionManager by lazy {
        SpeechSessionManager(infra.eventBus, infra.commandBus, infra.scope)
    }

    val speechPerceptionBridge by lazy {
        com.halo.runtime.speech.SpeechPerceptionBridge(infra.eventBus, infra.scope).apply { start() }
    }
}
