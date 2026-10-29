package com.halo.app.ui.screens.home

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.conversation.ConversationTurn
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class HomeNativeReceiptProjectionTest {
    private val now = Instant.ofEpochMilli(1)
    private val runId = AgentRunId("run")
    private val prepared = AgentStep.RunPrepared(AgentStepId("prepared"), runId, now,
        AgentRunKind.NATIVE, RunRecoveryPolicy.USER_RESUME, "conversation")
    private val invocation = AgentStep.ToolInvocationPrepared(AgentStepId("invocation"), runId, now,
        ToolInvocationId("invocation"), "call", "halo_open_app", "{}", "digest", "key", 1, "NEVER_REPLAY")
    private val receipt = AgentStep.ToolOutcomeRecorded(AgentStepId("receipt"), runId, now,
        invocation.invocationId, "call", invocation.toolId, true, "Literal tool detail", ActionTruth.DISPATCHED, null)
    private val run = AgentRun(runId, InteractionId("interaction"), "Literal user request", AgentRunState.COMPLETED,
        createdAt = now, updatedAt = now, steps = listOf(prepared, invocation, receipt))
    private val turn = ConversationTurn("turn", "Literal user request",
        "Action dispatched; completion is unconfirmed.", 1, "conversation",
        mapOf("providerId" to "native", "interactionId" to "interaction", "requestId" to "request", "runId" to "run"))
    private val response = PresentedAssistantResponse("conversation", "turn", "interaction", "request", turn.assistantText,
        ResponsePresentationSource.COMMITTED_CONVERSATION, providerId = "native")

    @Test fun exactCommittedNativeDispatchProjectsHostCopyWithoutChangingStoredTextOrDetails() {
        assertEquals(HomeNativeReceiptCopy.DISPATCHED_UNCONFIRMED, project())
        assertEquals("Action dispatched; completion is unconfirmed.", turn.assistantText)
        assertEquals("Literal tool detail", receipt.output)
        assertEquals(HomeNativeReceiptCopy.DISPATCHED_UNCONFIRMED,
            project(run = run.copy(steps = listOf(prepared, invocation, receipt.copy(actionTruth = ActionTruth.ACKNOWLEDGED)))))
    }

    @Test fun modelSameTextUserCopyAndTransientResponsesStayLiteral() {
        assertNull(project(response = response.copy(providerId = "openrouter")))
        assertNull(project(turn = turn.copy(metadata = turn.metadata + ("providerId" to "openrouter"))))
        assertNull(project(response = response.copy(source = ResponsePresentationSource.SESSION_COMPLETED)))
        assertNull(project(run = run.copy(steps = listOf(prepared.copy(kind = AgentRunKind.INTERACTIVE), invocation, receipt))))
    }

    @Test fun everyCommittedIdentityMustMatchAndMissingJournalNeverGuesses() {
        assertNull(project(response = response.copy(text = "Changed literal source")))
        assertNull(project(response = response.copy(interactionId = "other")))
        assertNull(project(response = response.copy(requestId = "other")))
        assertNull(project(response = response.copy(turnId = "other")))
        assertNull(project(response = response.copy(conversationId = "other")))
        assertNull(project(turn = turn.copy(metadata = turn.metadata - "runId")))
        assertNull(project(run = run.copy(id = AgentRunId("other"))))
        assertNull(project(run = run.copy(interactionId = InteractionId("other"))))
        assertNull(project(run = run.copy(steps = listOf(prepared.copy(conversationId = "other"), invocation, receipt))))
        assertNull(projectHomeNativeReceipt(response, listOf(turn), emptyList()))
        assertNull(project(run = run.copy(steps = listOf(prepared, invocation, receipt, receipt))))
        assertNull(project(run = run.copy(steps = listOf(prepared, invocation, receipt.copy(toolCallId = "other")))))
    }

    @Test fun confirmedDetailsAndScreenSettingsWorkflowBranchesKeepOriginalPresentation() {
        for (truth in ActionTruth.entries.filter { it !in setOf(ActionTruth.DISPATCHED, ActionTruth.ACKNOWLEDGED) }) {
            assertNull(project(run = run.copy(steps = listOf(prepared, invocation, receipt.copy(actionTruth = truth)))))
        }
        for (tool in listOf("halo_ui_click", "halo_ui_set_text", "halo_find_on_screen", "halo_open_settings")) {
            assertNull(project(run = run.copy(steps = listOf(prepared, invocation.copy(toolId = tool), receipt.copy(toolId = tool)))))
        }
        assertNull(project(run = run.copy(parentRunId = AgentRunId("parent"))))
        assertNull(project(run = run.copy(steps = run.steps + AgentStep.PlanCreated(AgentStepId("workflow"), runId, now,
            "halo.phone.workflow.v1:workflow", "step=1/2;kind=OPEN_APP"))))
        assertNull(project(run = run.copy(state = AgentRunState.WAITING_FOR_APPROVAL)))
        assertNull(project(run = run.copy(steps = listOf(prepared, invocation, receipt.copy(success = false)))))
    }

    private fun project(response: PresentedAssistantResponse = this.response,
        turn: ConversationTurn = this.turn, run: AgentRun = this.run) =
        projectHomeNativeReceipt(response, listOf(turn), listOf(run))
}
