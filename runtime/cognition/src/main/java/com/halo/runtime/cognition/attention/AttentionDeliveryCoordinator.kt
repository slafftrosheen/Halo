package com.halo.runtime.cognition.attention

import java.time.Instant

interface AttentionDeliveryCoordinator {
    suspend fun deliver(
        request: AttentionRequest,
        decision: AttentionDecision,
    ): DeliveryReceipt
}

class DefaultAttentionDeliveryCoordinator : AttentionDeliveryCoordinator {
    override suspend fun deliver(request: AttentionRequest, decision: AttentionDecision): DeliveryReceipt {
        // Basic stub implementation until fully wired to NotificationManager, Activity/UI, and TTS
        val outcome = when (decision) {
            is AttentionDecision.DeliverNow -> DeliveryOutcome.DELIVERED
            is AttentionDecision.Queue -> DeliveryOutcome.QUEUED
            is AttentionDecision.SurfaceSilently -> DeliveryOutcome.SURFACED_SILENTLY
            is AttentionDecision.Suppress -> if (decision.reason == AttentionDecisionReason.EXPIRED) DeliveryOutcome.EXPIRED else DeliveryOutcome.SUPPRESSED
        }
        val channel = if (decision is AttentionDecision.DeliverNow) decision.channel else null

        return DeliveryReceipt(
            requestId = request.id,
            outcome = outcome,
            channel = channel,
            deliveredAt = if (outcome == DeliveryOutcome.DELIVERED) Instant.now() else null
        )
    }
}
