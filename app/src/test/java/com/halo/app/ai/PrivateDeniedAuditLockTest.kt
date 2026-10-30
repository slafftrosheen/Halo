package com.halo.app.ai

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PrivateDeniedAuditLockTest {
    @Test fun deniedAuditProviderLookupDoesNotHoldThePrivacyAuthorityLock() {
        val store = PrivacyModeStore(Memory())
        val initializer = Executors.newSingleThreadExecutor { task -> Thread(task, "provider-initializer-fixture").apply { isDaemon = true } }
        var auditHeldLock = false
        var lookupCount = 0
        store.configuredProviders = {
            lookupCount++
            // Real provider initialization reads the current processing profile while it owns its registry lock.
            val profileRead = initializer.submit<ModeProfile> { store.profile(PrivacyMode.PRIVATE) }
            try { profileRead.get(1, TimeUnit.SECONDS) }
            catch (_: TimeoutException) { auditHeldLock = true }
            setOf("openrouter")
        }
        try {
            val request = RemoteDispatchRequest("openrouter", RemoteOperation.INFERENCE,
                setOf(RemoteDataCategory.TEXT, RemoteDataCategory.WORKSPACE))
            try { store.acquire(request, store.revision) {}; fail("Private must deny") }
            catch (_: RemoteDispatchBlocked) { }
            assertFalse("Denied audit enrichment must not hold the privacy lock needed by provider initialization", auditHeldLock)
            assertEquals(1, lookupCount)
            val receipt = store.activity.receipts.value.single()
            assertEquals("openrouter", receipt.providerId)
            assertEquals(RemoteDispatchOutcome.BLOCKED, receipt.outcome)
            assertEquals(PrivacyMode.PRIVATE, store.mode.value)
        } finally { initializer.shutdownNow(); assertTrue(initializer.awaitTermination(2, TimeUnit.SECONDS)) }
    }

    private class Memory : Storage {
        private val data = java.util.concurrent.ConcurrentHashMap<String, Any>()
        override fun getString(key: String, defaultValue: String?) = data[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) data.remove(key) else data[key] = value }
        override fun getInt(key: String, defaultValue: Int) = data[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = data[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun remove(key: String) { data.remove(key) }
        override fun clear() { data.clear() }
    }
}
