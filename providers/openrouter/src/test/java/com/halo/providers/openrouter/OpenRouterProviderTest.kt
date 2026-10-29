package com.halo.providers.openrouter

import com.halo.sdk.provider.AiCapability
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.toList
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenRouterProviderTest {
    @Test fun http404IsAnUnavailableModelForGenerateAndStreamWithoutRetryOrBodyDisclosure() = kotlinx.coroutines.runBlocking {
        for (streaming in listOf(false, true)) {
            val logger = DiagnosticLogger()
            var calls = 0
            val provider = diagnosticProvider("""{"error":{"code":404,"message":"PRIVATE UPSTREAM BODY 941"}}""",
                logger, code = 404) { calls++ }
            val request = com.halo.sdk.provider.AiRequest(text = "PRIVATE REQUEST 942",
                modelId = "test/unavailable:free", interactionId = "qa-unavailable-1")
            val failure = if (streaming) {
                val events = provider.stream(request, com.halo.sdk.provider.AiContext.EMPTY).toList()
                assertTrue(events.filterIsInstance<com.halo.sdk.provider.AiStreamEvent.Completed>().isEmpty())
                events.filterIsInstance<com.halo.sdk.provider.AiStreamEvent.Failed>().single().error as com.halo.sdk.provider.AiProviderException
            } else try {
                provider.generate(request, com.halo.sdk.provider.AiContext.EMPTY)
                error("Expected unavailable model")
            } catch (error: com.halo.sdk.provider.AiProviderException) { error }
            assertEquals(com.halo.sdk.provider.AiErrorType.MODEL_UNAVAILABLE, failure.type)
            assertEquals(com.halo.sdk.provider.AiFailureDetails(httpStatus = 404, providerCode = "404",
                recoverability = com.halo.sdk.provider.Recoverability.PERMANENT), failure.details)
            assertEquals(1, calls)
            assertTrue(logger.entries.single().second.startsWith("request_failure type=MODEL_UNAVAILABLE httpStatus=404 "))
            assertFalse(failure.message.orEmpty().contains("PRIVATE"))
            assertFalse(logger.entries.single().second.contains("PRIVATE"))
            assertFalse(logger.entries.single().second.contains("test-key"))
        }
    }

    @Test fun numericSse404PreservesUnavailableModelMetadataWithoutRetryOrBodyDisclosure() = kotlinx.coroutines.runBlocking {
        val logger = DiagnosticLogger()
        var calls = 0
        val provider = diagnosticProvider(
            "data: {\"error\":{\"code\":404,\"message\":\"PRIVATE SSE BODY 943\"}}\n\ndata: [DONE]\n\n",
            logger) { calls++ }
        val events = provider.stream(com.halo.sdk.provider.AiRequest(text = "PRIVATE REQUEST 944",
            modelId = "test/unavailable:free", interactionId = "qa-unavailable-2"),
            com.halo.sdk.provider.AiContext.EMPTY).toList()
        assertTrue(events.filterIsInstance<com.halo.sdk.provider.AiStreamEvent.Completed>().isEmpty())
        val failure = events.filterIsInstance<com.halo.sdk.provider.AiStreamEvent.Failed>().single().error as com.halo.sdk.provider.AiProviderException
        assertEquals(com.halo.sdk.provider.AiErrorType.MODEL_UNAVAILABLE, failure.type)
        assertEquals(com.halo.sdk.provider.AiFailureDetails(httpStatus = 404, providerCode = "404",
            recoverability = com.halo.sdk.provider.Recoverability.PERMANENT), failure.details)
        assertEquals(1, calls)
        assertTrue(logger.entries.single().second.startsWith("request_failure type=MODEL_UNAVAILABLE httpStatus=404 "))
        assertFalse(failure.message.orEmpty().contains("PRIVATE"))
        assertFalse(logger.entries.single().second.contains("PRIVATE"))
        assertFalse(logger.entries.single().second.contains("test-key"))
    }

    @Test fun explicitReasoningModesReachBothTransportsWithoutChangingCapsOrTools() = kotlinx.coroutines.runBlocking {
        for (streaming in listOf(false, true)) for (mode in com.halo.sdk.provider.AiReasoningMode.entries)
            for (tools in listOf(false, true)) {
                val cap = if (tools) 1800 else 600
                var calls = 0
                val response = if (streaming)
                    "data: {\"choices\":[{\"delta\":{\"content\":\"Visible answer\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
                else """{"choices":[{"message":{"content":"Visible answer"},"finish_reason":"stop"}]}"""
                val provider = diagnosticProvider(response, DiagnosticLogger()) { body ->
                    calls++
                    assertEquals("test/model", body.getString("model"))
                    assertEquals(streaming, body.getBoolean("stream"))
                    assertEquals(cap, body.getInt("max_tokens"))
                    if (mode == com.halo.sdk.provider.AiReasoningMode.DEFAULT) {
                        assertFalse("DEFAULT must preserve the provider default", body.has("reasoning"))
                    } else {
                        val reasoning = body.getJSONObject("reasoning")
                        assertEquals(1, reasoning.length())
                        assertEquals(mode == com.halo.sdk.provider.AiReasoningMode.ENABLED,
                            reasoning.getBoolean("enabled"))
                    }
                    assertEquals(tools, body.has("tools"))
                    if (tools) {
                        assertEquals("required", body.getString("tool_choice"))
                        assertTrue(body.getJSONObject("provider").getBoolean("require_parameters"))
                        assertEquals("halo_volume", body.getJSONArray("tools").getJSONObject(0)
                            .getJSONObject("function").getString("name"))
                    } else assertFalse(body.has("tool_choice"))
                }
                val request = com.halo.sdk.provider.AiRequest(text = "Synthetic bounded request", modelId = "test/model",
                    maxOutputTokens = cap, reasoningMode = mode,
                    toolUseMode = if (tools) com.halo.sdk.provider.ToolUseMode.REQUIRED else com.halo.sdk.provider.ToolUseMode.NONE,
                    turnKind = com.halo.sdk.provider.ProviderTurnKind.INITIAL)
                val context = com.halo.sdk.provider.AiContext.EMPTY.copy(availableTools = listOf(
                    com.halo.sdk.provider.ToolDefinition("halo_volume", "halo_volume", "Adjust volume")))
                val result = if (streaming) provider.stream(request, context).toList()
                    .filterIsInstance<com.halo.sdk.provider.AiStreamEvent.Completed>().single().response
                else provider.generate(request, context)
                assertEquals("Visible answer", result.text)
                assertEquals("One explicit request must not trigger automatic retries", 1, calls)
            }
    }

    @Test fun requestFailureDiagnosticsDistinguishHttpIoAndGenericWithoutLoggingContentOrRetrying() = kotlinx.coroutines.runBlocking {
        for (streaming in listOf(false, true)) for (kind in listOf("http", "io", "generic")) {
            val logger = DiagnosticLogger()
            var calls = 0
            val provider = diagnosticProvider("""{"error":{"message":"PRIVATE HTTP BODY 931"}}""", logger,
                code = if (kind == "http") 503 else 200) {
                calls++
                if (kind == "io") throw java.io.IOException("PRIVATE IO MESSAGE 932")
                if (kind == "generic") throw IllegalStateException("PRIVATE GENERIC MESSAGE 933")
            }
            val request = com.halo.sdk.provider.AiRequest(text = "PRIVATE SOURCE PROMPT 934",
                modelId = "nvidia/test:free", interactionId = "qa-request-1")
            val failure = if (streaming) {
                provider.stream(request, com.halo.sdk.provider.AiContext.EMPTY).toList()
                    .filterIsInstance<com.halo.sdk.provider.AiStreamEvent.Failed>().single().error as com.halo.sdk.provider.AiProviderException
            } else {
                try {
                    provider.generate(request, com.halo.sdk.provider.AiContext.EMPTY)
                    error("Expected provider failure")
                } catch (error: com.halo.sdk.provider.AiProviderException) { error }
            }
            val expectedType = if (kind == "http") com.halo.sdk.provider.AiErrorType.PROVIDER_UNAVAILABLE
                else com.halo.sdk.provider.AiErrorType.NETWORK_ERROR
            assertEquals(expectedType, failure.type)
            assertEquals(1, calls)
            val expectedCause = when (kind) { "io" -> "java.io.IOException"; "generic" -> "java.lang.IllegalStateException"; else -> "missing" }
            assertEquals("request_failure type=${expectedType.name} httpStatus=${if (kind == "http") "503" else "missing"} " +
                "exceptionClass=com.halo.sdk.provider.AiProviderException causeClass=$expectedCause " +
                "selectedModel=nvidia/test:free interactionId=qa-request-1", logger.entries.single().second)
            assertFalse(logger.entries.single().second.contains("PRIVATE"))
            assertFalse(logger.entries.single().second.contains("test-key"))
        }
    }

    @Test fun failureDiagnosticsRejectMalformedMetadataAndBrokenSinkPreservesExactFailure() = kotlinx.coroutines.runBlocking {
        for (streaming in listOf(false, true)) for (brokenSink in listOf(false, true)) {
            val logger = DiagnosticLogger(brokenSink)
            var calls = 0
            val original = com.halo.sdk.provider.AiProviderException(com.halo.sdk.provider.AiErrorType.NETWORK_ERROR,
                "PRIVATE EXCEPTION MESSAGE 935", details = com.halo.sdk.provider.AiFailureDetails(httpStatus = 999))
            val provider = diagnosticProvider("unused", logger) { calls++; throw original }
            val request = com.halo.sdk.provider.AiRequest(text = "PRIVATE SOURCE PROMPT 936",
                modelId = "x".repeat(161), interactionId = "unsafe\nPRIVATE INTERACTION 937")
            val failure = if (streaming) {
                provider.stream(request, com.halo.sdk.provider.AiContext.EMPTY).toList()
                    .filterIsInstance<com.halo.sdk.provider.AiStreamEvent.Failed>().single().error
            } else {
                try {
                    provider.generate(request, com.halo.sdk.provider.AiContext.EMPTY)
                    error("Expected provider failure")
                } catch (error: com.halo.sdk.provider.AiProviderException) { error }
            }
            assertSame(original, failure)
            assertEquals(1, calls)
            if (!brokenSink) assertEquals("request_failure type=NETWORK_ERROR httpStatus=missing " +
                "exceptionClass=com.halo.sdk.provider.AiProviderException causeClass=missing " +
                "selectedModel=invalid interactionId=invalid", logger.entries.single().second)
        }
    }

    @Test fun emptyCompletionsDistinguishLengthFromStopWithoutLoggingContentOrRetrying() = kotlinx.coroutines.runBlocking {
        for (finish in listOf(" LENGTH ", "stop")) {
            val logger = DiagnosticLogger()
            var calls = 0
            val response = JSONObject().put("model", "nvidia/test:free")
                .put("choices", org.json.JSONArray().put(JSONObject().put("finish_reason", finish)
                    .put("message", JSONObject().put("content", " ").put("reasoning", "PRIVATE REASONING 921"))))
                .put("usage", JSONObject().put("completion_tokens", 600)
                    .put("completion_tokens_details", JSONObject().put("reasoning_tokens", 600)))
            val provider = diagnosticProvider(response.toString(), logger) { body ->
                calls++
                assertEquals(600, body.getInt("max_tokens"))
                assertFalse(body.has("reasoning"))
                assertFalse(body.has("tools"))
            }
            val result = provider.generate(com.halo.sdk.provider.AiRequest(text = "PRIVATE SOURCE PROMPT 922",
                modelId = "nvidia/test:free", maxOutputTokens = 600, toolUseMode = com.halo.sdk.provider.ToolUseMode.NONE,
                interactionId = "qa-empty-1"),
                com.halo.sdk.provider.AiContext.EMPTY)
            assertTrue(result.text.isBlank()) // No fabricated answer; router still rejects this result.
            assertEquals(finish, result.finishReason)
            assertEquals(1, calls)
            assertEquals("OpenRouter", logger.entries.single().first)
            assertEquals("empty_completion finishReason=${finish.trim().lowercase()} " +
                "interactionId=qa-empty-1 selectedProvider=openrouter selectedModel=nvidia/test:free resolvedProvider=openrouter " +
                "resolvedModel=nvidia/test:free requestedOutputTokens=600 outputTokens=600 reasoningTokens=600",
                logger.entries.single().second)
            assertFalse(logger.entries.single().second.contains("PRIVATE"))
        }
    }

    @Test fun emptyStreamingCompletionRetainsUsageFromChoicesEmptyPacketAndStillCompletesBlank() = kotlinx.coroutines.runBlocking {
        val logger = DiagnosticLogger()
        val stream = "data: {\"model\":\"nvidia/test:free\",\"choices\":[{\"delta\":{\"reasoning\":\"PRIVATE REASONING 923\"},\"finish_reason\":\"length\"}]}\n\n" +
            "data: {\"choices\":[],\"usage\":{\"completion_tokens\":600,\"completion_tokens_details\":{\"reasoning_tokens\":600}}}\n\n" +
            "data: [DONE]\n\n"
        val provider = diagnosticProvider(stream, logger)
        val events = provider.stream(com.halo.sdk.provider.AiRequest(
            text = "PRIVATE SOURCE PROMPT 924", modelId = "nvidia/test:free", maxOutputTokens = 600),
            com.halo.sdk.provider.AiContext.EMPTY).toList()
        val result = events.filterIsInstance<com.halo.sdk.provider.AiStreamEvent.Completed>().single().response
        assertTrue(result.text.isBlank())
        assertEquals("length", result.finishReason)
        assertTrue(logger.entries.single().second.endsWith("outputTokens=600 reasoningTokens=600"))
        assertFalse(logger.entries.single().second.contains("PRIVATE"))
    }

    @Test fun emptyCompletionDiagnosticsRejectInjectedMetadataAndDoNotChangeOutcomeWhenSinkFails() = kotlinx.coroutines.runBlocking {
        for (fail in listOf(false, true)) {
            val logger = DiagnosticLogger(fail)
            val response = JSONObject().put("model", "unsafe\nPRIVATE MODEL 925")
                .put("choices", org.json.JSONArray().put(JSONObject().put("finish_reason", "PRIVATE FINISH 926")
                    .put("message", JSONObject().put("content", ""))))
                .put("usage", JSONObject().put("completion_tokens", -1)
                    .put("completion_tokens_details", JSONObject().put("reasoning_tokens", "PRIVATE USAGE 927")))
            val provider = diagnosticProvider(response.toString(), logger)
            val result = provider.generate(com.halo.sdk.provider.AiRequest(text = "PRIVATE SOURCE PROMPT 928",
                modelId = "x".repeat(161), maxOutputTokens = 600), com.halo.sdk.provider.AiContext.EMPTY)
            assertEquals("", result.text)
            if (!fail) assertEquals("empty_completion finishReason=unknown interactionId=missing selectedProvider=openrouter " +
                "selectedModel=invalid resolvedProvider=openrouter resolvedModel=invalid " +
                "requestedOutputTokens=600 outputTokens=missing reasoningTokens=missing", logger.entries.single().second)
        }
    }

    @Test fun ordinaryCompletionDoesNotEmitEmptyDiagnostic() = kotlinx.coroutines.runBlocking {
        val logger = DiagnosticLogger()
        val provider = diagnosticProvider("""{"choices":[{"message":{"content":"A useful answer"},"finish_reason":"stop"}]}""", logger)
        assertEquals("A useful answer", provider.generate(com.halo.sdk.provider.AiRequest(text = "Question",
            modelId = "nvidia/test:free"), com.halo.sdk.provider.AiContext.EMPTY).text)
        assertTrue(logger.entries.isEmpty())
    }

    private fun diagnosticProvider(response: String, logger: DiagnosticLogger,
        code: Int = 200, inspect: (JSONObject) -> Unit = {}): OpenRouterProvider {
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val buffer = okio.Buffer(); requireNotNull(request.body).writeTo(buffer)
            inspect(JSONObject(buffer.readUtf8()))
            okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(code).message("OK").body(okhttp3.ResponseBody.create(null, response)).build()
        }.build()
        return OpenRouterProvider(FakeStorage(), client, logger = logger)
    }

    private class DiagnosticLogger(private val fail: Boolean = false) : com.halo.sdk.runtime.RuntimeLogger {
        val entries = mutableListOf<Pair<String, String>>()
        override fun trace(category: String, message: String) { error("Unexpected trace diagnostic") }
        override fun debug(category: String, message: String) { error("Unexpected debug diagnostic") }
        override fun info(category: String, message: String) { error("Unexpected info diagnostic") }
        override fun warn(category: String, message: String) {
            if (fail) error("Diagnostic sink unavailable")
            entries += category to message
        }
        override fun error(category: String, message: String, throwable: Throwable?) { error("Unexpected error diagnostic") }
    }

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
