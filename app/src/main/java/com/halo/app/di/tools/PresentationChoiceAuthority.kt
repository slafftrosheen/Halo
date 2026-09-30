package com.halo.app.di.tools

import com.halo.runtime.cognition.agent.AgentInputRequest
import com.halo.runtime.cognition.agent.AgentRunStore
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.agent.ExpectedInput
import com.halo.runtime.cognition.interaction.PresentationChoice
import com.halo.runtime.cognition.interaction.PresentationValidator

/**
 * MINING-P2-001 choice authority.
 *
 * A generated `ChoiceGroup` may ONLY resolve an input request that already exists in the durable
 * journal. This class is the single place that answers two questions from that journal:
 *
 * - "which option ids does this pending request actually allow?"
 * - "does this exact request + option-id set exist and remain unresolved?"
 *
 * A choice that fails either question is rejected, so a model-generated surface can never invent a
 * new decision, a new action, or a new option.
 */
class PresentationChoiceAuthority(private val store: AgentRunStore) {

    data class PendingChoice(
        val requestId: String,
        val question: String,
        val options: List<PresentationChoice>,
        val allowMultiple: Boolean,
    )

    /** Every unresolved durable choice request. Bounded by the number of non-terminal runs. */
    suspend fun pendingChoices(): List<PendingChoice> {
        val runs = runCatching { store.nonTerminalRuns() }.getOrDefault(emptyList())
        val result = mutableListOf<PendingChoice>()
        runs.forEach { run ->
            val requested = run.steps.filterIsInstance<AgentStep.InputRequested>()
            val resolved = run.steps.filterIsInstance<AgentStep.InputResolved>().map { it.requestId }.toSet()
            requested.forEach { step ->
                if (step.requestId in resolved) return@forEach
                val clarification = step.input as? AgentInputRequest.Clarification ?: return@forEach
                val choice = clarification.expected as? ExpectedInput.Choice ?: return@forEach
                result += PendingChoice(
                    requestId = step.requestId.value,
                    question = clarification.question,
                    options = choice.options.map { option ->
                        PresentationChoice(
                            optionId = option.id,
                            label = option.label,
                            description = option.description,
                        )
                    },
                    allowMultiple = choice.allowMultiple,
                )
            }
        }
        return result
    }

    suspend fun pendingOptions(requestId: String): List<PresentationChoice>? =
        pendingChoices().firstOrNull { it.requestId == requestId }?.options

    /** The resolver handed to the presentation validator. */
    suspend fun binding(): PresentationValidator.ChoiceBindingResolver {
        val requests = pendingChoices().associateBy { it.requestId }
        return PresentationValidator.ChoiceBindingResolver { requestId, optionIds ->
            val pending = requests[requestId] ?: return@ChoiceBindingResolver false
            optionIds.isNotEmpty() && optionIds.all { id -> pending.options.any { it.optionId == id } }
        }
    }
}
