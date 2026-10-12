package com.halo.runtime.cognition.native

import com.halo.runtime.cognition.agent.AgentRunState

/**
 * Read-only, content-free recovery guidance for a journalled phone workflow.
 *
 * This is NOT a resume plan: it does not carry arguments, prior approvals,
 * accessibility tokens, screen text or authority to execute any action.
 * After interruption, Halo must get a fresh user command and observation.
 */
object NativeWorkflowRecoveryGuide {
    enum class Action {
        COMPLETE,
        REVIEW_INPUT,
        WAIT_FOR_DEVICE,
        INSPECT_UNCERTAIN_EFFECT,
        REQUEST_FRESH_INSTRUCTION,
        RUNNING,
        STOPPED
    }

    data class Guidance(
        val action: Action,
        /** First step for which there is no verified, contiguous prefix. */
        val nextOrdinal: Int?,
        val verifiedPrefix: Int,
        val total: Int,
        /** Fixed action category only; never an app label or dictated text. */
        val kind: String?,
    ) {
        val requiresFreshUserCommand: Boolean get() =
            action == Action.REQUEST_FRESH_INSTRUCTION || action == Action.INSPECT_UNCERTAIN_EFFECT
        /** This read-only advice never permits replay or execution. */
        val mayAutoResume: Boolean get() = false
    }

    fun assess(progress: NativeWorkflowJournal.Progress): Guidance {
        val ordered = progress.steps.associateBy { it.marker.ordinal }
        val prefix = (1..progress.total).takeWhile { ordinal ->
            ordered[ordinal]?.outcome == NativeActionOutcome.CONFIRMED
        }.size
        val next = (prefix + 1).takeIf { it <= progress.total }
        val unresolved = next?.let(ordered::get)
        val kind = unresolved?.marker?.kind
        val state = progress.parentState
        val action = when {
            next == null && state !in setOf(AgentRunState.CANCELLED, AgentRunState.FAILED) ->
                Action.COMPLETE
            state == AgentRunState.CANCELLED -> Action.STOPPED
            // Never trust a pending approval on an interrupted/terminal owner.
            state == AgentRunState.SUSPENDED &&
                unresolved?.state in setOf(AgentRunState.WAITING_FOR_APPROVAL,
                    AgentRunState.WAITING_FOR_CLARIFICATION) &&
                unresolved?.outcome == NativeActionOutcome.WAITING_FOR_INPUT -> Action.REVIEW_INPUT
            unresolved?.outcome in setOf(NativeActionOutcome.DISPATCHED,
                NativeActionOutcome.UNKNOWN) -> Action.INSPECT_UNCERTAIN_EFFECT
            unresolved?.outcome == NativeActionOutcome.WAITING_FOR_RESOURCE ||
                unresolved?.outcome == NativeActionOutcome.WAITING_FOR_CAPABILITY ->
                if (state == AgentRunState.EXECUTING || state == AgentRunState.SUSPENDED)
                    Action.WAIT_FOR_DEVICE else Action.REQUEST_FRESH_INSTRUCTION
            state == AgentRunState.EXECUTING && unresolved == null -> Action.RUNNING
            else -> Action.REQUEST_FRESH_INSTRUCTION
        }
        return Guidance(action, next, prefix, progress.total, kind)
    }
}
