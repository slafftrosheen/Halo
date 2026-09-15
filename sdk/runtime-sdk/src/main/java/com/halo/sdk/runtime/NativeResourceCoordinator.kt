package com.halo.sdk.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Native resources that should not be resident concurrently on constrained devices. */
enum class NativeResourceKind {
    HEAVY_LLM,
    HEAVY_ASR
}

enum class NativeResourceState {
    RELEASED,
    ACQUIRING,
    RESIDENT
}

data class NativeResourceSnapshot(
    val ownerId: String,
    val kind: NativeResourceKind,
    val state: NativeResourceState,
    val reason: String? = null
)

/** Owner of a native resource lease. */
interface NativeResourceOwner {
    val nativeResourceKind: NativeResourceKind
    suspend fun releaseIfIdle(): Boolean
}

/**
 * Process-scoped arbitration and observability for heavyweight native inference resources.
 * Lightweight wake-word/VAD resources intentionally do not participate here.
 */
class NativeResourceCoordinator {
    private val mutex = Mutex()
    private val owners = LinkedHashSet<NativeResourceOwner>()
    private val active = LinkedHashSet<NativeResourceOwner>()
    private val _snapshot = MutableStateFlow<List<NativeResourceSnapshot>>(emptyList())

    /** Read-only, UI/diagnostic-safe view of heavyweight residency. */
    val snapshot: StateFlow<List<NativeResourceSnapshot>> = _snapshot.asStateFlow()

    suspend fun register(owner: NativeResourceOwner) = mutex.withLock {
        owners += owner
        publish(owner, NativeResourceState.RELEASED, "registered")
    }

    suspend fun unregister(owner: NativeResourceOwner) = mutex.withLock {
        active -= owner
        owners -= owner
        remove(owner)
    }

    /**
     * Acquire [owner]'s resource. Conflicting active owners are asked to yield.
     * If any owner is busy, the new acquisition is rejected and no resource is
     * forcibly torn down.
     */
    suspend fun acquire(owner: NativeResourceOwner): Boolean = mutex.withLock {
        owners += owner
        if (active.contains(owner)) {
            publish(owner, NativeResourceState.RESIDENT, "already resident")
            return@withLock true
        }

        publish(owner, NativeResourceState.ACQUIRING, "acquisition requested")
        val conflicts = active.filter { it.nativeResourceKind != owner.nativeResourceKind }
        for (conflict in conflicts) {
            if (!conflict.releaseIfIdle()) {
                publish(owner, NativeResourceState.RELEASED, "blocked by busy ${conflict.nativeResourceKind}")
                return@withLock false
            }
            publish(conflict, NativeResourceState.RELEASED, "yielded to ${owner.nativeResourceKind}")
        }
        conflicts.forEach(active::remove)
        active += owner
        publish(owner, NativeResourceState.RESIDENT, "resident")
        true
    }

    suspend fun release(owner: NativeResourceOwner) = mutex.withLock {
        active -= owner
        if (owners.contains(owner)) {
            publish(owner, NativeResourceState.RELEASED, "released")
        }
    }

    suspend fun activeKinds(): Set<NativeResourceKind> = mutex.withLock {
        active.mapTo(LinkedHashSet()) { it.nativeResourceKind }
    }

    private fun publish(owner: NativeResourceOwner, state: NativeResourceState, reason: String) {
        val id = owner::class.qualifiedName ?: owner::class.simpleName ?: "unknown"
        val next = _snapshot.value.filterNot { it.ownerId == id } +
            NativeResourceSnapshot(id, owner.nativeResourceKind, state, reason)
        _snapshot.value = next.sortedBy { it.kind.name }
    }

    private fun remove(owner: NativeResourceOwner) {
        val id = owner::class.qualifiedName ?: owner::class.simpleName ?: "unknown"
        _snapshot.value = _snapshot.value.filterNot { it.ownerId == id }
    }
}
