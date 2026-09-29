package com.halo.app.di.tools.host

import android.content.Context
import com.halo.host.android.capability.AndroidAlarmAction
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.runtime.cognition.tools.ToolRejectionKind
import com.halo.runtime.cognition.tools.ToolResultEnvelope
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

class AlarmTools(private val context: Context) : ToolExecutor {
    private val alarmAction = AndroidAlarmAction(context)

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            id = "halo_set_alarm",
            name = "halo_set_alarm",
            description = "Set a system alarm for a plain clock time (for example '7 AM' or '7:30 PM'). " +
                "Do not use this for a specific future date; that is reported as unsupported.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "timeExpression" to mapOf("type" to "string", "description" to "Natural language clock time like '7 AM' or 'tomorrow at 7:15'"),
                    "label" to mapOf("type" to "string", "description" to "Optional label for the alarm")
                ),
                "required" to listOf("timeExpression"),
                "additionalProperties" to false
            )
        )
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: ToolRejectionKind) =
            ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)

        if (toolCall.functionName != "halo_set_alarm") return reject("Unknown tool", ToolRejectionKind.TOOL_UNKNOWN)

        val expression = toolCall.arguments["timeExpression"] as? String
            ?: return reject("timeExpression is required, for example '7 AM'", ToolRejectionKind.INVALID_ARGUMENTS)
        val label = toolCall.arguments["label"] as? String ?: "Halo alarm"

        return when (val outcome = alarmAction.execute(null, expression, label, skipUi = true)) {
            is com.halo.runtime.cognition.Outcome.Dispatched -> {
                ToolExecutionResult.Success(
                    toolCallId = toolCall.id,
                    toolId = toolCall.functionName,
                    output = ToolResultEnvelope.success(
                        actionTruth = ActionTruth.DISPATCHED,
                        details = buildString {
                            append("target=").append(outcome.target ?: "Android Clock").append('\n')
                            append("note=").append(outcome.message)
                        },
                    ),
                    actionTruth = ActionTruth.DISPATCHED,
                )
            }

            is com.halo.runtime.cognition.Outcome.NeedsClarification -> reject(outcome.prompt, ToolRejectionKind.INVALID_ARGUMENTS)
            is com.halo.runtime.cognition.Outcome.PermanentFailure -> reject(outcome.reason, ToolRejectionKind.UNSUPPORTED_OPERATION)
            is com.halo.runtime.cognition.Outcome.RetryableFailure -> reject(outcome.reason, ToolRejectionKind.CAPABILITY_UNAVAILABLE)
            else -> reject("Alarm dispatch did not complete", ToolRejectionKind.TRANSIENT_FAILURE)
        }
    }
}
