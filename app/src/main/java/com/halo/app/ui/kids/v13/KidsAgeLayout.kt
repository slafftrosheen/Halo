package com.halo.app.ui.kids.v13

/**
 * halo 13.0 · everything in Kids mode sized by age. Mirrors KidsAgeBand at 99aac2f
 * (EARLY 4–6: 72 dp, 2 choices, 2 stops · PRIMARY 7–9: 56 dp, 3, 3 · OLDER 10–12: 48 dp, 3, 4).
 */
enum class ReadAloud { ALWAYS, ON_TAP, BUDDY_ON_TAP }

data class KidsLayout(val targetDp: Int, val choices: Int, val stops: List<String>, val textSp: Int, val readAloud: ReadAloud)

object KidsAgeLayout {
    private val STOPS = listOf("LEARN", "MAKE", "FIND", "TELL")
    fun forBand(band: String): KidsLayout = when (band) {
        "EARLY" -> KidsLayout(72, 2, STOPS.take(2), 20, ReadAloud.ALWAYS)
        "PRIMARY" -> KidsLayout(56, 3, STOPS.take(3), 18, ReadAloud.ON_TAP)
        "OLDER" -> KidsLayout(48, 3, STOPS.take(4), 16, ReadAloud.BUDDY_ON_TAP)
        else -> error("unknown band $band")
    }
    /** Home is always three doors (KidsDestination ASK, LEARN, CREATE), whatever the age. */
    val home = listOf("ASK", "LEARN", "CREATE")
    /** Grown-up space locks again after this much idle time. */
    const val GROWN_UP_IDLE_SECONDS = 120
}
