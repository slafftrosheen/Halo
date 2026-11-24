package com.halo.app.ui.planner.v13

import java.time.LocalTime

/**
 * halo 13.0 · the day as a timeline you can drag.
 * - Events from other calendars are FIXED (calendarUid != null): they never move by drag.
 * - A task dropped on the timeline becomes a time-block (keeps OrganizerKind.TASK, gains a time).
 * - Overlaps sit side by side in columns; "Leave by" ribbons sit above events with a location.
 */
data class Item(val id: String, val start: LocalTime, val end: LocalTime, val fixed: Boolean, val leaveByMin: Int? = null)
data class Placed(val item: Item, val column: Int, val columns: Int)

object DayTimeline {
    const val SNAP_MIN = 15

    fun layout(items: List<Item>): List<Placed> {
        val sorted = items.sortedBy { it.start }
        val groups = mutableListOf<MutableList<Item>>()
        for (i in sorted) {
            val g = groups.lastOrNull()
            if (g != null && g.any { it.end > i.start }) g += i else groups += mutableListOf(i)
        }
        return groups.flatMap { g ->
            val cols = mutableListOf<LocalTime>()
            g.map { i ->
                val c = cols.indexOfFirst { it <= i.start }.let { if (it == -1) { cols += i.end; cols.size - 1 } else { cols[it] = i.end; it } }
                i to c
            }.let { placed -> placed.map { (i, c) -> Placed(i, c, cols.size) } }
        }
    }

    fun snap(t: LocalTime): LocalTime = LocalTime.of(t.hour, (t.minute / SNAP_MIN) * SNAP_MIN)

    /** Returns null when the drop is not allowed (fixed item) – the UI springs it back and says why. */
    fun drop(i: Item, newStart: LocalTime): Item? {
        if (i.fixed) return null
        val len = java.time.Duration.between(i.start, i.end)
        val s = snap(newStart)
        return i.copy(start = s, end = s.plus(len))
    }
}
