package com.halo.app.ui.onboarding.v13

/**
 * halo 13.0 · setting halo up for a child, then handing the phone over.
 * Grown-up part runs behind ui/screens/kids/KidsGrownUpGate.kt (device PIN or password).
 * Child part: no reading needed (buddy speaks every line), no points, scores or streaks.
 */
enum class AgeBand(val label: String) { AGES_4_6("4–6"), AGES_7_9("7–9"), AGES_10_12("10–12") }

data class KidSetup(
    val name: String,                  // first name or nickname only
    val band: AgeBand,
    val buddy: String = "fox",         // fox · owl · cloud · robot
    val sky: String = "day",
    val homeworkWindow: IntRange? = 16..18,   // KidsHomeworkSchedule default
    val camera: Boolean = false,       // asked later, when homework photo is first used
    val grownUpPinVerified: Boolean = false,
)

enum class HandOverStep { GROWN_UP_GATE, ABOUT_CHILD, GROWN_UP_RULES, HAND_OVER, CHILD_HELLO, CHILD_PICKS_THINGS }

object KidsHandOverFlow {
    fun next(step: HandOverStep, k: KidSetup): HandOverStep? = when (step) {
        HandOverStep.GROWN_UP_GATE -> if (k.grownUpPinVerified) HandOverStep.ABOUT_CHILD else null
        HandOverStep.ABOUT_CHILD -> if (k.name.isNotBlank()) HandOverStep.GROWN_UP_RULES else null
        HandOverStep.GROWN_UP_RULES -> HandOverStep.HAND_OVER
        HandOverStep.HAND_OVER -> HandOverStep.CHILD_HELLO
        HandOverStep.CHILD_HELLO -> HandOverStep.CHILD_PICKS_THINGS
        HandOverStep.CHILD_PICKS_THINGS -> null                       // lands in the child's world (KidsWorld)
    }
    /** Topics a 4–6 year old sees are pictures only; 10–12 get words too. */
    fun pictureOnly(b: AgeBand) = b == AgeBand.AGES_4_6
}
