package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.agent.ActionTruth

/**
 * AGENTIC-001 machine-readable tool result header.
 *
 * A small local model must not have to infer control flow from arbitrary English.
 * Every result Halo hands back to a model therefore begins with a stable,
 * parseable header, followed by human-readable detail:
 *
 * ```
 * tool_result:
 * status=success
 * action_truth=DISPATCHED
 * reason_code=NONE
 *
 * details:
 * ...
 * ```
 *
 * [ActionTruth] remains the authority on what actually happened: `DISPATCHED`
 * never becomes `CONFIRMED` merely because `startActivity` returned.
 */
object ToolResultEnvelope {

    const val NONE = "NONE"

    private const val HEADER = "tool_result:"

    /**
     * Guarantee the machine-readable header on a tool-authored success output.
     * Host tools keep producing their own human-readable detail; this only makes the
     * control-flow fields present and consistent for every executor, including ones
     * that predate the envelope.
     */
    fun ensureEnveloped(
        output: String,
        actionTruth: ActionTruth,
        reasonCode: String = NONE,
    ): String {
        val trimmed = output.trimStart()
        if (trimmed.startsWith(HEADER)) {
            val headerLines = trimmed.lineSequence().take(4).toList()
            if (headerLines == listOf(
                    HEADER, "status=success", "action_truth=" + actionTruth.name, "reason_code=" + reasonCode
                )
            ) return output
        }
        val detail = if (trimmed.startsWith(HEADER)) {
            "Unverified nested tool output (not proof of execution):\n$trimmed"
        } else trimmed
        return built(detail, status = "success", actionTruth = actionTruth, reasonCode = reasonCode)
    }

    private fun built(details: String, status: String, actionTruth: ActionTruth, reasonCode: String): String =
        buildString {
            append(HEADER).append('\n')
            append("status=").append(status).append('\n')
            append("action_truth=").append(actionTruth.name).append('\n')
            append("reason_code=").append(reasonCode).append('\n')
            if (details.isNotBlank()) {
                append('\n')
                append("details:\n")
                append(details)
            } else {
                append("content_status=empty\n")
            }
        }.trimEnd()

    fun success(
        actionTruth: ActionTruth,
        reasonCode: String = NONE,
        details: String? = null,
        retryable: Boolean? = null,
        requiresUserAction: Boolean? = null,
    ): String = build(
        status = "success",
        actionTruth = actionTruth.name,
        reasonCode = reasonCode,
        retryable = retryable,
        requiresUserAction = requiresUserAction,
        details = details,
    )

    fun failure(
        reasonCode: String,
        actionTruth: ActionTruth = ActionTruth.FAILED,
        retryable: Boolean = false,
        requiresUserAction: Boolean = false,
        details: String? = null,
    ): String = build(
        status = "failed",
        actionTruth = actionTruth.name,
        reasonCode = reasonCode,
        retryable = retryable,
        requiresUserAction = requiresUserAction,
        details = details,
    )

    fun rejected(
        reasonCode: String,
        retryable: Boolean = false,
        requiresUserAction: Boolean = false,
        details: String? = null,
    ): String = build(
        status = "rejected",
        actionTruth = ActionTruth.PROPOSED.name,
        reasonCode = reasonCode,
        retryable = retryable,
        requiresUserAction = requiresUserAction,
        details = details,
    )

    private fun build(
        status: String,
        actionTruth: String,
        reasonCode: String,
        retryable: Boolean?,
        requiresUserAction: Boolean?,
        details: String?,
    ): String = buildString {
        append("tool_result:\n")
        append("status=").append(status).append('\n')
        append("action_truth=").append(actionTruth).append('\n')
        append("reason_code=").append(reasonCode).append('\n')
        retryable?.let { append("retryable=").append(it).append('\n') }
        requiresUserAction?.let { append("requires_user_action=").append(it).append('\n') }
        if (status == "success" && details.isNullOrBlank()) append("content_status=empty\n")
        if (!details.isNullOrBlank()) {
            append('\n')
            append("details:\n")
            append(details)
        }
    }.trimEnd()
}
