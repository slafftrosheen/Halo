package com.halo.runtime.cognition.agent

import com.halo.runtime.cognition.tools.ConfirmationPolicy
import com.halo.runtime.cognition.tools.ToolDescriptor
import com.halo.runtime.cognition.tools.ToolRiskClass

/**
 * MINING-P1-001 short-lived exact approval grants.
 *
 * Halo's durable approval mechanism (`AgentInputRequest.Approval` + `InputResolved` in the journal)
 * remains authoritative and is not replaced. This is a bounded *convenience* layer for repeated
 * IDENTICAL requests, and it is intentionally narrow:
 *
 * - identity includes the scope, the tool id, the canonical arguments digest AND the security mode,
 *   so approving `set_volume(level=40)` can never approve `set_volume(level=100)`;
 * - denials are never cached;
 * - `NEVER_REPLAY`, `DESTRUCTIVE`, `SENSITIVE` and `ConfirmationPolicy.ALWAYS` are never reusable —
 *   Halo policy decides reuse, not this store.
 */
data class ToolApprovalGrantKey(
    val scopeId: String,
    val toolId: String,
    val argumentsDigest: String,
    val securityMode: String,
)

/**
 * Which descriptors may ever reuse an approval. Conservative by construction: the store cannot
 * widen policy, it can only skip a re-prompt for an action Halo has already classified as
 * repeatable.
 */
object ToolApprovalReusePolicy {
    fun allowsReuse(descriptor: ToolDescriptor): Boolean {
        val security = descriptor.security
        if (security.confirmation == ConfirmationPolicy.ALWAYS) return false
        return when (security.risk) {
            ToolRiskClass.SENSITIVE, ToolRiskClass.DESTRUCTIVE -> false
            else -> security.replaySafety != ReplaySafety.NEVER_REPLAY
        }
    }

    /** Stable identity of the security decision, so a policy change invalidates the grant. */
    fun securityModeOf(descriptor: ToolDescriptor): String =
        "${descriptor.security.risk.name}:${descriptor.security.confirmation.name}:" +
            descriptor.security.replaySafety.name
}

class EphemeralToolApprovalGrantStore(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Grant(val expiresAtMs: Long)

    private val grants = LinkedHashMap<ToolApprovalGrantKey, Grant>()

    @Synchronized
    fun grant(key: ToolApprovalGrantKey) {
        evictExpired()
        while (grants.size >= maxEntries) {
            val oldest = grants.keys.firstOrNull() ?: break
            grants.remove(oldest)
        }
        grants[key] = Grant(clock() + ttlMs)
    }

    @Synchronized
    fun isGranted(key: ToolApprovalGrantKey): Boolean {
        evictExpired()
        val grant = grants[key] ?: return false
        return grant.expiresAtMs > clock()
    }

    /** A denial must never be remembered; this exists only so callers cannot forget that. */
    fun recordDenial(key: ToolApprovalGrantKey) {
        revoke(key)
    }

    @Synchronized
    fun revoke(key: ToolApprovalGrantKey) {
        grants.remove(key)
    }

    @Synchronized
    fun clearScope(scopeId: String) {
        grants.keys.removeAll { it.scopeId == scopeId }
    }

    @Synchronized
    fun clearAll() = grants.clear()

    @Synchronized
    fun size(): Int {
        evictExpired()
        return grants.size
    }

    private fun evictExpired() {
        val now = clock()
        grants.entries.removeAll { it.value.expiresAtMs <= now }
    }

    companion object {
        /** Short on purpose: a grant is a convenience inside one burst of work. */
        const val DEFAULT_TTL_MS = 120_000L
        const val DEFAULT_MAX_ENTRIES = 64
    }
}
