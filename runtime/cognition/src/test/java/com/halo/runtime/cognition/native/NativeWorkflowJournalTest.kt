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
        val three = run("third", NativeWorkflowJournal.checkpoint(AgentRunId("third"),
            "flow-7", 3, 3, "ENTER_TEXT", t))
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
