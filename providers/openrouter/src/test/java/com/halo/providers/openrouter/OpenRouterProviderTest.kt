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
    @Test fun imageEditingRequiresAdvertisedReferenceParameter() = kotlinx.coroutines.runBlocking {
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK").body(okhttp3.ResponseBody.create(null,
                    """{"data":[{"id":"test/image","architecture":{"input_modalities":["text","image"]},"supported_parameters":{}}]}""")).build()
        }.build()
        val models = OpenRouterProvider(FakeStorage(), client).listImageModels()
        assertFalse("Image input alone does not advertise input_references support", models.single().supportsEditing)
    }

    @Test fun imageMetadataPreservesRequiredReferenceAndGenerationOmitsOptionalCount() = kotlinx.coroutines.runBlocking {
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val response = if (request.method == "GET")
                """{"data":[{"id":"test/edit","architecture":{"input_modalities":["text","image"]},"supported_parameters":{"input_references":{"min":1,"max":8}}}]}"""
            else {
                val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                val body = JSONObject(buffer.readUtf8())
                assertFalse("Single-image endpoint default also works for models without n support", body.has("n"))
                assertEquals("test/edit", body.getString("model"))
                assertEquals(1, body.getJSONArray("input_references").length())
                """{"data":[{"b64_json":"AQ==","media_type":"image/png"}]}"""
            }
            okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK").body(okhttp3.ResponseBody.create(null, response)).build()
        }.build()
        val provider = OpenRouterProvider(FakeStorage(), client)
        val model = provider.listImageModels().single()
        assertTrue(model.supportsEditing)
        assertTrue(model.requiresReference)
        assertArrayEquals(byteArrayOf(1), provider.generateImage(com.halo.sdk.provider.AiImageRequest(
            model.id, "edit", byteArrayOf(1))).bytes)
    }

    @Test fun requiredActionUsesRequiredChoiceButContinuationCanFinish() {
        val provider = OpenRouterProvider(FakeStorage())
        val method = provider.javaClass.getDeclaredMethod("buildRequestBody",
            com.halo.sdk.provider.AiRequest::class.java, com.halo.sdk.provider.AiContext::class.java,
            String::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        val context = com.halo.sdk.provider.AiContext.EMPTY.copy(availableTools = listOf(
            com.halo.sdk.provider.ToolDefinition("halo_volume", "halo_volume", "Adjust volume")))
        fun body(mode: com.halo.sdk.provider.ToolUseMode, kind: com.halo.sdk.provider.ProviderTurnKind) =
            method.invoke(provider, com.halo.sdk.provider.AiRequest(text = "change volume", toolUseMode = mode, turnKind = kind), context, "test/model", false) as JSONObject
        val initial = body(com.halo.sdk.provider.ToolUseMode.REQUIRED, com.halo.sdk.provider.ProviderTurnKind.INITIAL)
        assertEquals("required", initial.getString("tool_choice"))
        assertTrue(initial.getJSONObject("provider").getBoolean("require_parameters"))
        assertEquals("auto", body(com.halo.sdk.provider.ToolUseMode.REQUIRED, com.halo.sdk.provider.ProviderTurnKind.TOOL_CONTINUATION).getString("tool_choice"))
        assertFalse(body(com.halo.sdk.provider.ToolUseMode.NONE, com.halo.sdk.provider.ProviderTurnKind.INITIAL).has("tools"))
    }

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
