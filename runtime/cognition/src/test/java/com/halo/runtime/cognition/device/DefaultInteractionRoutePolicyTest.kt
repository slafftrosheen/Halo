package com.halo.runtime.cognition.device

import com.halo.cognition.language.InteractionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DefaultInteractionRoutePolicyTest {

    private val policy = DefaultInteractionRoutePolicy()

    @Test
    fun `evaluateHandoff stays when origin device is connected`() {
        val continuity = InteractionContinuity(
            interactionId = InteractionId("txn_1"),
            originDeviceId = "heycyan_123",
            currentInputRoute = "AudioInput",
            currentOutputRoute = "AudioOutput",
            startedAt = Instant.now()
        )
        val devices = listOf(RuntimeDevice("heycyan_123", "wearable", "Glasses", connected = true))

        val decision = policy.evaluateHandoff(continuity, devices)
        assertTrue(decision is HandoffDecision.Stay)
    }

    @Test
    fun `evaluateHandoff requires handoff when origin device disconnects`() {
        val continuity = InteractionContinuity(
            interactionId = InteractionId("txn_1"),
            originDeviceId = "heycyan_123",
            currentInputRoute = "AudioInput",
            currentOutputRoute = "AudioOutput",
            startedAt = Instant.now()
        )
        // heycyan is missing or disconnected
        val devices = listOf(RuntimeDevice("heycyan_123", "wearable", "Glasses", connected = false))

        val decision = policy.evaluateHandoff(continuity, devices)
        assertTrue(decision is HandoffDecision.Required)
        assertEquals("local_phone", (decision as HandoffDecision.Required).targetDeviceId)
        assertEquals(HandoffReason.ORIGIN_DISCONNECTED, decision.reason)
    }
}
