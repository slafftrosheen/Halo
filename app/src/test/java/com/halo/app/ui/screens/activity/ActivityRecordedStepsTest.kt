package com.halo.app.ui.screens.activity

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityRecordedStepsTest {
    @Test fun recordingPreviewIsBoundedWithoutLosingJournalEvents() {
        val steps = (1..12).map { ActivityStep("id$it", "Recorded", "Event $it") }
        assertEquals(listOf("id1","id2","id3","id4","id5"),
            activityVisibleRecordedSteps(steps, false).map { it.presentationId })
        assertEquals(steps, activityVisibleRecordedSteps(steps, true))
    }
    @Test fun smallPlansHaveNoHiddenEvents() {
        val steps = listOf(ActivityStep("step", "Finished", "Observed"))
        assertEquals(steps, activityVisibleRecordedSteps(steps, false))
    }
}
