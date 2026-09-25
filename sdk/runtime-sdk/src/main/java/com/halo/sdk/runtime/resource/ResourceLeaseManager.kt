package com.halo.sdk.runtime.resource

import kotlinx.coroutines.flow.StateFlow

interface ResourceLease {
    val leaseId: ResourceLeaseId
    val request: ResourceLeaseRequest
    val acquiredAtMs: Long
    val isReleased: Boolean
    suspend fun release()
}

interface ResourceLeaseManager {

    val snapshotState: StateFlow<ResourceLeaseSnapshot>

    fun tryAcquire(
        request: ResourceLeaseRequest,
        yieldHandler: ResourceYieldHandler? = null,
    ): ResourceAcquireResult

    suspend fun acquire(
        request: ResourceLeaseRequest,
        yieldHandler: ResourceYieldHandler? = null,
    ): ResourceAcquireResult

    fun snapshot(): ResourceLeaseSnapshot

    suspend fun release(
        leaseId: ResourceLeaseId
    )
}

suspend fun <T> ResourceLeaseManager.withLease(
    request: ResourceLeaseRequest,
    yieldHandler: ResourceYieldHandler? = null,
    block: suspend (ResourceLease) -> T,
): ResourceExecutionResult<T> {
    return when (val acquireResult = acquire(request, yieldHandler)) {
        is ResourceAcquireResult.Granted -> {
            val lease = acquireResult.lease
            try {
                val value = block(lease)
                ResourceExecutionResult.Success(value, lease)
            } catch (t: Throwable) {
                ResourceExecutionResult.Error(t)
            } finally {
                lease.release()
            }
        }
        else -> ResourceExecutionResult.FailedToAcquire(acquireResult)
    }
}
