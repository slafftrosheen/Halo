package com.halo.runtime.cognition.tools

sealed interface ToolExecutionResult {

    data class Success(
        val toolCallId: String,
        val toolId: String,
        val output: String,
        val actionTruth: com.halo.runtime.cognition.agent.ActionTruth = com.halo.runtime.cognition.agent.ActionTruth.CONFIRMED,
        val outputMetadata: ToolOutputMetadata? = null,
        val payloadRef: com.halo.runtime.cognition.context.PayloadRef? = null,
    ) : ToolExecutionResult

    data class Rejected(
        val toolCallId: String,
        val toolId: String,
        val reason: String,
        /**
         * AGENTIC-001: why the tool refused, in a stable category. Executors that
         * know better than "invalid arguments" must say so, otherwise a disabled
         * service or a missing permission reaches the model as a schema error.
         */
        val kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS,
    ) : ToolExecutionResult

    data class Failed(
        val toolCallId: String,
        val toolId: String,
        val message: String,
        val completionUnknown: Boolean = false,
        val failureKind: ToolFailureKind = ToolFailureKind.UNKNOWN,
        val code: String = failureKind.name,
        val hint: String? = null,
        val payload: String? = null,
    ) : ToolExecutionResult
}

data class ToolOutputMetadata(
    val truncated: Boolean,
    val originalChars: Int,
    val originalUtf8Bytes: Int,
)

/**
 * Stable rejection categories a tool executor may report. Mapped to the durable
 * [com.halo.runtime.cognition.agent.ToolRejectionCode] vocabulary by the turn
 * executor so the journal and the model-visible result agree.
 */
enum class ToolRejectionKind {
    TOOL_UNKNOWN,
    TOOL_DISABLED,
    INVALID_ARGUMENTS,
    CAPABILITY_UNAVAILABLE,
    PERMISSION_REQUIRED,
    USER_REJECTED,
    RESOURCE_UNAVAILABLE,
    INTERACTION_CANCELLED,
    UNSUPPORTED_OPERATION,
    TRANSIENT_FAILURE,
}

/** Result helpers keeping every host action result on the shared envelope. */
fun ToolExecutionResult.Rejected.enveloped(
    requiresUserAction: Boolean = kind == ToolRejectionKind.PERMISSION_REQUIRED ||
        kind == ToolRejectionKind.UNSUPPORTED_OPERATION,
): ToolExecutionResult.Rejected = copy(
    reason = ToolResultEnvelope.rejected(
        reasonCode = kind.name,
        retryable = kind == ToolRejectionKind.TRANSIENT_FAILURE ||
            kind == ToolRejectionKind.RESOURCE_UNAVAILABLE,
        requiresUserAction = requiresUserAction,
        details = reason.replace('\n', ' ').take(512),
    ),
)

/** Convenience: budgeted, enveloped failure with an explicit reason code. */
fun ToolExecutionResult.Failed.enveloped(): ToolExecutionResult.Failed {
    val envelope = ToolResultEnvelope.failure(
        reasonCode = code,
        actionTruth = if (completionUnknown) com.halo.runtime.cognition.agent.ActionTruth.UNKNOWN
        else com.halo.runtime.cognition.agent.ActionTruth.FAILED,
        retryable = failureKind == ToolFailureKind.TRANSIENT,
        requiresUserAction = failureKind == ToolFailureKind.PERMISSION,
        details = buildString {
            append(message.replace('\n', ' ').take(512))
            hint?.let { append("\nhint: ").append(it.replace('\n', ' ').take(512)) }
            payload?.let { append("\npayload:\n").append(it) }
        },
    )
    return copy(message = envelope)
}
