package com.halo.app.ui.screens.study

import java.time.LocalDate
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test
import com.halo.app.everyday.organizer.StudyExamLesson
import com.halo.app.everyday.organizer.StudyExamSchedule

class StudyExamPlanTest {
    private val exam = LocalDate.of(2026, 12, 2)
    private val zone = ZoneId.of("Europe/Riga")
    private val evening = LocalTime.of(18, 0)

    @Test fun planWorksBackwardsFromExamAndNeverSchedulesOnExamDay() {
        val now = exam.minusWeeks(2).atStartOfDay(zone)
        assertEquals(listOf(exam.minusDays(3), exam.minusDays(2), exam.minusDays(1)),
            studyExamDates(exam, evening, 3, now))
    }

    @Test fun pastSessionsDropOutWithoutLosingLaterDays() {
        val now = exam.minusDays(2).atTime(18, 1).atZone(zone)
        assertEquals(listOf(exam.minusDays(1)), studyExamDates(exam, evening, 7, now))
        assertEquals(emptyList<LocalDate>(), studyExamDates(exam, evening, 7, now.plusDays(1)))
    }

    @Test fun restAndQuickReviewDaysReceiveNoNewLessons() {
        val friday = LocalDate.of(2026, 12, 4)
        val days = List(5) { friday.plusDays(it.toLong()) }
        val lessons = List(4) { StudyExamLesson("lesson-$it", "Topic $it", 1) }
        val plan = StudyExamSchedule(days.last().plusDays(1), lessons,
            setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), quickReview = true)
        assertEquals(listOf(2, 0, 0, 2, 0), days.map { plan.lessonsOn(it, days).size })
        assertEquals(lessons, days.flatMap { plan.lessonsOn(it, days) })
    }
}
