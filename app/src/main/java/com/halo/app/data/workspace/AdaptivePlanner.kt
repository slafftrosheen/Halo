package com.halo.app.data.workspace

import java.text.Normalizer
import java.util.Locale

/** First-party adaptive block IDs. Layouts carry these IDs rather than content or routes. */
internal enum class AdaptiveSlot(vararg terms: String) {
    PLANNER("plan", "agenda", "calendar", "schedule", "appointment*", "event*", "календар*", "расписан*", "встреч*", "kalendār*", "kalendar*"),
    WEATHER("weather", "rain", "temperature", "forecast", "погод*", "дожд*", "laikapstāk*", "pogod*"),
    STUDY("study", "lesson*", "learn", "learning", "exam*", "practice", "учёб*", "урок*", "экзамен*", "mācīb*", "nauk*"),
    READS("read", "reading", "news", "headline*", "article*", "новост*", "стать*", "ziņ*", "wiadomoś*"),
    LIBRARY("library", "note*", "file*", "библиотек*", "замет*", "bibliotēk*", "bibliotek*"),
    FOCUS("focus", "concentrate", "фокус*", "концентр*", "fokus*", "skupi*"),
    WIND_DOWN("wind down", "quiet", "bedtime", "вечер*", "сон", "wycisz*"),
    TASKS("task*", "to do", "checklist*", "chores", "задач*", "дела", "uzdevum*", "zadani*"),
    REMINDERS("remind*", "напоминан*", "atgādinājum*", "przypomnieni*"),
    ALARMS_TIMERS("alarm*", "timer*", "countdown", "будильник*", "таймер*", "modinātāj*", "taimer*", "budzik*"),
    CLOCK("clock", "world time", "time zone", "date", "часы", "время", "часовой пояс", "pulksten*", "zegar*"),
    BRIEF("brief", "briefing", "summary", "overview", "сводк*", "итог*", "kopsavilkum*", "podsumowani*"),
    DEVICE("battery", "charging", "device*", "заряд*", "батаре*", "ierīc*", "bateri*"),
    ;

    private val patterns = terms.map { rule ->
        val prefix = rule.endsWith('*')
        val normalized = adaptiveText(rule.removeSuffix("*")).split(' ').joinToString("\\s+") { Regex.escape(it) }
        Regex("(?<![\\p{L}\\p{N}])" + normalized + if (prefix) "" else "(?![\\p{L}\\p{N}])")
    }

    fun mention(query: String): Int = patterns.minOf { it.find(query)?.range?.first ?: Int.MAX_VALUE }
}

private val marks = Regex("\\p{M}+")
private val separators = Regex("[^\\p{L}\\p{N}]+")
private fun adaptiveText(value: String): String =
    Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(marks, "").replace(separators, " ").trim()

internal object AdaptivePlanner {
    private val everyday = listOf(AdaptiveSlot.PLANNER, AdaptiveSlot.TASKS, AdaptiveSlot.WEATHER,
        AdaptiveSlot.REMINDERS, AdaptiveSlot.CLOCK, AdaptiveSlot.ALARMS_TIMERS, AdaptiveSlot.DEVICE)
    private val workspace = listOf(AdaptiveSlot.BRIEF, AdaptiveSlot.READS, AdaptiveSlot.STUDY,
        AdaptiveSlot.LIBRARY, AdaptiveSlot.FOCUS, AdaptiveSlot.WIND_DOWN, AdaptiveSlot.WEATHER)

    /** Explicit intent wins over suggestion preferences. Planner calls never fetch or invoke a tool. */
    fun select(request: String, available: Set<AdaptiveSlot>, limit: Int): List<AdaptiveSlot> {
        require(request.length <= 280 && limit in 1..9)
        val query = adaptiveText(request)
        return available.map { it to it.mention(query) }.filter { it.second != Int.MAX_VALUE }
            .sortedWith(compareBy<Pair<AdaptiveSlot, Int>> { it.second }.thenBy { it.first.ordinal })
            .map { it.first }.take(limit)
    }

    fun suggestions(available: Set<AdaptiveSlot>, hidden: Set<AdaptiveSlot>, limit: Int): List<List<AdaptiveSlot>> {
        require(limit in 1..9)
        val eligible = available - hidden
        return listOf(everyday + workspace, workspace + everyday)
            .map { order -> order.distinct().filter { it in eligible }.take(minOf(3, limit)) }
            .filter { it.isNotEmpty() }.distinct()
    }
}
