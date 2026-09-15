package com.halo.app.di

import android.content.Context
import com.halo.engines.execution.ExecutionEngine
import com.halo.engines.execution.service.ExecutionScheduler
import com.halo.engines.execution.service.CapabilityRegistry
import com.halo.sdk.provider.AiProviderRouter
import com.halo.engines.ai.AIEngine
import com.halo.engines.ai.DefaultAiProviderRouter
import com.halo.engines.interpretation.InterpretationEngine
import com.halo.engines.memory.store.EpisodicMemoryStore
import com.halo.engines.memory.ReflectionEngine
import com.halo.engines.planning.PlanningEngine
import com.halo.engines.reasoning.ReasoningEngine
import com.halo.engines.reasoning.service.GoalManager
import com.halo.engines.reasoning.strategy.DefaultReasoningStrategy
import com.halo.host.android.capability.AndroidApplicationCapability
import com.halo.host.android.capability.AndroidMediaCapability
import com.halo.host.android.capability.AndroidNavigationCapability
import com.halo.engines.execution.capability.SpeechOutputRouter
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.runtime.speech.SpeechPerceptionBridge
import com.halo.runtime.speech.SpeechSessionManager

/**
 * Owns the cognitive engines used by the Android host.
 *
 * Physical-device capabilities are deliberately outside this graph's startup
 * path. They are attached by the Devices flow when a peripheral is explicitly
 * connected.
 */
class EngineGraph(
    private val applicationContext: Context,
    private val infra: InfrastructureGraph,
    private val providerGraph: ProviderGraph
) {
    /** Shared arbitration boundary used by heavyweight native inference providers. */
    val nativeResourceCoordinator: NativeResourceCoordinator
        get() = providerGraph.nativeResourceCoordinator

    val goalManager by lazy { GoalManager() }

    /** Android-only capability set for the phone-first product. */
    val capabilityRegistry by lazy {
        CapabilityRegistry().apply {
            register(AndroidApplicationCapability(applicationContext))
            register(AndroidMediaCapability(applicationContext))
            register(AndroidNavigationCapability(applicationContext))
            register(
                com.halo.engines.execution.capability.SpeechCapability(
                    ttsProvider = providerGraph.providers.speechSynthesis,
                    outputRouter = SpeechOutputRouter { Result.success(Unit) }
                )
            )
        }
    }

    val executionScheduler by lazy { ExecutionScheduler(capabilityRegistry) }
    val executionEngine by lazy { ExecutionEngine(executionScheduler, infra.eventBus, infra.blackboard) }
    val planningEngine by lazy { PlanningEngine(infra.eventBus, infra.blackboard) }

    val interpretationEngine by lazy { InterpretationEngine(infra.blackboard, infra.eventBus) }
    val defaultReasoningStrategy by lazy { DefaultReasoningStrategy(goalManager) }
    val reasoningStrategy by lazy {
        com.halo.engines.reasoning.strategy.LlmReasoningStrategy(aiProviderRouter, goalManager, defaultReasoningStrategy)
    }
    val reasoningEngine by lazy {
        ReasoningEngine(strategy = reasoningStrategy, blackboard = infra.blackboard, eventBus = infra.eventBus)
    }
    val reflectionEngine by lazy { ReflectionEngine(EpisodicMemoryStore()) }

    val speechSessionManager by lazy { SpeechSessionManager(infra.eventBus, infra.commandBus, infra.scope) }
    val speechPerceptionBridge by lazy { SpeechPerceptionBridge(infra.eventBus, infra.scope) }

    /** Cognitive inference sees only the typed inference registry. */
    val aiProviderRouter by lazy {
        DefaultAiProviderRouter(
            providerRegistry = providerGraph.providers.aiRegistry,
            storage = ManualDependencyGraph.storage,
            eventBus = infra.eventBus,
            logger = infra.logger
        )
    }
    val aiEngine by lazy { AIEngine(aiProviderRouter, ManualDependencyGraph.storage) }
}
