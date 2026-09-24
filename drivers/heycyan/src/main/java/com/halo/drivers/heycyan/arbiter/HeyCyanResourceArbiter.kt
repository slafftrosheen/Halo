package com.halo.drivers.heycyan.arbiter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe and coroutine-aware arbiter for HeyCyan wearable hardware resources.
 *
 * Prevents conflicting hardware commands (e.g. concurrent camera capture while
 * video recording is active, or BLE thumbnail sync while Wi-Fi transfer runs).
 */
class HeyCyanResourceArbiter {

    private val lock = Any()
    private val activeLeases = mutableMapOf<String, RealLease>()
    private val stateEpoch = MutableStateFlow(0L)

    private inner class RealLease(
        override val id: String,
        override val operation: String,
        override val resources: Set<HeyCyanResource>,
        override val acquiredAtMs: Long = System.currentTimeMillis(),
    ) : HeyCyanOperationLease {

        private val closed = AtomicBoolean(false)

        override val isActive: Boolean
            get() = !closed.get()

        override fun release() {
            if (closed.compareAndSet(false, true)) {
                this@HeyCyanResourceArbiter.releaseLease(id)
            }
        }
    }

    private fun releaseLease(leaseId: String) {
        synchronized(lock) {
            activeLeases.remove(leaseId)
            stateEpoch.value = stateEpoch.value + 1
        }
    }

    private fun hasConflictLocked(requested: Set<HeyCyanResource>): Boolean {
        for (active in activeLeases.values) {
            for (req in requested) {
                for (held in active.resources) {
                    if (req.conflictsWith(held)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Non-blocking attempt to acquire a lease.
     * Returns null if any requested resource has a conflict.
     */
    fun tryAcquire(
        operation: String,
        resources: Set<HeyCyanResource>,
    ): HeyCyanOperationLease? {
        synchronized(lock) {
            if (hasConflictLocked(resources)) {
                return null
            }
            val lease = RealLease(
                id = UUID.randomUUID().toString(),
                operation = operation,
                resources = resources,
            )
            activeLeases[lease.id] = lease
            stateEpoch.value = stateEpoch.value + 1
            return lease
        }
    }

    /**
     * Suspends until the requested resources are free or timeout expires.
     */
    suspend fun acquire(
        operation: String,
        resources: Set<HeyCyanResource>,
        timeoutMs: Long = 5_000L,
    ): HeyCyanOperationLease {
        return withTimeout(timeoutMs) {
            while (true) {
                val observedEpoch = stateEpoch.value
                val lease = tryAcquire(operation, resources)
                if (lease != null) return@withTimeout lease
                stateEpoch.first { it != observedEpoch }
            }
            @Suppress("UNREACHABLE_CODE")
            throw IllegalStateException("Unreachable")
        }
    }

    /**
     * Convenience method to execute a block within a lease.
     */
    suspend fun <T> withLease(
        operation: String,
        resources: Set<HeyCyanResource>,
        timeoutMs: Long = 5_000L,
        block: suspend (HeyCyanOperationLease) -> T,
    ): T {
        val lease = acquire(operation, resources, timeoutMs)
        return try {
            block(lease)
        } finally {
            lease.release()
        }
    }

    fun hasActiveLease(resource: HeyCyanResource): Boolean = synchronized(lock) {
        activeLeases.values.any { lease -> lease.resources.any { it.conflictsWith(resource) } }
    }

    fun activeLeases(): List<HeyCyanOperationLease> = synchronized(lock) {
        activeLeases.values.toList()
    }
}
