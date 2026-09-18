package com.halo.app.ai

import com.halo.sdk.runtime.Storage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeAiPolicyTest {

    @Test
    fun persistedPolicyMapsDirectlyToRuntimeContextPolicy() {
        val storage = MapStorage().apply {
            putBoolean(KEY_CONVERSATION, false)
            putBoolean(KEY_DEVICE_STATE, true)
            putBoolean(KEY_RECENT_EVENTS, true)
            putBoolean(KEY_MEMORIES, true)
            putBoolean(KEY_TOOLS, false)
            putBoolean(KEY_LOCATION, true)
            putBoolean(KEY_IMAGES, false)
            putBoolean(KEY_AUDIO, true)
            putBoolean(KEY_VIDEO, true)
        }

        val policy = storage.readRuntimeAiContextPolicy()

        assertFalse(policy.includeConversation)
        assertTrue(policy.includeDeviceState)
        assertTrue(policy.includeRecentEvents)
        assertTrue(policy.includeMemories)
        assertFalse(policy.includeAvailableTools)
        assertTrue(policy.includeLocation)
        assertFalse(policy.includeImages)
        assertTrue(policy.includeAudio)
        assertTrue(policy.includeVideo)
    }

    private class MapStorage : Storage {
        private val values = mutableMapOf<String, Any?>()

        override fun getString(key: String, defaultValue: String?): String? =
            values[key] as? String ?: defaultValue

        override fun putString(key: String, value: String?) {
            values[key] = value
        }

        override fun getInt(key: String, defaultValue: Int): Int =
            values[key] as? Int ?: defaultValue

        override fun putInt(key: String, value: Int) {
            values[key] = value
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            values[key] as? Boolean ?: defaultValue

        override fun putBoolean(key: String, value: Boolean) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }

        override fun clear() {
            values.clear()
        }
    }
}
