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
import com.halo.engines.execution.capability.LocomotionCapability
import com.halo.engines.execution.capability.CameraCapability
import com.halo.engines.execution.capability.SpeechOutputRouter
import com.halo.sdk.runtime.embodiment.BuddyBotGateway
import com.halo.sdk.runtime.embodiment.HeyCyanGateway
import com.halo.app.capability.HeyCyanCapabilityBridge
import com.halo.runtime.speech.SpeechPerceptionBridge
import com.halo.runtime.speech.SpeechSessionManager
import com.halo.drivers.heycyan.capability.CapabilityRequest
import com.halo.drivers.heycyan.capability.HeyCyanCapabilityIds

/**
 * Owns all cognitive engines: interpretation, reasoning, planning, execution, reflection.
 * Also owns the RuntimeOrchestrator that coordinates the Alpha Loop.
 */
class EngineGraph(
    private val applicationContext: Context,
    private val infra: InfrastructureGraph,
    private val providerGraph: ProviderGraph,
    private val deviceSessionManager: com.halo.sdk.runtime.embodiment.DeviceSessionManager,
    private val heyCyanDriver: com.halo.drivers.heycyan.RealHeyCyanDriver
) {
    val goalManager by lazy { GoalManager() }

    val capabilityRegistry by lazy {
        CapabilityRegistry().apply {
            val buddyBotGateway = BuddyBotGateway(infra.eventBus, deviceSessionManager)
            val heyCyanGateway = HeyCyanGateway(infra.eventBus, deviceSessionManager)

            register(LocomotionCapability(buddyBotGateway))
            register(CameraCapability(heyCyanGateway))
            register(AndroidApplicationCapability(applicationContext))
            register(AndroidMediaCapability(applicationContext))
            register(AndroidNavigationCapability(applicationContext))
            register(
                com.halo.engines.execution.capability.SpeechCapability(
                    ttsProvider = providerGraph.providers.speechSynthesis,
                    outputRouter = SpeechOutputRouter {
                        heyCyanDriver.capabilityProvider.execute(
                            HeyCyanCapabilityIds.AUDIO_SPEAKER_OUTPUT,
                            CapabilityRequest.Empty,
                        ).let { outcome ->
                            when (outcome) {
                                com.halo.drivers.heycyan.capability.CapabilityOutcome.Success -> Result.success(Unit)
                                is com.halo.drivers.heycyan.capability.CapabilityOutcome.Failure -> Result.failure(
                                    IllegalStateException(outcome.message)
                                )
                            }
                        }
                    }
                )
            )
            register(
                HeyCyanCapabilityBridge(
                    heyCyanDriver.capabilityProvider,
                    heyCyanDriver.deviceState
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

    /** Cognitive inference sees only the typed inference registry; handoff providers cannot enter execution routing. */
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
