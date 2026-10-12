package com.halo.runtime.cognition.native

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class NativeWorkflowJournalTest {
    private val t = Instant.parse("2026-10-12T00:00:00Z")

    private fun run(id: String, marker: AgentStep.PlanCreated,
        truth: ActionTruth? = null): AgentRun {
        val rid = AgentRunId(id)
        val outcome = truth?.let {
            AgentStep.ToolOutcomeRecorded(
                AgentStepId("$id:result"), rid, t, ToolInvocationId("$id:invocation"),
                "$id:call", "halo_ui_click", true, "receipt", it, null
            )
        }
        return AgentRun(rid, InteractionId("same-interaction"), "Native phone script step",
            if (truth != null) AgentRunState.COMPLETED else AgentRunState.WAITING_FOR_APPROVAL,
            createdAt = t, updatedAt = t,
            steps = listOfNotNull(marker, outcome))
    }

    @Test fun checkpointsTrackEffectTruthNotMerelyAPlannedStep() {
        val one = run("first", NativeWorkflowJournal.checkpoint(AgentRunId("first"),
            "flow-7", 1, 3, "OPEN_APP", t), ActionTruth.DISPATCHED)
        val two = run("second", NativeWorkflowJournal.checkpoint(AgentRunId("second"),
            "flow-7", 2, 3, "TAP", t), ActionTruth.CONFIRMED)
        val pending = AgentInputRequest.Clarification(AgentInputRequestId("ask-third"),
            AgentRunId("third"), t, question = "Please review", field = "uiTakeover")
        val three = run("third", NativeWorkflowJournal.checkpoint(AgentRunId("third"),
            "flow-7", 3, 3, "ENTER_TEXT", t)).let { original ->
            original.copy(steps = original.steps + AgentStep.InputRequested(
                AgentStepId("pending-third"), original.id, t, pending.id, pending))
        }
        val view = NativeWorkflowJournal.progress(listOf(one, two, three)).single()
        assertEquals("flow-7", view.workflowId)
        assertEquals(3, view.total)
        assertEquals(3, view.steps.size)
        assertEquals(3, view.lastObservedOrdinal)
        assertEquals(1, view.confirmed)
        assertEquals(NativeActionOutcome.DISPATCHED, view.steps[0].outcome)
        assertEquals(NativeActionOutcome.WAITING_FOR_INPUT, view.steps[2].outcome)
        assertFalse(view.autoResumeAllowed)
    }

    @Test fun aDurableParentSurvivesEvenBeforeTheFirstAction() {
        val parentId = AgentRunId("phone-parent")
        val parent = AgentRun(parentId, InteractionId("interaction"), "Phone workflow",
            AgentRunState.INTERRUPTED, createdAt = t, updatedAt = t,
            steps = listOf(NativeWorkflowJournal.parentCheckpoint(parentId, "flow-parent", 4, t)))
        val projection = NativeWorkflowJournal.progress(listOf(parent)).single()
        assertEquals(parentId, projection.parentRunId)
        assertEquals(AgentRunState.INTERRUPTED, projection.parentState)
        assertEquals("flow-parent", projection.workflowId)
        assertEquals(4, projection.total)
        assertEquals(0, projection.confirmed)
        assertTrue(projection.steps.isEmpty())
        assertFalse(projection.autoResumeAllowed)
        assertNull(NativeWorkflowJournal.marker(parent))
        assertNotNull(NativeWorkflowJournal.parentMarker(parent))
    }

    @Test fun childReceiptsAreCorrelatedWithAnOwningParent() {
        val parentId = AgentRunId("owner")
        val parent = AgentRun(parentId, InteractionId("one-request"), "Phone workflow",
            AgentRunState.SUSPENDED, createdAt = t, updatedAt = t,
            steps = listOf(NativeWorkflowJournal.parentCheckpoint(parentId, "owned", 2, t)))
        val action = run("action", NativeWorkflowJournal.checkpoint(AgentRunId("action"),
            "owned", 1, 2, "TAP", t), ActionTruth.CONFIRMED)
        val view = NativeWorkflowJournal.progress(listOf(action, parent)).single()
        assertEquals(parentId, view.parentRunId)
        assertEquals(AgentRunState.SUSPENDED, view.parentState)
        assertEquals(1, view.confirmed)
        assertEquals(1, view.steps.size)
    }

    @Test fun resolvedInputIsNotShownAsAwaitingAnotherApproval() {
        val original = run("approval-step", NativeWorkflowJournal.checkpoint(
            AgentRunId("approval-step"), "workflow-input", 1, 2, "TAP", t))
        val input = AgentInputRequest.Clarification(
            AgentInputRequestId("request"), original.id, t,
            question = "Continue?", field = "uiTakeover"
        )
        val requested = AgentStep.InputRequested(AgentStepId("asked"), original.id, t, input.id, input)
        val approved = AgentStep.InputResolved(AgentStepId("answered"), original.id, t, input.id, "Clarified:continue")
        val waiting = original.copy(state = AgentRunState.WAITING_FOR_CLARIFICATION,
            steps = original.steps + requested)
        val resolved = waiting.copy(steps = waiting.steps + approved)
        assertEquals(NativeActionOutcome.WAITING_FOR_INPUT,
            NativeWorkflowJournal.progress(listOf(waiting)).single().steps.single().outcome)
        assertEquals(NativeActionOutcome.UNKNOWN,
            NativeWorkflowJournal.progress(listOf(resolved)).single().steps.single().outcome)
    }

    @Test fun checkpointsDoNotPersistTypedValueOrTargetLabel() {
        val marker = NativeWorkflowJournal.checkpoint(AgentRunId("test"),
            "flow-typed", 2, 3, "ENTER_TEXT", t)
        assertFalse(marker.summary.contains("private-password"))
        assertFalse(marker.summary.contains("Search"))
        assertEquals("ENTER_TEXT", NativeWorkflowJournal.marker(
            run("test", marker))?.kind)
    }

    @Test fun corruptOrForeignPlanEntriesAreIgnored() {
        val marker = NativeWorkflowJournal.checkpoint(AgentRunId("test"),
            "safe-id", 1, 2, "OPEN_APP", t)
        val corrupt = marker.copy(summary = "step=3/2;kind=OPEN_APP")
        assertNull(NativeWorkflowJournal.marker(run("test", corrupt)))
        assertTrue(NativeWorkflowJournal.progress(
            listOf(run("test", marker.copy(planId = "unrelated-workflow")))).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun untrustedStepNamesCannotEnterCheckpoint() {
        NativeWorkflowJournal.checkpoint(AgentRunId("test"), "safe", 1, 2,
            "TYPE_private_field_contents", t)
    }
}
