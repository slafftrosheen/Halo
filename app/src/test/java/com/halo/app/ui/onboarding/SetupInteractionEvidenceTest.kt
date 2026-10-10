package com.halo.app.ui.onboarding

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SetupInteractionEvidenceTest {
    private val id=AgentRunId("run")
    private val now=Instant.EPOCH
    private fun run(state:AgentRunState=AgentRunState.COMPLETED,steps:List<AgentStep> = emptyList()) =
        AgentRun(id,InteractionId("interaction"),"private request",state,createdAt=now,updatedAt=now,steps=steps)
    @Test fun actualReplyCanFinishButPendingOrFailedRunsCannot() {
        assertTrue(SetupInteractionEvidence.actionsConfirmed(emptyList()))
        assertTrue(SetupInteractionEvidence.actionsConfirmed(listOf(run())))
        assertFalse(SetupInteractionEvidence.actionsConfirmed(listOf(run()),requireAction=true)) // Clarification is a reply, not an executed native action.
        listOf(AgentRunState.WAITING_FOR_APPROVAL,AgentRunState.EXECUTING,AgentRunState.FAILED,AgentRunState.CANCELLED).forEach {
            assertFalse(SetupInteractionEvidence.actionsConfirmed(listOf(run(it))))
        }
    }
    @Test fun dispatchAcknowledgmentOrRejectionCannotBeReportedAsActionSuccess() {
        for(truth in ActionTruth.entries) {
            val step=AgentStep.ToolCompleted(AgentStepId("result"),id,now,"call","tool","private result",true,truth)
            assertEquals(truth==ActionTruth.CONFIRMED,SetupInteractionEvidence.actionsConfirmed(listOf(run(steps=listOf(step)))))
        }
        val failed=AgentStep.ToolFailed(AgentStepId("fail"),id,now,"call","tool","private error")
        assertFalse(SetupInteractionEvidence.actionsConfirmed(listOf(run(steps=listOf(failed)))))
        val rejected=AgentStep.ToolRejected(AgentStepId("reject"),id,now,"call","tool",ToolRejectionCode.AUTHORIZATION_DENIED,"private",false,true,"private")
        assertFalse(SetupInteractionEvidence.actionsConfirmed(listOf(run(steps=listOf(rejected)))))
    }
}
