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
        // Expiry check
        if (request.expiresAt != null && !context.currentTime.isBefore(request.expiresAt)) {
            return AttentionDecision.Suppress(
                reason = AttentionDecisionReason.EXPIRED
            )
        }

        if(request.source == AttentionSource.PersonalSuggestion) return AttentionDecision.SurfaceSilently(
            SilentSurface.NOTEBOOK_INBOX,AttentionDecisionReason.SUPPRESSED_BY_POLICY)
        val ceiling = AttentionSourcePolicy.ceiling(request.source)
        if(request.importance.ordinal > ceiling.ordinal) return evaluate(request.copy(importance = ceiling), context, preferences)
        // Direct user requests bypass proactive interruption rules
        if (request.source is AttentionSource.DirectUserRequest) {
            return AttentionDecision.DeliverNow(
                channel = DeliveryChannel.InApp,
                reason = AttentionDecisionReason.DIRECT_USER_REQUEST
            )
        }

        if(request.source == AttentionSource.Routine && request.preferredRoutineChannel == null) {
            return AttentionDecision.SurfaceSilently(SilentSurface.ACTIVITY, AttentionDecisionReason.ROUTINE_SILENT_ACTIVITY)
        }
        // Digests never speak implicitly and do not wait in a volatile queue by default.
        if (request.source is AttentionSource.NotificationDigest) {
            if (context.foregroundState == ForegroundState.HALO_FOREGROUND) {
                return AttentionDecision.DeliverNow(DeliveryChannel.InApp, AttentionDecisionReason.FOREGROUND_SURFACE)
            }
            val selected = request.preferredDigestChannel
            val quiet = context.userMode == HaloUserMode.SLEEP || context.userMode == HaloUserMode.QUIET ||
                (preferences.respectSystemDnd && context.doNotDisturb == DoNotDisturbState.ACTIVE) || context.activeInteraction || context.activeSpeechSession || context.userMode == HaloUserMode.FOCUS
            if (!quiet && (selected == DeliveryChannel.AndroidNotification || selected is DeliveryChannel.WearableSpeech)) {
                return AttentionDecision.DeliverNow(requireNotNull(selected), AttentionDecisionReason.UNTRUSTED_CEILING_ENFORCED)
            }
            return AttentionDecision.SurfaceSilently(SilentSurface.ACTIVITY, AttentionDecisionReason.ROUTINE_SILENT_ACTIVITY)
        }

        // Sleep / DND check
        if (context.userMode == HaloUserMode.SLEEP ||
            (preferences.respectSystemDnd && context.doNotDisturb == DoNotDisturbState.ACTIVE && request.importance != AttentionImportance.CRITICAL)) {
            return AttentionDecision.Queue(
                until = AttentionReleaseCondition.DND_ENDED,
                reason = AttentionDecisionReason.DND_ACTIVE
            )
        }

        if (context.activeInteraction || context.activeSpeechSession) {
            return AttentionDecision.Queue(AttentionReleaseCondition.INTERACTION_ENDED, AttentionDecisionReason.SUPPRESSED_BY_POLICY)
        }

        // Explicit speech is still gated while the screen is locked.
        if(!context.screenInteractive && request.preferredRoutineChannel is DeliveryChannel.WearableSpeech)
            return AttentionDecision.Queue(AttentionReleaseCondition.HALO_FOREGROUND, AttentionDecisionReason.SUPPRESSED_BY_POLICY)
        if(request.source == AttentionSource.Routine) {
            val selected = request.preferredRoutineChannel
            if (context.userMode in setOf(HaloUserMode.QUIET, HaloUserMode.FOCUS))
                return AttentionDecision.SurfaceSilently(SilentSurface.ACTIVITY, AttentionDecisionReason.QUIET_MODE)
            if(selected == DeliveryChannel.InApp && context.foregroundState != ForegroundState.HALO_FOREGROUND)
                return AttentionDecision.Queue(AttentionReleaseCondition.HALO_FOREGROUND, AttentionDecisionReason.SUPPRESSED_BY_POLICY)
            if(selected != null) return AttentionDecision.DeliverNow(selected, AttentionDecisionReason.ROUTINE_SILENT_ACTIVITY)
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
        if (preferences.initiativeLevel == AttentionInitiativeLevel.QUIET || preferences.quietByDefault ||
            request.importance.ordinal < AttentionImportance.IMPORTANT.ordinal || context.userMode in setOf(HaloUserMode.QUIET, HaloUserMode.FOCUS)) {
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
