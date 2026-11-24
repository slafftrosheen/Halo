package com.halo.app.ui.sources.v13

import com.halo.app.everyday.collectors.CollectorAttemptState
import com.halo.app.everyday.collectors.CollectorKind
import com.halo.app.everyday.collectors.CollectorSource
import com.halo.app.everyday.collectors.SourceCategory

/**
 * halo 13.0 · one calm place for everything halo reads from (SOURCES_PLANNER_13.md §2).
 * Builds on everyday/collectors (CollectorSource, CollectorKind, SourceCategory, CollectorAttemptState)
 * and adds Study files + Kids-safe lists as groups, so people see ONE list, not four screens.
 */
enum class SourceGroup { YOUR_OWN, NEWS_AND_READING, WEATHER_AND_ALERTS, CALENDARS, STUDY, KIDS_SAFE }

/** Plain state shown on every row. Colours: mint = up to date, apricot = needs you, red only for FAILED. */
enum class PlainState(val tone: Tone) {
    UP_TO_DATE(Tone.DONE), UPDATING(Tone.NEUTRAL), ON_THIS_PHONE(Tone.DONE), WAITING_FOR_INTERNET(Tone.NEUTRAL),
    TAKING_A_BREAK(Tone.NEUTRAL), NEEDS_YOU(Tone.NEEDS_YOU), CANT_REACH(Tone.ERROR), PAUSED(Tone.NEUTRAL), NEW(Tone.NEUTRAL)
}
enum class Tone { DONE, NEEDS_YOU, ERROR, NEUTRAL }

/** Who stands behind a source, in words people know. Never a score. */
enum class Kind { OFFICIAL, PUBLIC_NEWS, SCIENCE, COMMUNITY, YOUR_OWN, ADDED_BY_YOU }

/** Where halo may use a source. Off = still kept, never brought up (CollectorSource.muted). */
data class Uses(val todayBrief: Boolean, val answers: Boolean, val tellMeRightAway: Boolean, val kids: Boolean)

data class SourceRow(
    val id: String, val title: String, val publisher: String, val group: SourceGroup, val kind: Kind,
    val state: PlainState, val updatedMinutesAgo: Int?, val uses: Uses, val usedLast7Days: Int,
)

object SourceManager {
    fun group(s: CollectorSource): SourceGroup = when {
        s.kind.name.startsWith("LOCAL_") -> SourceGroup.YOUR_OWN
        s.kind == CollectorKind.WEATHER || s.kind == CollectorKind.ALERTS || s.kind == CollectorKind.AIR_QUALITY -> SourceGroup.WEATHER_AND_ALERTS
        s.kind == CollectorKind.PUBLIC_CALENDAR || s.category == SourceCategory.CALENDAR -> SourceGroup.CALENDARS
        else -> SourceGroup.NEWS_AND_READING
    }

    fun kind(s: CollectorSource): Kind = when {
        s.kind.name.startsWith("LOCAL_") -> Kind.YOUR_OWN
        s.id.startsWith("starter:hacker-news") -> Kind.COMMUNITY
        s.category == SourceCategory.OFFICIAL || s.kind == CollectorKind.ALERTS -> Kind.OFFICIAL
        s.category == SourceCategory.SCIENCE -> Kind.SCIENCE
        s.id.startsWith("starter:") || s.kind == CollectorKind.WEATHER -> Kind.PUBLIC_NEWS
        else -> Kind.ADDED_BY_YOU
    }

    /** CollectorAttemptState (11 technical states) → 9 plain ones. */
    fun state(s: CollectorSource, a: CollectorAttemptState, failuresInARow: Int): PlainState = when {
        !s.enabled -> PlainState.PAUSED
        !s.external -> PlainState.ON_THIS_PHONE
        else -> when (a) {
            CollectorAttemptState.NEVER -> PlainState.NEW
            CollectorAttemptState.FETCHING -> PlainState.UPDATING
            CollectorAttemptState.CURRENT, CollectorAttemptState.NOT_MODIFIED -> PlainState.UP_TO_DATE
            CollectorAttemptState.LOCAL_ONLY -> PlainState.ON_THIS_PHONE
            CollectorAttemptState.OFFLINE -> PlainState.WAITING_FOR_INTERNET
            CollectorAttemptState.BACKOFF, CollectorAttemptState.QUOTA -> PlainState.TAKING_A_BREAK
            CollectorAttemptState.DISABLED, CollectorAttemptState.CANCELLED -> PlainState.PAUSED
            CollectorAttemptState.FAILED -> if (failuresInARow >= 3) PlainState.NEEDS_YOU else PlainState.CANT_REACH
        }
    }

    fun uses(s: CollectorSource, alerts: Boolean) =
        Uses(todayBrief = s.selected && !s.muted, answers = s.selected, tellMeRightAway = alerts && s.kind == CollectorKind.ALERTS, kids = s.childApproved)

    /** "Every hour" etc. refreshMinutes is 15..1440 in CollectorSource. */
    fun howOften(minutes: Int): String = when {
        minutes <= 15 -> "every_15_min"; minutes <= 60 -> "every_hour"; minutes <= 360 -> "few_times_a_day"; else -> "once_a_day"
    }

    /** "Your mix": counts per category for the gentle balance view – shown, never judged. */
    fun mix(rows: List<CollectorSource>): Map<SourceCategory, Int> =
        rows.filter { it.selected && group(it) == SourceGroup.NEWS_AND_READING }.groupingBy { it.category }.eachCount()
}
