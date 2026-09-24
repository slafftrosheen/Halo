package com.halo.drivers.heycyan.arbiter

/**
 * A scoped lease on one or more HeyCyan resources.
 *
 * Implements [AutoCloseable] so callers can safely use Kotlin's `lease.use { ... }`.
 * Releasing the lease unblocks any operations queued for conflicting resources.
 */
interface HeyCyanOperationLease : AutoCloseable {
    val id: String
    val operation: String
    val resources: Set<HeyCyanResource>
    val acquiredAtMs: Long
    val isActive: Boolean

    fun release()

    override fun close() = release()
}
