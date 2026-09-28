package com.halo.app.di.tools.host

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.halo.host.android.capability.AlarmCommandResolver
import com.halo.host.android.capability.AlarmExecutionPlan
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.runtime.cognition.tools.ToolRejectionKind
import com.halo.runtime.cognition.tools.ToolResultEnvelope
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

class AlarmTools(private val context: Context) : ToolExecutor {
    private val resolver = AlarmCommandResolver()

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

        return when (val plan = resolver.resolve(null, expression, label)) {
            is AlarmExecutionPlan.SystemAlarm -> {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, plan.hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, plan.minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, plan.label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) == null) {
                    return reject(
                        "This device has no app that can set an alarm",
                        ToolRejectionKind.CAPABILITY_UNAVAILABLE,
                    )
                }
                context.startActivity(intent)
                ToolExecutionResult.Success(
                    toolCallId = toolCall.id,
                    toolId = toolCall.functionName,
                    output = ToolResultEnvelope.success(
                        actionTruth = ActionTruth.DISPATCHED,
                        details = buildString {
                            append("alarm_hour=").append(plan.hour).append('\n')
                            append("alarm_minute=").append(plan.minute).append('\n')
                            append("label=").append(plan.label ?: "Halo alarm").append('\n')
                            append(
                                "note: The Android alarm-clock app received the request. Android reports no " +
                                    "completion result, so this stays DISPATCHED; do not tell the user you " +
                                    "confirmed the alarm is set."
                            )
                        },
                    ),
                    actionTruth = ActionTruth.DISPATCHED,
                )
            }

            is AlarmExecutionPlan.HaloExactAlarm -> reject(
                "Halo cannot schedule an alarm for a specific future date yet. " +
                    "Say so plainly and offer to set a reminder instead; never claim the alarm was set.",
                ToolRejectionKind.UNSUPPORTED_OPERATION,
            )

            is AlarmExecutionPlan.Clarification -> reject(
                plan.prompt,
                ToolRejectionKind.INVALID_ARGUMENTS,
            )
        }
    }
}
