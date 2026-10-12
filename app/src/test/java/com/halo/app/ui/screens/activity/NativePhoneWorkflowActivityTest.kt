package com.halo.app.ui.screens.activity

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.native.NativeWorkflowJournal
import com.halo.runtime.cognition.ui.*
import com.halo.runtime.cognition.tools.ToolId
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class NativePhoneWorkflowActivityTest {
    private val time = Instant.parse("2026-10-12T18:00:00Z")

    private fun step(
        id: String, workflow: String, ordinal: Int, total: Int, kind: String,
        state: AgentRunState, truth: ActionTruth? = null,
        approval: Boolean = false
    ): AgentRun {
        val runId = AgentRunId(id)
        val steps = mutableListOf<AgentStep>(
            NativeWorkflowJournal.checkpoint(runId, workflow, ordinal, total, kind, time)
        )
        val operation = when (kind) {
            "TAP" -> UiOperation.ACTIVATE
            "ENTER_TEXT" -> UiOperation.SET_TEXT
            "SCROLL_FORWARD", "SCROLL_BACKWARD" -> UiOperation.SCROLL
            "BACK" -> UiOperation.BACK
            else -> null
        }
        if (truth != null) {
            if (truth == ActionTruth.CONFIRMED && operation != null) {
                steps += AgentStep.UiTransactionRecorded(AgentStepId("$id:ui"), runId, time,
                    UiTransactionRecord("$id:call", UiWorkflowPhase.WAITING_FOR_MODEL,
                        receipt = UiActionReceipt("$id:call", operation, "fake-target",
                            UiDispatchStatus.DISPATCHED, UiVerification.CONFIRMED,
                            truth, "before", "after", "effect",
                            settleStatus = UiSettleStatus.SETTLED)))
            }
            val tool = when (operation) {
                UiOperation.ACTIVATE -> "halo_ui_click"
                UiOperation.SET_TEXT -> "halo_ui_set_text"
                UiOperation.SCROLL -> "halo_ui_scroll"
                UiOperation.BACK -> "halo_ui_global_action"
                else -> "halo_ui_click"
            }
            steps += AgentStep.ToolOutcomeRecorded(
                AgentStepId("$id:outcome"), runId, time,
                ToolInvocationId("$id:invocation"), "$id:call", tool,
                true, "UNTRUSTED_SCREEN_CONTENT", truth, null
            )
        }
        if (approval) {
            val input = AgentInputRequest.Approval(
                AgentInputRequestId("$id:approval"), runId, time,
                invocationId = ToolInvocationId("$id:invocation"),
                toolId = ToolId("halo_ui_click"), action = "Tap control", risk = "SENSITIVE",
                argumentsDigest = "opaque-digest"
            )
            steps += AgentStep.InputRequested(
                AgentStepId("$id:requested"), runId, time, input.id, input
            )
        }
        return AgentRun(
            id = runId, interactionId = InteractionId("private-command"),
            request = "PRIVATE TYPED CONTENT", state = state,
            createdAt = time, updatedAt = time, steps = steps
        )
    }

    @Test fun confirmedWorkflowIsOneCardWithoutTypedArguments() {
        val first = step("first", "phone-flow", 1, 3, "OPEN_APP",
            AgentRunState.COMPLETED, ActionTruth.CONFIRMED)
        val second = step("second", "phone-flow", 2, 3, "TAP",
            AgentRunState.COMPLETED, ActionTruth.CONFIRMED)
        val third = step("third", "phone-flow", 3, 3, "ENTER_TEXT",
            AgentRunState.COMPLETED, ActionTruth.CONFIRMED)
        val cards = NativePhoneWorkflowActivity.project(listOf(first, second, third))
        assertEquals(1, cards.size)
        val card = cards.single()
        assertEquals(ActivityStatus.SUCCESS, card.status)
        assertEquals("3 of 3 steps verified", card.planProgress)
        assertEquals(3, card.steps.size)
        assertEquals(setOf("first", "second", "third"), card.relatedRunIds)
        assertFalse(card.toString().contains("PRIVATE TYPED CONTENT"))
        assertFalse(card.toString().contains("UNTRUSTED_SCREEN_CONTENT"))
    }

    @Test fun observedForegroundIsDescribedAsVerificationNotLaunchReceipt() {
        val parentId = AgentRunId("foreground-owner")
        val parent = AgentRun(parentId, InteractionId("private-command"), "Phone workflow",
            AgentRunState.COMPLETED, createdAt = time, updatedAt = time,
            steps = listOf(
                NativeWorkflowJournal.parentCheckpoint(parentId, "foreground-flow", 2, time),
                NativeWorkflowJournal.foregroundCheckpoint(parentId, "foreground-flow", 1,
                    AgentRunId("first"), "com.example.mail", time.toEpochMilli() + 200)
            ))
        val launch = step("first", "foreground-flow", 1, 2, "OPEN_APP",
            AgentRunState.COMPLETED, ActionTruth.DISPATCHED)
        val tap = step("second", "foreground-flow", 2, 2, "TAP",
            AgentRunState.COMPLETED, ActionTruth.CONFIRMED)
        val card = NativePhoneWorkflowActivity.project(listOf(parent, launch, tap)).single()
        assertEquals(ActivityStatus.SUCCESS, card.status)
        assertEquals("2 of 2 steps verified", card.planProgress)
        assertTrue(card.steps[0].phoneForegroundVerified)
        assertFalse(card.steps[1].phoneForegroundVerified)
        assertEquals(com.halo.runtime.cognition.native.NativeWorkflowRecoveryGuide.Action.COMPLETE,
            card.phoneGuidance?.action)
        assertFalse(card.toString().contains("com.example.mail"))
    }

    @Test fun unconfirmedEffectNeverCountsAsVerified() {
        val first = step("first", "unknown-flow", 1, 3, "OPEN_APP",
            AgentRunState.COMPLETED, ActionTruth.DISPATCHED)
        val second = step("second", "unknown-flow", 2, 3, "TAP",
            AgentRunState.COMPLETED, ActionTruth.CONFIRMED)
        val card = NativePhoneWorkflowActivity.project(listOf(first, second)).single()
        assertEquals(ActivityStatus.UNKNOWN, card.status)
        assertEquals("1 of 3 steps verified", card.planProgress)
        assertEquals("Not started", card.steps[2].message)
        assertNull(card.reviewRunId)
        assertEquals(1, card.phoneGuidance?.nextOrdinal)
        assertEquals(0, card.phoneGuidance?.verifiedPrefix)
        assertEquals(com.halo.runtime.cognition.native.NativeWorkflowRecoveryGuide.Action.INSPECT_UNCERTAIN_EFFECT,
            card.phoneGuidance?.action)
        assertFalse(card.phoneGuidance?.mayAutoResume ?: true)
    }

    @Test fun approvalReviewPointsToWaitingStep() {
        val first = step("first", "approval-flow", 1, 2, "OPEN_APP",
            AgentRunState.COMPLETED, ActionTruth.CONFIRMED)
        val second = step("second", "approval-flow", 2, 2, "TAP",
            AgentRunState.WAITING_FOR_APPROVAL, approval = true)
        val ownerId = AgentRunId("approval-owner")
        val owner = AgentRun(ownerId, InteractionId("private-command"),
            "Phone workflow", AgentRunState.SUSPENDED,
            createdAt = time, updatedAt = time,
            steps = listOf(NativeWorkflowJournal.parentCheckpoint(
                ownerId, "approval-flow", 2, time)))
        val card = NativePhoneWorkflowActivity.project(listOf(owner, first, second)).single()
        assertEquals(ActivityStatus.PENDING, card.status)
        assertEquals("second", card.reviewRunId)
        assertEquals("Waiting for approval or input", card.steps[1].message)
        assertEquals(com.halo.runtime.cognition.native.NativeWorkflowRecoveryGuide.Action.REVIEW_INPUT,
            card.phoneGuidance?.action)
    }

    @Test fun legacyWorkflowWithoutAnOwnerNeverExposesAnApproval() {
        val step = step("old-step", "old-flow", 1, 2, "TAP",
            AgentRunState.WAITING_FOR_APPROVAL, approval = true)
        val card = NativePhoneWorkflowActivity.project(listOf(step)).single()
        assertEquals(ActivityStatus.INTERRUPTED, card.status)
        assertNull(card.reviewRunId)
        assertEquals(com.halo.runtime.cognition.native.NativeWorkflowRecoveryGuide.Action.REQUEST_FRESH_INSTRUCTION,
            card.phoneGuidance?.action)
    }

    @Test fun incompleteScriptRequiresFreshUserDirection() {
        val first = step("first", "abandoned-flow", 1, 3, "OPEN_APP",
            AgentRunState.COMPLETED, ActionTruth.CONFIRMED)
        val card = NativePhoneWorkflowActivity.project(listOf(first)).single()
        assertEquals(ActivityStatus.INTERRUPTED, card.status)
        assertNull(card.reviewRunId)
        assertTrue(card.summary.contains("incomplete", ignoreCase = true))
        assertEquals(2, card.phoneGuidance?.nextOrdinal)
        assertEquals(com.halo.runtime.cognition.native.NativeWorkflowRecoveryGuide.Action.REQUEST_FRESH_INSTRUCTION,
            card.phoneGuidance?.action)
    }

    @Test fun ownerWithoutStepsIsInProgressBeforeTheFirstAction() {
        val id = AgentRunId("running-parent")
        val parent = AgentRun(id, InteractionId("request"),
            "Phone workflow", AgentRunState.EXECUTING,
            createdAt = time, updatedAt = time, steps = listOf(
                NativeWorkflowJournal.parentCheckpoint(id, "running-flow", 3, time)
            ))
        val card = NativePhoneWorkflowActivity.project(listOf(parent)).single()
        assertEquals(ActivityStatus.PENDING, card.status)
        assertNull(card.reviewRunId)
        assertEquals("0 of 3 steps verified", card.planProgress)
        assertEquals(com.halo.runtime.cognition.native.NativeWorkflowRecoveryGuide.Action.RUNNING,
            card.phoneGuidance?.action)
    }

    @Test fun parentWithoutStepsIsStillVisibleAfterImmediateCrash() {
        val parentId = AgentRunId("parent-first")
        val parent = AgentRun(parentId, InteractionId("command"),
            "Phone workflow", AgentRunState.INTERRUPTED,
            createdAt = time, updatedAt = time,
            steps = listOf(NativeWorkflowJournal.parentCheckpoint(
                parentId, "parent-first-workflow", 3, time)))
        val card = NativePhoneWorkflowActivity.project(listOf(parent)).single()
        assertEquals(ActivityStatus.INTERRUPTED, card.status)
        assertEquals("0 of 3 steps verified", card.planProgress)
        assertEquals(3, card.steps.size)
        assertTrue(card.steps.all { it.message == "Not started" })
        assertEquals(setOf("parent-first"), card.relatedRunIds)
        assertNull(card.reviewRunId)
    }

    @Test fun interruptedParentNeverOffersAnOrphanedStepApproval() {
        val parentId = AgentRunId("owner")
        val parent = AgentRun(parentId, InteractionId("command"),
            "Phone workflow", AgentRunState.INTERRUPTED,
            createdAt = time, updatedAt = time,
            steps = listOf(NativeWorkflowJournal.parentCheckpoint(
                parentId, "orphan", 2, time)))
        val approvedLater = step("late-approval", "orphan", 1, 2, "TAP",
            AgentRunState.WAITING_FOR_APPROVAL, approval = true)
        val card = NativePhoneWorkflowActivity.project(listOf(parent, approvedLater)).single()
        assertEquals(ActivityStatus.INTERRUPTED, card.status)
        assertNull(card.reviewRunId)
        assertEquals(setOf("owner", "late-approval"), card.relatedRunIds)
    }

    @Test fun ordinaryNativeRunsRemainSeparate() {
        val noMarker = AgentRun(AgentRunId("standalone"), InteractionId("single"),
            "Phone battery", AgentRunState.COMPLETED, createdAt = time, updatedAt = time)
        assertTrue(NativePhoneWorkflowActivity.project(listOf(noMarker)).isEmpty())
    }
}
