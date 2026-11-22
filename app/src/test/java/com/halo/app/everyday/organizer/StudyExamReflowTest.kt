package com.halo.app.everyday.organizer

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyExamReflowTest {
    private val zone = ZoneId.of("Europe/Riga")
    private val firstDay = LocalDate.of(2026, 3, 26)
    private val now = firstDay.minusDays(1).atStartOfDay(zone).toInstant()
    private val start = firstDay.atTime(18, 0)
    private val time = OrganizerTime(start, start.plusMinutes(25), zone.id)
    private val record = OrganizerRecord("plan", OrganizerKind.EVENT, "Exam prep", time = time,
        repeat = RepeatRule(RepeatFrequency.DAILY, count = 3), createdAt = now,
        studyExam = StudyExamSchedule(firstDay.plusDays(4), (0..2).map { StudyExamLesson("lesson-$it", "Lesson $it", 1) }))

    @Test fun movingFirstSessionKeepsLessonOrderAndStableOccurrenceIdsAcrossClockChange() {
        val first = time.instant(zone)
        val edits = reflowStudyExam(record, emptyList(), OccurrenceOverride(record.id, first, OccurrenceDisposition.REPLACED,
            time.onDate(firstDay.plusDays(1), zone)), now, zone)
        val rows = OrganizerRecurrence.between(record, edits, now, firstDay.plusDays(4).atStartOfDay(zone).toInstant(), zone)
        assertEquals(listOf(27, 28, 29), rows.map { it.start.atZone(zone).dayOfMonth })
        assertEquals(listOf(18, 18, 18), rows.map { it.start.atZone(zone).hour })
        assertEquals(listOf("lesson-0", "lesson-1", "lesson-2"), rows.flatMap { row -> row.examLessons.map { it.sessionId } })
        assertEquals(first, rows.first().originalStart)
        assertEquals(25 * 60L, requireNotNull(rows.last().end).epochSecond - rows.last().start.epochSecond)
    }

    @Test fun aMoveThatWouldPushASessionOntoTheExamDayIsRejected() {
        val move = OccurrenceOverride(record.id, time.instant(zone), OccurrenceDisposition.REPLACED,
            time.onDate(firstDay.plusDays(2), zone))
        assertTrue(runCatching { reflowStudyExam(record, emptyList(), move, now, zone) }.isFailure)
    }
}
