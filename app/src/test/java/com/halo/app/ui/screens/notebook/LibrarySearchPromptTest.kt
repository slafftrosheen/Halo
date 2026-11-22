package com.halo.app.ui.screens.notebook

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class LibrarySearchPromptTest {
    @Test fun lastMonthUsesTheSavedDateAcrossTheNewYear() {
        val today = LocalDate.of(2026, 1, 6)
        val prompt = parseLibrarySearchPrompt("Find last month’s receipt", today)
        assertEquals("receipt", prompt.terms)
        assertEquals(YearMonth.of(2025, 12), prompt.monthSaved)
        assertTrue(prompt.includes(Instant.parse("2025-12-15T11:00:00Z").toEpochMilli(), ZoneOffset.UTC))
        assertFalse(prompt.includes(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(), ZoneOffset.UTC))
        assertEquals("чек", parseLibrarySearchPrompt("Найди чек за прошлый месяц", today).terms)
        assertNull(parseLibrarySearchPrompt("last month's notebook", today).monthSaved)
    }
}
