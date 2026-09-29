package com.halo.app.di

import android.content.Context
import com.halo.engines.execution.ExecutionEngine
import com.halo.engines.execution.service.CapabilityRegistry
import com.halo.engines.execution.service.ExecutionScheduler
import com.halo.engines.ai.AIEngine
import com.halo.engines.ai.DefaultAiProviderRouter
import com.halo.engines.interpretation.InterpretationEngine
import com.halo.engines.memory.ReflectionEngine
import com.halo.engines.memory.store.EpisodicMemoryStore
import com.halo.engines.planning.PlanningEngine
import com.halo.engines.reasoning.ReasoningEngine
import com.halo.engines.reasoning.service.GoalManager
import com.halo.engines.reasoning.strategy.DefaultReasoningStrategy
import com.halo.host.android.capability.AndroidApplicationCapability
import com.halo.host.android.capability.AndroidCommunicationCapability
import com.halo.host.android.capability.AndroidDeviceCapability
import com.halo.host.android.capability.AndroidMediaCapability
import com.halo.host.android.capability.AndroidMemoryCapability
import com.halo.host.android.capability.AndroidNavigationCapability
import com.halo.host.android.capability.AndroidWebCapability
import com.halo.runtime.cognition.response.CommandResponseProcess
import com.halo.runtime.speech.SpeechPerceptionBridge
import com.halo.runtime.speech.SpeechSessionManager
import com.halo.sdk.runtime.NativeResourceCoordinator

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
    private val providerGraph: ProviderGraph,
    private val agentRunCoordinator: com.halo.runtime.cognition.agent.AgentRunCoordinator? = null,
    private val clarificationCoordinator: com.halo.runtime.cognition.clarification.ClarificationCoordinator? = null,
    private val agentRunStore: com.halo.runtime.cognition.agent.AgentRunStore? = null,
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
            register(AndroidCommunicationCapability(applicationContext))
            register(AndroidWebCapability(applicationContext))
            register(AndroidDeviceCapability(applicationContext))
            register(AndroidMemoryCapability(applicationContext))

            // Do not register SpeechCapability in the phone-first runtime.
            // User-facing speech output is owned by the single TtsRequested ->
            // AndroidSpeaker path, which provides interruption and lifecycle
            // events. Keeping a second execution capability here would create a
            // competing Android TextToSpeech owner for CommunicationTask.
        }
    }

    val executionScheduler by lazy { ExecutionScheduler(capabilityRegistry) }
    val executionEngine by lazy {
        ExecutionEngine(executionScheduler, infra.eventBus, infra.blackboard, agentRunCoordinator)
    }
    val commandResponseProcess by lazy {
        CommandResponseProcess(
            eventBus = infra.eventBus,
            blackboard = infra.blackboard,
            scope = infra.scope,
            logger = infra.logger,
            conversationStore = ManualDependencyGraph.conversationStore,
            agentRunCoordinator = agentRunCoordinator,
        )
    }
    val planningEngine by lazy { PlanningEngine(infra.eventBus, infra.blackboard) }

    val interpretationEngine by lazy { InterpretationEngine(infra.blackboard, infra.eventBus, clarificationCoordinator = clarificationCoordinator, agentRunCoordinator = agentRunCoordinator, agentRunStore = agentRunStore) }
    val defaultReasoningStrategy by lazy { DefaultReasoningStrategy(goalManager) }
    val reasoningEngine by lazy {
        ReasoningEngine(
            strategy = defaultReasoningStrategy,
            blackboard = infra.blackboard,
            eventBus = infra.eventBus
        )
    }
    val reflectionEngine by lazy { ReflectionEngine(EpisodicMemoryStore()) }

    val speechSessionManager by lazy {
        SpeechSessionManager(infra.eventBus, infra.commandBus, infra.scope)
    }
    val speechPerceptionBridge by lazy { SpeechPerceptionBridge(infra.eventBus, infra.scope) }

    /** Conversational inference sees only the typed inference registry. */
    val aiProviderRouter by lazy {
        DefaultAiProviderRouter(
            providerRegistry = providerGraph.providers.aiRegistry,
            storage = ManualDependencyGraph.storage,
            eventBus = infra.eventBus,
            logger = infra.logger,
            defaultInferenceProviderId = providerGraph.providers.inference.id
        )
    }
    val aiEngine by lazy { AIEngine(aiProviderRouter, ManualDependencyGraph.storage) }
}
