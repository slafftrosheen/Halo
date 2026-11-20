package com.halo.runtime.cognition.tools

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.turn.DefaultToolTurnExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.HaloEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WorkflowPlanToolTest {
    private class Store : AgentRunStore {
        val rows = MutableStateFlow<List<AgentRun>>(emptyList())
        override fun observeRuns() = rows
        override suspend fun get(runId: AgentRunId) = rows.value.find { it.id == runId }
        override suspend fun create(run: AgentRun) { rows.value += run }
        override suspend fun update(run: AgentRun) { rows.value = rows.value.map { if (it.id == run.id) run else it } }
        override suspend fun nonTerminalRuns() = rows.value.filterNot { it.state.isTerminal }
        override suspend fun claimResume(runId: AgentRunId, expectedState: AgentRunState) = false
        override suspend fun resolveInputIfPending(requestId: AgentInputRequestId, resolution: AgentInputResolution) = false
        override suspend fun appendStep(step: AgentStep): Boolean {
            val run = requireNotNull(get(step.runId)); if (run.steps.any { it.id == step.id }) return false
            update(run.copy(steps = run.steps + step)); return true
        }
    }
    private val bus = object : EventBus {
        override suspend fun publish(event: HaloEvent) = Unit
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
        override fun events(): Flow<HaloEvent> = emptyFlow()
    }
    @Test fun planIsDurableBeforeActionButDoesNotGrantApprovalOrCompleteAnything() = runTest {
        val store = Store(); val coordinator = DefaultAgentRunCoordinator(store, CoroutineScope(Dispatchers.Unconfined))
        val interaction = InteractionId("workflow-fixture")
        val run = coordinator.start(AgentRequest(interaction, "Create a fixture note"))
        coordinator.transition(run, AgentRunState.PLANNING); coordinator.transition(run, AgentRunState.EXECUTING)
        val plan = WorkflowPlanTool(store, coordinator)
        var writes = 0
        val action = object : ToolExecutor {
            override fun definitions() = listOf(ToolDefinition("fixture_write", "fixture_write", "Fixture write", mapOf(
                "type" to "object", "properties" to emptyMap<String, Any>(), "additionalProperties" to false)))
            override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
                writes++; return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Saved")
            }
        }
        val actionDescriptor = ToolDescriptor(ToolId("fixture_write"), action.definitions().single(), ToolCategory.WORKSPACE,
            security = ToolSecurityPolicy(ToolRiskClass.REVERSIBLE, ConfirmationPolicy.NONE, ReplaySafety.NEVER_REPLAY))
        val registry = ToolRegistry(listOf(plan, action), listOf(WorkflowPlanTool.descriptor(), actionDescriptor))
        assertTrue(registry.operabilityReport(listOf(ToolFeatureOperability("workflow", setOf(WorkflowPlanTool.ID)))).isEmpty())
        val executor = DefaultToolTurnExecutor(registry, PolicyToolAuthorizer(defaultPolicyProvider = { ToolApprovalPolicy.ALWAYS_ALLOW },
            descriptorProvider = registry::descriptor, agentRunStore = store), null, coordinator, store, bus)
        executor.execute(run, interaction, "before-plan", listOf(ToolCall("first", "fixture_write", emptyMap())), true)
        assertEquals(0, writes)
        val call = ToolCall("plan", WorkflowPlanTool.ID, mapOf("summary" to "Create and verify the note", "steps" to listOf("Create the note", "Read it back")))
        assertTrue(plan.execute(call, ToolExecutionContext(interaction.value)) is ToolExecutionResult.Success)
        assertTrue(plan.execute(call, ToolExecutionContext(interaction.value)) is ToolExecutionResult.Success)
        val journal = store.get(run)!!.steps
        assertEquals(1, journal.filterIsInstance<AgentStep.PlanCreated>().size)
        assertEquals(listOf("Create the note", "Read it back"), journal.filterIsInstance<AgentStep.PlanCreated>().single().steps)
        assertFalse(journal.any { it is AgentStep.ToolOutcomeRecorded })
        executor.execute(run, interaction, "after-plan", listOf(ToolCall("second", "fixture_write", emptyMap())), true)
        assertEquals(1, writes)
        val denied = DefaultToolTurnExecutor(registry, PolicyToolAuthorizer(defaultPolicyProvider = { ToolApprovalPolicy.DENY },
            descriptorProvider = registry::descriptor, agentRunStore = store), null, coordinator, store, bus)
        denied.execute(run, interaction, "denied", listOf(ToolCall("third", "fixture_write", emptyMap())), true)
        assertEquals(1, writes)
    }
    @Test fun workflowCeilingPreservesSpentTimeCountersAndCustomLimits() {
        var now = 0L
        val clock = object : AgentRunClock { override fun nowMs() = now }
        val guard = AgentRunGuard("run", "interaction", AgentRunLimits.INTERACTIVE, clock = clock)
        now = 50_000
        guard.enablePlannedWorkflow()
        assertEquals(250_000L, guard.remainingMs)
        now = 300_001
        assertTrue(guard.beforeRound() is RunGuardDecision.Stop)
        guard.enablePlannedWorkflow()
        assertEquals(0L, guard.remainingMs)
        val custom = AgentRunGuard("custom", "interaction", AgentRunLimits.INTERACTIVE.copy(maxWallClockMs = 1000), clock = clock)
        custom.enablePlannedWorkflow(); assertEquals(1000L, custom.remainingMs)
    }
}
