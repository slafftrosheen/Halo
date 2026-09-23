package com.halo.runtime.cognition.attention

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DefaultAttentionPolicyEngineTest {
    private val engine = DefaultAttentionPolicyEngine()

    @Test
    fun `direct user request delivers immediately`() = runBlocking {
        val request = AttentionRequest(
            id = AttentionRequestId("req_1"),
            source = AttentionSource.DirectUserRequest,
            deliveryIntent = DeliveryIntent.Inform(UserFacingContent("Time", "It is 8 AM"))
        )
        val context = AttentionContext()

        val decision = engine.evaluate(request, context)
        assertTrue(decision is AttentionDecision.DeliverNow)
        assertEquals(AttentionDecisionReason.DIRECT_USER_REQUEST, (decision as AttentionDecision.DeliverNow).reason)
    }

    @Test
    fun `DND active queues non-critical attention request`() = runBlocking {
        val request = AttentionRequest(
            id = AttentionRequestId("req_2"),
            source = AttentionSource.AgentCompletion,
            importance = AttentionImportance.NORMAL,
            deliveryIntent = DeliveryIntent.Inform(UserFacingContent("Done", "Task finished"))
        )
        val context = AttentionContext(
            doNotDisturb = DoNotDisturbState.ACTIVE,
            foregroundState = ForegroundState.OTHER_APP_FOREGROUND
        )

        val decision = engine.evaluate(request, context)
        assertTrue(decision is AttentionDecision.Queue)
        assertEquals(AttentionDecisionReason.DND_ACTIVE, (decision as AttentionDecision.Queue).reason)
    }

    @Test
    fun `routine completion in background surfaces silently in activity`() = runBlocking {
        val request = AttentionRequest(
            id = AttentionRequestId("req_3"),
            source = AttentionSource.Routine,
            deliveryIntent = DeliveryIntent.Inform(UserFacingContent("Routine", "Summary ready"))
        )
        val context = AttentionContext(
            foregroundState = ForegroundState.OTHER_APP_FOREGROUND
        )

        val decision = engine.evaluate(request, context)
        assertTrue(decision is AttentionDecision.SurfaceSilently)
        assertEquals(SilentSurface.ACTIVITY, (decision as AttentionDecision.SurfaceSilently).surface)
        assertEquals(AttentionDecisionReason.ROUTINE_SILENT_ACTIVITY, decision.reason)
    }

    @Test
    fun `expired attention request is suppressed`() = runBlocking {
        val now = Instant.now()
        val request = AttentionRequest(
            id = AttentionRequestId("req_4"),
            source = AttentionSource.AgentCompletion,
            deliveryIntent = DeliveryIntent.Inform(UserFacingContent("Expired", "Old message")),
            expiresAt = now.minusSeconds(10)
        )
        val context = AttentionContext(currentTime = now)

        val decision = engine.evaluate(request, context)
        assertTrue(decision is AttentionDecision.Suppress)
        assertEquals(AttentionDecisionReason.EXPIRED, (decision as AttentionDecision.Suppress).reason)
    }
}
