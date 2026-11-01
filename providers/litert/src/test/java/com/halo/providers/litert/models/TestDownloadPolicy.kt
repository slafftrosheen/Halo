package com.halo.providers.litert.models

import com.halo.sdk.provider.*

/** Explicit synthetic Power authority for transfer fixtures; production defaults remain closed. */
internal object TestDownloadPolicy : RemoteDispatchPolicy {
    override val revision = 1L
    override fun acquire(request: RemoteDispatchRequest, expectedRevision: Long, recordActivity: Boolean,
        cancel: () -> Unit): RemoteDispatchLease {
        if (expectedRevision != revision) throw RemoteDispatchBlocked()
        return object : RemoteDispatchLease {
            override fun requireCurrent() = Unit
            override fun finish(outcome: RemoteDispatchOutcome, providerReportedCostUsd: String?) = Unit
        }
    }
}
