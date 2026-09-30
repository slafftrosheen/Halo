package com.halo.providers.litert.runtime

import com.halo.sdk.provider.RuntimeConversationMessage
import com.halo.sdk.provider.Role

/**
 * MINING-P0-001 a model-emitted native tool request is an *intent*, never an execution.
 *
 * LiteRT may emit a malformed or half-paired request. Halo must reject it visibly instead of
 * executing it, so a malformed native call becomes structured diagnostic truth on the existing
 * runtime path rather than a silent drop.
 */
enum class LiteRtToolCallRejection {
    /** The SDK produced a call with no function name. */
    MISSING_FUNCTION_NAME,

    /** The SDK produced a call with an empty/unusable argument object. */
    INCOMPLETE_ARGUMENTS,

    /** A call with the same identity arrived twice with different content. */
    CONFLICTING_DUPLICATE_ID,
    ;

    val label: String
        get() = when (this) {
            MISSING_FUNCTION_NAME -> "missing_function_name"
            INCOMPLETE_ARGUMENTS -> "incomplete_arguments"
            CONFLICTING_DUPLICATE_ID -> "conflicting_duplicate_id"
        }
}

/**
 * Content-free observation of the native tool channel for one generation.
 *
 * Counts and structural booleans only: never argument values, never generated text.
 */
data class LiteRtNativeToolChannelObservation(
    val emittedToolCalls: Int = 0,
    val acceptedToolCalls: Int = 0,
    val rejectedToolCalls: Int = 0,
    val duplicateSuppressed: Int = 0,
    /** True when every accepted call carried a usable function name and argument object. */
    val callIdComplete: Boolean = true,
    val argumentsStructurallyComplete: Boolean = true,
    /** Tool responses present in history for this turn. */
    val pairedResponseCount: Int = 0,
    /** Model tool calls in history with no matching tool response (half-paired after trimming). */
    val unpairedModelToolCalls: Int = 0,
    val rejections: List<LiteRtToolCallRejection> = emptyList(),
) {
    val observedNativeCall: Boolean get() = acceptedToolCalls > 0 || rejectedToolCalls > 0

    fun summaryLine(): String = buildString {
        append("emitted=$emittedToolCalls accepted=$acceptedToolCalls rejected=$rejectedToolCalls")
        append(" duplicateSuppressed=$duplicateSuppressed")
        append(" callIdComplete=$callIdComplete argumentsComplete=$argumentsStructurallyComplete")
        append(" pairedResponses=$pairedResponseCount unpaired=$unpairedModelToolCalls")
        if (rejections.isNotEmpty()) append(" reasons=${rejections.joinToString(",") { it.label }}")
    }
}

object LiteRtNativeToolCallValidator {

    /** Returns the rejection reason, or null when the emitted call is structurally usable. */
    fun rejectionFor(name: String?, arguments: Map<String, Any?>?): LiteRtToolCallRejection? = when {
        name.isNullOrBlank() -> LiteRtToolCallRejection.MISSING_FUNCTION_NAME
        arguments == null -> LiteRtToolCallRejection.INCOMPLETE_ARGUMENTS
        else -> null
    }

    /**
     * Inspects declared history for tool call/result pairing. A half-paired history after context
     * trimming is a real defect: the model asked for a tool and never saw its result.
     */
    fun pairing(messages: List<RuntimeConversationMessage>): Pair<Int, Int> {
        val declaredCalls = messages.sumOf { message ->
            if (message.role == Role.ASSISTANT) message.toolCalls.size else 0
        }
        val responses = messages.count { it.role == Role.TOOL }
        return responses to (declaredCalls - responses).coerceAtLeast(0)
    }
}
