package com.halo.app.ui.screens.activity

import com.halo.sdk.runtime.TimelineEvent
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityUiMapperTest {
    private val mapper = ActivityUiMapper()

    @Test fun `durable dispatch is unresolved until the same call has a definitive receipt`() {
        fun receipt(call: String, truth: com.halo.runtime.cognition.agent.ActionTruth) =
            com.halo.runtime.cognition.agent.AgentStep.ToolCompleted(
                com.halo.runtime.cognition.agent.AgentStepId("receipt-$call-$truth"),
                com.halo.runtime.cognition.agent.AgentRunId("run"), Instant.EPOCH,
                call, "halo_open_app", "private result", true, truth)
        val dispatched = receipt("one", com.halo.runtime.cognition.agent.ActionTruth.DISPATCHED)
        val confirmed = receipt("one", com.halo.runtime.cognition.agent.ActionTruth.CONFIRMED)
        assertTrue(activityHasUnconfirmedReceipts(listOf(dispatched)))
        assertFalse(activityHasUnconfirmedReceipts(listOf(dispatched, confirmed)))
        assertTrue(activityHasUnconfirmedReceipts(listOf(dispatched,
            receipt("other", com.halo.runtime.cognition.agent.ActionTruth.CONFIRMED))))
    }

    @Test fun `returned tool without effect proof is not completed`() {
        val story = mapper.map(listOf(event("returned", "halo_open_app", "Executor returned", 1000, "Returned"))).events.single()
        assertEquals(ActivityStatus.UNKNOWN, story.status)
        assertEquals("Result not confirmed", activityStatusLabel(story))
        assertEquals("App action", story.title)
        assertEquals("Delivery not confirmed", story.summary)
    }

    @Test fun `later answer does not confirm an earlier dispatch`() {
        val receipt = event("dispatch", "halo_open_app", "Returned", 1000, "Returned", "run")
            .copy(intentId = "call", details = mapOf("actionTruth" to "DISPATCHED"))
        val answer = event("answer", "Halo", "I sent the request", 2000, "Completed", "run")
        assertEquals(ActivityStatus.UNKNOWN, mapper.map(listOf(receipt, answer)).events.single().status)
    }

    @Test fun `confirmed receipt supersedes dispatch for the same action only`() {
        val dispatch = event("dispatch", "halo_open_app", "Returned", 1000, "Returned", "run")
            .copy(intentId = "call", details = mapOf("actionTruth" to "DISPATCHED"))
        val confirmed = dispatch.copy(presentationId = "confirmed", timestamp = Instant.ofEpochMilli(2000),
            details = mapOf("actionTruth" to "CONFIRMED"))
        assertEquals(ActivityStatus.SUCCESS, mapper.map(listOf(dispatch, confirmed)).events.single().status)
        assertEquals(ActivityStatus.UNKNOWN, mapper.map(listOf(dispatch, confirmed.copy(intentId = "other-call"))).events.single().status)
    }

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


    @Test
    fun `grouped requested run remains pending until there is completion evidence`() {
        val pending = mapper.map(
            listOf(
                event("request", "Open app", "Starting", 1_000L, "Requested", "run-x"),
                event("started", "Open app", "Working", 2_000L, "Started", "run-x")
            )
        ).events.single()
        assertEquals(ActivityStatus.PENDING, pending.status)
        val completed = mapper.map(
            listOf(
                event("request", "Open app", "Starting", 1_000L, "Requested", "run-x"),
                event("done", "Open app", "Opened", 2_000L, "Completed", "run-x")
            )
        ).events.single()
        assertEquals(ActivityStatus.SUCCESS, completed.status)
    }

    @Test
    fun `failed evidence remains failed even after later unrelated statuses`() {
        val state = mapper.map(listOf(
            event("started", "Open app", "Starting", 1_000L, "Started", "run-z"),
            event("failed", "Open app", "Rejected", 2_000L, "Failed", "run-z"),
            event("finished", "Open app", "Cleanup finished", 3_000L, "Finished", "run-z")
        ))
        assertEquals(ActivityStatus.FAILED, state.events.single().status)
    }

    @Test
    fun `Activity time follows requested app locale and not fixed system hour format`() {
        val whenUtc = Instant.parse("2026-10-15T18:03:00Z")
        val us = whenUtc.activityDisplayTime(java.util.Locale.US, java.time.ZoneOffset.UTC)
        val fr = whenUtc.activityDisplayTime(java.util.Locale.FRANCE, java.time.ZoneOffset.UTC)
        assertTrue(us.contains("PM"))
        assertTrue(fr.contains("18"))
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
