package com.halo.providers.openrouter

import com.halo.sdk.provider.AiCapability
import com.halo.sdk.runtime.Storage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenRouterProviderTest {

    private class FakeStorage : Storage {
        private val data = mutableMapOf<String, Any?>("openrouter_api_key" to "test-key")
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
    fun `openrouter model metadata accurately maps modalities, parameters, and pricing`() {
        val modelJson = JSONObject("""
            {
              "id": "test/vision-model",
              "name": "Vision Model",
              "context_length": 128000,
              "architecture": {
                "input_modalities": [
                  "text",
                  "image"
                ],
                "output_modalities": [
                  "text"
                ],
                "instruct_type": "chatml"
              },
              "supported_parameters": [
                "tools",
                "tool_choice",
                "stream"
              ],
              "pricing": {
                "prompt": "0",
                "completion": "0"
              }
            }
        """.trimIndent())

        val provider = OpenRouterProvider(FakeStorage())

        val mapCapabilitiesMethod = provider.javaClass.getDeclaredMethod("mapCapabilities", JSONObject::class.java).apply { isAccessible = true }
        val supportsParameterMethod = provider.javaClass.getDeclaredMethod("supportsParameter", JSONObject::class.java, String::class.java).apply { isAccessible = true }
        val isFreeModelMethod = provider.javaClass.getDeclaredMethod("isFreeModel", JSONObject::class.java).apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val caps = mapCapabilitiesMethod.invoke(provider, modelJson) as Set<AiCapability>
        assertTrue(caps.contains(AiCapability.TEXT))
        assertTrue(caps.contains(AiCapability.IMAGE))
        assertFalse(caps.contains(AiCapability.AUDIO))

        val supportsTools = supportsParameterMethod.invoke(provider, modelJson, "tools") as Boolean
        assertTrue(supportsTools)

        val supportsStreaming = supportsParameterMethod.invoke(provider, modelJson, "stream") as Boolean
        assertTrue(supportsStreaming)

        val isFree = isFreeModelMethod.invoke(provider, modelJson) as Boolean
        assertTrue(isFree)
    }

    @Test
    fun `openrouter model without tools parameter reports supportsTools false`() {
        val modelJson = JSONObject("""
            {
              "id": "test/basic-model",
              "name": "Basic Model",
              "architecture": {
                "input_modalities": ["text"],
                "instruct_type": "chatml"
              },
              "supported_parameters": ["max_tokens"]
            }
        """.trimIndent())

        val provider = OpenRouterProvider(FakeStorage())
        val supportsParameterMethod = provider.javaClass.getDeclaredMethod("supportsParameter", JSONObject::class.java, String::class.java).apply { isAccessible = true }

        val supportsTools = supportsParameterMethod.invoke(provider, modelJson, "tools") as Boolean
        assertFalse(supportsTools)

        val supportsStreaming = supportsParameterMethod.invoke(provider, modelJson, "stream") as Boolean
        assertFalse(supportsStreaming)
    }
}
