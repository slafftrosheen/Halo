package com.halo.engines.execution

import com.halo.runtime.cognition.agent.*
import com.halo.cognition.language.InteractionId
import kotlinx.coroutines.CancellationException
import java.time.Instant
import com.halo.engines.execution.service.ExecutionScheduler
import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.runtime.cognition.ExecutionContext
import com.halo.runtime.cognition.ExecutionPlan
import com.halo.runtime.cognition.ExecutionResultPublished
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.OutcomePublished
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.runtime.cognition.blackboard.PlanKnowledge
import com.halo.sdk.runtime.Engine
import com.halo.sdk.runtime.EngineContext
import com.halo.sdk.runtime.EngineHealth
import com.halo.sdk.runtime.EngineMetadata
import com.halo.sdk.runtime.EngineState
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.ExecutionFinished
import com.halo.sdk.runtime.ExecutionStarted
import com.halo.sdk.runtime.PlanGenerated
import com.halo.sdk.runtime.ServiceKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Single deterministic driver for physical and digital actions.
 *
 * All execution passes through this engine by matching a [Task] to a registered
 * capability in [ExecutionScheduler]. Platform implementations remain outside
 * the cognition/runtime layer.
 */
class ExecutionEngine(
    private val scheduler: ExecutionScheduler,
    private val eventBus: EventBus,
    private val blackboard: Blackboard,
    private val agentRunCoordinator: AgentRunCoordinator? = null,
) : Engine {

    override val metadata = EngineMetadata(
        id = Identifier("engine.execution.core"),
        version = Version(1, 0, 0),
        description = "Core Deterministic Execution Engine"
    )

    private val _health = MutableStateFlow(EngineHealth.Healthy)
    override val health: StateFlow<EngineHealth> = _health

    private val _state = MutableStateFlow<EngineState>(EngineState.Created)
    override val state: StateFlow<EngineState> = _state

    override val dependencies: Set<ServiceKey<*>> = emptySet()

    private var context: EngineContext? = null
    private var job: Job? = null
    private val executionMutex = Mutex()

    override suspend fun initialize(context: EngineContext) {
        this.context = context
        _state.value = EngineState.Initializing
    }

    override suspend fun start() {
        if (job?.isActive == true) return

        val runtimeContext = requireNotNull(context)
        _state.value = EngineState.Running

        job = CoroutineScope(runtimeContext.runtime.scheduler.io).launch {
            eventBus.observe(PlanGenerated::class.java).collect { event ->
                val plan = try {
                    val planKnowledge = blackboard.flow.value.firstOrNull { it.id.value == event.planId && it.payload is PlanKnowledge }
                    (planKnowledge?.payload as? PlanKnowledge)?.plan
                } catch (error: Exception) {
                    runtimeContext.runtime.logger.error("execution", "Failed to resolve generated plan", error)
                    null
                }

                if (plan == null) return@collect

                executionMutex.withLock {
                    val executionId = UUID.randomUUID().toString()
                    eventBus.publish(ExecutionStarted(id = executionId, planId = event.planId))

                    try {
                        val outcome = executePlan(plan, executionId)
                        eventBus.publish(
                            OutcomePublished(outcome = outcome::class.simpleName ?: "Unknown")
                        )
                    } finally {
                        // ExecutionFinished is the aggregation boundary used by presentation.
                        // It must always correlate with the exact ExecutionStarted id.
                        eventBus.publish(ExecutionFinished(id = executionId, planId = event.planId))
                    }
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

    suspend fun executePlan(
        plan: ExecutionPlan,
        executionId: String = UUID.randomUUID().toString()
    ): Outcome {
        val coordinator = agentRunCoordinator
        val runId = plan.agentRunId?.let(::AgentRunId) ?: coordinator?.start(
            AgentRequest(InteractionId(plan.interactionId ?: UUID.randomUUID().toString()), plan.userRequest ?: "Deterministic action",
                speechOutputTarget = plan.speechOutputTarget, speechOutputFallback = plan.speechOutputFallback,
                sourceCaptureId = plan.sourceCaptureId))?.also {
                coordinator?.transition(it, AgentRunState.PLANNING)
            }
        if (runId != null && coordinator != null) {
            coordinator.recordStep(AgentStep.PlanCreated(AgentStepId(UUID.randomUUID().toString()), runId,
                Instant.now(), plan.id.toString(), "Execute ${plan.tasks.size} action(s)"))
            coordinator.transition(runId, AgentRunState.EXECUTING)
        }
        var finalOutcome: Outcome = Outcome.Success("Plan executed successfully")

        for (task in plan.tasks) {
            val result = try {
                scheduler.executeTaskGraph(task,
                    beforeDispatch = { dispatched ->
                        if (runId != null && coordinator != null) {
                            val operationId = dispatched.id.toString()
                            val invocationId = com.halo.runtime.cognition.agent.ToolInvocationId(UUID.randomUUID().toString())
                            val argumentsDigest = com.halo.runtime.cognition.agent.ToolArgumentCanonicalizer
                                .digest(dispatched.toString())
                            coordinator.recordStep(AgentStep.ToolRequested(AgentStepId(UUID.randomUUID().toString()), runId,
                                Instant.now(), operationId, dispatched::class.java.simpleName, "{}"))
                            coordinator.recordStep(AgentStep.ToolInvocationPrepared(AgentStepId(UUID.randomUUID().toString()), runId,
                                Instant.now(), invocationId, operationId, dispatched::class.java.simpleName,
                                "{}", argumentsDigest, "${runId.value}:$operationId:prep", 1,
                                com.halo.runtime.cognition.tools.ReplaySafety.NEVER_REPLAY.name))
                            // RUNTIME-018: claim before dispatch. Does not prove the external
                            // action occurred; it proves Halo owns this exact invocation.
                            check(coordinator.recordStep(AgentStep.ToolExecutionClaimed(AgentStepId(UUID.randomUUID().toString()), runId,
                                Instant.now(), invocationId, operationId, dispatched::class.java.simpleName,
                                "${runId.value}:$operationId", com.halo.runtime.cognition.tools.ReplaySafety.NEVER_REPLAY.name))) {
                                "Action already claimed; refusing replay"
                            }
                        }
                    },
                    afterDispatch = { dispatched, outcome ->
                        if (runId != null && coordinator != null) {
                            val operationId = dispatched.id.toString()
                            if (outcome is Outcome.Success) {
                                coordinator.recordStep(AgentStep.ToolCompleted(AgentStepId(UUID.randomUUID().toString()), runId,
                                    Instant.now(), operationId, dispatched::class.java.simpleName, outcome.message.orEmpty(), true))
                            } else if (outcome !is Outcome.Partial) {
                                coordinator.recordStep(AgentStep.ToolFailed(AgentStepId(UUID.randomUUID().toString()), runId,
                                    Instant.now(), operationId, dispatched::class.java.simpleName, outcome.messageForTelemetry()))
                            }
                        }
                    })
            } catch (error: CancellationException) {
                withContext(NonCancellable) { runId?.let { coordinator?.cancel(it, "Execution cancelled") } }
                throw error
            } catch (error: Exception) {
                runId?.let { coordinator?.transition(it, AgentRunState.INTERRUPTED, "Execution stopped without completion evidence") }
                throw error
            }
            val taskLabel = when (task) {
                is com.halo.runtime.cognition.DeviceTask ->
                    "DeviceTask:${task.action}"
                else -> task::class.simpleName ?: "UnknownTask"
            }
            context?.runtime?.logger?.info(
                "HALO-TXN",
                "execution=$executionId task=$taskLabel outcome=${result::class.simpleName ?: "UnknownOutcome"}"
            )

            eventBus.publish(
                ExecutionResultPublished(
                    executionId = executionId,
                    planId = plan.id.toString(),
                    task = task,
                    outcome = result,
                    message = result.messageForTelemetry()
                )
            )

            when (result) {
                is Outcome.PermanentFailure,
                is Outcome.RetryableFailure,
                is Outcome.Cancelled,
                is Outcome.NeedsClarification -> {
                    finalOutcome = result
                    break
                }

                is Outcome.Partial -> finalOutcome = result
                is Outcome.Success -> Unit
            }
        }

        if (runId != null && coordinator != null) {
            coordinator.recordStep(AgentStep.ResponseProduced(AgentStepId(UUID.randomUUID().toString()), runId,
                Instant.now(), executionId, finalOutcome.messageForTelemetry()))
            when (finalOutcome) {
                is Outcome.Success -> coordinator.complete(runId)
                is Outcome.Partial -> coordinator.transition(runId, AgentRunState.INTERRUPTED, finalOutcome.messageForTelemetry())
                is Outcome.Cancelled -> coordinator.cancel(runId, finalOutcome.messageForTelemetry())
                else -> coordinator.fail(runId, finalOutcome.messageForTelemetry())
            }
        }
        return finalOutcome
    }

    private fun Outcome.messageForTelemetry(): String = when (this) {
        is Outcome.Success -> message.orEmpty()
        is Outcome.PermanentFailure -> reason
        is Outcome.RetryableFailure -> reason
        is Outcome.Partial -> message
        is Outcome.Cancelled -> reason.orEmpty()
        is Outcome.NeedsClarification -> prompt
    }
}
