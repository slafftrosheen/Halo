package com.halo.runtime.cognition.interaction

import com.halo.cognition.language.DayRelation
import com.halo.cognition.language.TemporalValue
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.TemporalInputMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * RUNTIME-019 bounded interaction vocabulary.
 *
 * A deliberately small, typed content model — never an arbitrary component
 * tree. Runtime describes WHAT is being asked; hosts decide how each kind is
 * rendered (sheet, dialog, overlay, spoken question, remote display). If a
 * future need does not fit these primitives, extend the vocabulary with a new
 * typed kind rather than adding generic containers.
 */

/** Stable bounded choice option. */
data class InteractionChoice(
    val id: String,
    val label: String,
    val description: String? = null,
)

/** Portable constraints for free-form text entry. */
data class TextInputConstraints(
    val minLength: Int = 0,
    val maxLength: Int = 2000,
    val singleLine: Boolean = false,
    /** Obscure input; do not echo in logs; avoid casual persistence. */
    val sensitive: Boolean = false,
)

/**
 * Temporal answer using java.time primitives. Halo's parser-side
 * [TemporalValue] (TimeOfDay/InstantValue/DayPart) represents fuzzy spoken
 * expressions; picker results need full calendar dates, so interaction
 * responses carry this explicit model. Conversions back to [TemporalValue]
 * are provided so downstream resolvers keep one temporal language.
 */
sealed interface TemporalInputValue {
    data class InstantValue(val instant: Instant) : TemporalInputValue
    data class LocalDateValue(val date: LocalDate) : TemporalInputValue
    data class LocalTimeValue(val time: LocalTime, val dayRelation: DayRelation = DayRelation.TODAY) : TemporalInputValue
    data class DateTimeValue(val date: LocalDate, val time: LocalTime) : TemporalInputValue
}

/** Which file kinds a host picker may offer for a FILE_SELECTION surface. */
enum class FileKind {
    IMAGE,
    DOCUMENT,
    PDF,
    AUDIO,
    VIDEO,
    ANY,
}

/**
 * Portable reference to a host-selected file. Hosts resolve the reference
 * through their own content-access layer; Runtime never sees platform Uris or
 * raw filesystem paths as contract. Persistent use goes through explicit
 * import into the Workspace.
 */
data class InteractionFileReference(
    val referenceId: String,
    val displayName: String? = null,
    val mimeType: String? = null,
)

/**
 * Semantic progress phase for PROGRESS surfaces. Derived from execution
 * state by the Runtime projector; never invented by hosts or the model.
 */
enum class InteractionProgressPhase {
    PREPARING,
    THINKING,
    WAITING,
    ACTING,
    TRANSFERRING,
    FINALIZING,
}

/** Semantic trust requirement for resolving an interaction. */
enum class InteractionTrustRequirement {
    ANY_AUTHENTICATED_USER_INPUT,
    EXPLICIT_CONFIRMATION,
    STRONG_CONFIRMATION,
}

sealed interface InteractionContent {

    /** Yes/no decision, e.g. an approval. */
    data class Confirmation(
        val subject: String,
        val detail: String? = null,
        val risk: InteractionRiskPresentation? = null,
        val cancelScope: InteractionCancelScope = InteractionCancelScope.INPUT_ONLY,
        val speech: InteractionSpeechPresentation? = null,
    ) : InteractionContent

    /** Select one or more bounded options. */
    data class Choice(
        val prompt: String,
        val options: List<InteractionChoice>,
        val allowMultiple: Boolean = false,
        val cancelScope: InteractionCancelScope = InteractionCancelScope.INPUT_ONLY,
        val speech: InteractionSpeechPresentation? = null,
    ) : InteractionContent

    /** Free-form text answer. */
    data class TextInput(
        val prompt: String,
        val placeholder: String? = null,
        val initialValue: String? = null,
        val constraints: TextInputConstraints = TextInputConstraints(),
        val cancelScope: InteractionCancelScope = InteractionCancelScope.INPUT_ONLY,
        val speech: InteractionSpeechPresentation? = null,
    ) : InteractionContent

    /** Date/time answer using [TemporalInputValue]. */
    data class TemporalInput(
        val prompt: String,
        val initial: TemporalInputValue? = null,
        val mode: TemporalInputMode,
        val cancelScope: InteractionCancelScope = InteractionCancelScope.INPUT_ONLY,
        val speech: InteractionSpeechPresentation? = null,
    ) : InteractionContent

    /** Host-native file selection. */
    data class FileSelection(
        val prompt: String,
        val acceptedKinds: Set<FileKind>,
        val allowMultiple: Boolean = false,
        val cancelScope: InteractionCancelScope = InteractionCancelScope.INPUT_ONLY,
        val speech: InteractionSpeechPresentation? = null,
    ) : InteractionContent

    /** Execution progress; derived from run state, replaced in place. */
    data class Progress(
        val label: String,
        val phase: InteractionProgressPhase,
        val progress: Float? = null,
        val detail: String? = null,
        val cancellable: Boolean = true,
    ) : InteractionContent

    /**
     * Completed durable action. [truth] comes from the journal
     * ([ActionTruth]) or [ToolExecutionResult]-equivalent records — never
     * from model prose. UNKNOWN stays visibly unknown.
     */
    data class Result(
        val headline: String,
        val detail: String? = null,
        val truth: ActionTruth? = null,
        val followUps: List<InteractionFollowUp> = emptyList(),
    ) : InteractionContent

    /** Structured failure. No stack traces, class names, or raw exceptions. */
    data class Error(
        val headline: String,
        val detail: String,
        val recoverable: Boolean = false,
        val followUps: List<InteractionFollowUp> = emptyList(),
    ) : InteractionContent
}
