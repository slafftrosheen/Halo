package com.halo.app.ui.study.v13

/**
 * halo 13.0 · "when does this card come back?" in plain words.
 * Mirrors StudyReviewPolicy at 99aac2f (steps 1, 2, 4, 7, 15, 30 days; ratings AGAIN, ALMOST, KNOWN) so the button
 * can say the day before it is tapped. knownStreak/wrongStreak are scheduling inputs only and are never shown.
 */
object ReviewPlain {
    val STEPS = intArrayOf(1, 2, 4, 7, 15, 30)
    const val SECONDS_PER_CARD = 40

    fun nextIndex(rating: String, index: Int, knownStreak: Int, wrongStreak: Int): Int {
        val previous = index.coerceIn(0, STEPS.lastIndex)
        return when (rating) {
            "ALMOST" -> previous
            "KNOWN" -> if (knownStreak + 1 >= 2) (previous + 2).coerceAtMost(STEPS.lastIndex) else (previous + 1).coerceAtMost(STEPS.lastIndex)
            "AGAIN" -> if (wrongStreak + 1 >= 2) 0 else (previous - 1).coerceAtLeast(0)
            else -> error("unknown rating $rating")
        }
    }

    fun comesBackInDays(rating: String, index: Int, knownStreak: Int = 0, wrongStreak: Int = 0) =
        STEPS[nextIndex(rating, index, knownStreak, wrongStreak)]

    /** "tomorrow", "in 4 days", "next week", "in about a month". */
    fun phrase(days: Int): String = when {
        days <= 1 -> "tomorrow"
        days < 7 -> "in $days days"
        days == 7 -> "in 7 days"
        days < 30 -> "in about ${(days + 3) / 7} weeks"
        else -> "in about a month"
    }

    fun minutesFor(cards: Int) = ((cards * SECONDS_PER_CARD) + 59) / 60
}
