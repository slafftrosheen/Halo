package com.halo.providers.elevenlabs.tts

import com.halo.sdk.provider.Credential
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class ElevenLabsTtsProviderTest {
    @Test
    fun saveVerifiesAndReloadsFromPersistence() = runBlocking {
        val storage = RecordingStorage()
        val provider = ElevenLabsTtsProvider(storage)
        provider.setCredential(Credential(ElevenLabsTtsProvider.ID, "placeholder-secret"))
        assertTrue(provider.hasCredential())
        assertEquals("placeholder-secret", provider.credential()?.value)
        provider.close()

        val reopened = ElevenLabsTtsProvider(storage)
        withTimeout(2_000) { while (!reopened.state.value.configured) delay(10) }
        assertTrue(reopened.hasCredential())
        assertEquals("placeholder-secret", reopened.credential()?.value)
        reopened.close()
    }

    @Test
    fun failedReadbackDoesNotPublishConfiguredState() = runBlocking {
        val storage = RecordingStorage()
        val provider = ElevenLabsTtsProvider(storage)
        storage.dropCredentialReads = true
        var failed = false
        try {
            provider.setCredential(Credential(ElevenLabsTtsProvider.ID, "placeholder-secret"))
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
        assertFalse(provider.hasCredential())
        assertFalse(provider.state.value.configured)
        provider.close()
    }

    @Test
    fun backgroundReloadDoesNotReadOnConstructingThread() = runBlocking {
        val storage = RecordingStorage()
        val constructingThread = Thread.currentThread().name
        val provider = ElevenLabsTtsProvider(storage)
        withTimeout(2_000) { while (storage.readThreads.isEmpty()) delay(10) }
        assertTrue(storage.readThreads.none { it == constructingThread })
        provider.close()
    }

    private class RecordingStorage : Storage {
        private val strings = ConcurrentHashMap<String, String>()
        private val ints = ConcurrentHashMap<String, Int>()
        private val booleans = ConcurrentHashMap<String, Boolean>()
        val readThreads = CopyOnWriteArrayList<String>()
        @Volatile var dropCredentialReads: Boolean = false
        override fun getString(key: String, defaultValue: String?): String? {
            readThreads += Thread.currentThread().name
            if (dropCredentialReads && key == ElevenLabsTtsProvider.KEY) return defaultValue
            return strings[key] ?: defaultValue
        }
        override fun putString(key: String, value: String?) { if (value == null) strings.remove(key) else strings[key] = value }
        override fun getInt(key: String, defaultValue: Int): Int = ints[key] ?: defaultValue
        override fun putInt(key: String, value: Int) { ints[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = booleans[key] ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { booleans[key] = value }
        override fun remove(key: String) { strings.remove(key); ints.remove(key); booleans.remove(key) }
        override fun clear() { strings.clear(); ints.clear(); booleans.clear() }
    }
}
