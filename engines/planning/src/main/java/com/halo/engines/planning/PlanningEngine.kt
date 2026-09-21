package com.halo.engines.planning

import com.halo.icl.ontology.CommunicationPayload
import com.halo.icl.ontology.ConversationPayload
import com.halo.icl.ontology.DevicePayload
import com.halo.icl.ontology.Goal
import com.halo.icl.ontology.GoalType
import com.halo.icl.ontology.MemoryPayload
import com.halo.icl.ontology.MediaPayload
import com.halo.icl.ontology.NavigationPayload
import com.halo.icl.ontology.SearchPayload
import com.halo.runtime.cognition.ApplicationTask
import com.halo.runtime.cognition.CommunicationTask
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.ExecutionPlan
import com.halo.runtime.cognition.MediaTask
import com.halo.runtime.cognition.MemoryTask
import com.halo.runtime.cognition.NavigationTask
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.WebTask
import com.halo.sdk.runtime.*
import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.runtime.cognition.GoalCreated
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Deterministic Goal -> ExecutionPlan compiler.
 *
 * Planning does not execute anything.
 *
 * Responsibilities:
 *   Goal -> one or more typed runtime Tasks
 *
 * It must never:
 *   - call Android APIs
 *   - invoke capabilities
 *   - call an LLM
 *   - publish execution results
 */
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.runtime.cognition.blackboard.Knowledge
import com.halo.runtime.cognition.blackboard.KnowledgeId
import com.halo.runtime.cognition.blackboard.KnowledgeSource
import com.halo.runtime.cognition.blackboard.PlanKnowledge
import kotlinx.datetime.Clock

class PlanningEngine(
    private val eventBus: EventBus,
    private val blackboard: Blackboard
) : Engine {

    override val metadata = EngineMetadata(
        id = Identifier("engine.planning.core"),
        version = Version(1, 0, 0),
        description = "Core Deterministic Planning Engine"
    )

    private val _health = MutableStateFlow(EngineHealth.Healthy)
    override val health: StateFlow<EngineHealth> = _health

    private val _state = MutableStateFlow<EngineState>(EngineState.Created)
    override val state: StateFlow<EngineState> = _state

    override val dependencies: Set<ServiceKey<*>> = emptySet()

    private var context: EngineContext? = null
    private var job: Job? = null

    override suspend fun initialize(context: EngineContext) {
        this.context = context
        _state.value = EngineState.Initializing
    }

    override suspend fun start() {
        if (job?.isActive == true) return

        val runtimeContext = requireNotNull(context)
        _state.value = EngineState.Running

        job = CoroutineScope(runtimeContext.runtime.scheduler.compute).launch {
            eventBus.observe(GoalCreated::class.java).collect { event ->
                try {
                    val plan = createPlan(
                        goal = event.goal,
                        responseDelivery = event.responseDelivery
                    )

                    blackboard.publish(
                        Knowledge(
                            id = KnowledgeId(plan.id.toString()),
                            payload = PlanKnowledge(plan),
                            confidence = com.halo.runtime.cognition.blackboard.Confidence(1.0f, 1.0f, 1.0f),
                            source = KnowledgeSource.RULE,
                            createdAt = Clock.System.now()
                        )
                    )
                    eventBus.publish(
                        PlanGenerated(
                            id = java.util.UUID.randomUUID().toString(),
                            planId = plan.id.toString()
                        )
                    )
                } catch (e: Exception) {
                    runtimeContext.runtime.logger.error("planning", "Failed to generate plan for goal", e)
                }
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
            _state.value = EngineState.Disposed
        }
    }

    fun createPlan(
        goal: Goal,
        responseDelivery: ResponseDelivery = ResponseDelivery.VISUAL_ONLY
    ): ExecutionPlan {
        val task = createTask(goal)

        return ExecutionPlan(
            goal = goal,
            tasks = listOf(task),
            responseDelivery = responseDelivery
        )
    }

    private fun createTask(goal: Goal): Task {
        return when (goal.type) {
            GoalType.CONVERSATION -> {
                val payload = goal.payload as? ConversationPayload
                    ?: return unsupportedGoal(goal)

                CommunicationTask(
                    targetId = "user",
                    payload = payload.topic.orEmpty()
                )
            }

            GoalType.COMMUNICATION -> {
                val payload = goal.payload as? CommunicationPayload
                    ?: return unsupportedGoal(goal)

                CommunicationTask(
                    targetId = payload.target,
                    payload = payload.message.orEmpty()
                )
            }

            GoalType.NAVIGATION -> {
                val payload = goal.payload as? NavigationPayload
                    ?: return unsupportedGoal(goal)

                NavigationTask(
                    coordinates = payload.destination
                )
            }

            GoalType.MEDIA -> {
                val payload = goal.payload as? MediaPayload
                    ?: return unsupportedGoal(goal)

                MediaTask(
                    mediaAction = payload.action,
                    mediaId = payload.query
                )
            }

            GoalType.DEVICE -> {
                val payload = goal.payload as? DevicePayload
                    ?: return unsupportedGoal(goal)

                DeviceTask(
                    action = payload.action,
                    parameters = if (payload.target.isBlank()) emptyMap()
                    else mapOf("target" to payload.target)
                )
            }

            GoalType.MEMORY -> {
                val payload = goal.payload as? MemoryPayload
                    ?: return unsupportedGoal(goal)

                MemoryTask(
                    memoryAction = payload.fact
                )
            }

            GoalType.SEARCH -> {
                val payload = goal.payload as? SearchPayload
                    ?: return unsupportedGoal(goal)

                WebTask(
                    url = payload.query
                )
            }

            GoalType.AUTOMATION -> unsupportedGoal(goal)
        }
    }

    private fun unsupportedGoal(goal: Goal): Nothing {
        throw IllegalArgumentException("Unsupported goal type or missing payload: ${goal.type}")
    }
}
