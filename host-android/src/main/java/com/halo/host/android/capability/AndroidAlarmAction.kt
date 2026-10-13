package com.halo.host.android.capability

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.halo.cognition.language.TemporalValue
import com.halo.runtime.cognition.Outcome

/** Single Android alarm dispatch primitive shared by deterministic and tool execution. */
class AndroidAlarmAction(
    private val context: Context,
    private val resolver: AlarmCommandResolver = AlarmCommandResolver(),
) {
    fun execute(
        temporalValue: TemporalValue?,
        timeExpression: String,
        label: String = "Halo alarm",
        skipUi: Boolean,
        repeatDays: List<Int> = emptyList(),
    ): Outcome {
        if (repeatDays.any { it !in 1..7 } || repeatDays.size != repeatDays.distinct().size)
            return Outcome.PermanentFailure("Repeat days must be unique Calendar day values 1..7")
        return when (val plan = resolver.resolve(temporalValue, timeExpression, label)) {
        is AlarmExecutionPlan.SystemAlarm -> {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, plan.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, plan.minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, plan.label ?: label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                if (repeatDays.isNotEmpty()) putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, ArrayList(repeatDays))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) == null) {
                Outcome.PermanentFailure("This device has no app that can set an alarm")
            } else {
                context.startActivity(intent)
                Outcome.Dispatched("Clock accepted alarm request", target = "Android Clock")
            }
        }
        is AlarmExecutionPlan.Clarification -> Outcome.NeedsClarification(plan.prompt, plan.field)
        is AlarmExecutionPlan.HaloExactAlarm -> Outcome.PermanentFailure(
            "Date-specific system alarms are unsupported. Use a Halo reminder instead.",
        )
    }
    }
}
