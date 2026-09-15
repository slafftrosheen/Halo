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
    LOADING,
    RESIDENT,
    IN_USE
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
    val nativeResourceId: String
        get() = nativeResourceKind.name + ":" + (this::class.qualifiedName ?: this::class.simpleName ?: "unknown")
    suspend fun releaseIfIdle(): Boolean
}

/** Process-scoped arbitration and observability for heavyweight native resources. */
class NativeResourceCoordinator {
    private val mutex = Mutex()
    private val owners = LinkedHashSet<NativeResourceOwner>()
    private val active = LinkedHashSet<NativeResourceOwner>()
    private val _snapshot = MutableStateFlow<List<NativeResourceSnapshot>>(emptyList())
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

    suspend fun acquire(owner: NativeResourceOwner): Boolean = mutex.withLock {
        owners += owner
        if (active.contains(owner)) {
            publish(owner, NativeResourceState.RESIDENT, "already resident")
            return@withLock true
        }
        publish(owner, NativeResourceState.ACQUIRING, "acquisition requested")

        // A resource kind is exclusive, while different heavyweight kinds may coexist.
        // This guarantees at most one LLM and one ASR owner at a time.
        val conflicts = active.filter { it.nativeResourceKind == owner.nativeResourceKind }
        for (conflict in conflicts) {
            if (!conflict.releaseIfIdle()) {
                publish(
                    owner,
                    NativeResourceState.RELEASED,
                    "blocked by busy ${conflict.nativeResourceKind}"
                )
                return@withLock false
            }
            publish(conflict, NativeResourceState.RELEASED, "yielded to ${owner.nativeResourceKind}")
        }
        conflicts.forEach(active::remove)
        active += owner
        publish(owner, NativeResourceState.RESIDENT, "resident")
        true
    }

    /** Update provider lifecycle state without changing the arbitration lease. */
    suspend fun mark(owner: NativeResourceOwner, state: NativeResourceState, reason: String) = mutex.withLock {
        owners += owner
        publish(owner, state, reason)
    }

    suspend fun release(owner: NativeResourceOwner) = mutex.withLock {
        active -= owner
        if (owners.contains(owner)) publish(owner, NativeResourceState.RELEASED, "released")
    }

    suspend fun activeKinds(): Set<NativeResourceKind> = mutex.withLock {
        active.mapTo(LinkedHashSet()) { it.nativeResourceKind }
    }

    private fun publish(owner: NativeResourceOwner, state: NativeResourceState, reason: String) {
        val next = _snapshot.value.filterNot { it.ownerId == owner.nativeResourceId } +
            NativeResourceSnapshot(owner.nativeResourceId, owner.nativeResourceKind, state, reason)
        _snapshot.value = next.sortedBy { it.ownerId }
    }

    private fun remove(owner: NativeResourceOwner) {
        _snapshot.value = _snapshot.value.filterNot { it.ownerId == owner.nativeResourceId }
    }
}
