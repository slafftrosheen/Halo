package com.halo.runtime.cognition.facts

import com.halo.cognition.language.CurrentDateIntent
import com.halo.cognition.language.CurrentTimeIntent
import com.halo.cognition.language.SystemFactIntent
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class RuntimeFactResult(val text: String)

interface RuntimeFactResolver {
    suspend fun resolve(intent: SystemFactIntent): RuntimeFactResult?
}

class DefaultRuntimeFactResolver(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : RuntimeFactResolver {
    override suspend fun resolve(intent: SystemFactIntent): RuntimeFactResult? {
        val now = ZonedDateTime.now(clock).withZoneSameInstant(zoneId)
        return when (intent) {
            is CurrentTimeIntent -> {
                val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                RuntimeFactResult("It is ${now.format(formatter)}.")
            }
            is CurrentDateIntent -> {
                val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                RuntimeFactResult("Today is ${now.format(formatter)}.")
            }
            else -> null
        }
    }
}
