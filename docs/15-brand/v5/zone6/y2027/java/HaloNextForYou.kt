package com.halo.app.y2027

/*
 * halo 2027 · Next for you (board 49). One suggestion at a time, always with reasons.
 * Uses AndroidDayContext (repo @98aa2fb) as the main input. Reference model.
 */
enum class Helpfulness { ONLY_WHEN_ASKED, SUGGEST_I_DECIDE, QUIET_HELPER }
enum class QuietTime { SLEEPING, DRIVING, IN_A_MEETING }

data class Reason(val title: String, val source: String, val onThisPhone: Boolean, val forgettable: Boolean = false)

data class Suggestion(
    val id: String,
    val kind: String,               // "leave_time", "move_left_over", "renew"
    val title: String,              // "Leave by 9:20 for the market"
    val line: String,               // "Rain starts around 10 — the planters need the covers."
    val primary: String,            // "Remind me 9:10" — names the action
    val secondary: String = "Not today",
    val reasons: List<Reason>,      // shown in "Why this?" (lilac = help)
    val safeToDoQuietly: Boolean = false, // only small, undoable, non-hard-stop actions
)

class NextForYouPolicy(
    private val level: Helpfulness,
    private val quiet: Set<QuietTime>,
    private val maxPerDay: Int = 3,
) {
    private val shownToday = mutableListOf<String>()
    private val dismissedToday = mutableSetOf<String>()

    /** Never more than one card; never during quiet times; never the same dismissed kind twice a day. */
    fun pick(candidates: List<Suggestion>, now: Set<QuietTime>): Suggestion? {
        if (level == Helpfulness.ONLY_WHEN_ASKED) return null
        if (now.any { it in quiet }) return null
        if (shownToday.size >= maxPerDay) return null
        return candidates.firstOrNull { it.kind !in dismissedToday && it.reasons.isNotEmpty() }
            ?.also { shownToday += it.id }
    }

    fun mayDoQuietly(s: Suggestion) = level == Helpfulness.QUIET_HELPER && s.safeToDoQuietly
    fun dismiss(s: Suggestion) { dismissedToday += s.kind }
}

/** After 21:00 Home shows "Today, done": done count, what's left, tomorrow in one line. */
data class DayWrap(val doneCount: Int, val doneLine: String, val leftOver: List<String>, val tomorrow: String)
