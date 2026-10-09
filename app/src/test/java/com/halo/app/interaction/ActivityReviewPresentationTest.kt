package com.halo.app.interaction

import com.halo.runtime.cognition.agent.AgentInputRequestId
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.interaction.*
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityReviewPresentationTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }
    @Test fun explicitReviewSelectsTheRequestedRunWithoutResolvingIt() = runTest {
        val store = InMemoryInteractionSurfaceStore()
        var resolutions = 0
        val presenter = AndroidInteractionPresenter(store, object : InteractionResponseHandler {
            override suspend fun respond(response: InteractionResponse): InteractionResponseResult {
                resolutions++
                return InteractionResponseResult.Accepted
            }
        })
        store.publish(confirmation("first"))
        store.publish(confirmation("selected"))
        presenter.showRun(AgentRunId("selected"))
        assertEquals("selected", presenter.state.value.primary?.runId?.value)
        assertEquals(0, resolutions)
        assertEquals(2, store.active.value.size)
        presenter.respond(InteractionResponse.Confirmed(InteractionSurfaceId("selected")))
        assertEquals(1, resolutions)
    }

    @Test fun goneReviewDoesNotInventARequest() = runTest {
        val store = InMemoryInteractionSurfaceStore()
        val presenter = AndroidInteractionPresenter(store, object : InteractionResponseHandler {
            override suspend fun respond(response: InteractionResponse) = InteractionResponseResult.NoLongerPending
        })
        presenter.showRun(AgentRunId("gone"))
        assertNull(presenter.state.value.primary)
        assertTrue(store.active.value.isEmpty())
    }

    private fun confirmation(run: String) = InteractionSurface(
        id = InteractionSurfaceId(run), runId = AgentRunId(run), interactionId = null,
        inputRequestId = AgentInputRequestId("input-$run"),
        kind = InteractionSurfaceKind.CONFIRMATION, title = null, message = null,
        content = InteractionContent.Confirmation("Approve an action"), actions = emptyList(),
        presentation = InteractionPresentationPolicy(), createdAt = Instant.EPOCH, expiresAt = null,
        provenance = InteractionSurfaceProvenance(InteractionSurfaceSource.AGENT_INPUT),
    )
}
