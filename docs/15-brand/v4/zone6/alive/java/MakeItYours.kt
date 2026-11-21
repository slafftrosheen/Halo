package com.halo.app.ui.look

/*
 * halo · Make it yours (pack 7.2) — guided customisation, views, help level, keep-layout, change log.
 * Reference code, not compiled against 98aa2fb. Uses existing owners:
 *   HaloPreset / HaloMotion / HaloLook (6.4), VisualPreferences.guidanceAmount, KidsGrownUpGate (phone lock).
 */

import com.halo.app.ui.theme.HaloMotion
import com.halo.app.ui.theme.HaloPreset
import com.halo.app.wallpaper.HaloWallpaper
import java.time.Instant

/** Day-one question. Maps 1:1 to VisualPreferences.guidanceAmount. No age, no skill test. */
enum class HaloHelpLevel { SHOW_ME_EVERYTHING, A_LITTLE, I_KNOW_MY_WAY }

/** Views, like Learn sizes. A view is presentation only — same halo, same dock grammar. */
enum class HaloView(val dockButtonDp: Int, val homeBigButtons: Boolean, val labelsAlways: Boolean, val defaultMotion: HaloMotion) {
    EASY(64, true, true, HaloMotion.GENTLE),
    STANDARD(56, false, true, HaloMotion.GENTLE),
    COMPACT(48, false, false, HaloMotion.FULL),
}

enum class HaloButtonSize(val dp: Int) { REGULAR(48), LARGE(56), EXTRA_LARGE(64) }

/** The one swappable dock slot. Home, the ring and Places are fixed. Tablets ignore this (all six places shown). */
enum class DockSlot { WORKSPACE, LEARN, LIBRARY }

/** Each preset ships with a matching live wallpaper. */
val HaloPreset.defaultWallpaper: HaloWallpaper get() = when (this) {
    HaloPreset.NIGHT_MINT -> HaloWallpaper.DRIFT
    HaloPreset.PAPER_SAGE -> HaloWallpaper.TIDE
    HaloPreset.SLATE_BLUE -> HaloWallpaper.CONSTELLATION
    HaloPreset.COOL_LILAC -> HaloWallpaper.AURORA
    HaloPreset.CORAL_DAY -> HaloWallpaper.DAY_ARC
    HaloPreset.WARM_ROSE -> HaloWallpaper.PAPER
}

data class MakeItYoursState(
    val preset: HaloPreset = HaloPreset.NIGHT_MINT,
    val wallpaper: HaloWallpaper = HaloWallpaper.DRIFT,
    val wallpaperReacts: Boolean = true,
    val calmerAtNight: Boolean = true,
    val view: HaloView = HaloView.STANDARD,
    val buttonSize: HaloButtonSize = HaloButtonSize.LARGE,
    val dockSlot: DockSlot = DockSlot.WORKSPACE,
    val motion: HaloMotion = HaloMotion.GENTLE,
    val textScale: Float = 1f,
    val helpLevel: HaloHelpLevel = HaloHelpLevel.A_LITTLE,
    val readAloud: Boolean = false,
    val keepLayout: KeepLayout? = null,
)

/** “Keep this layout” — set by the person or by family. Unlock = the phone’s own lock (same rule as KidsGrownUpGate). */
data class KeepLayout(val keptBy: String?, val since: Instant)

/** Plain-words change log for “Put back how it was” (7 days). */
data class LookChange(val at: Instant, val what: String, val from: String, val to: String, val before: MakeItYoursState)

class LookChangeLog(private val keepDays: Long = 7) {
    private val items = ArrayDeque<LookChange>()
    fun record(c: LookChange) { items.addFirst(c); prune(c.at) }
    fun list(now: Instant): List<LookChange> { prune(now); return items.toList() }
    private fun prune(now: Instant) { items.removeAll { it.at.isBefore(now.minusSeconds(keepDays * 86_400)) } }
}

/** “Do it with me”: 3 questions, two or three pictured answers each, each answer previewed live. */
data class WithMeAnswer(val titleRes: Int, val lineRes: Int, val apply: (MakeItYoursState) -> MakeItYoursState)
data class WithMeQuestion(val questionRes: Int, val answers: List<WithMeAnswer>)

object WithMe {
    fun questions(r: R) = listOf(
        WithMeQuestion(r.q_feel, listOf(
            WithMeAnswer(r.a_calm_night, r.a_calm_night_line) { it.copy(preset = HaloPreset.COOL_LILAC, wallpaper = HaloWallpaper.AURORA) },
            WithMeAnswer(r.a_bright_paper, r.a_bright_paper_line) { it.copy(preset = HaloPreset.WARM_ROSE, wallpaper = HaloWallpaper.PAPER) })),
        WithMeQuestion(r.q_size, listOf(
            WithMeAnswer(r.a_comfortable, r.a_comfortable_line) { it.copy(textScale = 1f, buttonSize = HaloButtonSize.LARGE) },
            WithMeAnswer(r.a_bigger, r.a_bigger_line) { it.copy(textScale = 1.3f, buttonSize = HaloButtonSize.EXTRA_LARGE) })),
        WithMeQuestion(r.q_motion, listOf(
            WithMeAnswer(r.a_lively, r.a_lively_line) { it.copy(motion = HaloMotion.FULL) },
            WithMeAnswer(r.a_calm, r.a_calm_line) { it.copy(motion = HaloMotion.GENTLE) },
            WithMeAnswer(r.a_still, r.a_still_line) { it.copy(motion = HaloMotion.STILL) })),
    )
    /** String ids from strings_alive.xml (indirection keeps this file free of the generated R class). */
    data class R(val q_feel: Int, val a_calm_night: Int, val a_calm_night_line: Int, val a_bright_paper: Int, val a_bright_paper_line: Int,
                 val q_size: Int, val a_comfortable: Int, val a_comfortable_line: Int, val a_bigger: Int, val a_bigger_line: Int,
                 val q_motion: Int, val a_lively: Int, val a_lively_line: Int, val a_calm: Int, val a_calm_line: Int, val a_still: Int, val a_still_line: Int)
}

/**
 * Preview → Apply, same pattern as selectPersisted: the whole phone renders `preview`; nothing is written until apply().
 * Writes are blocked while keepLayout != null unless the phone lock was just confirmed.
 */
class MakeItYoursController(
    private val load: () -> MakeItYoursState,
    private val persist: (MakeItYoursState) -> Unit,
    private val log: LookChangeLog,
    private val confirmPhoneLock: suspend () -> Boolean,
) {
    var saved: MakeItYoursState = load(); private set
    var preview: MakeItYoursState = saved; private set
    fun try_(change: (MakeItYoursState) -> MakeItYoursState) { preview = change(preview) }
    fun undo() { preview = saved }
    suspend fun apply(describe: (MakeItYoursState, MakeItYoursState) -> List<Triple<String, String, String>>): Boolean {
        if (saved.keepLayout != null && !confirmPhoneLock()) return false
        val now = Instant.now()
        describe(saved, preview).forEach { (what, from, to) -> log.record(LookChange(now, what, from, to, saved)) }
        saved = preview; persist(saved); return true
    }
    suspend fun putBack(entry: LookChange) = run { preview = entry.before; apply { _, _ -> listOf(Triple(entry.what, entry.to, entry.from)) } }
}
