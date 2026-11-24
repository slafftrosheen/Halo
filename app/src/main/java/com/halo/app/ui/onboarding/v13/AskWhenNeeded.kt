package com.halo.app.ui.onboarding.v13

/**
 * halo 13.0 · ask for access only when a feature needs it.
 * Uses the 17 ids of ui/onboarding/CapabilityGuideProgress.ids and its rule:
 * "Persist before launching Android; interrupted requests must never automatically re-prompt."
 *
 * The halo sheet comes BEFORE the Android dialog and says, in plain words:
 *   what for · what stays on the phone · what happens if you say no.
 * Buttons: [Allow] [Only this time] (where Android supports it) [Not now].
 */
data class AccessReason(val id: String, val feature: String, val why: Int, val ifNo: Int, val onlyOnce: Boolean)

enum class AskOutcome { SHOW_HALO_SHEET, LAUNCH_ANDROID, SHOW_SETTINGS_PATH, DONT_ASK }

data class AskHistory(val notNowCount: Int, val androidDenials: Int, val pending: Boolean)

object AskWhenNeeded {
    /** Called when the person STARTS a feature (taps mic, opens camera…). Never at app start, never in onboarding. */
    fun next(granted: Boolean, h: AskHistory, startedByPerson: Boolean): AskOutcome = when {
        granted -> AskOutcome.DONT_ASK
        !startedByPerson || h.pending -> AskOutcome.DONT_ASK          // no surprise prompts, no re-prompt loops
        h.androidDenials >= 2 -> AskOutcome.SHOW_SETTINGS_PATH         // Android won't show its dialog again
        h.notNowCount >= 3 -> AskOutcome.DONT_ASK                      // the feature shows a quiet "Turn on" button instead
        else -> AskOutcome.SHOW_HALO_SHEET
    }
}
