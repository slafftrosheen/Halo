package com.halo.runtime.cognition.agent.recovery

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.tools.ReplaySafety
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ToolRecoveryTruthTest {
    private val id = AgentRunId("run-1")
    private val time = Instant.parse("2026-10-10T12:00:00Z")
    private val invocationId = ToolInvocationId("inv-1")
    private val planner = DefaultAgentRecoveryPlanner()

    private fun prepared() = AgentStep.ToolInvocationPrepared(
        AgentStepId("prep"), id, time, invocationId, "call-1", "halo_read",
        "{}", "digest", "key-1", 1, ReplaySafety.SAFE.name)

    private fun claimed() = AgentStep.ToolExecutionClaimed(
        AgentStepId("claim"), id, time, invocationId, "call-1", "halo_read",
        "key-1", ReplaySafety.SAFE.name)

    private fun run(vararg evidence: AgentStep) = AgentRun(
        id, InteractionId("interaction-1"), "Read status", AgentRunState.SUSPENDED,
        createdAt = time, updatedAt = time,
        steps = listOf(AgentStep.RunPrepared(AgentStepId("start"), id, time,
            AgentRunKind.INTERACTIVE, RunRecoveryPolicy.USER_RESUME)) + evidence)

    private fun plan(run: AgentRun) = planner.plan(run, null, CapabilitySnapshot())

    @Test fun safeClaimUsesOriginalKeyAndInteraction() {
        val p = plan(run(prepared(), claimed()))
        assertTrue(p is RecoveryPlan.RetryTool)
        p as RecoveryPlan.RetryTool
        assertEquals("key-1", p.invocation.idempotencyKey)
        assertEquals(InteractionId("interaction-1"), p.invocation.interactionId)
    }

    @Test fun legacyCompletionMustNotReplay() {
        val legacy = AgentStep.ToolCompleted(AgentStepId("completed"), id, time,
            "call-1", "halo_read", "done", true)
        assertFalse(plan(run(prepared(), claimed(), legacy)) is RecoveryPlan.RetryTool)
    }

    @Test fun unknownModernResultDoesNotBecomeSuccess() {
        val outcome = AgentStep.ToolOutcomeRecorded(AgentStepId("unknown"), id, time,
            invocationId, "call-1", "halo_read", true, "unverified", ActionTruth.UNKNOWN, null)
        assertTrue(plan(run(prepared(), claimed(), outcome)) is RecoveryPlan.UncertainExternalAction)
        assertEquals(ToolCallResolution.COMPLETION_UNKNOWN,
            resolveToolCalls(listOf("call-1"), listOf(prepared(), claimed(), outcome))["call-1"])
    }

    @Test fun unknownLegacyResultDoesNotBecomeSuccess() {
        val outcome = AgentStep.ToolCompleted(AgentStepId("legacy"), id, time,
            "call-1", "halo_read", "unsure", true, ActionTruth.UNKNOWN)
        assertTrue(plan(run(prepared(), claimed(), outcome)) is RecoveryPlan.UncertainExternalAction)
        assertEquals(ToolCallResolution.COMPLETION_UNKNOWN,
            resolveToolCalls(listOf("call-1"), listOf(prepared(), claimed(), outcome))["call-1"])
    }

    @Test fun orphanClaimCannotGuessReplaySafety() {
        assertTrue(plan(run(claimed())) is RecoveryPlan.UnsupportedLegacyState)
    }

    @Test fun mismatchedIdempotencyKeyIsBlocked() {
        assertTrue(plan(run(prepared(), claimed().copy(idempotencyKey = "wrong"))) is RecoveryPlan.UnsupportedLegacyState)
    }

    @Test fun sameEvidenceCannotBeClaimedAgain() {
        val first = run(prepared(), claimed())
        val result = plan(first)
        val claim = AgentStep.RecoveryClaimed(AgentStepId("recovery"), id, time,
            result::class.simpleName ?: "Unknown", recoveryEvidenceStepId(first))
        assertTrue(plan(first.copy(steps = first.steps + claim)) is RecoveryPlan.UncertainExternalAction)
    }
}
