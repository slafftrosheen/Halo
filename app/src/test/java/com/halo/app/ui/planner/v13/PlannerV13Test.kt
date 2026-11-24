package com.halo.app.ui.planner.v13

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PlannerV13Test {
    private val mon = LocalDate.of(2026, 11, 23)

    @Test fun everyThursdayAtFive() {
        val d = NaturalAdd.parse("Swimming with Mia every Thursday at 5", mon)
        assertEquals(DayOfWeek.THURSDAY, d.weekly); assertEquals(LocalTime.of(17, 0), d.time)
        assertTrue(d.family); assertEquals("Swimming with Mia", d.title); assertTrue(d.unsure.isNotEmpty())
    }

    @Test fun russianTomorrow() {
        val d = NaturalAdd.parse("Позвонить маме завтра в 10", mon)
        assertEquals(mon.plusDays(1), d.date); assertEquals(LocalTime.of(10, 0), d.time)
    }

    @Test fun explicitTimesPreserveMorningAndMidnight() {
        assertEquals(LocalTime.MIDNIGHT, NaturalAdd.parse("Train tomorrow at 12 am", mon).time)
        assertEquals(LocalTime.of(5, 30), NaturalAdd.parse("Train tomorrow at 05:30", mon).time)
    }

    @Test fun invalidTimeRemainsInTheTitleForReview() {
        val draft = NaturalAdd.parse("Call tomorrow at 25:99", mon)
        assertNull(draft.time)
        assertEquals("Call at 25:99", draft.title)
        assertEquals(mon.plusDays(1), draft.date)
    }

    @Test fun fixedEventsDontMove() = assertNull(DayTimeline.drop(Item("a", LocalTime.of(9, 0), LocalTime.of(10, 0), fixed = true), LocalTime.NOON))

    @Test fun threeOptionsAcrossTheWeek() {
        val o = FindTime.options(emptyList(), mon.atTime(7, 0), 7, Duration.ofHours(1))
        assertEquals(3, o.size); assertEquals(3, o.map { it.start.toLocalDate() }.distinct().size)
    }

    @Test fun aMultiDayEventBlocksTheEntireMiddleDay() {
        val booking = Busy(mon.atTime(10, 0), mon.plusDays(2).atTime(10, 0))
        val slots = FindTime.options(listOf(booking), mon.atTime(7, 0), 3, Duration.ofHours(1))
        assertEquals(listOf(mon.atTime(8, 0), mon.plusDays(2).atTime(10, 15)), slots.map { it.start })
    }

    @Test fun aSlotCanEndAtClosingTimeWithNoActualMeetingToBuffer() {
        val slots = FindTime.options(emptyList(), mon.atTime(20, 0), 1, Duration.ofHours(1))
        assertEquals(mon.atTime(21, 0), slots.single().end)
    }

    @Test fun neverSuggestsMustDoTasks() {
        val s = TooMuchToday.suggest(DayLoad(500, 480), listOf(Movable("a", 60, true, 0), Movable("b", 60, false, 1)))
        assertEquals(listOf("b"), s.map { it.id })
    }
    @Test fun russianTitleAndFamily() {
        val d = NaturalAdd.parse("Обед с мамой завтра в 13", mon)
        assertEquals("Обед с мамой", d.title); assertTrue(d.family); assertEquals(LocalTime.of(13, 0), d.time)
    }
}
