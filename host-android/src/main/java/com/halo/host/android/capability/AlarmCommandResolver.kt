package com.halo.host.android.capability

import com.halo.cognition.language.DayRelation
import com.halo.cognition.language.TemporalExpressionParser
import com.halo.cognition.language.TemporalParseResult
import com.halo.cognition.language.TemporalValue
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

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
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val parser: TemporalExpressionParser = TemporalExpressionParser()
) {
    fun resolve(temporalValue: TemporalValue?, expression: String = "", label: String? = "Halo alarm"): AlarmExecutionPlan {
        val parsedVal = if (temporalValue != null) temporalValue
        else if (expression.isNotBlank()) {
            val res = parser.parse(expression)
            if (res is TemporalParseResult.Parsed) res.value else null
        } else null

        return when (parsedVal) {
            is TemporalValue.TimeOfDay -> {
                val occurrence = parsedVal.resolveOccurrence(clock, zoneId)
                val now = ZonedDateTime.now(clock).withZoneSameInstant(zoneId)
                val systemNextOccurrence = if (parsedVal.time.isAfter(now.toLocalTime())) {
                    now.toLocalDate().atTime(parsedVal.time).atZone(zoneId)
                } else {
                    now.toLocalDate().plusDays(1).atTime(parsedVal.time).atZone(zoneId)
                }

                if (occurrence == systemNextOccurrence) {
                    AlarmExecutionPlan.SystemAlarm(
                        hour = parsedVal.time.hour,
                        minute = parsedVal.time.minute,
                        label = label
                    )
                } else {
                    AlarmExecutionPlan.HaloExactAlarm(
                        triggerAt = occurrence.toInstant(),
                        label = label
                    )
                }
            }
            is TemporalValue.DayPart -> {
                val partName = parsedVal.part.name.lowercase()
                AlarmExecutionPlan.Clarification(
                    prompt = "What time in the $partName?",
                    field = "time"
                )
            }
            is TemporalValue.InstantValue -> {
                AlarmExecutionPlan.HaloExactAlarm(
                    triggerAt = parsedVal.instant,
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

fun TemporalValue.TimeOfDay.resolveOccurrence(
    clock: Clock,
    zoneId: ZoneId,
): ZonedDateTime {
    val now = ZonedDateTime.now(clock).withZoneSameInstant(zoneId)

    return when (dayRelation) {
        DayRelation.TODAY ->
            now.toLocalDate().atTime(time).atZone(zoneId)

        DayRelation.TOMORROW ->
            now.toLocalDate().plusDays(1).atTime(time).atZone(zoneId)

        null -> {
            val today = now.toLocalDate().atTime(time).atZone(zoneId)
            if (today.isAfter(now)) today else today.plusDays(1)
        }
    }
}
