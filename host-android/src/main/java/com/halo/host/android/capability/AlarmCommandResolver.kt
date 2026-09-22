package com.halo.host.android.capability

import com.halo.cognition.language.TemporalExpressionParser
import com.halo.cognition.language.TemporalValue
import java.time.Instant

sealed interface AlarmExecutionPlan {
    data class SystemAlarm(
        val hour: Int,
        val minute: Int,
        val label: String?,
    ) : AlarmExecutionPlan

    data class HaloExactAlarm(
        val triggerAt: Instant,
        val label: String?,
    ) : AlarmExecutionPlan

    data class Clarification(
        val prompt: String,
        val field: String = "time"
    ) : AlarmExecutionPlan
}

class AlarmCommandResolver(
    private val parser: TemporalExpressionParser = TemporalExpressionParser()
) {
    fun resolve(temporalValue: TemporalValue?, expression: String = "", label: String? = "Halo alarm"): AlarmExecutionPlan {
        val value = temporalValue ?: if (expression.isNotBlank()) parser.parse(expression) else null
        return when (value) {
            is TemporalValue.TimeOfDay -> {
                AlarmExecutionPlan.SystemAlarm(
                    hour = value.time.hour,
                    minute = value.time.minute,
                    label = label
                )
            }
            is TemporalValue.DayPart -> {
                val partName = value.part.name.lowercase()
                AlarmExecutionPlan.Clarification(
                    prompt = "What time in the $partName?",
                    field = "time"
                )
            }
            is TemporalValue.InstantValue -> {
                AlarmExecutionPlan.HaloExactAlarm(
                    triggerAt = value.instant,
                    label = label
                )
            }
            null -> {
                AlarmExecutionPlan.Clarification(
                    prompt = "What time would you like to set the alarm for?",
                    field = "time"
                )
            }
        }
    }
}
