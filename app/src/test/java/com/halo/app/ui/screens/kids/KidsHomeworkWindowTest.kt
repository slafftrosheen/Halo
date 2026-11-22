package com.halo.app.ui.screens.kids

import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsHomeworkWindowTest {
    @Test fun closedByDefaultAndOpenOnlyDuringApprovedWindow() {
        val schedule = KidsHomeworkWindow()
        assertFalse(schedule.includes(LocalTime.of(17, 0)))
        val open = schedule.copy(enabled = true)
        assertFalse(open.includes(LocalTime.of(15, 59)))
        assertTrue(open.includes(LocalTime.of(16, 0)))
        assertTrue(open.includes(LocalTime.of(17, 59)))
        assertFalse(open.includes(LocalTime.of(18, 0)))
    }

    @Test fun overnightScheduleIncludesMidnightAndExcludesEnd() {
        val open = KidsHomeworkWindow(true, LocalTime.of(23, 0), LocalTime.of(1, 0))
        assertFalse(open.includes(LocalTime.of(22, 59)))
        assertTrue(open.includes(LocalTime.of(23, 0)))
        assertTrue(open.includes(LocalTime.MIDNIGHT))
        assertTrue(open.includes(LocalTime.of(0, 59)))
        assertFalse(open.includes(LocalTime.of(1, 0)))
    }
}
