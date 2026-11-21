package com.halo.app.privacy

import com.halo.sdk.runtime.resource.*

/** Host admission sits in front of the existing resource manager. Its lease semantics remain unchanged. */
internal class PauseResourceLeaseManager(
    private val delegate: ResourceLeaseManager,
    private val allowed: () -> Boolean,
) : ResourceLeaseManager by delegate {
    override fun tryAcquire(request: ResourceLeaseRequest, ownerYieldHandler: ResourceYieldHandler?): ResourceAcquireResult {
        if (!allowed()) return unavailable()
        return recheck(delegate.tryAcquire(request, ownerYieldHandler))
    }

    override suspend fun acquire(request: ResourceLeaseRequest, ownerYieldHandler: ResourceYieldHandler?): ResourceAcquireResult {
        if (!allowed()) return unavailable()
        return recheck(delegate.acquire(request, ownerYieldHandler))
    }

    private fun recheck(result: ResourceAcquireResult): ResourceAcquireResult {
        if (result is ResourceAcquireResult.Granted && !allowed()) {
            result.lease.release()
            return unavailable()
        }
        return result
    }

    private fun unavailable() = ResourceAcquireResult.Unavailable("Halo is paused")
}
