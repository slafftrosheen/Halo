package com.halo.app.ui.study.v13

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * halo 13.0 · a test date becomes a calm day-by-day plan.
 * Rest days are kept free. The day before the test is light: a quick look, no new material.
 * Nothing is booked in Planner until the learner taps "Put it in Planner".
 */
data class PlanDay(val date: LocalDate, val newCards: Int, val light: Boolean = false, val rest: Boolean = false)

object ExamPlan {
    fun make(cards: Int, today: LocalDate, exam: LocalDate, restDays: Set<DayOfWeek> = emptySet()): List<PlanDay> {
        require(cards >= 0 && exam.isAfter(today))
        val days = generateSequence(today) { it.plusDays(1) }.takeWhile { it.isBefore(exam) }.toList()
        val lightDay = days.last()
        val study = days.filter { it != lightDay && it.dayOfWeek !in restDays }.ifEmpty { listOf(lightDay) }
        val base = cards / study.size; var extra = cards % study.size
        return days.map { d ->
            when {
                d in study -> PlanDay(d, base + if (extra-- > 0) 1 else 0, light = d == lightDay && study.size == 1)
                d == lightDay -> PlanDay(d, 0, light = true)
                else -> PlanDay(d, 0, rest = true)
            }
        }
    }
}
