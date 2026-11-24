package com.halo.app.ui.study.v13

/**
 * halo 13.0 · plain help and plain results.
 * Names mirror com.halo.sdk.runtime.study.TutorStage and com.halo.app.data.study.StudyVerdict at repo 99aac2f.
 * Rule kept from LearningHelpLadder: "neither points nor elapsed time gate any rung".
 */
enum class Tone { MINT, APRICOT, NEUTRAL, LILAC }

data class Rung(val stage: String, val labelKey: String, val askFirst: Boolean = false)

object HelpPlain {
    val ladder = listOf(
        Rung("ASK", "ln13_help_remind"), Rung("HINT", "ln13_help_hint"), Rung("EXPLAIN", "ln13_help_explain"),
        Rung("ANALOGY", "ln13_help_another_way"), Rung("CHECK_ATTEMPT", "ln13_help_check_try"),
        Rung("PARTIAL_SOLUTION", "ln13_help_one_step"), Rung("SOLUTION", "ln13_help_show_all", askFirst = true),
    )
    /** Every rung is always available, for everyone, kids included. Only "Show it all" asks once. */
    fun available(stage: String, triesSoFar: Int, secondsOnQuestion: Int): Boolean = ladder.any { it.stage == stage }

    fun verdict(verdict: String): Pair<String, Tone> = when (verdict) {
        "CHECKS_OUT" -> "ln13_check_ok" to Tone.MINT
        "NEEDS_WORK" -> "ln13_check_fix" to Tone.APRICOT
        "INSUFFICIENT_EVIDENCE" -> "ln13_check_cant_tell" to Tone.NEUTRAL
        "UNVERIFIED" -> "ln13_check_guess" to Tone.LILAC
        else -> error("unknown verdict $verdict")
    }

    /** LearningStage (7) and StudyLearningPhase (4) shown as one path. */
    fun pathStep(stage: String): String = when (stage) {
        "START", "DISCOVER", "EXPLAIN", "EXPLORE" -> "LOOK"
        "PRACTICE", "TRY" -> "TRY"
        "REFLECT", "REVIEW" -> "CHECK"
        "CONTINUE", "COMPLETE" -> "DONE"
        else -> error("unknown stage $stage")
    }
}
