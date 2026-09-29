package com.halo.app.di.tools.host

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
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
        ),
        // RUNTIME-056: a countdown is a different intent from an alarm, and "start a
        // 10 minute timer" is one of the most common spoken phone requests.
        ToolDefinition(
            id = "halo_timer",
            name = "halo_timer",
            description = "Start a countdown timer in the system clock app. Use this for a relative duration such as " +
                "'10 minutes'; use halo_set_alarm for a clock time or a wake-up.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "seconds" to mapOf("type" to "integer", "minimum" to 1, "maximum" to MAX_TIMER_SECONDS),
                    "label" to mapOf("type" to "string", "maxLength" to 80),
                ),
                "required" to listOf("seconds"),
                "additionalProperties" to false,
            ),
        )
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: ToolRejectionKind) =
            ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)

        if (toolCall.functionName == "halo_timer") return startTimer(toolCall)

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

    /**
     * Timers are dispatched to the system clock app. Halo does not own the countdown, so
     * the receipt is DISPATCHED: the timer app was opened with the request, and claiming
     * more than that would be inventing evidence.
     */
    private fun startTimer(toolCall: ToolCall): ToolExecutionResult {
        val seconds = (toolCall.arguments["seconds"] as? Number)?.toInt()
            ?: return ToolExecutionResult.Rejected(
                toolCall.id, toolCall.functionName,
                "seconds is required", ToolRejectionKind.INVALID_ARGUMENTS,
            )
        if (seconds !in 1..MAX_TIMER_SECONDS) {
            return ToolExecutionResult.Rejected(
                toolCall.id, toolCall.functionName,
                "seconds must be between 1 and $MAX_TIMER_SECONDS", ToolRejectionKind.INVALID_ARGUMENTS,
            )
        }
        val label = (toolCall.arguments["label"] as? String)?.takeIf { it.isNotBlank() }?.take(80)
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(AlarmClock.EXTRA_MESSAGE, label ?: "Halo timer")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            return ToolExecutionResult.Rejected(
                toolCall.id, toolCall.functionName,
                "No installed app accepts a timer request on this device",
                ToolRejectionKind.CAPABILITY_UNAVAILABLE,
            )
        }
        return try {
            context.startActivity(intent)
            ToolExecutionResult.Success(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                output = ToolResultEnvelope.success(
                    actionTruth = ActionTruth.DISPATCHED,
                    details = buildString {
                        append("seconds=").append(seconds).append('\n')
                        append("target=").append(intent.resolveActivity(context.packageManager)?.packageName ?: "clock app").append('\n')
                        append("note=The timer is owned by the clock app; Halo did not verify it is counting down.")
                    },
                ),
                actionTruth = ActionTruth.DISPATCHED,
            )
        } catch (_: ActivityNotFoundException) {
            ToolExecutionResult.Failed(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                message = "The timer app could not be opened",
                failureKind = com.halo.runtime.cognition.tools.ToolFailureKind.UNAVAILABLE,
                code = "TIMER_APP_UNAVAILABLE",
            )
        }
    }

    private companion object {
        /** A day is the practical ceiling for a countdown; longer belongs to an alarm. */
        const val MAX_TIMER_SECONDS = 86_400
    }
}
