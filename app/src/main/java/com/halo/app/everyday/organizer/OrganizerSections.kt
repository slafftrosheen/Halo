package com.halo.app.everyday.organizer

import java.time.Instant
import java.time.ZoneId

enum class OrganizerSection { TODAY, PLANNER, TIME, ROUTINES, INBOX, RESEARCH }

object OrganizerSections {
    fun records(section: OrganizerSection, records: List<OrganizerRecord>, now: Instant, zone: ZoneId,
        occurrenceIds: Set<String>? = null, outstandingIds: Set<String> = emptySet()): List<OrganizerRecord> {
        val from = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val end = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        return records.filter { it.deletedAt == null && it.status != OrganizerStatus.CANCELLED }.filter { record ->
            val work = record.commitment
            val dueToday = if (section != OrganizerSection.TODAY) false else if (record.repeat != null) occurrenceIds?.contains(record.id)
                ?: OrganizerRecurrence.between(record, emptyList(), from, end, zone).any { !it.completed }
                else when (record.kind) {
                    OrganizerKind.EVENT -> record.time?.let { it.instant(zone) < end && (it.end ?: it.start).atZone(it.zone(zone)).toInstant() >= from } == true
                    OrganizerKind.REMINDER -> record.time?.instant(zone)?.let { it >= from && it < end } == true || record.id in outstandingIds
                    OrganizerKind.ALARM -> record.time?.instant(zone)?.let { it >= from && it < end } == true
                    OrganizerKind.TASK -> record.time?.instant(zone)?.let { it < end } == true
                    OrganizerKind.TIMER -> true
                }
            when (section) {
                OrganizerSection.TODAY -> record.status == OrganizerStatus.ACTIVE && work.state != HaloWorkState.COMPLETED &&
                    work.purpose !in setOf(CommitmentPurpose.INBOX, CommitmentPurpose.STOPWATCH) &&
                    (work.state in setOf(HaloWorkState.RUNNING, HaloWorkState.NEEDS_USER) || record.kind == OrganizerKind.TIMER || record.id in outstandingIds ||
                        dueToday || record.kind == OrganizerKind.TASK &&
                        work.owner == CommitmentOwner.ME && record.time == null && record.priority >= TaskPriority.HIGH)
                OrganizerSection.PLANNER -> record.status == OrganizerStatus.ACTIVE && work.state != HaloWorkState.COMPLETED &&
                    work.purpose !in setOf(CommitmentPurpose.INBOX, CommitmentPurpose.STOPWATCH) &&
                    (record.kind != OrganizerKind.EVENT || record.repeat != null || record.time?.let { (it.end ?: it.start).atZone(it.zone(zone)).toInstant() >= from } == true)
                OrganizerSection.TIME -> record.kind in setOf(OrganizerKind.ALARM, OrganizerKind.TIMER) || work.purpose == CommitmentPurpose.STOPWATCH
                OrganizerSection.ROUTINES -> work.purpose in setOf(CommitmentPurpose.ROUTINE, CommitmentPurpose.WATCH)
                OrganizerSection.INBOX -> work.purpose == CommitmentPurpose.INBOX
                OrganizerSection.RESEARCH -> false
            }
        }.sortedWith(compareBy<OrganizerRecord> { it.commitment.state != HaloWorkState.NEEDS_USER }.thenBy { it.time?.instant(zone) ?: Instant.MAX }
            .thenByDescending { it.priority }.thenBy { it.id })
    }
    fun responsibility(records: List<OrganizerRecord>) = CommitmentOwner.entries.associateWith { owner -> records.filter { it.commitment.owner == owner } }
}
