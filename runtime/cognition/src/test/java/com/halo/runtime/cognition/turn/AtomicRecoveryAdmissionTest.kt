package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.agent.recovery.DefaultAgentRecoveryPlanner
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.HaloEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Verifies that a losing resume cannot observe UI or manufacture tool results. */
class AtomicRecoveryAdmissionTest {
    private val runId = AgentRunId("run-ui")
    private val time = Instant.parse("2026-10-10T14:00:00Z")

    private fun suspendedRun() = AgentRun(
        id = runId, interactionId = InteractionId("interaction-ui"),
        request = "Tap a button", state = AgentRunState.SUSPENDED,
        createdAt = time, updatedAt = time,
        steps = listOf(
            AgentStep.RunPrepared(AgentStepId("prepared"), runId, time,
                AgentRunKind.INTERACTIVE, RunRecoveryPolicy.USER_RESUME),
            AgentStep.ProviderTurnCompleted(AgentStepId("provider"), runId, time,
                ProviderTurnId("turn-1"), "", listOf(
                    ProviderToolCallRecord("ui-call", "halo_ui_tap", """{"snapshotId":"expired"}""")
                ), "tool_calls"),
        )
    )

    private class Store(run: AgentRun, private val permitClaim: Boolean) : AgentRunStore {
        val runs = MutableStateFlow(listOf(run))
        var claims = 0
        var writes = 0
        override fun observeRuns(): Flow<List<AgentRun>> = runs
        override suspend fun get(runId: AgentRunId) = runs.value.firstOrNull { it.id == runId }
        override suspend fun create(run: AgentRun) { runs.value += run }
        override suspend fun update(run: AgentRun) { runs.value = runs.value.map { if (it.id == run.id) run else it } }
        override suspend fun nonTerminalRuns() = runs.value.filterNot { it.state.isTerminal }
        override suspend fun claimResume(runId: AgentRunId, expectedState: AgentRunState) = false
        override suspend fun claimRecovery(
            runId: AgentRunId,
            expectedState: AgentRunState,
            claim: AgentStep.RecoveryClaimed,
        ): Boolean {
            claims++
            val run = get(runId) ?: return false
            if (!permitClaim || run.state != expectedState) return false
            update(run.copy(state = AgentRunState.EXECUTING, steps = run.steps + claim))
            return true
        }
        override suspend fun appendStep(step: AgentStep): Boolean {
            writes++
            val run = get(step.runId) ?: return false
            update(run.copy(steps = run.steps + step))
            return true
        }
        override suspend fun resolveInputIfPending(
            requestId: AgentInputRequestId,
            resolution: AgentInputResolution,
        ) = false
    }

    private val bus = object : EventBus {
        override suspend fun publish(event: HaloEvent) {}
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
        override fun events(): Flow<HaloEvent> = emptyFlow()
    }

    @Test fun losingResumeNeverAppendsOrObserves() = runTest {
        val store = Store(suspendedRun(), permitClaim = false)
        var uiObserved = 0
        val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)
        val executor = DefaultAgentRecoveryExecutor(store, coordinator, null, bus,
            DefaultAgentRecoveryPlanner(), uiRecoveryObserver = { uiObserved++; null })
        assertEquals(RecoveryExecutionResult.Noop, executor.executeRecovery(runId))
        assertEquals(1, store.claims)
        assertEquals(0, store.writes)
        assertEquals(0, uiObserved)
        assertFalse(store.runs.value.first().steps.any { it is AgentStep.ToolOutcomeRecorded })
    }

    @Test fun winningResumeWritesExactlyOneReconciledOutcome() = runTest {
        val store = Store(suspendedRun(), permitClaim = true)
        val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)
        val executor = DefaultAgentRecoveryExecutor(store, coordinator, null, bus,
            DefaultAgentRecoveryPlanner())
        val result = executor.executeRecovery(runId)
        assertTrue(result is RecoveryExecutionResult.StartTurn)
        assertEquals(1, store.claims)
        assertEquals(1, store.writes)
        assertEquals(1, store.runs.value.first().steps.filterIsInstance<AgentStep.RecoveryClaimed>().size)
        val outcome = store.runs.value.first().steps.filterIsInstance<AgentStep.ToolOutcomeRecorded>().single()
        assertEquals("ui-call", outcome.toolCallId)
        assertFalse(outcome.success)
        assertEquals(ActionTruth.FAILED, outcome.actionTruth)
    }
}
