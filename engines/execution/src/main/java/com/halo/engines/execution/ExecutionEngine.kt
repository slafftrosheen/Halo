package com.halo.engines.execution
import com.halo.runtime.cognition.ApplicationTask
import com.halo.runtime.cognition.CommunicationTask
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.ExecutionContext
import com.halo.runtime.cognition.ExecutionPlan
import com.halo.runtime.cognition.MediaTask
import com.halo.runtime.cognition.MemoryTask
import com.halo.runtime.cognition.NavigationTask
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.OutcomePublished
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.WebTask
import com.halo.sdk.runtime.*
import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.runtime.services.Services
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.engines.execution.service.ExecutionScheduler
import com.halo.runtime.cognition.ExecutionResultPublished
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ExecutionEngine acts as the single deterministic driver for physical and digital actions in the Alpha Runtime.
 * 
 * Alpha Architecture constraints:
 * - NO pub/sub listeners in the host attempting to execute raw intents.
 * - ALL execution must pass through this engine by matching a [Task] to a registered [Capability].
 * - Platform agnosticism is strictly maintained. The implementation of a capability 
 *   (e.g., `AndroidDeviceCapability`) is provided by the host layer and injected into the [CapabilityRegistry].
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
                try {
                    val planKnowledge = blackboard.latest(com.halo.runtime.cognition.blackboard.PlanKnowledge::class)
                    val plan = (planKnowledge?.payload as? com.halo.runtime.cognition.blackboard.PlanKnowledge)?.plan
                    
                    if (plan != null) {
                        executionMutex.withLock {
                            eventBus.publish(ExecutionStarted(id = java.util.UUID.randomUUID().toString(), planId = event.planId))
                            val outcome = executePlan(plan)
                            eventBus.publish(ExecutionFinished(id = java.util.UUID.randomUUID().toString(), planId = event.planId))
                            eventBus.publish(OutcomePublished(outcome = outcome::class.simpleName ?: "Unknown"))
                        }
                    }
                } catch (e: Exception) {
                    runtimeContext.runtime.logger.error("execution", "Failed to execute plan", e)
                    val outcome = Outcome.PermanentFailure("Execution failed: ${e.message}", e)
                    eventBus.publish(ExecutionFinished(id = java.util.UUID.randomUUID().toString(), planId = event.planId))
                    eventBus.publish(OutcomePublished(outcome = outcome::class.simpleName ?: "PermanentFailure"))
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

    suspend fun executePlan(plan: ExecutionPlan): Outcome {
        var finalOutcome: Outcome = Outcome.Success("Plan executed successfully")
        
        for (task in plan.tasks) {
            val result = scheduler.executeTaskGraph(task)
            eventBus.publish(
                ExecutionResultPublished(
                    planId = plan.id.toString(),
                    task = task,
                    outcome = result,
                    message = (result as? Outcome.Success)?.message ?: (result as? Outcome.PermanentFailure)?.reason ?: ""
                )
            )
            if (result is Outcome.PermanentFailure || result is Outcome.RetryableFailure) {
                finalOutcome = result
                break
            } else if (result is Outcome.Partial) {
                finalOutcome = result
            }
        }
        
        return finalOutcome
    }
}
