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
    fun `activity keeps only user and halo turns`() {
        val state = mapper.map(
            listOf(
                event("1", "Engine Started", "engine.reasoning", 1_000L, "Started"),
                event("2", "You", "What time is it?", 2_000L, "Sent"),
                event("3", "Reasoning", "Provider: local", 3_000L, "Started"),
                event("4", "Halo", "It is 18:30.", 4_000L, "Completed")
            )
        )

        assertEquals(2, state.events.size)
        assertEquals(ActivitySpeaker.Halo, state.events[0].speaker)
        assertEquals("It is 18:30.", state.events[0].message)
        assertEquals(ActivitySpeaker.User, state.events[1].speaker)
        assertEquals("What time is it?", state.events[1].message)
    }

    @Test
    fun `failed halo turn remains visible as failure`() {
        val state = mapper.map(
            listOf(event("1", "Halo", "Cannot reach provider", 1_000L, "Failed"))
        )

        assertEquals(1, state.events.size)
        assertTrue(state.events.single().failed)
    }

    @Test
    fun `normal halo and user turns are not failures`() {
        val state = mapper.map(
            listOf(
                event("1", "You", "Hello", 1_000L, "Sent"),
                event("2", "Halo", "Hi", 2_000L, "Completed")
            )
        )

        assertFalse(state.events.any { it.failed })
    }

    private fun event(
        id: String,
        title: String,
        description: String,
        timestamp: Long,
        status: String
    ) = TimelineEvent(
        id = id,
        title = title,
        description = description,
        timestamp = Instant.ofEpochMilli(timestamp),
        status = status
    )
}
