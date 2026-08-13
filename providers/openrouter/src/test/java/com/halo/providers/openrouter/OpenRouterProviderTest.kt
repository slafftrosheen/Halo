package com.halo.providers.openrouter

import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OpenRouterProviderTest {

    private class FakeStorage : Storage {
        private val data = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = data[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { data[key] = value }
        override fun getInt(key: String, defaultValue: Int) = data[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = data[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun remove(key: String) { data.remove(key) }
        override fun clear() { data.clear() }
    }

    @Test
    fun `status is UNCONFIGURED when API key is missing`() = runTest {
        val storage = FakeStorage()
        val provider = OpenRouterProvider(storage)
        
        assertEquals(ProviderStatus.State.UNCONFIGURED, provider.getStatus().state)
    }

    @Test
    fun `status is READY when API key is present`() = runTest {
        val storage = FakeStorage()
        storage.putString("openrouter_api_key", "sk-or-test")
        val provider = OpenRouterProvider(storage)
        
        assertEquals(ProviderStatus.State.READY, provider.getStatus().state)
    }
}
