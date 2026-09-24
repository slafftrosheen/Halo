package com.halo.runtime.cognition.attention

import com.halo.cognition.language.InteractionId
import java.time.Instant

@JvmInline
value class AttentionRequestId(val value: String)

enum class HaloUserMode {
    NORMAL,
    FOCUS,
    QUIET,
    SLEEP,
}

enum class ForegroundState {
    HALO_FOREGROUND,
    OTHER_APP_FOREGROUND,
    SCREEN_LOCKED,
    UNKNOWN,
}

enum class DoNotDisturbState {
    OFF,
    ACTIVE,
}

enum class DeviceMotionState {
    STATIONARY,
    MOVING,
    UNKNOWN,
}

data class AttentionContext(
    val foregroundState: ForegroundState = ForegroundState.HALO_FOREGROUND,
    val screenInteractive: Boolean = true,
    val activeInteraction: Boolean = false,
    val activeSpeechSession: Boolean = false,
    val activeAgentRun: Boolean = false,
    val mediaPlaybackActive: Boolean = false,
    val doNotDisturb: DoNotDisturbState = DoNotDisturbState.OFF,
    val deviceMotion: DeviceMotionState? = null,
    val currentTime: Instant = Instant.now(),
    val userMode: HaloUserMode = HaloUserMode.NORMAL,
)

enum class AttentionImportance {
    BACKGROUND,
    NORMAL,
    IMPORTANT,
    CRITICAL,
}

enum class AttentionUrgency {
    WHEN_CONVENIENT,
    SOON,
    IMMEDIATE,
}

sealed interface AttentionSource {
    data object DirectUserRequest : AttentionSource
    data object AgentCompletion : AttentionSource
    data object Routine : AttentionSource
    data object NotificationDigest : AttentionSource
    data object Reminder : AttentionSource
    data object DeviceEvent : AttentionSource
    data object SystemHealth : AttentionSource
}

data class UserFacingContent(
    val title: String,
    val body: String,
    val summary: String? = null,
)

sealed interface DeliveryIntent {
    data class Inform(val content: UserFacingContent) : DeliveryIntent
    data class RequestApproval(val approvalId: String, val summary: String) : DeliveryIntent
    data class RequestClarification(val clarificationId: String, val question: String) : DeliveryIntent
    data class ActionRequired(val summary: String) : DeliveryIntent
}

data class AttentionRequest(
    val id: AttentionRequestId,
    val source: AttentionSource,
    val interactionId: InteractionId? = null,
    val importance: AttentionImportance = AttentionImportance.NORMAL,
    val urgency: AttentionUrgency = AttentionUrgency.WHEN_CONVENIENT,
    val deliveryIntent: DeliveryIntent,
    val expiresAt: Instant? = null,
    val deduplicationKey: String? = null,
    val explanation: String = "",
    val createdAt: Instant = Instant.now(),
    val preferredDigestChannel: DeliveryChannel? = null,
)

sealed interface DeliveryChannel {
    data object InApp : DeliveryChannel
    data object AndroidNotification : DeliveryChannel
    data class WearableVisual(val deviceId: String) : DeliveryChannel
    data class WearableSpeech(val deviceId: String) : DeliveryChannel
    data object PhoneSpeech : DeliveryChannel
}

enum class SilentSurface {
    ACTIVITY,
    HOME_PENDING,
    NOTEBOOK_INBOX,
}

enum class AttentionDecisionReason {
    DIRECT_USER_REQUEST,
    FOREGROUND_SURFACE,
    QUIET_MODE,
    DND_ACTIVE,
    IMPORTANT_FAILURE_NOTIFIED,
    ROUTINE_SILENT_ACTIVITY,
    DEVICE_DISCONNECTED_SILENT,
    UNTRUSTED_CEILING_ENFORCED,
    EXPIRED,
    SUPPRESSED_BY_POLICY,
}

enum class AttentionReleaseCondition {
    INTERACTION_ENDED,
    HALO_FOREGROUND,
    DND_ENDED,
}

sealed interface AttentionDecision {
    data class DeliverNow(
        val channel: DeliveryChannel,
        val reason: AttentionDecisionReason,
    ) : AttentionDecision

    data class Queue(
        val until: AttentionReleaseCondition,
        val reason: AttentionDecisionReason,
    ) : AttentionDecision

    data class SurfaceSilently(
        val surface: SilentSurface,
        val reason: AttentionDecisionReason,
    ) : AttentionDecision

    data class Suppress(
        val reason: AttentionDecisionReason,
    ) : AttentionDecision
}

enum class DeliveryOutcome {
    DELIVERED,
    QUEUED,
    SURFACED_SILENTLY,
    SUPPRESSED,
    EXPIRED,
    FAILED,
}

data class DeliveryReceipt(
    val requestId: AttentionRequestId,
    val outcome: DeliveryOutcome,
    val channel: DeliveryChannel? = null,
    val deliveredAt: Instant? = null,
    val reason: String? = null,
)

data class AttentionPreferences(
    val quietByDefault: Boolean = true,
    val respectSystemDnd: Boolean = true,
    val initiativeLevel: String = "Quiet" // "Quiet", "Balanced", "Active"
)
