package com.halo.engines.execution
import com.halo.runtime.cognition.ExecutionPlan
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task
import com.halo.sdk.runtime.*
import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.runtime.services.Services
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.engines.execution.service.ExecutionScheduler
import com.halo.runtime.cognition.ExecutionContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    private val scheduler: ExecutionScheduler
) {
    suspend fun executePlan(plan: ExecutionPlan): Outcome {
        var finalOutcome: Outcome = Outcome.Success("Plan executed successfully")
        
        for (task in plan.tasks) {
            val result = scheduler.executeTaskGraph(task)
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
