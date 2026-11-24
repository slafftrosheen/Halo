package com.halo.app.ui.planner.v13

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Duration

/** halo 13.0 · "Find 1 hour for the gym this week" → three calm options, never auto-booked. */
data class Busy(val start: LocalDateTime, val end: LocalDateTime)
data class Slot(val start: LocalDateTime, val end: LocalDateTime, val why: String)

object FindTime {
    fun options(busy: List<Busy>, from: LocalDateTime, days: Int, length: Duration,
                dayStart: LocalTime = LocalTime.of(8, 0), dayEnd: LocalTime = LocalTime.of(21, 0),
                buffer: Duration = Duration.ofMinutes(15), wanted: Int = 3): List<Slot> {
        require(days in 1..31 && wanted > 0 && !length.isNegative && !length.isZero &&
            !buffer.isNegative && dayStart < dayEnd)
        val out = mutableListOf<Slot>()
        val blocked = busy.filter { it.end > it.start }.sortedBy { it.start }
        for (d in 0 until days) {
            val day = from.toLocalDate().plusDays(d.toLong())
            var cursor = maxOf(day.atTime(dayStart), from)
            val end = day.atTime(dayEnd)
            for (event in blocked) {
                val bufferedStart = event.start.minus(buffer)
                val bufferedEnd = event.end.plus(buffer)
                if (bufferedEnd <= cursor) continue
                if (bufferedStart >= end || Duration.between(cursor, bufferedStart) >= length) break
                cursor = bufferedEnd
            }
            if (Duration.between(cursor, end) >= length) {
                out += Slot(cursor, cursor.plus(length), if (d == 0) "today" else "free_gap")
            }
            if (out.size == wanted) break                    // one option per day → spread across the week
        }
        return out
    }
}
