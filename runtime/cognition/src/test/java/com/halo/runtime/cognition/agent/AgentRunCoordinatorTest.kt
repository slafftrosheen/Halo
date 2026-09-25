package com.halo.runtime.cognition.agent

import com.halo.cognition.language.InteractionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AgentRunCoordinatorTest {
    private ddclass Store : AgentRunStore {
        val rows = MutableStateFlow<List<AgentRun>>(emptyList())
        override fun observeRuns() = rows
        override suspend fun get(runId: AgentRunId) = rows.value.find { it.id == runId }
        override suspend fun create(run: AgentRun) { rows.value += run }
        override suspend fun update(run: AgentRun) { rows.value = rows.value.map { if (it.id == run.id) run else it } }
        override suspend fun nonTerminalRuns() = rows.value.filterNot { it.state.isTerminal }
        override suspend fun claimResume(runId: AgentRunId, expectedState: AgentRunState): Boolean {
            val run = rows.value.find { it.id == runId } ?: return false
            if (run.state != expectedState) return false
            update(run.copy(state = AgentRunState.EXECUTING))
            return true
        }
        override suspend fun appendStep(step: AgentStep): Boolean {
            val run = requireNotNull(get(step.runId))
            if (run.steps.any { it.id == step.id || (step is AgentStep.ToolDispatched &&
                it is AgentStep.ToolDispatched && step.idempotencyKey != null && it.idempotencyKey == step.idempotencyKey) }) return false
            update(run.copy(steps = run.steps + step, currentStepId = step.id))
            return true
        }
        override suspend fun resolveInputIfPending(
            requestId: AgentInputRequestId,
            resolution: AgentInputResolution,
        ): Boolean = false
    }
    @Test fun `restart reconciles persisted dispatch without replay`() = runTest {
        val store = Store()
        val first = DefaultAgentRunCoordinator(store, backgroundScope)
        val id = first.start(AgentRequest(InteractionId("interaction"), "Alarm"))
        first.transition(id, AgentRunState.PLANNING)
        first.transition(id, AgentRunState.EXECUTING)
        val dispatch = AgentStep.ToolDispatched(AgentStepId("dispatch"), id, Instant.now(), "call", "alarm", "key")
        assertTrue(first.recordStep(dispatch))
        assertFalse(first.recordStep(dispatch.copy(id = AgentStepId("duplicate"))))
        val restarted = DefaultAgentRunCoordinator(store, backgroundScope)
        restarted.reconcileInterruptedRuns()
        val run = requireNotNull(store.get(id))
        assertEquals(AgentRunState.INTERRUPTED, run.state)
        assertEquals(InteractionId("interaction"), run.interactionId)
        assertTrue(run.hasUnknownAction)
        assertEquals(2, run.steps.size)
        assertNotNull(run.completedAt)
    }
    @Test fun `terminal runs reject resume and survive restart`() = runTest {
        val store = Store()
        val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)
        val id = coordinator.start(AgentRequest(InteractionId("interaction"), "Request"))
        coordinator.transition(id, AgentRunState.PLANNING)
        coordinator.transition(id, AgentRunState.EXECUTING)
        coordinator.complete(id)
        assertTrue(runCatching { coordinator.resume(id) }.isFailure)
        DefaultAgentRunCoordinator(store, backgroundScope).reconcileInterruptedRuns()
        assertEquals(AgentRunState.COMPLETED, store.get(id)?.state)
    }
}
