package com.halo.runtime.kernel.resource

import com.halo.sdk.runtime.resource.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class DefaultResourceLeaseManager : ResourceLeaseManager {

    private val lock = Mutex()
    private val activeLeasesMap = mutableMapOf<ResourceLeaseId, ActiveLease>()
    private val waitingRequestsMap = mutableMapOf<String, ResourceWaitRecord>()
    private val stateEpoch = MutableStateFlow(0L)

    private val _snapshotState = MutableStateFlow(ResourceLeaseSnapshot(emptyList(), emptyList()))
    override val snapshotState: StateFlow<ResourceLeaseSnapshot> = _snapshotState.asStateFlow()

    private inner class ActiveLease(
        override val leaseId: ResourceLeaseId,
        override val request: ResourceLeaseRequest,
        override val acquiredAtMs: Long = System.currentTimeMillis(),
    ) : ResourceLease {

        private val closed = AtomicBoolean(false)

        override val isReleased: Boolean
            get() = closed.get()

        override suspend fun release() {
            if (closed.compareAndSet(false, true)) {
                this@DefaultResourceLeaseManager.releaseLease(leaseId)
            }
        }
    }

    override fun snapshot(): ResourceLeaseSnapshot {
        return _snapshotState.value
    }

    private fun updateSnapshotLocked() {
        val active = activeLeasesMap.values.map {
            ResourceLeaseRecord(
                leaseId = it.leaseId,
                holder = it.request.holder,
                claims = it.request.claims,
                priority = it.request.priority,
                acquiredAtMs = it.acquiredAtMs,
            )
        }
        val waiting = waitingRequestsMap.values.toList()
        _snapshotState.value = ResourceLeaseSnapshot(active, waiting)
        stateEpoch.value = stateEpoch.value + 1
    }

    private fun findConflictsLocked(request: ResourceLeaseRequest): List<ResourceConflict> {
        val conflicts = mutableListOf<ResourceConflict>()

        for (active in activeLeasesMap.values) {
            // Same holder does not conflict with itself
            if (isSameHolder(active.request.holder, request.holder)) continue

            for (requestedClaim in request.claims) {
                for (heldClaim in active.request.claims) {
                    if (claimsConflict(requestedClaim, heldClaim)) {
                        conflicts.add(
                            ResourceConflict(
                                requested = requestedClaim,
                                heldBy = active.request.holder,
                                heldLeaseId = active.leaseId,
                                heldSinceMs = active.acquiredAtMs,
                                priority = active.request.priority,
                            )
                        )
                    }
                }
            }
        }
        return conflicts
    }

    private fun claimsConflict(a: ResourceClaim, b: ResourceClaim): Boolean {
        if (a.key != b.key) return false
        return a.access == ResourceAccess.EXCLUSIVE || b.access == ResourceAccess.EXCLUSIVE
    }

    private fun isSameHolder(a: ResourceHolder, b: ResourceHolder): Boolean {
        if (a.id == b.id) return true
        if (a.runId != null && a.runId == b.runId) return true
        if (a.toolInvocationId != null && a.toolInvocationId == b.toolInvocationId) return true
        if (a.interactionId != null && a.interactionId == b.interactionId) return true
        return false
    }

    override fun tryAcquire(
        request: ResourceLeaseRequest,
        yieldHandler: ResourceYieldHandler?,
    ): ResourceAcquireResult {
        val (conflicts, lease) = synchronized(this) {
            val conflicts = findConflictsLocked(request)
            if (conflicts.isNotEmpty()) {
                return@synchronized Pair(conflicts, null)
            }
            val lease = ActiveLease(
                leaseId = ResourceLeaseId(UUID.randomUUID().toString()),
                request = request,
            )
            activeLeasesMap[lease.leaseId] = lease
            updateSnapshotLocked()
            Pair(emptyList(), lease)
        }

        if (lease != null) {
            return ResourceAcquireResult.Granted(lease)
        }
        return ResourceAcquireResult.Busy(conflicts)
    }

    override suspend fun acquire(
        request: ResourceLeaseRequest,
        yieldHandler: ResourceYieldHandler?,
    ): ResourceAcquireResult {
        val tryResult = tryAcquire(request, yieldHandler)
        if (tryResult is ResourceAcquireResult.Granted) {
            return tryResult
        }

        // Handle cooperative yield if configured
        val initialConflicts = (tryResult as ResourceAcquireResult.Busy).conflicts
        if (request.preemption == ResourcePreemptionPolicy.COOPERATIVE && yieldHandler != null) {
            val preemptable = initialConflicts.filter { it.priority < request.priority }
            for (conflict in preemptable) {
                val yieldReq = ResourceYieldRequest(
                    requestedClaim = conflict.requested,
                    requestingHolder = request.holder,
                    requestingPriority = request.priority,
                    heldLeaseId = conflict.heldLeaseId,
                    heldClaim = conflict.requested,
                )
                // UNLOCKED call to yieldHandler
                val decision = yieldHandler.requestYield(yieldReq)
                if (decision == ResourceYieldDecision.RELEASING) {
                    lock.withLock {
                        val epoch = stateEpoch.value
                        withTimeoutOrNull(250L) {
                            stateEpoch.first { it != epoch }
                        }
                    }
                }
            }
            // Retry after yield attempt
            val retryResult = tryAcquire(request, yieldHandler)
            if (retryResult is ResourceAcquireResult.Granted) {
                return retryResult
            }
        }

        val timeoutMs = when (val policy = request.waitPolicy) {
            is ResourceWaitPolicy.FailFast -> return tryResult
            is ResourceWaitPolicy.Wait -> policy.timeoutMs
            is ResourceWaitPolicy.SuspendRun -> 5_000L
        }

        val waitRecord = ResourceWaitRecord(
            requestId = request.requestId,
            holder = request.holder,
            claims = request.claims,
            priority = request.priority,
            requestedAtMs = System.currentTimeMillis(),
        )

        lock.withLock {
            waitingRequestsMap[request.requestId] = waitRecord
            updateSnapshotLocked()
        }

        return try {
            val granted = withTimeoutOrNull(timeoutMs) {
                while (true) {
                    val observedEpoch = stateEpoch.value
                    val acquireTry = tryAcquire(request, yieldHandler)
                    if (acquireTry is ResourceAcquireResult.Granted) {
                        return@withTimeoutOrNull acquireTry
                    }
                    stateEpoch.first { it != observedEpoch }
                }
                @Suppress("UNREACHABLE_CODE")
                null
            }
            granted ?: ResourceAcquireResult.TimedOut(findConflictsLocked(request))
        } catch (e: CancellationException) {
            throw e
        } finally {
            lock.withLock {
                waitingRequestsMap.remove(request.requestId)
                updateSnapshotLocked()
            }
        }
    }

    override suspend fun release(leaseId: ResourceLeaseId) {
        releaseLease(leaseId)
    }

    private fun releaseLease(leaseId: ResourceLeaseId) {
        synchronized(this) {
            val lease = activeLeasesMap.remove(leaseId)
            if (lease != null) {
                updateSnapshotLocked()
            }
        }
    }
}
