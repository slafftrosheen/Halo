package com.halo.app.di.tools.host

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.os.Build
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
                    "label" to mapOf("type" to "string", "maxLength" to 80, "description" to "Optional label for the alarm"),
                    "repeatDays" to mapOf("type" to "array", "maxItems" to 7, "uniqueItems" to true,
                        "items" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 7)),
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
        ),
        ToolDefinition("halo_clock_view", "halo_clock_view",
            "Open the system clock's alarms or timers page. This does not verify clock contents.",
            mapOf("type" to "object", "properties" to mapOf(
                "action" to mapOf("type" to "string", "enum" to listOf("show_alarms", "show_timers"))),
                "required" to listOf("action"), "additionalProperties" to false)),
        ToolDefinition("halo_clock_modify", "halo_clock_modify",
            "Ask the clock app to dismiss the next alarm, snooze a ringing alarm or dismiss expired timers. Approval required. Result is dispatch only.",
            mapOf("type" to "object", "properties" to mapOf(
                "action" to mapOf("type" to "string", "enum" to listOf("dismiss_next_alarm", "snooze_alarm", "dismiss_expired_timers"))),
                "required" to listOf("action"), "additionalProperties" to false))
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: ToolRejectionKind) =
            ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)

        if (toolCall.functionName == "halo_timer") return startTimer(toolCall)
        if (toolCall.functionName in setOf("halo_clock_view", "halo_clock_modify")) return clockAction(toolCall)

        if (toolCall.functionName != "halo_set_alarm") return reject("Unknown tool", ToolRejectionKind.TOOL_UNKNOWN)
        if (toolCall.arguments.keys.any { it !in setOf("timeExpression", "label", "repeatDays") } ||
            toolCall.arguments["label"]?.let { it !is String } == true)
            return reject("Unsupported alarm arguments", ToolRejectionKind.INVALID_ARGUMENTS)

        val expression = toolCall.arguments["timeExpression"] as? String
            ?: return reject("timeExpression is required, for example '7 AM'", ToolRejectionKind.INVALID_ARGUMENTS)
        val label = toolCall.arguments["label"] as? String ?: "Halo alarm"
        val rawDays = toolCall.arguments["repeatDays"]
        if (rawDays != null && rawDays !is List<*>) return reject("repeatDays must be an array", ToolRejectionKind.INVALID_ARGUMENTS)
        val days = (rawDays as? List<*>).orEmpty()
        if (days.any { it !is Number || it.toDouble() != it.toInt().toDouble() || it.toInt() !in 1..7 } ||
            days.size > 7 || days.size != days.map { (it as Number).toInt() }.distinct().size || label.length > 80)
            return reject("Supply a label up to 80 characters and unique repeat days 1..7", ToolRejectionKind.INVALID_ARGUMENTS)

        return when (val outcome = alarmAction.execute(null, expression, label, skipUi = false, repeatDays = days.map { (it as Number).toInt() })) {
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
        val rawSeconds = toolCall.arguments["seconds"] as? Number
        if (toolCall.arguments.keys.any { it !in setOf("seconds", "label") } ||
            toolCall.arguments["label"]?.let { it !is String || it.length > 80 } == true ||
            rawSeconds?.let { !it.toDouble().isFinite() || it.toDouble() != it.toInt().toDouble() } == true)
            return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Use whole timer seconds and a label up to 80 characters", ToolRejectionKind.INVALID_ARGUMENTS)
        val seconds = rawSeconds?.toInt()
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

    private fun clockAction(call: ToolCall): ToolExecutionResult {
        val action = call.arguments["action"] as? String ?: return ToolExecutionResult.Rejected(
            call.id, call.functionName, "Clock action is required", ToolRejectionKind.INVALID_ARGUMENTS)
        val show = action in setOf("show_alarms", "show_timers")
        val modify = action in setOf("dismiss_next_alarm", "snooze_alarm", "dismiss_expired_timers")
        if (!show && !modify || show && call.functionName != "halo_clock_view" ||
            modify && call.functionName != "halo_clock_modify")
            return ToolExecutionResult.Rejected(call.id, call.functionName,
                "Unsupported clock action for this tool", ToolRejectionKind.INVALID_ARGUMENTS)
        if (action == "dismiss_expired_timers" && Build.VERSION.SDK_INT < 28)
            return ToolExecutionResult.Rejected(call.id, call.functionName,
                "This Android version cannot dismiss expired timers by Intent",
                ToolRejectionKind.UNSUPPORTED_OPERATION)
        val intent = clockIntent(action) ?: return ToolExecutionResult.Rejected(
            call.id, call.functionName, "Unsupported clock action", ToolRejectionKind.INVALID_ARGUMENTS)
        if (intent.resolveActivity(context.packageManager) == null)
            return ToolExecutionResult.Rejected(call.id, call.functionName,
                "No installed clock app supports this action",
                ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        return try {
            context.startActivity(intent)
            ToolExecutionResult.Success(call.id, call.functionName,
                ToolResultEnvelope.success(actionTruth = ActionTruth.DISPATCHED,
                    details = "action=$action\nnote=The clock app received the request; the actual alarm/timer state was not verified."),
                ActionTruth.DISPATCHED)
        } catch (_: ActivityNotFoundException) {
            ToolExecutionResult.Rejected(call.id, call.functionName,
                "The clock handler is unavailable", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        } catch (_: SecurityException) {
            ToolExecutionResult.Rejected(call.id, call.functionName,
                "Android denied this clock operation", ToolRejectionKind.PERMISSION_REQUIRED)
        }
    }

    internal fun clockIntent(action: String): Intent? = when (action) {
        "show_alarms" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
        "show_timers" -> Intent(AlarmClock.ACTION_SHOW_TIMERS)
        "dismiss_next_alarm" -> Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
            putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_NEXT)
        }
        "snooze_alarm" -> Intent(AlarmClock.ACTION_SNOOZE_ALARM)
        "dismiss_expired_timers" -> if (Build.VERSION.SDK_INT >= 28)
            Intent(AlarmClock.ACTION_DISMISS_TIMER) else null
        else -> null
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        /** A day is the practical ceiling for a countdown; longer belongs to an alarm. */
        const val MAX_TIMER_SECONDS = 86_400
    }
}
