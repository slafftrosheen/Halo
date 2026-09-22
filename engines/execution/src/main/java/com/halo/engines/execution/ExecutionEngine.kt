package com.halo.engines.execution

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
    private val blackboard: Blackboard
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
                    val planKnowledge = blackboard.latest(PlanKnowledge::class)
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
        var finalOutcome: Outcome = Outcome.Success("Plan executed successfully")

        for (task in plan.tasks) {
            val result = try {
                scheduler.executeTaskGraph(task)
            } catch (error: Exception) {
                context?.runtime?.logger?.error(
                    "execution",
                    "Task ${task::class.simpleName ?: "unknown"} threw during execution",
                    error
                )
                Outcome.PermanentFailure("Unable to complete action.", error)
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
                is Outcome.Cancelled -> {
                    finalOutcome = result
                    break
                }

                is Outcome.Partial -> finalOutcome = result
                is Outcome.Success -> Unit
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
    }
}
