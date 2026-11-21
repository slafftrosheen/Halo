package com.halo.app.y2027

/*
 * halo 2027 · Hand it to halo (board 47). Reference model, not compiled against 98aa2fb.
 * A multi-step ask becomes a visible plan. Some steps are HARD STOPS and always wait for the user,
 * whatever the helpfulness level and whatever device the user is on.
 */
enum class StepKind { READ, DO, ASK_FIRST }
enum class StepState { WAITING, NOW, NEEDS_YOU, DONE, SKIPPED, FAILED }
enum class Touches { CALENDAR, WEB, MESSAGES, CONTACTS, FILES, PAYMENTS, LOCATION, CAMERA_SCREEN }

/** What always pauses. Kept as data so it can be shown on the Privacy page verbatim. */
enum class HardStop(val plain: String) {
    MONEY("Paying or booking with money"),
    MESSAGE_TO_PEOPLE("Sending a message to someone"),
    DELETE("Deleting anything"),
    POST("Posting anywhere public"),
}

data class PlanStep(
    val id: String,
    val title: String,              // "Book for 4 at 19:00"
    val kind: StepKind,
    val touches: Set<Touches> = emptySet(),
    val hardStop: HardStop? = null, // non-null → kind is forced to ASK_FIRST
    val state: StepState = StepState.WAITING,
    val liveLine: String? = null,   // "19:00 and 19:30 are free for 4 people"
) {
    val effectiveKind get() = if (hardStop != null) StepKind.ASK_FIRST else kind
}

data class ReceiptLine(val text: String, val detail: String, val undoLabel: String?, val undoUntilMillis: Long?)

data class TaskPlan(
    val id: String,
    val title: String,              // "Table for Saturday"
    val steps: List<PlanStep>,
    val receipt: List<ReceiptLine> = emptyList(),
) {
    val current get() = steps.firstOrNull { it.state == StepState.NOW || it.state == StepState.NEEDS_YOU }
    val doneCount get() = steps.count { it.state == StepState.DONE }
    val needsYou get() = steps.any { it.state == StepState.NEEDS_YOU }

    /** Dock island + ring caption, using the Zone 6 meanings. */
    val dockState get() = when {
        needsYou -> "need"              // Apricot island, caption "Waiting for you"
        current != null -> "work"       // mint edge, caption "Working"
        doneCount == steps.size -> "done"
        else -> "idle"
    }

    /** "Step 2 of 4" — used in the island, the status-bar chip and the lock screen. */
    fun stepLabel() = "Step ${(steps.indexOf(current).takeIf { it >= 0 } ?: doneCount) + 1} of ${steps.size}"
}

/** The rule in one place: a step may run without asking only if it is not a hard stop. */
fun PlanStep.mayRunWithoutAsking(level: Helpfulness): Boolean =
    hardStop == null && (effectiveKind == StepKind.READ || level == Helpfulness.QUIET_HELPER || effectiveKind == StepKind.DO)
