package com.halo.app.ui.screens.study

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
