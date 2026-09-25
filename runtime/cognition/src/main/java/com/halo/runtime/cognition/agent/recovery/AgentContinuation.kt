package com.halo.runtime.cognition.agent.recovery

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*

/**
 * RUNTIME-018 provider-neutral continuation transcript.
 *
 * Enough provider-neutral information to continue the model conversation after
 * process death, rebuilt from durable evidence — never by asking the provider to
 * "invent" what happened. Provider adapters translate [AgentContinuationMessage]s
 * into their own message types.
 */
data class AgentContinuation(
    val messages: List<AgentContinuationMessage>,
    val exposedToolIds: Set<String>,
    val providerId: String?,
    val modelId: String?,
)

data class AgentContinuationMessage(
    val role: AgentContinuationRole,
    val text: String,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val argumentsJson: String? = null,
)

enum class AgentContinuationRole { USER, ASSISTANT, TOOL }

/**
 * Rebuilds the tool-loop transcript from durable journal evidence:
 * run request -> assistant tool calls (ProviderTurnCompleted) -> tool outcomes
 * (ToolOutcomeRecorded) -> repeated per round.
 */
object AgentContinuationAssembler {

    fun fromJournal(run: AgentRun, excludeLastCompletedTurn: Boolean = false): AgentContinuation {
        val messages = mutableListOf<AgentContinuationMessage>()
        messages += AgentContinuationMessage(AgentContinuationRole.USER, run.request)

        val allTurns = run.steps.filterIsInstance<AgentStep.ProviderTurnCompleted>()
            .sortedBy { it.createdAt.toEpochMilli() }
        val turns = if (excludeLastCompletedTurn && allTurns.isNotEmpty()) allTurns.dropLast(1) else allTurns
        for (turn in turns) {
            if (turn.assistantText.isNotBlank() || turn.toolCalls.isEmpty()) {
                messages += AgentContinuationMessage(AgentContinuationRole.ASSISTANT, turn.assistantText)
            }
            for (call in turn.toolCalls) {
                messages += AgentContinuationMessage(
                    role = AgentContinuationRole.ASSISTANT,
                    text = "",
                    toolCallId = call.toolCallId,
                    toolName = call.toolName,
                    argumentsJson = call.argumentsJson,
                )
                resultFor(run, call.toolCallId)?.let { result ->
                    messages += AgentContinuationMessage(
                        role = AgentContinuationRole.TOOL,
                        text = result.text,
                        toolCallId = call.toolCallId,
                        toolName = result.toolId,
                    )
                }
            }
        }

        val started = run.steps.filterIsInstance<AgentStep.ProviderTurnStarted>().lastOrNull()
        return AgentContinuation(
            messages = messages,
            exposedToolIds = started?.exposedToolIds ?: emptySet(),
            providerId = started?.providerId,
            modelId = started?.modelId,
        )
    }

    private data class DurableToolResult(val toolId: String, val text: String)

    private fun resultFor(run: AgentRun, toolCallId: String): DurableToolResult? {
        val step = run.steps.asReversed().firstOrNull { candidate ->
            when (candidate) {
                is AgentStep.ToolOutcomeRecorded -> candidate.toolCallId == toolCallId
                is AgentStep.ToolRejected -> candidate.toolCallId == toolCallId
                is AgentStep.ToolCompleted -> candidate.toolCallId == toolCallId
                is AgentStep.ToolFailed -> candidate.toolCallId == toolCallId
                else -> false
            }
        } ?: return null
        return when (step) {
            is AgentStep.ToolOutcomeRecorded -> DurableToolResult(step.toolId, step.output)
            is AgentStep.ToolRejected -> DurableToolResult(step.toolId, step.modelResult)
            is AgentStep.ToolCompleted -> DurableToolResult(step.toolId, step.resultSummary)
            is AgentStep.ToolFailed -> DurableToolResult(
                step.toolId,
                "tool_result: status=failed reason_code=PERMANENT retryable=false\nreason: " +
                    step.reason.replace('\n', ' ').take(512),
            )
            else -> null
        }
    }
}
