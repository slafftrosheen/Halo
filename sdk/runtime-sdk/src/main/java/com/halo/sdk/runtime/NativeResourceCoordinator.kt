package com.halo.sdk.runtime

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Native resources that should not be resident concurrently on constrained devices.
 * Lightweight wake-word/VAD resources intentionally do not participate here.
 */
enum class NativeResourceKind {
    HEAVY_LLM,
    HEAVY_ASR
}

/** Owner of a native resource lease.
 *
 * [releaseIfIdle] must never release an actively used resource. Returning false
 * means the resource is busy or currently changing state and the conflicting
 * acquisition must be rejected rather than racing native teardown.
 */
interface NativeResourceOwner {
    val nativeResourceKind: NativeResourceKind
    suspend fun releaseIfIdle(): Boolean
}

/**
 * Process-scoped arbitration for heavyweight native inference resources.
 *
 * The coordinator is DI-owned; it is deliberately not a singleton or service
 * locator. Owners register once, acquire before making a heavyweight native
 * model resident, and unregister when their lifecycle ends.
 */
class NativeResourceCoordinator {
    private val mutex = Mutex()
    private val owners = LinkedHashSet<NativeResourceOwner>()
    private val active = LinkedHashSet<NativeResourceOwner>()

    suspend fun register(owner: NativeResourceOwner) = mutex.withLock {
        owners += owner
    }

    suspend fun unregister(owner: NativeResourceOwner) = mutex.withLock {
        active -= owner
        owners -= owner
    }

    /**
     * Acquire [owner]'s resource. Conflicting active owners are asked to yield.
     * If any owner is busy, the new acquisition is rejected and no resource is
     * forcibly torn down.
     */
    suspend fun acquire(owner: NativeResourceOwner): Boolean = mutex.withLock {
        owners += owner
        if (active.contains(owner)) return@withLock true

        val conflicts = active.filter { it.nativeResourceKind != owner.nativeResourceKind }
        for (conflict in conflicts) {
            if (!conflict.releaseIfIdle()) return@withLock false
        }
        conflicts.forEach(active::remove)
        active += owner
        true
    }

    suspend fun release(owner: NativeResourceOwner) = mutex.withLock {
        active -= owner
    }

    suspend fun activeKinds(): Set<NativeResourceKind> = mutex.withLock {
        active.mapTo(LinkedHashSet()) { it.nativeResourceKind }
    }
}
