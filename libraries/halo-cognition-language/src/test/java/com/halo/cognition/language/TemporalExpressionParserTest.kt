package com.halo.cognition.language

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalExpressionParserTest {
    private val parser = TemporalExpressionParser()

    @Test
    fun `parse 8`() {
        val result = parser.parse("8")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.TimeOfDay
        assertEquals(LocalTime.of(8, 0), value.time)
    }

    @Test
    fun `parse 8 am`() {
        val result = parser.parse("8 am")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.TimeOfDay
        assertEquals(LocalTime.of(8, 0), value.time)
    }

    @Test
    fun `parse 8 pm`() {
        val result = parser.parse("8 pm")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.TimeOfDay
        assertEquals(LocalTime.of(20, 0), value.time)
    }

    @Test
    fun `parse 8 30`() {
        val result = parser.parse("8:30")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.TimeOfDay
        assertEquals(LocalTime.of(8, 30), value.time)
    }

    @Test
    fun `parse 08 30`() {
        val result = parser.parse("08:30")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.TimeOfDay
        assertEquals(LocalTime.of(8, 30), value.time)
    }

    @Test
    fun `parse 20 30`() {
        val result = parser.parse("20:30")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.TimeOfDay
        assertEquals(LocalTime.of(20, 30), value.time)
    }

    @Test
    fun `parse at 8`() {
        val result = parser.parse("at 8")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.TimeOfDay
        assertEquals(LocalTime.of(8, 0), value.time)
    }

    @Test
    fun `parse at eight`() {
        val result = parser.parse("at eight")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.TimeOfDay
        assertEquals(LocalTime.of(8, 0), value.time)
    }

    @Test
    fun `parse at eight thirty`() {
        val result = parser.parse("at eight thirty")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.TimeOfDay
        assertEquals(LocalTime.of(8, 30), value.time)
    }

    @Test
    fun `parse today at 8`() {
        val result = parser.parse("today at 8")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.TimeOfDay
        assertEquals(LocalTime.of(8, 0), value.time)
        assertEquals(DayRelation.TODAY, value.dayRelation)
    }

    @Test
    fun `parse today at 8 pm`() {
        val result = parser.parse("today at 8 pm")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.TimeOfDay
        assertEquals(LocalTime.of(20, 0), value.time)
        assertEquals(DayRelation.TODAY, value.dayRelation)
    }

    @Test
    fun `parse tomorrow at 8`() {
        val result = parser.parse("tomorrow at 8")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.TimeOfDay
        assertEquals(LocalTime.of(8, 0), value.time)
        assertEquals(DayRelation.TOMORROW, value.dayRelation)
    }

    @Test
    fun `parse tomorrow at eight`() {
        val result = parser.parse("tomorrow at eight")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.TimeOfDay
        assertEquals(LocalTime.of(8, 0), value.time)
        assertEquals(DayRelation.TOMORROW, value.dayRelation)
    }

    @Test
    fun `parse tomorrow evening`() {
        val result = parser.parse("tomorrow evening")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.DayPart
        assertEquals(DayPartKind.EVENING, value.part)
        assertEquals(DayRelation.TOMORROW, value.dayRelation)
    }

    @Test
    fun `parse tonight`() {
        val result = parser.parse("tonight")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.DayPart
        assertEquals(DayPartKind.NIGHT, value.part)
        assertEquals(DayRelation.TODAY, value.dayRelation)
    }

    @Test
    fun `parse tonight at nine`() {
        val result = parser.parse("tonight at nine")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.TimeOfDay
        assertEquals(LocalTime.of(21, 0), value.time)
        assertEquals(DayRelation.TODAY, value.dayRelation)
    }

    @Test
    fun `parse morning`() {
        val result = parser.parse("morning")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.DayPart
        assertEquals(DayPartKind.MORNING, value.part)
    }

    @Test
    fun `parse evening`() {
        val result = parser.parse("evening")
        assertTrue(result is TemporalParseResult.Parsed)
        val value = (result as TemporalParseResult.Parsed).value as TemporalValue.DayPart
        assertEquals(DayPartKind.EVENING, value.part)
    }

    @Test
    fun `parse 25 00 is invalid`() {
        val result = parser.parse("25:00")
        assertTrue(result is TemporalParseResult.Invalid)
    }

    @Test
    fun `parse 8 99 is invalid`() {
        val result = parser.parse("8:99")
        assertTrue(result is TemporalParseResult.Invalid)
    }

    @Test
    fun `parse banana is invalid`() {
        val result = parser.parse("banana")
        assertTrue(result is TemporalParseResult.Invalid)
    }

    @Test
    fun `parse empty is incomplete`() {
        val result = parser.parse("")
        assertTrue(result is TemporalParseResult.Incomplete)
    }
}
