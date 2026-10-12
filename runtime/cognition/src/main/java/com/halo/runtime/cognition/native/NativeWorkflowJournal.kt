package com.halo.runtime.cognition.native

import com.halo.runtime.cognition.agent.AgentRun
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.AgentRunState
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.agent.AgentStepId
import com.halo.runtime.cognition.agent.ActionTruth
import java.time.Instant
import java.security.MessageDigest

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
    private const val PARENT_PREFIX = "halo.phone.workflow.parent.v1:"
    private const val FOREGROUND_PREFIX = "halo.phone.foreground.v1:"
    private val foregroundPattern = Regex("^step=([1-4]);packageSha256=([0-9a-f]{64})$")
    private val parentPattern = Regex("^total=([2-4])$")
    private val summaryPattern = Regex("^step=([1-4])/([2-4]);kind=([A-Z_]+)$")

    data class ParentMarker(val workflowId: String, val total: Int)
    data class Marker(val workflowId: String, val ordinal: Int, val total: Int, val kind: String)
    data class Step(val marker: Marker, val runId: AgentRunId, val state: AgentRunState,
        val outcome: NativeActionOutcome, val recordedAt: Instant,
        /** True only when a separate trusted foreground observation follows dispatch. */
        val foregroundVerified: Boolean = false)
    data class Progress(val workflowId: String, val total: Int, val steps: List<Step>,
        val parentRunId: AgentRunId? = null, val parentState: AgentRunState? = null,
        val createdAt: Instant? = null) {
        val confirmed: Int get() = steps.count { it.outcome == NativeActionOutcome.CONFIRMED }
        val lastObservedOrdinal: Int get() = steps.maxOfOrNull { it.marker.ordinal } ?: 0
        /** No legacy screen token or unverified external effect is replayable. */
        val autoResumeAllowed: Boolean get() = false
    }

    /** A durable owning run, deliberately without any device or text arguments. */
    fun parentCheckpoint(runId: AgentRunId, id: String, total: Int,
        at: Instant = Instant.now()): AgentStep.PlanCreated {
        require(id.length in 1..100 && id.all { it.isLetterOrDigit() || it == '-' })
        require(total in 2..4)
        return AgentStep.PlanCreated(AgentStepId(java.util.UUID.randomUUID().toString()),
            runId, at, PARENT_PREFIX + id, "total=$total")
    }

    fun parentMarker(run: AgentRun): ParentMarker? =
        run.steps.filterIsInstance<AgentStep.PlanCreated>()
            .lastOrNull { it.planId.startsWith(PARENT_PREFIX) }?.let { step ->
                val id = step.planId.removePrefix(PARENT_PREFIX)
                val total = parentPattern.matchEntire(step.summary)?.groupValues?.get(1)?.toIntOrNull()
                    ?: return@let null
                if (id.length !in 1..100 || id.any { !it.isLetterOrDigit() && it != '-' })
                    return@let null
                ParentMarker(id, total)
            }

    /**
     * A separate foreground observation on the owning run. Stores only a
     * package digest and ordinal; the original app launch receipt remains
     * DISPATCHED. The runtime admits this marker only after host validation.
     */
    fun foregroundCheckpoint(runId: AgentRunId, id: String, ordinal: Int,
        packageName: String, capturedAtMs: Long): AgentStep.PlanCreated {
        require(id.length in 1..100 && id.all { it.isLetterOrDigit() || it == '-' })
        require(ordinal in 1..4 && capturedAtMs > 0)
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")))
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(packageName.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return AgentStep.PlanCreated(AgentStepId(java.util.UUID.randomUUID().toString()),
            runId, Instant.ofEpochMilli(capturedAtMs),
            FOREGROUND_PREFIX + id, "step=$ordinal;packageSha256=$hash")
    }

    fun foregroundVerifiedOrdinals(parent: AgentRun, workflowId: String): Set<Int> =
        parent.steps.filterIsInstance<AgentStep.PlanCreated>()
            .filter { it.planId == FOREGROUND_PREFIX + workflowId }
            .mapNotNull { step -> foregroundPattern.matchEntire(step.summary)
                ?.groupValues?.get(1)?.toIntOrNull() }
            .toSet()

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
            if (id.length !in 1..100 || id.any { !it.isLetterOrDigit() && it != '-' } ||
                ordinal !in 1..total || total !in 2..4) return@let null
            Marker(id, ordinal, total, matched.groupValues[3])
        }

    fun progress(runs: List<AgentRun>): List<Progress> {
        val steps = runs.mapNotNull { run ->
            val marker = marker(run) ?: return@mapNotNull null
            Step(marker, run.id, run.state, outcome(run), run.createdAt)
        }.groupBy { it.marker.workflowId }
        val parents = runs.mapNotNull { run ->
            val parent = parentMarker(run) ?: return@mapNotNull null
            parent.workflowId to (run to parent)
        }.toMap()
        return (steps.keys + parents.keys).map { id ->
            val parent = parents[id]
            val total = parent?.second?.total ?: steps.getValue(id).first().marker.total
            val provenOrdinals = parent?.first?.let { foregroundVerifiedOrdinals(it, id) }
                ?: emptySet()
            val entries = steps[id].orEmpty().filter { it.marker.total == total }
                .groupBy { it.marker.ordinal }
                .values.map { choices -> choices.maxBy { it.recordedAt } }
                .sortedBy { it.marker.ordinal }
                .map { step ->
                    if (step.marker.kind == "OPEN_APP" && step.marker.ordinal in provenOrdinals &&
                        step.outcome == NativeActionOutcome.DISPATCHED)
                        step.copy(outcome = NativeActionOutcome.CONFIRMED, foregroundVerified = true)
                    else step
                }
            Progress(id, total, entries, parent?.first?.id, parent?.first?.state,
                parent?.first?.createdAt ?: entries.minOfOrNull { it.recordedAt })
        }
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
            AgentRunState.WAITING_FOR_APPROVAL, AgentRunState.WAITING_FOR_CLARIFICATION -> {
                val pendingRequest = run.steps.filterIsInstance<AgentStep.InputRequested>()
                    .lastOrNull { request -> run.steps.none { response ->
                        response is AgentStep.InputResolved && response.requestId == request.requestId
                    } }
                if (pendingRequest != null) NativeActionOutcome.WAITING_FOR_INPUT
                else NativeActionOutcome.UNKNOWN
            }
            AgentRunState.WAITING_FOR_RESOURCE -> NativeActionOutcome.WAITING_FOR_RESOURCE
            AgentRunState.WAITING_FOR_CAPABILITY -> NativeActionOutcome.WAITING_FOR_CAPABILITY
            AgentRunState.CANCELLED -> NativeActionOutcome.CANCELLED
            AgentRunState.FAILED -> NativeActionOutcome.FAILED
            else -> NativeActionOutcome.UNKNOWN
        }
    }
}
