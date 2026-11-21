package com.halo.app.ui.zone6.learn

/*
 * halo Learn — one path, three sizes. REFERENCE (presentation model only). Pack 7.1, boards 27–32.
 * Maps existing owners: StudyLearningPhase, LearningHelpLadder, StudyExplanationDepth, StudyVerdict,
 * StudyReviewIntensity ratings, KidsDestination, KidsSpokenChoice, KidsGrownUpGate.
 */

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class LearnStep { CHOOSE, LOOK, TRY, HELP, KEEP }

/** Picked by the learner or a grown-up — never derived from age. */
enum class LearnSize(val maxChoices: Int, val minTarget: Dp, val readAloudByDefault: Boolean, val freeAnswer: Boolean, val tapToHearFirst: Boolean) {
    PICTURES(maxChoices = 3, minTarget = 56.dp, readAloudByDefault = true, freeAnswer = false, tapToHearFirst = true),
    SIMPLE(maxChoices = 3, minTarget = 48.dp, readAloudByDefault = false, freeAnswer = false, tapToHearFirst = false),
    FULL(maxChoices = 6, minTarget = 48.dp, readAloudByDefault = false, freeAnswer = true, tapToHearFirst = false),
}

/** Visible step from the existing phase. LearningStage stays internal. */
fun visibleStep(phase: String /* StudyLearningPhase.name */, helpOpen: Boolean, draftsPending: Boolean): LearnStep = when {
    helpOpen -> LearnStep.HELP
    phase == "EXPLORE" -> LearnStep.LOOK
    phase == "TRY" -> LearnStep.TRY
    phase == "REVIEW" || phase == "COMPLETE" || draftsPending -> LearnStep.KEEP
    else -> LearnStep.CHOOSE
}

enum class HelpRung(val label: String, val confirmFirst: Boolean) { HINT("Hint", false), THINK("Help me think", false), FULL("Full solution", true) }
/** Kids (PICTURES) never get FULL before a try; their help is "together", step by step. */
fun rungsFor(size: LearnSize, attempts: Int): List<HelpRung> =
    if (size == LearnSize.PICTURES && attempts == 0) listOf(HelpRung.HINT, HelpRung.THINK) else HelpRung.entries

enum class Feedback(val tone: Color) { ALMOST(Color(0xFFC9B8FF)), RIGHT(Color(0xFF0CF2BA)) }   // never red, never shake

/** Shown before the tap, from the real schedule. */
data class RatingOption(val label: String, val nextDue: String)   // Again · Almost · I knew it

/** Spoken choice: first tap reads, second tap chooses (existing rule). */
class TapToHear {
    private var armed: String? = null
    fun onTap(id: String, speak: (String) -> Unit, choose: (String) -> Unit) {
        if (armed == id) { armed = null; choose(id) } else { armed = id; speak(id) }
    }
}

object LearnMotion {
    const val STEP_STAGGER_MS = 50
    const val RING_FILL_MS = 900
    const val COUNT_INTERVAL_MS = 380
    const val RUNG_INTERVAL_MS = 420
    const val CARD_FLIP_MS = 350
    const val PAGE_FLIP_MS = 800
    const val SPRING_DAMPING = 0.62f
    const val SPRING_STIFFNESS = 380f
}
