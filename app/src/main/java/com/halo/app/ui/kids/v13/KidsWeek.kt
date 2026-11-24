package com.halo.app.ui.kids.v13

/**
 * halo 13.0 · "Leo's week" for the grown-up: what the child explored and made, and where hints helped.
 * Never a score. Stays on the phone.
 */
data class KidsEvent(val topic: String, val kind: String, val hints: Int = 0)

data class KidsWeekLine(val topic: String, val adventures: Int, val made: Int, val neededHints: Boolean)

object KidsWeek {
    /** Words a child summary or any kids string may never contain (EN + RU stems). */
    val FORBIDDEN = listOf("score", "point", "streak", "rank", "behind", "fail", "wrong", "lazy", "worse",
        "очк", "балл", "рейтинг", "серия", "отста", "провал", "неправильн", "хуже")

    /** Matched at the start of a word, so "карточки" (cards) is fine but "очков" (points) is not.
     *  Known gap: "очки" also means glasses, so a Russian line about glasses would be flagged. Needs a native-speaker review. */
    private val PATTERN = Regex("(?iU)\\b(" + FORBIDDEN.joinToString("|") + ")")
    fun check(text: String): Boolean = !PATTERN.containsMatchIn(text)

    fun summary(events: List<KidsEvent>): List<KidsWeekLine> = events.groupBy { it.topic }.map { (topic, list) ->
        KidsWeekLine(topic, list.count { it.kind == "ADVENTURE" }, list.count { it.kind == "MADE" }, list.any { it.hints > 0 })
    }.sortedByDescending { it.adventures + it.made }

    /** Homework help only inside the window, and never the answer itself (hints and "look again" only). */
    fun homeworkHelp(insideWindow: Boolean, askedForAnswer: Boolean): String = when {
        !insideWindow -> "LATER"
        askedForAnswer -> "HINT"
        else -> "LOOK_AGAIN_OR_HINT"
    }
}
