package com.halo.app.data.workspace

import java.util.Locale

/** Local Today collector slots. A plan carries only stable slot identifiers. */
internal enum class AdaptiveSlot(private vararg val terms: String) {
    PLANNER("plan", "calendar", "agenda", "schedule", "appointment", "today", "tomorrow", "план", "календар", "расписан", "plān", "planu"),
    WEATHER("weather", "rain", "temperature", "forecast", "погод", "дожд", "laikapstāk", "pogod"),
    STUDY("study", "lesson", "learn", "exam", "practice", "уч", "урок", "экзамен", "māc", "nauk"),
    READS("read", "news", "headlines", "article", "новост", "стать", "las", "wiadomo"),
    LIBRARY("library", "notes", "files", "библиот", "замет", "bibliot"),
    FOCUS("focus", "concentrate", "фокус", "концентр", "fokus", "skup"),
    WIND_DOWN("wind down", "quiet", "bedtime", "вечер", "сон", "wycisz"),
    ;

    fun mentioned(query: String): Int = terms.map { query.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: Int.MAX_VALUE

}

internal object AdaptivePlanner {
    /** Select by expressed intent. The local ranking never calls a provider or dispatches a tool. */
    fun select(request: String, available: Set<AdaptiveSlot>, limit: Int): List<AdaptiveSlot> {
        require(request.length <= 280 && limit in 1..9)
        val query = request.lowercase(Locale.ROOT)
        return available.map { it to it.mentioned(query) }
            .filter { it.second != Int.MAX_VALUE }
            .sortedBy { it.second }
            .map { it.first }
            .take(limit)
    }
}
