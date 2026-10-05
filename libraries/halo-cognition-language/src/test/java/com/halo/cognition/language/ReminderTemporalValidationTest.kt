package com.halo.cognition.language

import org.junit.Assert.*
import org.junit.Test

class ReminderTemporalValidationTest {
    @Test fun usableTimesAreCanonicalAndMissingInvalidAmbiguousTimesClarify() {
        assertNull(ReminderTemporalValidation.canonical(""))
        assertNull(ReminderTemporalValidation.canonical("tomorrow"))
        assertNull(ReminderTemporalValidation.canonical("at seven"))
        assertNull(ReminderTemporalValidation.canonical("at 99:99"))
        assertEquals("tomorrow at 09:00", ReminderTemporalValidation.canonical("tomorrow morning"))
        assertEquals("tomorrow at 07:00", ReminderTemporalValidation.canonical("tomorrow at seven in the morning"))
        assertEquals("at 19:00", ReminderTemporalValidation.canonical("seven in the evening"))
        assertEquals("in 10 minutes", ReminderTemporalValidation.canonical("in 10 minutes"))
        assertNull(ReminderTemporalValidation.canonical("in 0 minutes"))
        assertEquals("tomorrow at 07:00", ReminderTemporalValidation.canonical("tomorrow at seven"))
        assertNull(ReminderTemporalValidation.canonical("today at 07:00", java.time.LocalTime.NOON))
    }

    @Test fun supportedAndInvalidTemporalMatrix() {
        val expected = mapOf("tomorrow morning" to "tomorrow at 09:00", "tomorrow afternoon" to "tomorrow at 15:00",
            "tomorrow evening" to "tomorrow at 19:00", "tomorrow night" to "tomorrow at 21:00",
            "tomorrow at 7" to "tomorrow at 07:00", "tomorrow at 7:30" to "tomorrow at 07:30",
            "today at 18:00" to "today at 18:00", "at 7 pm" to "at 19:00",
            "in 20 minutes" to "in 20 minutes", "in 2 hours" to "in 2 hours")
        expected.forEach { (input, output) -> assertEquals(input, output, ReminderTemporalValidation.canonical(input, java.time.LocalTime.NOON)) }
        listOf("tomorrow eventually", "at banana", "in later", "32:91").forEach { assertNull(it, ReminderTemporalValidation.canonical(it)) }
        listOf("at seven", "at 7").forEach { assertTrue(ReminderTemporalValidation.ambiguous(it)); assertNull(ReminderTemporalValidation.canonical(it)) }
    }

}
