package com.halo.runtime.cognition.agent

import com.halo.cognition.language.InteractionId
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunCoordinatorTest {
    private val coordinator = DefaultAgentRunCoordinator()

    @Test
    fun `start run creates active run in created state`() = runBlocking {
        val request = AgentRequest(InteractionId("txn_1"), "Set an alarm for 8 AM")
        val runId = coordinator.start(request)

        val run = coordinator.activeRuns.value.first()
        assertEquals(runId, run.id)
        assertEquals(AgentRunState.CREATED, run.state)
        assertEquals("Set an alarm for 8 AM", run.request)
    }

    @Test
    fun `record step appends step and updates currentStepId`() = runBlocking {
        val request = AgentRequest(InteractionId("txn_1"), "Set an alarm")
        val runId = coordinator.start(request)

        val stepId = AgentStepId("step_1")
        val step = AgentStep.PlanCreated(stepId, runId, Instant.now(), "plan_1", "Set alarm at 8 AM")
        coordinator.recordStep(step)

        val run = coordinator.activeRuns.value.first()
        assertEquals(1, run.steps.size)
        assertEquals(stepId, run.currentStepId)
    }

    @Test
    fun `reconcile interrupted runs marks active runs as interrupted`() = runBlocking {
        val request = AgentRequest(InteractionId("txn_1"), "Long running process")
        coordinator.start(request)

        coordinator.reconcileInterruptedRuns()

        val run = coordinator.activeRuns.value.first()
        assertEquals(AgentRunState.INTERRUPTED, run.state)
        assertTrue(run.failureReason?.contains("restarted") == true)
    }
}
