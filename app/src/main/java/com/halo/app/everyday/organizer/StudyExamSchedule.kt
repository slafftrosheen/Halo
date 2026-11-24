package com.halo.app.everyday.organizer

import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

data class StudyExamLesson(val sessionId: String, val title: String, val version: Long) {
    init {
        require(sessionId.matches(Regex("[A-Za-z0-9_.:-]{1,160}")))
        require(title.isNotBlank() && title.length <= 120)
        require(version > 0)
    }
}

data class StudyExamSchedule(val examDate: LocalDate, val lessons: List<StudyExamLesson>,
    val restDays: Set<DayOfWeek> = emptySet(), val quickReview: Boolean = false) {
    init {
        require(examDate.year in 1970..2199)
        require(lessons.size <= 50 && lessons.distinctBy(StudyExamLesson::sessionId).size == lessons.size)
        require(restDays.size < DayOfWeek.entries.size)
    }

    fun isRest(date: LocalDate) = date.dayOfWeek in restDays
    fun isLight(date: LocalDate) = quickReview && date == examDate.minusDays(1) && !isRest(date)

    fun lessonsOn(date: LocalDate, dates: List<LocalDate>): List<StudyExamLesson> {
        val learning = dates.filterNot { isRest(it) || isLight(it) }
        return lessonsOn(learning.indexOf(date), learning.size)
    }

    fun lessonsOn(index: Int, days: Int): List<StudyExamLesson> {
        if (index !in 0 until days || lessons.isEmpty()) return emptyList()
        val first = (index * lessons.size + days - 1) / days
        val end = ((index + 1) * lessons.size + days - 1) / days
        return lessons.subList(first, end)
    }
}

internal fun OrganizerRecord.examLessonsFor(originalStart: Instant): List<StudyExamLesson> {
    val plan = studyExam ?: return emptyList()
    val time = requireNotNull(time)
    val date = originalStart.atZone(ZoneId.of(time.zoneId)).toLocalDate()
    return plan.lessonsOn(date, List(repeat?.count ?: 1) { time.start.toLocalDate().plusDays(it.toLong()) })
}

/** Keep the moved lesson anchored and make room on either side without changing nominal occurrence IDs. */
internal fun reflowStudyExam(record: OrganizerRecord, current: List<OccurrenceOverride>, move: OccurrenceOverride,
    now: Instant, deviceZone: ZoneId): List<OccurrenceOverride> {
    val exam = requireNotNull(record.studyExam)
    val base = requireNotNull(record.time)
    val zone = ZoneId.of(base.zoneId)
    val existing = current.associateBy(OccurrenceOverride::originalStart)
    val slots = OrganizerRecurrence.slots(base, record.repeat, deviceZone).take(record.repeat?.count ?: 1)
        .filter { existing[it.identity]?.disposition != OccurrenceDisposition.CANCELLED || it.identity == move.originalStart }
        .toList()
    val selected = slots.indexOfFirst { it.identity == move.originalStart }
    require(selected >= 0)
    val replacement = requireNotNull(move.replacementTime)
    require(!replacement.allDay && !replacement.followDeviceZone && replacement.zoneId == zone.id)
    val originalTimes = slots.map { slot ->
        OrganizerRecurrence.exact(record, current, slot.identity, deviceZone)?.let { occurrence ->
            val prior = existing[slot.identity]?.replacementTime
            (prior ?: base).copy(start = occurrence.start.atZone(zone).toLocalDateTime(),
                end = occurrence.end?.atZone(zone)?.toLocalDateTime(),
                startOffset = occurrence.start.atZone(zone).offset, endOffset = occurrence.end?.atZone(zone)?.offset)
        } ?: base.onDate(slot.identity.atZone(zone).toLocalDate(), zone)
    }
    require(originalTimes[selected].instant(zone) > now && replacement.instant(zone) > now) { "Choose a future study session" }
    require(replacement.start.toLocalDate() < exam.examDate && !exam.isRest(replacement.start.toLocalDate())) {
        "Choose a study day before the exam"
    }
    fun findDay(start: LocalDate, step: Long): LocalDate {
        var day = start
        while (exam.isRest(day)) day = day.plusDays(step)
        return day
    }
    val dates = originalTimes.map { it.start.toLocalDate() }.toMutableList()
    dates[selected] = replacement.start.toLocalDate()
    for (index in selected - 1 downTo 0) dates[index] = findDay(minOf(dates[index], dates[index + 1].minusDays(1)), -1)
    for (index in selected + 1 until dates.size) dates[index] = findDay(maxOf(dates[index], dates[index - 1].plusDays(1)), 1)
    require(dates.last() < exam.examDate) { "There is no room for the remaining lessons before the exam" }
    return slots.mapIndexedNotNull { index, slot ->
        if (index == selected) return@mapIndexedNotNull move
        val old = originalTimes[index]
        val date = dates[index]
        if (date == old.start.toLocalDate()) return@mapIndexedNotNull null
        val next = old.onDate(date, zone)
        require(old.instant(zone) > now && next.instant(zone) > now) { "The move would affect a past study session" }
        (existing[slot.identity] ?: OccurrenceOverride(record.id, slot.identity, OccurrenceDisposition.REPLACED, next))
            .copy(disposition = OccurrenceDisposition.REPLACED, replacementTime = next)
    }
}

internal fun OrganizerTime.onDate(date: LocalDate, zone: ZoneId): OrganizerTime {
    val start = date.atTime(start.toLocalTime()).atZone(zone)
    val end = start.plus(requireNotNull(duration()))
    return copy(start = start.toLocalDateTime(), end = end.toLocalDateTime(), startOffset = start.offset, endOffset = end.offset)
}
