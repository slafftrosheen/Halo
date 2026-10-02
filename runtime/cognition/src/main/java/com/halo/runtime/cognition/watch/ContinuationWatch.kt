package com.halo.runtime.cognition.watch

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentRunId
import java.time.Instant

@JvmInline
value class ContinuationWatchId(val value: String)

enum class WatchStatus {
    ACTIVE,
    MATCH_CLAIMED,
    SATISFIED,
    EXPIRED,
    CANCELLED,
    FAILED
}

enum class ContinuationWakePolicy {
    SURFACE_TO_USER,
    RECONCILE_ONLY
}

enum class WatchMatchMode {
    NEXT_TRANSITION,
    CURRENT_OR_NEXT
}

sealed interface WatchBehavior {
    data class Notify(
        val message: String
    ) : WatchBehavior

    data class MakeRunResumable(
        val runId: AgentRunId,
        val wakePolicy: ContinuationWakePolicy = ContinuationWakePolicy.SURFACE_TO_USER
    ) : WatchBehavior

    data class ReconcileRun(
        val runId: AgentRunId
    ) : WatchBehavior
}

sealed interface WatchCondition {
    data object NetworkAvailable : WatchCondition
    data object NetworkLost : WatchCondition

    data class BatteryBelow(val percent: Int) : WatchCondition
    data class BatteryAbove(val percent: Int) : WatchCondition

    data class DeviceConnected(val deviceId: String) : WatchCondition
    data class DeviceDisconnected(val deviceId: String) : WatchCondition

    data class AppForegrounded(val packageName: String) : WatchCondition

    data class NotificationFromApp(val packageName: String) : WatchCondition

    data class ProviderAvailable(val providerId: String) : WatchCondition

    data class CapabilityAvailable(val capabilityId: String) : WatchCondition
}

data class ContinuationWatch(
    val id: ContinuationWatchId,
    val runId: AgentRunId?,
    val interactionId: InteractionId?,
    val condition: WatchCondition,
    val behavior: WatchBehavior,
    val status: WatchStatus,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val deduplicationKey: String?,
    val matchMode: WatchMatchMode = WatchMatchMode.NEXT_TRANSITION,
    val matchedAt: Instant? = null,
    val presentationText: String? = null
)
