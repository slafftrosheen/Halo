package com.halo.app.ui.screens.home

import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.agent.AgentStepId
import com.halo.runtime.cognition.agent.AgentInputRequest
import com.halo.runtime.cognition.agent.AgentInputRequestId
import com.halo.runtime.cognition.agent.RunGuardReason
import com.halo.runtime.cognition.interaction.PresentationNode
import com.halo.runtime.cognition.interaction.PresentationStepState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeTaskProgressTest {
    private fun completed(truth: ActionTruth, success: Boolean = true, toolId: String = "private_tool_name") = AgentStep.ToolCompleted(
        AgentStepId("receipt"), AgentRunId("run"), Instant.EPOCH, "call", toolId,
        "private result", success, truth)

    @Test fun dispatchDoesNotClaimConfirmedDeliveryOrExposeTechnicalContent() {
        val root = homeTaskProgress(listOf(completed(ActionTruth.DISPATCHED))).root as PresentationNode.Checklist
        assertEquals("App action · Delivery not confirmed", root.items.single().text)
        assertEquals(PresentationStepState.SKIPPED, root.items.single().state)
        assertFalse(root.toString().contains("private"))
    }

    @Test fun openingSettingsIsCompleteWhenTheScreenWasDispatched() {
        val root = homeTaskProgress(listOf(completed(ActionTruth.DISPATCHED, toolId = "halo_open_settings"))).root as PresentationNode.Checklist
        assertEquals("Settings · Action confirmed", root.items.single().text)
        assertEquals(PresentationStepState.COMPLETED, root.items.single().state)
    }

    @Test fun latestReceiptReplacesEarlierStateForTheSameAction() {
        val root = homeTaskProgress(listOf(completed(ActionTruth.UNKNOWN), completed(ActionTruth.CONFIRMED))).root as PresentationNode.Checklist
        assertEquals(1, root.items.size)
        assertEquals(PresentationStepState.COMPLETED, root.items.single().state)
    }

    @Test fun failureNeverAppearsComplete() {
        val root = homeTaskProgress(listOf(completed(ActionTruth.FAILED, false))).root as PresentationNode.Checklist
        assertEquals("App action · Could not finish", root.items.single().text)
        assertEquals(PresentationStepState.FAILED, root.items.single().state)
    }

    @Test fun unresolvedInputIsVisibleUntilItsMatchingResolution() {
        val run = AgentRunId("run")
        val request = AgentInputRequest.Clarification(AgentInputRequestId("question"), run, Instant.EPOCH,
            question = "private question", field = "private field")
        val asked = AgentStep.InputRequested(AgentStepId("asked"), run, Instant.EPOCH, request.id, request)
        val waiting = homeTaskProgress(listOf(asked)).root as PresentationNode.Checklist
        assertEquals("Waiting for you", waiting.items.single().text)
        val answered = AgentStep.InputResolved(AgentStepId("answered"), run, Instant.EPOCH, request.id, "private answer")
        val resolved = homeTaskProgress(listOf(asked, answered)).root as PresentationNode.Checklist
        assertEquals(0, resolved.items.size)
    }

    @Test fun runGuardShowsAStoppedStateWithoutItsTechnicalSummary() {
        val stopped = AgentStep.RunGuardTriggered(AgentStepId("guard"), AgentRunId("run"), Instant.EPOCH,
            RunGuardReason.DEADLINE_EXCEEDED, 1, null, null, 1000, "private technical summary")
        val root = homeTaskProgress(listOf(stopped)).root as PresentationNode.Checklist
        assertEquals("Stopped safely", root.items.single().text)
        assertEquals(PresentationStepState.SKIPPED, root.items.single().state)
    }
}
