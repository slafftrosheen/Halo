package com.halo.runtime.cognition.agent

import com.halo.runtime.cognition.tools.ToolId
import java.time.Instant

/**
 * RUNTIME-018 durable pending input; RUNTIME-019 extends the request with a
 * structured [ExpectedInput] describing WHAT INFORMATION is required — never
 * the UI. Renderers derive host-native presentations from it.
 *
 * A run can suspend waiting for user input without holding a live coroutine.
 * The request lives in the journal; process death leaves it pending and
 * recovery restores it as [AgentRunState.WAITING_FOR_APPROVAL]/
 * [AgentRunState.WAITING_FOR_CLARIFICATION].
 */
sealed interface AgentInputRequest {
    val id: AgentInputRequestId
    val runId: AgentRunId
    val createdAt: Instant
    /** Durable expiry policy; presentation may display it but never owns it. */
    val expiresAt: Instant?

    data class Approval(
        override val id: AgentInputRequestId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        override val expiresAt: Instant? = null,
        val invocationId: ToolInvocationId,
        val toolId: ToolId,
        /** Human-readable action summary; never contains raw secret arguments. */
        val action: String,
        val detail: String? = null,
        val risk: String,
        /** Digest of the exact approved arguments; mismatch invalidates the approval. */
        val argumentsDigest: String,
        /** Spoken variant for voice-first hosts; plain semantic content. */
        val speechPrompt: String? = null,
    ) : AgentInputRequest {
        val expectedInput: ExpectedInput get() = ExpectedInput.Confirmation
    }

    data class Clarification(
        override val id: AgentInputRequestId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        override val expiresAt: Instant? = null,
        val question: String,
        val field: String,
        /** Structured expected response; the default is free-form text. */
        val expected: ExpectedInput = ExpectedInput.Text(),
        /** Spoken variant for voice-first hosts; plain semantic content. */
        val speechPrompt: String? = null,
    ) : AgentInputRequest
}

@JvmInline
value class AgentInputRequestId(val value: String) {
    override fun toString(): String = value
}

/**
 * RUNTIME-019: what information a durable input request needs. Portable and
 * bounded; renderers map it onto native pickers/dialogs/voice prompts.
 */
sealed interface ExpectedInput {
    /** Yes/no (approve/deny). */
    data object Confirmation : ExpectedInput

    data class Text(
        val placeholder: String? = null,
        val initialValue: String? = null,
        val minLength: Int = 0,
        val maxLength: Int = 2000,
        val singleLine: Boolean = false,
        /** Do not echo in logs; obscure input; avoid casual persistence. */
        val sensitive: Boolean = false,
    ) : ExpectedInput

    data class Choice(
        val options: List<ExpectedChoice>,
        val allowMultiple: Boolean = false,
    ) : ExpectedInput {
        init { require(options.size <= MAX_CHOICES) { "Too many choices" } }
        companion object { const val MAX_CHOICES = 20 }
    }

    /** Temporal answers use Halo's own TemporalValue model, not platform pickers. */
    data class Temporal(val mode: TemporalInputMode = TemporalInputMode.DATE_TIME) : ExpectedInput

    data class Files(
        val acceptedKinds: Set<FileKind> = setOf(FileKind.ANY),
        val allowMultiple: Boolean = false,
    ) : ExpectedInput
}

data class ExpectedChoice(
    val id: String,
    val label: String,
    val description: String? = null,
)

enum class TemporalInputMode { DATE, TIME, DATE_TIME }

enum class FileKind { IMAGE, DOCUMENT, PDF, AUDIO, VIDEO, ANY }

sealed interface AgentInputResolution {
    data object Approved : AgentInputResolution
    data object Denied : AgentInputResolution
    data class Clarified(val value: String) : AgentInputResolution
    data object Cancelled : AgentInputResolution
    data object Expired : AgentInputResolution
}

/**
 * Canonical durable storage form of an input resolution — the string persisted
 * as `InputResolved.resolution`. Shared by the Room journal and the recovery
 * continuation so encoders never drift.
 */
object AgentInputResolutions {
    const val EXPIRED = "Expired"

    fun encode(resolution: AgentInputResolution): String = when (resolution) {
        is AgentInputResolution.Approved -> "Approved"
        is AgentInputResolution.Denied -> "Denied"
        is AgentInputResolution.Cancelled -> "Cancelled"
        is AgentInputResolution.Expired -> EXPIRED
        is AgentInputResolution.Clarified -> "Clarified:" + resolution.value
    }
}
