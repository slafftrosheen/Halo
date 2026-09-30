package com.halo.runtime.cognition.attention

import java.time.Duration
import java.time.Instant

enum class AttentionInitiativeLevel { QUIET, BALANCED, ACTIVE;
    companion object { fun fromStored(raw: String?) = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: QUIET }
}
data class AttentionBudget(val maxInterruptionsPerHour: Int = 6, val minimumInterval: Duration = Duration.ofMinutes(2),
    val perDedupKeyCooldown: Duration = Duration.ofMinutes(15)) {
    companion object {
        val QUIET = AttentionBudget(3, Duration.ofMinutes(5))
        val BALANCED = AttentionBudget()
        val ACTIVE = AttentionBudget(12, Duration.ofMinutes(1))
    }
}
object AttentionSourcePolicy {
    fun ceiling(source: AttentionSource) = when(source) {
        AttentionSource.DirectUserRequest, AttentionSource.SystemHealth -> AttentionImportance.CRITICAL
        AttentionSource.NotificationDigest, AttentionSource.Routine, AttentionSource.DeviceEvent -> AttentionImportance.NORMAL
        else -> AttentionImportance.IMPORTANT
    }
}
/** Admission consults durable dispatch receipts. Silent surfaces do not consume disruption budget. */
class BudgetedAttentionPolicyEngine(private val delegate: AttentionPolicyEngine,
    private val recentDispatches: suspend () -> List<Pair<Instant, String?>>, private val budgetOverride: AttentionBudget? = null) : AttentionPolicyEngine {
    override suspend fun evaluate(request: AttentionRequest, context: AttentionContext, preferences: AttentionPreferences): AttentionDecision {
        val ceiling = AttentionSourcePolicy.ceiling(request.source)
        val safe = if(request.importance.ordinal > ceiling.ordinal) request.copy(importance = ceiling) else request
        val decision = delegate.evaluate(safe, context, preferences)
        if (request.source == AttentionSource.DirectUserRequest || decision !is AttentionDecision.DeliverNow || decision.channel == DeliveryChannel.InApp) return decision
        val budget = budgetOverride ?: when(preferences.initiativeLevel) {
            AttentionInitiativeLevel.QUIET -> AttentionBudget.QUIET
            AttentionInitiativeLevel.BALANCED -> AttentionBudget.BALANCED
            AttentionInitiativeLevel.ACTIVE -> AttentionBudget.ACTIVE
        }
        val recent = recentDispatches().filter { it.first.isAfter(context.currentTime.minusSeconds(3600)) }
        val blocked = recent.size >= budget.maxInterruptionsPerHour || recent.any { it.first.plus(budget.minimumInterval).isAfter(context.currentTime) } ||
            (request.deduplicationKey != null && recent.any { it.second == request.deduplicationKey && it.first.plus(budget.perDedupKeyCooldown).isAfter(context.currentTime) })
        return if(blocked) AttentionDecision.SurfaceSilently(SilentSurface.ACTIVITY, AttentionDecisionReason.SUPPRESSED_BY_POLICY) else decision
    }
}
interface AttentionReceiptStore {
    suspend fun terminal(id: AttentionRequestId): DeliveryReceipt?
    suspend fun claim(request: AttentionRequest, channel: DeliveryChannel?): Boolean
    suspend fun record(request: AttentionRequest, receipt: DeliveryReceipt)
    suspend fun history(): List<DeliveryReceipt>
}

class AuditedAttentionPolicyEngine(private val delegate: AttentionPolicyEngine,
    private val audit: (AttentionRequest, AttentionPreferences, AttentionDecision) -> Unit) : AttentionPolicyEngine {
    override suspend fun evaluate(request: AttentionRequest, context: AttentionContext, preferences: AttentionPreferences): AttentionDecision =
        delegate.evaluate(request, context, preferences).also { audit(request, preferences, it) }
}
