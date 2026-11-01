package com.halo.app.everyday.organizer

import java.time.*
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Pure recurrence calculation. Never uses 24-hour milliseconds to advance a wall-clock day. */
object OrganizerRecurrence {
    data class Slot(val identity: Instant, val start: Instant)

    /** Floating alarms move their display/delivery time, never their logical occurrence identity. */
    fun slots(time: OrganizerTime, rule: RepeatRule?, deviceZone: ZoneId): Sequence<Slot> {
        val identityZone = ZoneId.of(time.zoneId)
        return starts(time.copy(followDeviceZone = false), rule, identityZone).map { identity ->
            val start = if (time.followDeviceZone) identity.atZone(identityZone).toLocalDate()
                .atTime(time.start.toLocalTime()).atZone(deviceZone).toInstant() else identity
            Slot(identity, start)
        }
    }
    fun starts(time: OrganizerTime, rule: RepeatRule?, deviceZone: ZoneId): Sequence<Instant> = sequence {
        val zone = time.zone(deviceZone)
        val anchor = time.start
        var emitted = 0
        fun permitted(instant: Instant) = rule?.until?.let { instant <= it } ?: true
        // DTSTART belongs to the recurrence set even when BYDAY does not include its weekday.
        val first = anchor.atZone(zone).toInstant()
        if (!permitted(first)) return@sequence
        yield(first); emitted++
        if (rule == null || rule.count == 1) return@sequence
        var period = 0L
        while (true) {
            val dates = when (rule.frequency) {
                RepeatFrequency.DAILY -> listOf(anchor.toLocalDate().plusDays((period + 1) * rule.interval))
                RepeatFrequency.WEEKLY -> {
                    val monday = anchor.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .plusWeeks(period * rule.interval)
                    (rule.weekdays.ifEmpty { setOf(anchor.dayOfWeek) }).sortedBy { it.value }.map { monday.plusDays(it.value - 1L) }
                }
                RepeatFrequency.MONTHLY -> {
                    val month = YearMonth.from(anchor).plusMonths((period + 1) * rule.interval)
                    // RFC 5545: invalid dates (e.g. February 31) are omitted, not clamped.
                    if (anchor.dayOfMonth <= month.lengthOfMonth()) listOf(month.atDay(anchor.dayOfMonth)) else emptyList()
                }
                RepeatFrequency.YEARLY -> {
                    val year = anchor.year.toLong() + (period + 1) * rule.interval
                    if (year > 2199) return@sequence
                    val month = YearMonth.of(year.toInt(), anchor.month)
                    if (anchor.dayOfMonth <= month.lengthOfMonth()) listOf(month.atDay(anchor.dayOfMonth)) else emptyList()
                }
            }
            for (date in dates) {
                if (date.year > 2199) return@sequence
                val local = date.atTime(anchor.toLocalTime())
                if (local <= anchor) continue
                // java.time resolves a gap forward by its length and uses the earlier overlap offset.
                val instant = local.atZone(zone).toInstant()
                if (!permitted(instant)) return@sequence
                yield(instant)
                emitted++
                if (rule.count != null && emitted >= rule.count) return@sequence
            }
            period++
            // A monthly rule can have several empty months, but its calendar still advances.
            if (rule.frequency == RepeatFrequency.MONTHLY && YearMonth.from(anchor).plusMonths((period + 1) * rule.interval).year > 2199)
                return@sequence
        }
    }

    fun between(record: OrganizerRecord, overrides: List<OccurrenceOverride>, from: Instant, until: Instant,
        deviceZone: ZoneId, limit: Int = 1000): List<OrganizerOccurrence> {
        require(until > from && Duration.between(from, until).toDays() <= 3660 && limit in 1..10000)
        if (record.deletedAt != null || record.status != OrganizerStatus.ACTIVE) return emptyList()
        val time = record.time ?: return emptyList()
        val exceptions = overrides.filter { it.recordId == record.id }.associateBy { it.originalStart }
        val duration = time.end?.let { Duration.between(time.start.atZone(ZoneId.of(time.zoneId)), it.atZone(ZoneId.of(time.zoneId))) }
        fun occurrence(original: Instant, actualStart: Instant, exception: OccurrenceOverride?): OrganizerOccurrence? {
            if (exception?.disposition == OccurrenceDisposition.CANCELLED) return null
            val replacement = exception?.replacementTime
            val start = replacement?.instant(deviceZone) ?: actualStart
            val end = if (replacement != null) replacement.end?.let { end ->
                if (replacement.allDay) start.atZone(replacement.zone(deviceZone)).plusDays(
                    ChronoUnit.DAYS.between(replacement.start.toLocalDate(), end.toLocalDate())).toInstant()
                else start.plus(Duration.between(replacement.start.atZone(ZoneId.of(replacement.zoneId)), end.atZone(ZoneId.of(replacement.zoneId))))
            }
                else duration?.let {
                    // All-day ends follow local midnight; timed durations retain elapsed duration across DST.
                    if (time.allDay) start.atZone(time.zone(deviceZone)).plusDays(ChronoUnit.DAYS.between(time.start.toLocalDate(), time.end!!.toLocalDate())).toInstant()
                    else start.plus(it)
                }
            if (start >= until || (end ?: start.plusNanos(1)) <= from) return null
            return OrganizerOccurrence(record.copy(title = exception?.replacementTitle ?: record.title,
                details = exception?.replacementDetails ?: record.details, location = exception?.replacementLocation ?: record.location,
                sourceUrl = if (exception?.sourceUrlOverride != null) exception.sourceUrlOverride.takeIf { it.isNotEmpty() } else record.sourceUrl), original, start, end,
                exception?.disposition == OccurrenceDisposition.COMPLETED)
        }
        val rows = mutableListOf<OrganizerOccurrence>()
        for (slot in slots(time, record.repeat, deviceZone)) {
            if (slot.start >= until) break
            val exception = exceptions[slot.identity]
            if (exception?.replacementTime != null) continue
            occurrence(slot.identity, slot.start, exception)?.let(rows::add)
            // Report oversized queries explicitly; never silently omit scheduled occurrences.
            require(rows.size <= limit) { "Too many occurrences; narrow the date range" }
        }
        // Moved-in occurrences can originate beyond this window. Moved-out ones are already filtered above.
        exceptions.values.filter { it.replacementTime != null }
            .forEach { occurrence(it.originalStart, it.originalStart, it)?.let(rows::add) }
        require(rows.size <= limit) { "Too many occurrences; narrow the date range" }
        return rows.sortedWith(compareBy<OrganizerOccurrence> { it.start }.thenBy { it.key })
    }

    fun contains(record: OrganizerRecord, originalStart: Instant, deviceZone: ZoneId): Boolean {
        val time = record.time ?: return false
        return slots(time, record.repeat, deviceZone).takeWhile { it.identity <= originalStart }.any { it.identity == originalStart }
    }

    fun conflicts(events: List<OrganizerOccurrence>): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        val sorted = events.filter { it.record.kind == OrganizerKind.EVENT && it.end != null && !it.completed }.sortedBy { it.start }
        sorted.forEachIndexed { index, event ->
            for (other in sorted.drop(index + 1)) {
                if (other.start >= event.end!!) break
                if (other.end!! > event.start) {
                    result.getOrPut(event.key) { mutableSetOf() }.add(other.key)
                    result.getOrPut(other.key) { mutableSetOf() }.add(event.key)
                }
            }
        }
        return result
    }
}
