package com.halo.app.ui.screens.activity

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivitySuspendedLabelTest {
    @Test fun suspendedRunIsNotLabeledAsActivelyExecutingOrCompleted() {
        val story = ActivityStory(
            id = "agent:paused", correlationId = "interaction", kind = ActivityKind.HALO,
            title = "Task", summary = "Waiting for resumption",
            timestamp = Instant.EPOCH, status = ActivityStatus.PENDING, steps = emptyList(),
            suspended = true,
        )
        assertEquals("Paused", activityStatusLabel(story))
        // It remains unfinished in the Running ledger until a terminal result.
        assertEquals(listOf(story), filterActivityStories(listOf(story), ActivityFilter.RUNNING))
        assertEquals(emptyList<ActivityStory>(), filterActivityStories(listOf(story), ActivityFilter.HISTORY))
    }
}
