package com.halo.runtime.cognition.interaction

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.agent.AgentInputRequestId
import com.halo.runtime.cognition.agent.AgentRunId
import java.time.Instant

/**
 * RUNTIME-019 portable interaction protocol.
 *
 * Halo Runtime asks for human interaction; it does not ask a host to show a
 * dialog. The Runtime owns what is being asked, why, which responses are
 * legal, which execution the interaction belongs to, and whether it expired.
 * Hosts own how it looks, where it appears, and how input is collected.
 *
 * This file contains the renderable projection: an [InteractionSurface] is a
 * semantic object rebuilt from the durable journal ([AgentInputRequest] and
 * run state). It must never carry presentation details (colors, layout,
 * component names, platform types) and must never encode an arbitrary UI tree.
 *
 * No Android imports are permitted in this package.
 */

/** Identity of one renderable interaction surface. */
@JvmInline
value class InteractionSurfaceId(val value: String) {
    override fun toString(): String = value
}

/** Identity of one semantic action button/affordance on a surface. */
@JvmInline
value class InteractionActionId(val value: String) {
    override fun toString(): String = value
}

/**
 * Schema version of the surface contract. Hosts that encounter a newer
 * schema version they do not understand must fall back to a generic
 * "unsupported interaction" state instead of guessing.
 */
const val CURRENT_SURFACE_SCHEMA_VERSION: Int = 1

/** Semantic kind of an interaction. Never a host presentation concept. */
enum class InteractionSurfaceKind {
    CONFIRMATION,
    CLARIFICATION,
    CHOICE,
    TEXT_INPUT,
    TEMPORAL_INPUT,
    FILE_SELECTION,
    PROGRESS,
    RESULT,
    ERROR,
}

/** What a surface is derived from. Not a vendor or product name. */
enum class InteractionSurfaceSource {
    AGENT_INPUT,
    AGENT_RUN,
    TOOL_RESULT,
    SYSTEM,
}

data class InteractionSurfaceProvenance(
    val source: InteractionSurfaceSource,
    val sourceId: String? = null,
)

/**
 * Semantic action the host may offer. The label is display copy; [semantic]
 * is what the Runtime understands. Hosts order and style actions by semantic.
 */
data class InteractionAction(
    val id: InteractionActionId,
    val label: String,
    val semantic: InteractionActionSemantic,
)

enum class InteractionActionSemantic {
    CONFIRM,
    DENY,
    SUBMIT,
    CANCEL,
    DISMISS,
    RETRY,
    CONTINUE,
}

/**
 * Human-readable risk framing for confirmations. Renderers must show neutral
 * copy (e.g. "This sends a message outside Halo"), never raw enum names.
 */
enum class InteractionRiskLevel {
    READ_ONLY,
    REVERSIBLE,
    EXTERNAL_SIDE_EFFECT,
    SENSITIVE,
    DESTRUCTIVE,
}

data class InteractionRiskPresentation(
    val level: InteractionRiskLevel,
    /** Optional plain-language consequence sentence; no enum names. */
    val summary: String? = null,
)

/** How urgently the host should present a surface. */
enum class InteractionUrgency {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL,
}

/**
 * Modality through which a surface may be presented or resolved.
 * VISUAL = a graphical host surface; VOICE = spoken prompt/answer;
 * TEXT = chat-like input; DISPLAY = remote/ambient display.
 */
enum class InteractionModality {
    VISUAL,
    VOICE,
    TEXT,
    DISPLAY,
}

/**
 * Runtime intent for presentation, expressed without host UI concepts.
 * Hosts decide sheets vs dialogs vs overlays vs notifications.
 */
data class InteractionPresentationPolicy(
    val urgency: InteractionUrgency = InteractionUrgency.NORMAL,
    val preferredModalities: Set<InteractionModality> = setOf(InteractionModality.VISUAL),
    /** Modalities that MUST be available to resolve the surface (e.g. visual-only for destructive actions). */
    val requiredModalities: Set<InteractionModality> = emptySet(),
    val requiresForegroundAttention: Boolean = false,
)

/**
 * Optional spoken variant of surface copy. Visual labels often read awkwardly
 * aloud; this lets a voice host speak a natural question without changing the
 * semantic content. Purely additive metadata — absence means hosts derive
 * their own spoken form from title/message.
 */
data class InteractionSpeechPresentation(
    val prompt: String,
    val concisePrompt: String? = null,
)

/** Scope of a CANCEL action: abandon only the pending input, or the whole run. */
enum class InteractionCancelScope {
    INPUT_ONLY,
    RUN,
}

/**
 * Semantic follow-up affordance on result surfaces. Hosts translate supported
 * follow-ups into native navigation. No arbitrary URIs or intents.
 */
sealed interface InteractionFollowUp {
    data class OpenWorkspaceObject(val objectId: String) : InteractionFollowUp
    data class OpenRunDetails(val runId: AgentRunId) : InteractionFollowUp
    data object Dismiss : InteractionFollowUp
}

/**
 * A renderable interaction. Derived from durable execution state:
 * - input-required surfaces are a stable projection of one [AgentInputRequest]
 *   (surface id = deterministic function of the input request id);
 * - progress/result surfaces are projections of run/invocation state with
 *   stable run-scoped ids.
 *
 * Surfaces are presentation state, not durable truth: they can be rebuilt
 * from the journal after process death and dismissed freely by hosts.
 */
data class InteractionSurface(
    val id: InteractionSurfaceId,
    val runId: AgentRunId?,
    val interactionId: InteractionId?,
    val inputRequestId: AgentInputRequestId?,

    val kind: InteractionSurfaceKind,
    val title: String?,
    val message: String?,

    val content: InteractionContent,
    val actions: List<InteractionAction>,

    val presentation: InteractionPresentationPolicy,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val schemaVersion: Int = CURRENT_SURFACE_SCHEMA_VERSION,
    val provenance: InteractionSurfaceProvenance,
) {
    /** Speech metadata for voice-first hosts, when the producer supplied one. */
    val speech: InteractionSpeechPresentation?
        get() = when (val c = content) {
            is InteractionContent.Confirmation -> c.speech
            is InteractionContent.Choice -> c.speech
            is InteractionContent.TextInput -> c.speech
            is InteractionContent.TemporalInput -> c.speech
            else -> null
        }
}
