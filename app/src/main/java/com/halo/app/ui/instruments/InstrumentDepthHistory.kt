package com.halo.app.ui.instruments

import android.content.SharedPreferences
import com.halo.app.ui.adaptive.v13.ExperienceLevel
import com.halo.app.ui.adaptive.v13.LevelAdvisor
import com.halo.app.ui.adaptive.v13.LevelDecision
import com.halo.app.ui.adaptive.v13.LevelSignals
import java.time.LocalDate

/** A rolling on-device count per instrument bar. Tool inputs and action names are never recorded. */
internal class InstrumentDepthHistory(private val preferences: SharedPreferences, private val tool: String) {
    private data class Day(val date: Long, val advanced: Int = 0, val help: Int = 0)

    fun visit(today: LocalDate, advanced: Boolean = false, help: Boolean = false) {
        val date = today.toEpochDay()
        val recent = read(date)
        val last = recent.find { it.date == date } ?: Day(date)
        val updated = last.copy(advanced = (last.advanced + if (advanced) 1 else 0).coerceAtMost(12),
            help = (last.help + if (help) 1 else 0).coerceAtMost(6))
        if (updated != last || recent.none { it.date == date }) {
            val days = recent.filterNot { it.date == date } + updated
            preferences.edit().putString("$tool.depth.days", days.joinToString(";") { "${it.date},${it.advanced},${it.help}" }).apply()
        }
    }

    fun offer(today: LocalDate, current: InstrumentLevel, manuallySet: Boolean): LevelDecision.Offer? {
        val date = today.toEpochDay()
        val days = read(date)
        val since = (date - preferences.getLong("$tool.depth.offer", date - 14)).coerceIn(0, 14).toInt()
        val level = ExperienceLevel.entries[current.ordinal]
        return LevelAdvisor.decide(level, LevelSignals(
            daysUsed = days.size, advancedActions14d = days.sumOf { it.advanced }, helpOpens14d = days.sumOf { it.help },
            undoAfterAdapt7d = 0, largeTextOrTalkBack = false, kidsProfile = false,
            chosenByPerson = level.takeIf { manuallySet }), since) as? LevelDecision.Offer
    }

    fun shown(today: LocalDate) {
        preferences.edit().putLong("$tool.depth.offer", today.toEpochDay()).apply()
    }

    private fun read(today: Long): List<Day> =
        preferences.getString("$tool.depth.days", "").orEmpty().split(';').mapNotNull { row ->
            val columns = row.split(',')
            val date = columns.getOrNull(0)?.toLongOrNull()?.takeIf { it in today - 13..today } ?: return@mapNotNull null
            val advanced = columns.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 12) ?: return@mapNotNull null
            val help = columns.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 6) ?: return@mapNotNull null
            Day(date, advanced, help)
        }.distinctBy { it.date }
}
