package com.halo.sdk.runtime

import com.halo.sdk.runtime.resource.*
import kotlinx.coroutines.*
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

enum class NativeNextUseHint {
    EXPECT_ASR,
    EXPECT_LLM,
    NONE
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

enum class NativeMemoryPressure { NORMAL, LOW, CRITICAL }
data class NativeResourceBudget(
    val totalBytes: Long = 0,
    val availableBytes: Long = 0,
    val pressure: NativeMemoryPressure = NativeMemoryPressure.NORMAL,
    val idleGraceMillis: Long = 30_000,
    val allowConcurrentHeavyweights: Boolean = true
)

/** Process-scoped arbitration and observability for heavyweight native resources. */
class NativeResourceCoordinator(
    private val resourceLeaseManager: ResourceLeaseManager? = null,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val expiry = mutableMapOf<NativeResourceOwner, Job>()
    private val activeLeases = mutableMapOf<NativeResourceOwner, ResourceLease>()

    private val _budget = MutableStateFlow(NativeResourceBudget())
    val budget: StateFlow<NativeResourceBudget> = _budget.asStateFlow()
    private val _nextUseHint = MutableStateFlow(NativeNextUseHint.NONE)
    val nextUseHint: StateFlow<NativeNextUseHint> = _nextUseHint.asStateFlow()

    suspend fun hint(next: NativeNextUseHint) = mutex.withLock {
        _nextUseHint.value = next
        val idleKind = when (next) {
            NativeNextUseHint.EXPECT_ASR -> NativeResourceKind.HEAVY_LLM
            NativeNextUseHint.EXPECT_LLM -> NativeResourceKind.HEAVY_ASR
            NativeNextUseHint.NONE -> null
        }
        if (idleKind != null && !_budget.value.allowConcurrentHeavyweights) {
            active.filter { it.nativeResourceKind == idleKind && it.releaseIfIdle() }
                .forEach { owner ->
                    active -= owner
                    expiry.remove(owner)?.cancel()
                    activeLeases.remove(owner)?.release()
                    publish(owner, NativeResourceState.RELEASED, "next use hint=$next")
                }
        }
    }

    suspend fun updateBudget(value: NativeResourceBudget) {
        _budget.value = value
        if (value.pressure != NativeMemoryPressure.NORMAL) releaseIdle("memory_pressure")
    }

    suspend fun releaseIdle(reason: String) = mutex.withLock {
        val released = active.filter { it.releaseIfIdle() }
        released.forEach { owner ->
            active -= owner
            expiry.remove(owner)?.cancel()
            activeLeases.remove(owner)?.release()
            publish(owner, NativeResourceState.RELEASED, reason)
        }
    }

    private val owners = LinkedHashSet<NativeResourceOwner>()
    private val active = LinkedHashSet<NativeResourceOwner>()
    private val _snapshot = MutableStateFlow<List<NativeResourceSnapshot>>(emptyList())
    val snapshot: StateFlow<List<NativeResourceSnapshot>> = _snapshot.asStateFlow()

    suspend fun register(owner: NativeResourceOwner) = mutex.withLock {
        owners += owner
        publish(owner, NativeResourceState.RELEASED, "registered")
    }

    suspend fun unregister(owner: NativeResourceOwner) = mutex.withLock {
        expiry.remove(owner)?.cancel()
        active -= owner
        owners -= owner
        activeLeases.remove(owner)?.release()
        remove(owner)
    }

    suspend fun acquire(owner: NativeResourceOwner): Boolean {
        val resourceId = when (owner.nativeResourceKind) {
            NativeResourceKind.HEAVY_LLM -> HaloResourceIds.ComputeInference
            NativeResourceKind.HEAVY_ASR -> HaloResourceIds.ComputeAsr
        }
        val claims = mutableSetOf(
            ResourceClaim(ResourceKey(resourceId, ResourceEndpointId("host:primary")), ResourceAccess.EXCLUSIVE)
        )
        if (_budget.value.pressure != NativeMemoryPressure.NORMAL || !_budget.value.allowConcurrentHeavyweights) {
            claims.add(ResourceClaim(ResourceKey(HaloResourceIds.ComputeHeavy, ResourceEndpointId("host:primary")), ResourceAccess.EXCLUSIVE))
        }

        if (resourceLeaseManager != null) {
            val req = ResourceLeaseRequest(
                requestId = "native:${owner.nativeResourceId}",
                holder = ResourceHolder(id = owner.nativeResourceId, kind = ResourceHolderKind.PROVIDER),
                claims = claims,
                priority = ResourcePriority.INTERACTIVE,
                waitPolicy = ResourceWaitPolicy.Wait(500L),
                preemption = ResourcePreemptionPolicy.COOPERATIVE,
            )
            when (val acq = resourceLeaseManager.acquire(req)) {
                is ResourceAcquireResult.Granted -> {
                    activeLeases[owner] = acq.lease
                }
                else -> {
                    mutex.withLock {
                        publish(owner, NativeResourceState.RELEASED, "generic resource busy: $acq")
                    }
                    return false
                }
            }
        }

        val acquired = withTimeoutOrNull(500) {
            while (true) {
                val success = mutex.withLock {
                    owners += owner
                    expiry.remove(owner)?.cancel()
                    val conflicts = active.filter { it !== owner &&
                        (it.nativeResourceKind == owner.nativeResourceKind || !_budget.value.allowConcurrentHeavyweights ||
                            _budget.value.pressure != NativeMemoryPressure.NORMAL) }
                    var blocked = false
                    for (conflict in conflicts) {
                        if (conflict.releaseIfIdle()) {
                            active -= conflict
                            expiry.remove(conflict)?.cancel()
                            activeLeases.remove(conflict)?.release()
                            publish(conflict, NativeResourceState.RELEASED, "yielded to ${owner.nativeResourceKind}")
                        } else blocked = true
                    }
                    if (blocked) {
                        publish(owner, NativeResourceState.ACQUIRING, "busy")
                        false
                    } else {
                        active += owner
                        publish(owner, NativeResourceState.RESIDENT, "lease acquired")
                        true
                    }
                }
                if (success) return@withTimeoutOrNull true
                delay(10)
            }
            @Suppress("UNREACHABLE_CODE") false
        } ?: false

        if (!acquired) {
            mutex.withLock {
                activeLeases.remove(owner)?.release()
                publish(owner, NativeResourceState.RELEASED, "busy")
            }
        }
        return acquired
    }

    /** Update provider lifecycle state without changing the arbitration lease. */
    suspend fun mark(owner: NativeResourceOwner, state: NativeResourceState, reason: String) = mutex.withLock {
        owners += owner
        publish(owner, state, reason)
        expiry.remove(owner)?.cancel()
        if (state == NativeResourceState.RESIDENT) expiry[owner] = scope.launch {
            delay(if (_budget.value.pressure == NativeMemoryPressure.NORMAL) _budget.value.idleGraceMillis else 0)
            while (isActive) {
                val released = mutex.withLock {
                    if (owner !in active) true else if (owner.releaseIfIdle()) {
                        active -= owner
                        expiry.remove(owner)?.cancel()
                        activeLeases.remove(owner)?.release()
                        publish(owner, NativeResourceState.RELEASED, "idle grace elapsed")
                        true
                    } else false
                }
                if (released) break
                delay(500)
            }
        }
    }

    suspend fun release(owner: NativeResourceOwner) = mutex.withLock {
        expiry.remove(owner)?.cancel()
        active -= owner
        activeLeases.remove(owner)?.release()
        if (owners.contains(owner)) publish(owner, NativeResourceState.RELEASED, "released")
    }

    suspend fun activeKinds(): Set<NativeResourceKind> = mutex.withLock {
        active.mapTo(LinkedHashSet()) { it.nativeResourceKind }
    }

    private fun publish(owner: NativeResourceOwner, state: NativeResourceState, reason: String) {
        val message = "NATIVE_RESOURCE_TRANSITION owner=${owner.nativeResourceId} kind=${owner.nativeResourceKind.name} state=${state.name} reason=$reason"
        println(message)
        val next = _snapshot.value.filterNot { it.ownerId == owner.nativeResourceId } +
            NativeResourceSnapshot(owner.nativeResourceId, owner.nativeResourceKind, state, reason)
        _snapshot.value = next.sortedBy { it.ownerId }
    }

    private fun remove(owner: NativeResourceOwner) {
        _snapshot.value = _snapshot.value.filterNot { it.ownerId == owner.nativeResourceId }
    }
}
