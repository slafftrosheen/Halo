package com.halo.runtime.cognition.memory

import com.halo.runtime.cognition.agent.*
import java.time.Instant

class EpisodeEligibilityPolicy {
    fun eligible(run: AgentRun, objectIds: Set<String>, committedTurn: Boolean): Boolean = run.parentRunId == null && run.state.isTerminal &&
        (run.interactionId.value.startsWith("routine:") || objectIds.isNotEmpty() || run.steps.any { it is AgentStep.ToolOutcomeRecorded || it is AgentStep.ToolCompleted || it is AgentStep.ToolFailed || it is AgentStep.ToolRejected } ||
            (committedTurn && run.steps.filterIsInstance<AgentStep.ProviderTurnCompleted>().size > 1))
}
/** Uses journal receipts, never planner thoughts, private arguments or guessed LLM action truth. */
class EpisodeBuilder(private val eligibility: EpisodeEligibilityPolicy = EpisodeEligibilityPolicy()) {
    fun build(run: AgentRun, referencedObjects: Set<String> = emptySet(), referencedMemories: Set<String> = emptySet(), committedTurnId: String? = null, scopeId: String? = null): Episode? {
        if(!eligibility.eligible(run, referencedObjects, committedTurnId != null)) return null
        // Canonical P4/P5 receipts take precedence over mirrored legacy completion rows.
        data class Receipt(val success: Boolean,val truth: ActionTruth)
        val completed=linkedMapOf<String,Receipt>()
        run.steps.filterIsInstance<AgentStep.ToolCompleted>().forEach { completed[it.toolCallId]=Receipt(it.success,it.actionTruth) }
        run.steps.filterIsInstance<AgentStep.ToolOutcomeRecorded>().forEach { completed[it.toolCallId]=Receipt(it.success,it.actionTruth) }
        val requested=linkedMapOf<String,String>()
        run.steps.forEach { step -> when(step) {
            is AgentStep.ToolRequested -> requested.putIfAbsent(step.toolCallId,step.toolId)
            is AgentStep.ToolInvocationPrepared -> requested.putIfAbsent(step.toolCallId,step.toolId)
            else -> Unit
        } }
        val unresolved = run.hasUnknownAction || completed.values.any { it.truth !in setOf(ActionTruth.CONFIRMED,ActionTruth.FAILED) }
        val confirmed = completed.values.count { it.success && it.truth == ActionTruth.CONFIRMED }
        val rejected = run.steps.any { it is AgentStep.ToolRejected }
        val outcome = when {
            unresolved -> EpisodeOutcome.INTERRUPTED
            run.state == AgentRunState.CANCELLED -> EpisodeOutcome.CANCELLED
            run.state == AgentRunState.INTERRUPTED -> EpisodeOutcome.INTERRUPTED
            run.state == AgentRunState.FAILED -> if(confirmed > 0) EpisodeOutcome.PARTIAL else if(rejected) EpisodeOutcome.BLOCKED else EpisodeOutcome.FAILED
            run.state == AgentRunState.COMPLETED && (completed.values.any { !it.success } || run.steps.any { it is AgentStep.ToolFailed } || rejected) -> if(confirmed > 0) EpisodeOutcome.PARTIAL else EpisodeOutcome.BLOCKED
            else -> EpisodeOutcome.SUCCESS
        }
        val tools = requested.values.take(12)
        val summary = buildString {
            append("Task ended: ${outcome.name.lowercase()}. ")
            append("$confirmed action(s) confirmed; ${requested.size} requested. ")
            if(unresolved) append("Some action completion is unknown; success is not confirmed. ")
            if(tools.isNotEmpty()) append("Tools: ${tools.joinToString(" → ")}. ")
            append("Duration: ${(java.time.Duration.between(run.startedAt ?: run.createdAt, run.completedAt ?: run.updatedAt).seconds).coerceAtLeast(0)} seconds.")
        }.take(MemoryLimits.EPISODE)
        return Episode(EpisodeId("episode:${run.id.value}"), run.interactionId.value, run.id.value, run.startedAt ?: run.createdAt,
            run.completedAt ?: run.updatedAt, outcome, summary, referencedObjects.take(20).toSet(), referencedMemories.take(12).toSet(), tools,
            if(outcome == EpisodeOutcome.SUCCESS) WorkflowSignature.of(tools) else null, unresolved, scopeId)
    }
}
