package com.halo.app.ui.fx

/*
 * halo DevKit v1.3 · Learning motion wiring (Kids + Study, contract v1).
 *
 * REFERENCE, not a drop-in replacement of HaloFx.kt (Codex owns the implementation).
 * The six new assets use the existing naming, so the current loader paths work unchanged:
 *   assets/halo/fx/halo_fx_<asset>_<dark|light>.json
 *   assets/halo/fx/static/halo_fx_<asset>_<dark|light>.svg   (reduced motion / loading)
 *
 * 1) Add one-shot moments to HaloFxMoment:
 *      LEARN_OPEN("learn_open"), LEARN_INSIGHT("learn_insight"),
 *      LEARN_ADVANCE("learn_advance"), LEARN_RETRY("learn_retry"),
 *    and to haloMomentFallback():
 *      LEARN_OPEN -> HaloGlyph.READ_MODE, LEARN_INSIGHT -> HaloGlyph.SUCCESS,
 *      LEARN_ADVANCE -> HaloGlyph.NEXT,   LEARN_RETRY -> HaloGlyph.QUESTION
 *    (LEARN_RETRY must never fall back to ERROR/WARNING: a misunderstanding is not a failure.)
 *
 * 2) Staged (marker) assets reuse StagedHaloFx exactly like HaloToolFx / HaloPairingFx:
 */

/** Camera page framing (H20). Phase must come from the frame analyser, never from a timer. */
enum class HaloLearnFramePhase(val marker: String, val loop: Boolean) {
    SEARCHING("searching", true),   // no page quad yet
    LOCKED("locked", false),        // page quad stable for >= 300 ms
    UNSURE("unsure", true),         // quad found but low confidence / blur
}

/** Topic map (K13). GROW when branches are actually returned; CHOOSE on tap. */
enum class HaloLearnBranchPhase(val marker: String, val loop: Boolean) {
    GROW("grow", false),
    CHOOSE("choose", false),
}

/*
 * @Composable fun HaloLearnFrameFx(phase: HaloLearnFramePhase, modifier: Modifier = Modifier) =
 *     StagedHaloFx("learn_frame", phase.marker, phase.loop, modifier)      // fallback glyph: CAMERA
 * @Composable fun HaloLearnBranchFx(phase: HaloLearnBranchPhase, modifier: Modifier = Modifier) =
 *     StagedHaloFx("learn_branch", phase.marker, phase.loop, modifier)     // fallback glyph: SEARCH
 * and in haloStagedFallback():  "learn_frame" -> HaloGlyph.CAMERA ; "learn_branch" -> HaloGlyph.SEARCH
 */

/* 3) Intent (one recommended action) — reference policy matching LEARNING_HANDOFF.md §3/§5.
 *    Render with Modifier.haloTrace(enabled = action == thisControl, accent, lapOnAppear = true).
 *    Pure function so it can be unit-tested against the contract. */

enum class LearnAction { NONE, MIC, HINT, NEXT, TRY_AGAIN, ANOTHER_WAY, CAPTURE, CHECK_EXTRACTION, RESUME, CONTINUE_CREATE, PRACTICE, REVIEW }

enum class CheckResult { CORRECT_REASONING, NOT_YET }

data class LearnIntentInput(
    val kids: Boolean,
    val stopped: Boolean,                 // runtime.stop or barge-in: nothing is highlighted
    val haloSpeaking: Boolean,
    val micOpen: Boolean,
    val busy: Boolean,                    // thinking / checking / generating / reading
    val awaitingAnswer: Boolean,          // halo asked a question and is waiting
    val msSinceHaloFinishedSpeaking: Long,
    val lastCheck: CheckResult?,          // result of the most recent CHECKED attempt
    val unsureExtraction: Boolean,        // H21 has a low-confidence box
    val pageLocked: Boolean,              // H20 frame analyser locked
    val blockingSheet: Boolean,           // consent / permission / destructive confirm on screen
)

fun recommendedLearnAction(i: LearnIntentInput): LearnAction {
    val hesitationMs = if (i.kids) 12_000L else 20_000L
    return when {
        i.stopped || i.busy || i.micOpen || i.haloSpeaking || i.blockingSheet -> LearnAction.NONE // never on consent/permission/destructive
        i.unsureExtraction -> LearnAction.CHECK_EXTRACTION
        i.pageLocked -> LearnAction.CAPTURE
        i.lastCheck == CheckResult.CORRECT_REASONING -> LearnAction.NEXT
        i.lastCheck == CheckResult.NOT_YET -> LearnAction.ANOTHER_WAY
        i.awaitingAnswer && i.msSinceHaloFinishedSpeaking >= hesitationMs -> LearnAction.HINT
        i.awaitingAnswer -> LearnAction.MIC
        else -> LearnAction.NONE
    }
}

/* 4) Celebration guard (C06/C07): bloom_kids only for the FIRST checked independent success per idea,
 *    never after Stop, never for time spent. */
fun shouldBloom(result: CheckResult?, alreadyBloomedForIdea: Boolean, stoppedSinceAttempt: Boolean): Boolean =
    result == CheckResult.CORRECT_REASONING && !alreadyBloomedForIdea && !stoppedSinceAttempt
