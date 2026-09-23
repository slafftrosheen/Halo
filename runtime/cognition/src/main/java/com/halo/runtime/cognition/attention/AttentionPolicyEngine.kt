package com.halo.runtime.cognition.attention

interface AttentionPolicyEngine {
    suspend fun evaluate(
        request: AttentionRequest,
        context: AttentionContext,
        preferences: AttentionPreferences = AttentionPreferences(),
    ): AttentionDecision
}

class DefaultAttentionPolicyEngine : AttentionPolicyEngine {

    override suspend fun evaluate(
        request: AttentionRequest,
        context: AttentionContext,
        preferences: AttentionPreferences,
    ): AttentionDecision {
        // Direct user requests bypass proactive interruption rules
        if (request.source is AttentionSource.DirectUserRequest) {
            return AttentionDecision.DeliverNow(
                channel = DeliveryChannel.InApp,
                reason = AttentionDecisionReason.DIRECT_USER_REQUEST
            )
        }

        // Expiry check
        if (request.expiresAt != null && context.currentTime.isAfter(request.expiresAt)) {
            return AttentionDecision.Suppress(
                reason = AttentionDecisionReason.EXPIRED
            )
        }

        // Sleep / DND check
        if (context.userMode == HaloUserMode.SLEEP ||
            (preferences.respectSystemDnd && context.doNotDisturb == DoNotDisturbState.ACTIVE && request.importance != AttentionImportance.CRITICAL)) {
            return AttentionDecision.Queue(
                until = AttentionReleaseCondition.DND_ENDED,
                reason = AttentionDecisionReason.DND_ACTIVE
            )
        }

        // Halo in foreground
        if (context.foregroundState == ForegroundState.HALO_FOREGROUND) {
            return AttentionDecision.DeliverNow(
                channel = DeliveryChannel.InApp,
                reason = AttentionDecisionReason.FOREGROUND_SURFACE
            )
        }

        // Routine completion
        if (request.source is AttentionSource.Routine) {
            return AttentionDecision.SurfaceSilently(
                surface = SilentSurface.ACTIVITY,
                reason = AttentionDecisionReason.ROUTINE_SILENT_ACTIVITY
            )
        }

        // Quiet mode default
        if (preferences.quietByDefault) {
            return AttentionDecision.SurfaceSilently(
                surface = SilentSurface.ACTIVITY,
                reason = AttentionDecisionReason.QUIET_MODE
            )
        }

        return AttentionDecision.DeliverNow(
            channel = DeliveryChannel.AndroidNotification,
            reason = AttentionDecisionReason.IMPORTANT_FAILURE_NOTIFIED
        )
    }
}
