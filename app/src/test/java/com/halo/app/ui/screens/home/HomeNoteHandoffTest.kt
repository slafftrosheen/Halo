package com.halo.app.ui.screens.home

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.tools.ToolId
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeNoteHandoffTest {
    private val now = Instant.parse("2026-10-23T00:00:00Z")
    private val id = AgentRunId("requested-note-run")
    private fun outcome(truth: ActionTruth = ActionTruth.CONFIRMED, success: Boolean = true,
        callId: String = "call", toolId: String = "halo_create_empty_note", output: String = "tool_result:\nstatus=success\n\ndetails:\nCreated note: note-37 - 'Untitled'") =
        AgentStep.ToolOutcomeRecorded(AgentStepId("outcome"), id, now, ToolInvocationId("invocation"), callId,
            toolId, success, output, truth, null)
    private fun run(state: AgentRunState, steps: List<AgentStep> = emptyList(), runId: AgentRunId = id) =
        AgentRun(runId, InteractionId("note"), "Create an empty note", state, createdAt = now, updatedAt = now, steps = steps)
    private fun prepared() = AgentStep.ToolInvocationPrepared(AgentStepId("prepared"), id, now, ToolInvocationId("invocation"),
        "call", "halo_create_empty_note", "{}", "digest", "key", 1, "NEVER")
    private fun request(name: String = "approval", invocation: String = "invocation"): AgentStep.InputRequested {
        val requestId = AgentInputRequestId(name)
        return AgentStep.InputRequested(AgentStepId(name), id, now, requestId,
            AgentInputRequest.Approval(requestId, id, now, invocationId = ToolInvocationId(invocation),
                toolId = ToolId("halo_create_empty_note"), action = "Create a note", risk = "low", argumentsDigest = "digest"))
    }
    private fun refused(name: String = "approval", resolution: AgentInputResolution = AgentInputResolution.Denied) =
        AgentStep.InputResolved(AgentStepId("resolved-$name"), id, now, AgentInputRequestId(name), AgentInputResolutions.encode(resolution))

    @Test fun approvalWaitFollowsOnlyTheOriginalRunAndReturnsItsConfirmedNoteOnce() = runTest {
        val runs = MutableStateFlow(listOf(run(AgentRunState.WAITING_FOR_APPROVAL)))
        val awaiting = async { awaitCreatedHomeNote(runs, id, "call") }
        runCurrent()
        runs.value += run(AgentRunState.COMPLETED, listOf(outcome(output = "Created note: unrelated - 'Other'")), AgentRunId("other"))
        runCurrent(); assertFalse(awaiting.isCompleted)
        runs.value = listOf(run(AgentRunState.EXECUTING))
        runCurrent(); assertFalse(awaiting.isCompleted)
        runs.value = listOf(run(AgentRunState.COMPLETED, listOf(outcome())))
        assertEquals("note-37", awaiting.await())
        runs.value = listOf(run(AgentRunState.COMPLETED, listOf(outcome(output = "Created note: later - 'Other'"))))
        assertEquals("note-37", awaiting.await())
    }

    @Test fun immediateCompletionUsesTheSameConfirmedJournalEvidence() = runTest {
        assertEquals("note-37", awaitCreatedHomeNote(MutableStateFlow(listOf(run(AgentRunState.COMPLETED, listOf(outcome())))), id, "call"))
    }

    @Test fun declinedCancelledFailedOrInterruptedRunsNeverOpenANote() = runTest {
        for (state in listOf(AgentRunState.CANCELLED, AgentRunState.FAILED, AgentRunState.INTERRUPTED))
            assertNull(awaitCreatedHomeNote(MutableStateFlow(listOf(run(state, listOf(outcome())))), id, "call"))
    }

    @Test fun proposedUnknownFailedWrongCallOrDifferentToolNeverAuthorizesNavigation() {
        for (step in listOf(outcome(truth = ActionTruth.PROPOSED), outcome(truth = ActionTruth.UNKNOWN),
            outcome(success = false), outcome(callId = "other"), outcome(toolId = "halo_create_note")))
            assertNull(createdHomeNoteId(run(AgentRunState.COMPLETED, listOf(step)), "call"))
    }

    @Test fun malformedOrUnboundedNoteIdentifiersAreRejected() {
        for (text in listOf("Created note: ../other - 'Invalid'", "Created note: ${"x".repeat(161)} - 'Invalid'", "No note created"))
            assertNull(createdHomeNoteId(run(AgentRunState.COMPLETED, listOf(outcome(output = text))), "call"))
    }

    @Test fun cancellingAnObserverDoesNotMutateTheRun() = runTest {
        val runs = MutableStateFlow(listOf(run(AgentRunState.WAITING_FOR_APPROVAL)))
        val awaiting = async { awaitCreatedHomeNote(runs, id, "call") }
        runCurrent(); awaiting.cancel(); awaiting.join()
        assertTrue(awaiting.isCancelled)
        assertEquals(AgentRunState.WAITING_FOR_APPROVAL, runs.value.single().state)
    }

    @Test fun durableRefusalReleasesTheWaitEvenWhenTheRuntimeRunRemainsNonterminal() = runTest {
        for (resolution in listOf(AgentInputResolution.Denied, AgentInputResolution.Cancelled, AgentInputResolution.Expired)) {
            val pending = run(AgentRunState.WAITING_FOR_APPROVAL, listOf(prepared(), request(), refused(resolution = resolution)))
            assertNull(awaitCreatedHomeNote(MutableStateFlow(listOf(pending)), id, "call"))
            assertEquals(AgentRunState.WAITING_FOR_APPROVAL, pending.state)
        }
    }

    @Test fun unrelatedInvocationRefusalDoesNotReleaseTheOriginalWait() = runTest {
        val runs = MutableStateFlow(listOf(run(AgentRunState.WAITING_FOR_APPROVAL,
            listOf(prepared(), request(), request("other", "different-invocation"), refused("other")))))
        val awaiting = async { awaitCreatedHomeNote(runs, id, "call") }
        runCurrent(); assertFalse(awaiting.isCompleted)
        runs.value = listOf(run(AgentRunState.CANCELLED))
        assertNull(awaiting.await())
    }

    @Test fun olderRefusalDoesNotOverrideANewerApprovalRequestForTheSameInvocation() = runTest {
        val runs = MutableStateFlow(listOf(run(AgentRunState.WAITING_FOR_APPROVAL,
            listOf(prepared(), request(), refused(), request("new")))))
        val awaiting = async { awaitCreatedHomeNote(runs, id, "call") }
        runCurrent(); assertFalse(awaiting.isCompleted)
        runs.value = listOf(run(AgentRunState.COMPLETED, listOf(outcome())))
        assertEquals("note-37", awaiting.await())
    }
}
