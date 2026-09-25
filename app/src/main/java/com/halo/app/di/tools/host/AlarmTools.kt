package com.halo.app.di.tools.host

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.halo.host.android.capability.AlarmCommandResolver
import com.halo.host.android.capability.AlarmExecutionPlan
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

class AlarmTools(private val context: Context) : ToolExecutor {
    private val resolver = AlarmCommandResolver()

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            id = "halo_set_alarm",
            name = "halo_set_alarm",
            description = "Set an alarm. Provide a specific time expression.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "timeExpression" to mapOf("type" to "string", "description" to "Natural language time expression like 'tomorrow at 7 AM'"),
                    "label" to mapOf("type" to "string", "description" to "Optional label for the alarm")
                ),
                "required" to listOf("timeExpression"),
                "additionalProperties" to false
            )
        )
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)

        if (toolCall.functionName == "halo_set_alarm") {
            val expression = toolCall.arguments["timeExpression"] as? String ?: return reject("Missing time expression")
            val label = toolCall.arguments["label"] as? String ?: "Halo alarm"

            val plan = resolver.resolve(null, expression, label)
            return when (plan) {
                is AlarmExecutionPlan.SystemAlarm -> {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, plan.hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, plan.minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, plan.label)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent.resolveActivity(context.packageManager) == null) {
                        return reject("No system alarm app available")
                    }
                    context.startActivity(intent)
                    ToolExecutionResult.Success(toolCall.id, toolCall.functionName, HostActionReceipt.DISPATCHED.name)
                }
                is AlarmExecutionPlan.HaloExactAlarm -> {
                    reject("Setting alarms for a specific date in the future is not supported yet by system alarm. Tell the user you can set a reminder instead.")
                }
                is AlarmExecutionPlan.Clarification -> {
                    reject("Clarification required: \${plan.prompt}")
                }
            }
        }
        return reject("Unknown tool")
    }
}
