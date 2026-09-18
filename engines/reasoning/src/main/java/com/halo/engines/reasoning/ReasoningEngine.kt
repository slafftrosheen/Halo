package com.halo.engines.reasoning

import com.halo.cognition.language.ConversationIntent
import com.halo.cognition.language.QuestionIntent
import com.halo.engines.reasoning.strategy.DefaultReasoningStrategy
import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.runtime.cognition.GoalCreated
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.runtime.cognition.blackboard.InterpretationKnowledge
import com.halo.sdk.runtime.Engine
import com.halo.sdk.runtime.EngineContext
import com.halo.sdk.runtime.EngineHealth
import com.halo.sdk.runtime.EngineMetadata
import com.halo.sdk.runtime.EngineState
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.ServiceKey
import com.halo.sdk.runtime.defaultResponseDelivery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Converts actionable Blackboard InterpretationKnowledge into GoalCreated events.
 *
 * Conversation and question intents are owned by CognitiveSession. They must not
 * also enter deterministic planning/execution, otherwise one utterance can create
 * both an LLM response and a bogus executable CommunicationTask.
 */
class ReasoningEngine(
    private val strategy: DefaultReasoningStrategy,
    private val blackboard: Blackboard,
    private val eventBus: EventBus
) : Engine {

    override val metadata = EngineMetadata(
        id = Identifier("engine.reasoning.core"),
        version = Version(1, 0, 0),
        description = "Core deterministic reasoning engine"
    )

    private val _health = MutableStateFlow(EngineHealth.Healthy)
    override val health: StateFlow<EngineHealth> = _health

    private val _state = MutableStateFlow<EngineState>(EngineState.Created)
    override val state: StateFlow<EngineState> = _state

    override val dependencies: Set<ServiceKey<*>> = emptySet()

    private var context: EngineContext? = null
    private var job: Job? = null
    private val processedKnowledgeIds = mutableSetOf<String>()

    override suspend fun initialize(context: EngineContext) {
        check(_state.value != EngineState.Disposed)
        this.context = context
        _state.value = EngineState.Initializing
        _health.value = EngineHealth.Healthy
    }

    override suspend fun start() {
        check(context != null) { "ReasoningEngine must be initialized before start" }
        if (job?.isActive == true) return

        val runtimeContext = requireNotNull(context)
        _state.value = EngineState.Running

        job = kotlinx.coroutines.CoroutineScope(runtimeContext.runtime.scheduler.compute).launch {
            try {
                blackboard.stream()
                    .filter { it.payload is InterpretationKnowledge }
                    .collect { knowledge ->
                        val knowledgeId = knowledge.id.value
                        if (!processedKnowledgeIds.add(knowledgeId)) return@collect

                        val interpretation =
                            (knowledge.payload as InterpretationKnowledge).interpretation

                        if (interpretation.intent is ConversationIntent ||
                            interpretation.intent is QuestionIntent
                        ) {
                            runtimeContext.runtime.logger.debug(
                                category = "reasoning",
                                message = "Conversation knowledge $knowledgeId delegated to CognitiveSession"
                            )
                            return@collect
                        }

                        try {
                            val goal = strategy.generateGoal(
                                interpretation = interpretation,
                                parentKnowledgeId = knowledgeId
                            )

                            println(
                                "[HALO-TXN] reasoning knowledgeId=$knowledgeId " +
                                    "intent=${interpretation.intent::class.simpleName} " +
                                    "goal=${goal.type} payload=${goal.payload}"
                            )

                            eventBus.publish(
                                GoalCreated(
                                    goal = goal,
                                    responseDelivery = interpretation.source.defaultResponseDelivery()
                                )
                            )
                            _health.value = EngineHealth.Healthy

                            runtimeContext.runtime.logger.debug(
                                category = "reasoning",
                                message = "Goal ${goal.id.value} created from knowledge $knowledgeId"
                            )
                        } catch (exception: Exception) {
                            _health.value = EngineHealth.Degraded
                            runtimeContext.runtime.logger.error(
                                category = "reasoning",
                                message = "Failed to reason from knowledge $knowledgeId",
                                throwable = exception
                            )
                        }
                    }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _health.value = EngineHealth.Failed
                _state.value = EngineState.Failed(
                    exception.message ?: "Reasoning stream failed"
                )
            }
        }
    }

    override suspend fun stop() {
        withContext(NonCancellable) {
            job?.cancel()
            job = null
            if (_state.value != EngineState.Disposed) {
                _state.value = EngineState.Stopped
            }
        }
    }

    override suspend fun dispose() {
        withContext(NonCancellable) {
            job?.cancel()
            job = null
            context = null
            processedKnowledgeIds.clear()
            _health.value = EngineHealth.Offline
            _state.value = EngineState.Disposed
        }
    }
}
