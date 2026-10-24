package com.halo.app.ai

import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ProviderMetadataPolicyTest {
    @Test fun remoteMetadataCannotBypassMissingOrDeniedModePolicy() = runBlocking {
        var called = false
        assertTrue(runCatching { providerMetadataCheck(null, "remote", false) { called = true } }.exceptionOrNull() is RemoteDispatchBlocked)
        val denied = object : RemoteDispatchPolicy {
            override val revision = 7L
            override fun acquire(request: RemoteDispatchRequest, expectedRevision: Long, recordActivity: Boolean,
                cancel: () -> Unit): RemoteDispatchLease = throw RemoteDispatchBlocked()
        }
        assertTrue(runCatching { providerMetadataCheck(denied, "remote", false) { called = true } }.exceptionOrNull() is RemoteDispatchBlocked)
        assertFalse(called)
    }
    @Test fun admittedMetadataUsesProviderCheckAndAccountMetadataWithoutGenerationIntent() = runBlocking {
        var request: RemoteDispatchRequest? = null
        var completed = false
        val policy = object : RemoteDispatchPolicy {
            override val revision = 7L
            override fun acquire(value: RemoteDispatchRequest, expectedRevision: Long, recordActivity: Boolean,
                cancel: () -> Unit): RemoteDispatchLease {
                request = value; assertEquals(7L, expectedRevision)
                return object : RemoteDispatchLease {
                    override fun requireCurrent() = Unit
                    override fun finish(outcome: RemoteDispatchOutcome, providerReportedCostUsd: String?) { completed = outcome == RemoteDispatchOutcome.COMPLETED }
                }
            }
        }
        val value = providerMetadataCheck(policy, "remote", false) {
            val intent = RemoteDispatchContext.intent.get()!!
            assertEquals(RemoteOperation.PROVIDER_CHECK, intent.request.operation)
            assertEquals(setOf(RemoteDataCategory.ACCOUNT_METADATA), intent.request.categories)
            "metadata"
        }
        assertEquals("metadata", value); assertTrue(completed)
        assertEquals(RemoteOperation.PROVIDER_CHECK, request!!.operation)
        assertNull(RemoteDispatchContext.intent.get())
    }
    @Test fun onDeviceInventoryDoesNotRequireRemoteAuthority() = runBlocking {
        assertEquals("local", providerMetadataCheck(null, "local", true) { "local" })
    }
}
