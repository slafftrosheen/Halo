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

    fun fromJournal(run: AgentRun): AgentContinuation {
        val messages = mutableListOf<AgentContinuationMessage>()
        messages += AgentContinuationMessage(AgentContinuationRole.USER, run.request)

        val turns = run.steps.filterIsInstance<AgentStep.ProviderTurnCompleted>()
            .sortedBy { it.createdAt.toEpochMilli() }
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
                outcomeFor(run, call.toolCallId)?.let { outcome ->
                    messages += AgentContinuationMessage(
                        role = AgentContinuationRole.TOOL,
                        text = outcome.output,
                        toolCallId = outcome.toolCallId,
                        toolName = outcome.toolId,
                    )
                }
            }
        }

        val lastTurn = turns.lastOrNull()
        val started = run.steps.filterIsInstance<AgentStep.ProviderTurnStarted>().lastOrNull()
        return AgentContinuation(
            messages = messages,
            exposedToolIds = started?.exposedToolIds ?: emptySet(),
            providerId = started?.providerId,
            modelId = started?.modelId,
        )
    }

    private fun outcomeFor(run: AgentRun, toolCallId: String): AgentStep.ToolOutcomeRecorded? =
        run.steps.filterIsInstance<AgentStep.ToolOutcomeRecorded>()
            .lastOrNull { it.toolCallId == toolCallId }
}
