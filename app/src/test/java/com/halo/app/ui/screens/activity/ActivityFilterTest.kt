package com.halo.app.ui.screens.activity

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ActivityFilterTest {
    private fun story(id: String, status: ActivityStatus, review: Boolean = false, seconds: Long = 0) =
        ActivityStory(id, null, ActivityKind.HALO, id, id, Instant.ofEpochSecond(seconds), status,
            emptyList(), reviewRunId = id.takeIf { review })

    @Test fun approvalsPrecedeNewerHistoryAndRunningWork() {
        val review = story("review", ActivityStatus.PENDING, true)
        val done = story("done", ActivityStatus.SUCCESS, seconds = 20)
        val running = story("running", ActivityStatus.PENDING, seconds = 10)
        assertEquals(listOf(review, done, running), filterActivityStories(listOf(done, running, review), ActivityFilter.ALL))
        assertEquals(listOf(review), filterActivityStories(listOf(done, running, review), ActivityFilter.REVIEW))
        assertEquals(listOf(running), filterActivityStories(listOf(done, running, review), ActivityFilter.RUNNING))
    }

    @Test fun historicalFilteringPreservesInterruptedFailedAndUnconfirmedResults() {
        val history = listOf(ActivityStatus.SUCCESS, ActivityStatus.FAILED, ActivityStatus.INTERRUPTED,
            ActivityStatus.UNKNOWN, ActivityStatus.CANCELLED).map { story(it.name, it) }
        val filtered = filterActivityStories(history + story("waiting", ActivityStatus.PENDING), ActivityFilter.HISTORY)
        assertEquals(history, filtered)
        assertEquals("Result not confirmed", activityStatusLabel(history.first { it.status == ActivityStatus.UNKNOWN }))
        assertEquals("Couldn’t finish", activityStatusLabel(history.first { it.status == ActivityStatus.FAILED }))
    }
}
