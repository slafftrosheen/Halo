package com.halo.app.ui.screens.activity

import com.halo.runtime.cognition.agent.AgentInputRequest
import com.halo.runtime.cognition.agent.AgentRun
import com.halo.runtime.cognition.agent.AgentRunState
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.agent.isTerminal
import com.halo.runtime.cognition.native.NativeActionOutcome
import com.halo.runtime.cognition.native.NativeWorkflowJournal

/**
 * One user-visible card per deterministic multi-step phone command.
 *
 * Only journaled execution truth contributes to progress. The fixed labels
 * contain no app contents, field values, approval digests or screen tokens.
 * This projector cannot resume or authorize an operation.
 */
internal object NativePhoneWorkflowActivity {
    fun project(runs: List<AgentRun>): List<ActivityStory> {
        val byId = runs.associateBy { it.id }
        return NativeWorkflowJournal.progress(runs).mapNotNull { workflow ->
            val entries = workflow.steps.sortedBy { it.marker.ordinal }
            val parent = workflow.parentRunId?.let { byId[it] }
            val stepRuns = entries.mapNotNull { byId[it.runId] }
            val recordedRuns = stepRuns + listOfNotNull(parent)
            if (recordedRuns.isEmpty()) return@mapNotNull null
            val parentIsTerminal = parent?.state?.isTerminal == true
            val waiting = stepRuns.lastOrNull { run ->
                !parentIsTerminal &&
                run.state in setOf(AgentRunState.WAITING_FOR_APPROVAL, AgentRunState.WAITING_FOR_CLARIFICATION) &&
                    run.steps.filterIsInstance<AgentStep.InputRequested>().any { requested ->
                        run.steps.none { it is AgentStep.InputResolved && it.requestId == requested.requestId }
                    }
            }
            val hasUnconfirmed = entries.any {
                it.outcome == NativeActionOutcome.DISPATCHED || it.outcome == NativeActionOutcome.UNKNOWN
            }
            val hasFailure = entries.any {
                it.outcome == NativeActionOutcome.FAILED || it.outcome == NativeActionOutcome.REJECTED
            }
            val hasCancelled = entries.any { it.outcome == NativeActionOutcome.CANCELLED }
            val hasWaiting = entries.any {
                it.outcome in setOf(NativeActionOutcome.WAITING_FOR_INPUT,
                    NativeActionOutcome.WAITING_FOR_RESOURCE, NativeActionOutcome.WAITING_FOR_CAPABILITY)
            }
            val fullyConfirmed = entries.size == workflow.total &&
                entries.map { it.marker.ordinal }.toSet().size == workflow.total &&
                entries.all { it.outcome == NativeActionOutcome.CONFIRMED }
            val status = when {
                parent?.state == AgentRunState.CANCELLED || hasCancelled -> ActivityStatus.CANCELLED
                parent?.state == AgentRunState.FAILED || hasFailure -> ActivityStatus.FAILED
                parent?.state == AgentRunState.INTERRUPTED -> ActivityStatus.INTERRUPTED
                hasWaiting && waiting != null -> ActivityStatus.PENDING
                hasUnconfirmed -> ActivityStatus.UNKNOWN
                fullyConfirmed -> ActivityStatus.SUCCESS
                else -> ActivityStatus.INTERRUPTED
            }
            val summary = when (status) {
                ActivityStatus.SUCCESS -> "All steps verified."
                ActivityStatus.UNKNOWN -> "An action is not verified. Inspect the current app before continuing."
                ActivityStatus.FAILED -> "A step failed or was rejected. Later actions were not confirmed."
                ActivityStatus.CANCELLED -> "Stopped. Remaining steps will not run."
                ActivityStatus.PENDING -> if (waiting != null) "Waiting for your review."
                    else "Waiting for a device resource or capability."
                ActivityStatus.INTERRUPTED -> "Workflow incomplete. Check the app and request the remaining steps."
            }
            val stepByIndex = entries.associateBy { it.marker.ordinal }
            val displayedSteps = (1..workflow.total).map { ordinal ->
                val step = stepByIndex[ordinal]
                val statusText = when (step?.outcome) {
                    NativeActionOutcome.CONFIRMED -> "Verified"
                    NativeActionOutcome.DISPATCHED -> "Dispatched, not verified"
                    NativeActionOutcome.UNKNOWN -> "Result unknown"
                    NativeActionOutcome.REJECTED -> "Rejected"
                    NativeActionOutcome.FAILED -> "Failed"
                    NativeActionOutcome.CANCELLED -> "Stopped"
                    NativeActionOutcome.WAITING_FOR_INPUT -> "Waiting for approval or input"
                    NativeActionOutcome.WAITING_FOR_CAPABILITY -> "Permission or capability needed"
                    NativeActionOutcome.WAITING_FOR_RESOURCE -> "Waiting for device"
                    null -> "Not started"
                }
                ActivityStep(
                    presentationId = "phone-workflow:${workflow.workflowId}:$ordinal",
                    title = "Step $ordinal of ${workflow.total}: ${kindLabel(step?.marker?.kind)}",
                    message = statusText,
                    phoneOrdinal = ordinal,
                    phoneKind = step?.marker?.kind,
                    phoneOutcome = step?.outcome,
                    failed = step != null && step.outcome in setOf(
                        NativeActionOutcome.FAILED, NativeActionOutcome.REJECTED,
                        NativeActionOutcome.UNKNOWN, NativeActionOutcome.DISPATCHED
                    ),
                )
            }
            ActivityStory(
                id = "phone-workflow:${workflow.workflowId}",
                correlationId = null,
                kind = ActivityKind.HALO,
                title = "Phone workflow",
                summary = summary,
                timestamp = recordedRuns.minOf { it.createdAt },
                status = status,
                steps = displayedSteps,
                planProgress = "${workflow.confirmed} of ${workflow.total} steps verified",
                reviewRunId = waiting?.id?.value,
                relatedRunIds = recordedRuns.map { it.id.value }.toSet(),
                phoneTotal = workflow.total,
                phoneConfirmed = workflow.confirmed,
            )
        }
    }

    private fun kindLabel(kind: String?): String = when (kind) {
        "OPEN_APP" -> "Open app"
        "FIND" -> "Find screen control"
        "TAP" -> "Tap control"
        "ENTER_TEXT" -> "Enter text"
        "SCROLL_FORWARD" -> "Scroll down"
        "SCROLL_BACKWARD" -> "Scroll up"
        "BACK" -> "Go back"
        else -> "Phone action"
    }
}
