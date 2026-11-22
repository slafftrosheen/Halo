package com.halo.app.everyday.organizer

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class OrganizerScheduleEdit { MOVE, RESIZE }

/** Converts a snapped calendar gesture into the exact time the preview displays. */
internal fun rescheduledEventTime(
    source: OrganizerTime,
    occurrence: OrganizerOccurrence,
    operation: OrganizerScheduleEdit,
    steps: Int,
    deviceZone: ZoneId,
): OrganizerTime {
    require(steps != 0 && steps in -1344..1344)
    val originalEnd = requireNotNull(occurrence.end)
    val zone = source.zone(deviceZone)
    if (occurrence.allDay) {
        val originalStart = occurrence.start.atZone(zone).toLocalDate()
        val endDate = originalEnd.atZone(zone).toLocalDate().plusDays(steps.toLong())
        val startDate = if (operation == OrganizerScheduleEdit.MOVE) originalStart.plusDays(steps.toLong()) else originalStart
        return OrganizerTime(startDate.atStartOfDay(), maxOf(startDate.plusDays(1), endDate).atStartOfDay(),
            zone.id, allDay = true, followDeviceZone = source.followDeviceZone).also(OrganizerTime::validate)
    }
    fun snapped(value: Instant): Instant {
        val local = value.atZone(zone)
        val tick = ((local.minute % 15) * 60000 + local.second * 1000 + local.nano / 1000000)
        return value.plusMillis(steps * 900000L - tick + if (tick >= 450000) 900000 else 0)
    }
    val newStart = if (operation == OrganizerScheduleEdit.MOVE) snapped(occurrence.start) else occurrence.start
    val newEnd = when (operation) {
        OrganizerScheduleEdit.MOVE -> newStart.plus(Duration.between(occurrence.start, originalEnd))
        OrganizerScheduleEdit.RESIZE -> maxOf(minOf(originalEnd, newStart.plus(15, ChronoUnit.MINUTES)), snapped(originalEnd))
    }
    val start = newStart.atZone(zone)
    val end = newEnd.atZone(zone)
    val localStart = start.toLocalDateTime()
    val localEnd = end.toLocalDateTime()
    return OrganizerTime(localStart, localEnd, zone.id, followDeviceZone = source.followDeviceZone,
        startOffset = start.offset.takeIf { localStart.atZone(zone).toInstant() != newStart },
        endOffset = end.offset.takeIf { localEnd.atZone(zone).toInstant() != newEnd }).also(OrganizerTime::validate)
}
