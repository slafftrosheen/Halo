package com.halo.app.ui.screens.activity

import com.halo.sdk.runtime.TimelineEvent
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityUiMapperTest {
    private val mapper = ActivityUiMapper()

    @Test
    fun `activity maps all timeline events into ledger`() {
        val state = mapper.map(
            listOf(
                event("1", "Engine Started", "engine.reasoning", 1_000L, "Started"),
                event("2", "You", "What time is it?", 2_000L, "Sent"),
                event("3", "Reasoning", "Provider: local", 3_000L, "Started"),
                event("4", "Halo", "It is 18:30.", 4_000L, "Completed")
            )
        )

        assertEquals(4, state.events.size)
        assertEquals(ActivityKind.HALO, state.events[0].kind)
        assertEquals("It is 18:30.", state.events[0].summary)
        assertEquals(ActivityKind.USER, state.events[2].kind)
        assertEquals("What time is it?", state.events[2].summary)
    }

    @Test
    fun `failed halo turn remains visible as failure`() {
        val state = mapper.map(
            listOf(event("1", "Halo", "Cannot reach provider", 1_000L, "Failed"))
        )

        assertEquals(1, state.events.size)
        assertTrue(state.events.single().status == ActivityStatus.FAILED)
    }

    @Test
    fun `normal halo and user turns are not failures`() {
        val state = mapper.map(
            listOf(
                event("1", "You", "Hello", 1_000L, "Sent"),
                event("2", "Halo", "Hi", 2_000L, "Completed")
            )
        )

        assertFalse(state.events.any { it.status == ActivityStatus.FAILED })
    }

    @Test
    fun `same execution identity produces distinct presentation ids and avoids duplicate keys`() {
        val state = mapper.map(
            listOf(
                event("ExecutionStarted:exec-1:1000:started", "Execution Started", "plan-1", 1_000L, "Started", "exec-1"),
                event("ExecutionResult:exec-1:2000:result", "Execution Result", "result", 2_000L, "Completed", "exec-1"),
                event("ExecutionFinished:exec-1:3000:finished", "Execution Finished", "plan-1", 3_000L, "Finished", "exec-1")
            )
        )

        assertEquals(1, state.events.size)
        val story = state.events.single()
        assertEquals("exec-1", story.correlationId)
        assertEquals(3, story.steps.size)
        assertEquals(3, story.steps.map { it.presentationId }.distinct().size)
    }

    private fun event(
        presentationId: String,
        title: String,
        description: String,
        timestamp: Long,
        status: String,
        correlationId: String? = null
    ) = TimelineEvent(
        presentationId = presentationId,
        correlationId = correlationId,
        title = title,
        description = description,
        timestamp = Instant.ofEpochMilli(timestamp),
        status = status
    )
}
