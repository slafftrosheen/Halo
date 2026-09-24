package com.halo.app.automation

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.routine.*
import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.routine.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant

class RoutineExecutor(private val db: AppDatabase, private val repository: RoomRoutineRepository,
    private val coordinator: AgentRunCoordinator, private val store: AgentRunStore,
    private val registry: ToolRegistry, private val condition: suspend (RoutineCondition) -> Boolean,
    private val currentlyAllowed: (String) -> Boolean) {
    private val policy = DefaultToolAutomationPolicy()
    fun validate(r: Routine) {
        require(r.name.isNotBlank() && r.name.length <= 120) { "Enter a name of at most 120 characters" }
        require(r.actions.size in 1..10) { "A routine requires 1–10 actions" }
        require(r.conditions.size <= 10)
        require(r.trigger !is RoutineTrigger.AppStarted) { "AppStarted is unsupported" }
        require(r.conditions.none { it is RoutineCondition.BatteryAbove }) { "Battery target is unsupported" }
        when (val t = r.trigger) {
            is RoutineTrigger.DeviceConnected -> require(t.deviceId.isNotBlank()) { "Choose a device" }
            is RoutineTrigger.DeviceDisconnected -> require(t.deviceId.isNotBlank()) { "Choose a device" }
            else -> Unit
        }
        r.actions.forEachIndexed { index, action ->
            require(policy.policyFor(action.toolId) == AutomationPermission.ALLOWED_WITH_ROUTINE_GRANT) { "${action.toolId} cannot run unattended" }
            require(currentlyAllowed(action.toolId)) { "Tool disabled by current permission policy" }
            val call = call(action, "validate:$index")
            require(registry.validate(call) == null) { "Unknown tool or invalid arguments" }
        }
    }
    private fun call(action: RoutineAction, id: String): ToolCall {
        require(action.argumentsJson.length <= 1024) { "Arguments too large" }
        val json = JSONObject(action.argumentsJson)
        require(json.keys().asSequence().all { it == "window_minutes" }) { "Unsupported digest argument" }
        val value = if (json.has("window_minutes")) json.get("window_minutes") else 60
        require(value is Number && value.toDouble().isFinite() && value.toDouble() % 1.0 == 0.0) { "Window must be whole minutes" }
        return ToolCall(id, action.toolId, mapOf("window_minutes" to value.toLong().coerceIn(15, 1440)))
    }
    suspend fun execute(id: RoutineId, occurrence: Instant, manual: Boolean = false): String? {
        coordinator.reconcileInterruptedRuns()
        val identity = "routine:${id.value}:${occurrence.toEpochMilli()}"
        val runId = AgentRunId(identity)
        // The run and occurrence claim commit together. A crashed/duplicate claim is never resumed.
        val routine = db.withTransaction {
            if (repository.dao.run(identity) != null) return@withTransaction null
            val r = repository.get(id) ?: return@withTransaction null
            if (!manual && !r.enabled) return@withTransaction null
            val now = Instant.now()
            store.create(AgentRun(runId, InteractionId(identity), r.name, AgentRunState.CREATED,
                createdAt = now, updatedAt = now, startedAt = now))
            check(repository.dao.insertRun(RoutineRunEntity(identity, id.value, runId.value, occurrence.toEpochMilli(), "RUNNING")) != -1L)
            repository.dao.lastRun(id.value, occurrence.toEpochMilli())
            r
        } ?: return null
        var completed = 0
        suspend fun finish(state: RoutineRunState, reason: String) {
            coordinator.recordStep(AgentStep.ResponseProduced(AgentStepId("$identity:response"), runId, Instant.now(), identity, reason))
            if (state == RoutineRunState.COMPLETED) coordinator.complete(runId)
            else if (state == RoutineRunState.SKIPPED || state == RoutineRunState.CANCELLED) coordinator.cancel(runId, reason)
            else coordinator.fail(runId, reason)
            repository.dao.updateRunState(identity, state.name)
        }
        try {
            coordinator.transition(runId, AgentRunState.PLANNING)
            coordinator.recordStep(AgentStep.PlanCreated(AgentStepId("$identity:plan"), runId, Instant.now(), identity,
                "Triggered ${routine.name}; ${routine.conditions.size} conditions, ${routine.actions.size} sequential actions"))
            try { validate(routine) } catch (e: IllegalArgumentException) {
                finish(RoutineRunState.BLOCKED, e.message ?: "Routine blocked"); return e.message
            }
            routine.conditions.forEachIndexed { index, c ->
                val passed = condition(c)
                coordinator.recordStep(AgentStep.PlanCreated(AgentStepId("$identity:condition:$index"), runId,
                    Instant.now(), identity, "Condition ${index + 1}: ${if (passed) "Passed" else "Not met"}"))
                if (!passed) { finish(RoutineRunState.SKIPPED, "Condition not met"); return "Skipped: condition not met" }
            }
            coordinator.transition(runId, AgentRunState.EXECUTING)
            val output = mutableListOf<String>()
            routine.actions.forEachIndexed { index, action ->
                val current = repository.get(id)
                if (current == null || (!manual && !current.enabled) || current.updatedAt != routine.updatedAt) {
                    finish(RoutineRunState.CANCELLED, "Routine changed or disabled"); return "Cancelled"
                }
                validate(current)
                val key = "$identity:action:$index"
                val call = call(action, key)
                coordinator.recordStep(AgentStep.ToolRequested(AgentStepId("$key:request"), runId, Instant.now(), key, action.toolId, action.argumentsJson))
                // Persist dispatch BEFORE executing; COG-010 globally deduplicates this key.
                if (!coordinator.recordStep(AgentStep.ToolDispatched(AgentStepId("$key:dispatch"), runId, Instant.now(), key, action.toolId, key))) {
                    finish(RoutineRunState.INTERRUPTED, "Dispatch already recorded; completion unknown"); return "Interrupted"
                }
                when (val result = registry.executeAuthorized(ToolAuthorization.Authorized(call))) {
                    is ToolExecutionResult.Success -> {
                        coordinator.recordStep(AgentStep.ToolCompleted(AgentStepId("$key:result"), runId, Instant.now(), key, action.toolId,
                            result.output, true, result.actionTruth))
                        completed++
                        output += result.output
                    }
                    else -> {
                        val unknown = result is ToolExecutionResult.Failed && result.completionUnknown
                        val reason = when (result) { is ToolExecutionResult.Failed -> result.message; is ToolExecutionResult.Rejected -> result.reason; else -> "Failed" }
                        if (!unknown) coordinator.recordStep(AgentStep.ToolFailed(AgentStepId("$key:result"), runId, Instant.now(), key, action.toolId, reason))
                        finish(if (unknown) RoutineRunState.INTERRUPTED else if (completed > 0) RoutineRunState.PARTIAL else if (result is ToolExecutionResult.Rejected) RoutineRunState.BLOCKED else RoutineRunState.FAILED, reason)
                        return reason
                    }
                }
            }
            val text = output.joinToString("\n")
            finish(RoutineRunState.COMPLETED, text)
            return text
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                val run = store.get(runId)
                val unknown = run?.hasUnknownAction == true
                if (run != null && !run.state.isTerminal) {
                    if (unknown) coordinator.transition(runId, AgentRunState.INTERRUPTED, "Cancelled after dispatch; completion unknown")
                    else coordinator.cancel(runId, "Routine execution cancelled")
                }
                repository.dao.updateRunState(identity, if (unknown) "INTERRUPTED" else "CANCELLED")
            }
            throw e
        }
        catch (e: Exception) {
            val run = store.get(runId)
            if (run != null && !run.state.isTerminal) coordinator.fail(runId, e.message ?: "Routine failed")
            repository.dao.updateRunState(identity, if (run?.hasUnknownAction == true) "INTERRUPTED" else if (completed > 0) "PARTIAL" else "FAILED")
            return e.message ?: "Routine failed"
        }
    }
}
