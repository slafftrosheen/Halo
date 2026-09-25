package com.halo.runtime.cognition.interaction

import com.halo.runtime.cognition.agent.AgentInputResolution
import com.halo.runtime.cognition.agent.AgentInputRequest

/**
 * RUNTIME-019 typed host responses to an [InteractionSurface].
 *
 * Host renderers produce these; the Runtime validates them against the active
 * surface, its durable [AgentInputRequest], and expiry — then journals one
 * atomic resolution. Hosts never call execution APIs directly.
 */
sealed interface InteractionResponse {
    val surfaceId: InteractionSurfaceId

    /** User answered "yes" to a Confirmation (or tapped a CONFIRM semantic action). */
    data class Confirmed(
        override val surfaceId: InteractionSurfaceId,
    ) : InteractionResponse

    /** User answered "no" to a Confirmation (DENY semantic action). */
    data class Denied(
        override val surfaceId: InteractionSurfaceId,
    ) : InteractionResponse

    /** User picked bounded options on a Choice surface. */
    data class ChoiceSelected(
        override val surfaceId: InteractionSurfaceId,
        val optionIds: Set<String>,
    ) : InteractionResponse

    /** User submitted text on a TextInput surface. */
    data class TextSubmitted(
        override val surfaceId: InteractionSurfaceId,
        val value: String,
    ) : InteractionResponse

    /** User submitted a date/time on a TemporalInput surface. */
    data class TemporalSubmitted(
        override val surfaceId: InteractionSurfaceId,
        val value: TemporalInputValue,
    ) : InteractionResponse

    /** User selected host files on a FileSelection surface. */
    data class FilesSelected(
        override val surfaceId: InteractionSurfaceId,
        val references: List<InteractionFileReference>,
    ) : InteractionResponse

    /**
     * User cancelled the interaction. Scope (input-only vs whole run) comes
     * from the surface content's [InteractionContent.cancelScope], not from
     * button text.
     */
    data class Cancelled(
        override val surfaceId: InteractionSurfaceId,
    ) : InteractionResponse

    /**
     * Host dismissed the presentation (swipe away, back gesture, overlay
     * hidden). NOT a denial and NOT a run cancellation: the durable request
     * stays pending unless surface policy explicitly resolves dismissal.
     */
    data class Dismissed(
        override val surfaceId: InteractionSurfaceId,
    ) : InteractionResponse
}

/**
 * Deterministic normalized textual form of a temporal answer. This is what a
 * [com.halo.runtime.cognition.agent.AgentInputResolution.Clarified] persists;
 * domain resolvers convert it back into Halo [TemporalValue] semantics.
 */
fun TemporalInputValue.normalized(): String = when (this) {
    is TemporalInputValue.InstantValue -> "instant:" + instant.toString()
    is TemporalInputValue.LocalDateValue -> "date:" + date.toString()
    is TemporalInputValue.LocalTimeValue -> "time:" + time.toString() + ":" + dayRelation.name
    is TemporalInputValue.DateTimeValue -> "datetime:" + date.toString() + "T" + time.toString()
}

/** The durable resolution each response maps onto. */
fun InteractionResponse.toResolution(
    content: InteractionContent,
): AgentInputResolution = when (this) {
    is InteractionResponse.Confirmed -> AgentInputResolution.Approved
    is InteractionResponse.Denied -> AgentInputResolution.Denied
    is InteractionResponse.ChoiceSelected -> {
        val choice = content as? InteractionContent.Choice
            ?: error("ChoiceSelected requires a Choice surface")
        AgentInputResolution.Clarified(value = choice.options.joinToString(separator = ",") { it.id })
    }
    is InteractionResponse.TextSubmitted -> AgentInputResolution.Clarified(value = value)
    is InteractionResponse.TemporalSubmitted -> AgentInputResolution.Clarified(value = value.normalized())
    is InteractionResponse.FilesSelected -> AgentInputResolution.Clarified(value = references.joinToString(separator = ",") { it.referenceId })
    is InteractionResponse.Cancelled -> AgentInputResolution.Cancelled
    is InteractionResponse.Dismissed -> error("Dismissed is presentation state and has no durable resolution")
}
