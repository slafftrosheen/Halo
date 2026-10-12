package com.halo.runtime.cognition.native

import com.halo.runtime.cognition.agent.AgentRun
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.AgentRunState
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.agent.AgentStepId
import com.halo.runtime.cognition.agent.ActionTruth
import java.time.Instant

/**
 * Redacted durable correlation across the individually authorized native runs of
 * one bounded phone script. Uses the existing PlanCreated journal codec (no schema
 * migration) and deliberately contains no arguments, target names or typed text.
 *
 * A checkpoint is evidence of a planned step, NOT evidence of a completed effect.
 * It does not contain enough authority or screen state to replay the workflow.
 */
object NativeWorkflowJournal {
    private const val PREFIX = "halo.phone.workflow.v1:"
    private val summaryPattern = Regex("^step=([1-4])/([2-4]);kind=([A-Z_]+)$")

    data class Marker(val workflowId: String, val ordinal: Int, val total: Int, val kind: String)
    data class Step(val marker: Marker, val runId: AgentRunId, val state: AgentRunState,
        val outcome: NativeActionOutcome)
    data class Progress(val workflowId: String, val total: Int, val steps: List<Step>) {
        val confirmed: Int get() = steps.count { it.outcome == NativeActionOutcome.CONFIRMED }
        val lastObservedOrdinal: Int get() = steps.maxOfOrNull { it.marker.ordinal } ?: 0
        /** No legacy screen token or unverified external effect is replayable. */
        val autoResumeAllowed: Boolean get() = false
    }

    fun checkpoint(runId: AgentRunId, id: String, ordinal: Int, total: Int, kind: String,
        at: Instant = Instant.now()): AgentStep.PlanCreated {
        require(id.length in 1..100 && id.all { it.isLetterOrDigit() || it == '-' })
        require(total in 2..4 && ordinal in 1..total)
        require(kind.length in 1..40 && kind.all { it in 'A'..'Z' || it == '_' })
        return AgentStep.PlanCreated(
            AgentStepId(java.util.UUID.randomUUID().toString()), runId, at,
            PREFIX + id, "step=$ordinal/$total;kind=$kind"
        )
    }

    fun marker(run: AgentRun): Marker? = run.steps.filterIsInstance<AgentStep.PlanCreated>()
        .lastOrNull { it.planId.startsWith(PREFIX) }?.let { step ->
            val id = step.planId.removePrefix(PREFIX)
            val matched = summaryPattern.matchEntire(step.summary) ?: return@let null
            val ordinal = matched.groupValues[1].toIntOrNull() ?: return@let null
            val total = matched.groupValues[2].toIntOrNull() ?: return@let null
            if (id.isBlank() || ordinal !in 1..total || total !in 2..4) return@let null
            Marker(id, ordinal, total, matched.groupValues[3])
        }

    fun progress(runs: List<AgentRun>): List<Progress> =
        runs.mapNotNull { run ->
            val marker = marker(run) ?: return@mapNotNull null
            Step(marker, run.id, run.state, outcome(run))
        }.groupBy { it.marker.workflowId }
            .map { (id, entries) ->
                val total = entries.first().marker.total
                Progress(id, total, entries
                    .filter { it.marker.total == total }
                    .groupBy { it.marker.ordinal }
                    .values.map { steps -> steps.maxBy { it.marker.ordinal } }
                    .sortedBy { it.marker.ordinal })
            }

    private fun outcome(run: AgentRun): NativeActionOutcome {
        val recorded = run.steps.filterIsInstance<AgentStep.ToolOutcomeRecorded>().lastOrNull()
        if (recorded != null) return when {
            !recorded.success -> NativeActionOutcome.FAILED
            recorded.actionTruth == ActionTruth.CONFIRMED -> NativeActionOutcome.CONFIRMED
            recorded.actionTruth == ActionTruth.DISPATCHED || recorded.actionTruth == ActionTruth.ACKNOWLEDGED ->
                NativeActionOutcome.DISPATCHED
            recorded.actionTruth == ActionTruth.UNKNOWN -> NativeActionOutcome.UNKNOWN
            else -> NativeActionOutcome.UNKNOWN
        }
        if (run.steps.any { it is AgentStep.ToolRejected }) return NativeActionOutcome.REJECTED
        return when (run.state) {
            AgentRunState.WAITING_FOR_APPROVAL, AgentRunState.WAITING_FOR_CLARIFICATION ->
                NativeActionOutcome.WAITING_FOR_INPUT
            AgentRunState.WAITING_FOR_RESOURCE -> NativeActionOutcome.WAITING_FOR_RESOURCE
            AgentRunState.WAITING_FOR_CAPABILITY -> NativeActionOutcome.WAITING_FOR_CAPABILITY
            AgentRunState.CANCELLED -> NativeActionOutcome.CANCELLED
            AgentRunState.FAILED -> NativeActionOutcome.FAILED
            else -> NativeActionOutcome.UNKNOWN
        }
    }
}
