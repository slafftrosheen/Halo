package com.halo.providers.openrouter

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CancellationException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class OpenRouterProvider(
    private val storage: Storage,
    private val client: OkHttpClient = defaultClient()
) : AiInferenceProvider {

    override val id: String = "openrouter"
    override val displayName: String = "OpenRouter"
    override val isLocal: Boolean = false

    private val _status = MutableStateFlow(ProviderStatus.UNCONFIGURED)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()

    private val apiKey: String? get() = storage.getString("openrouter_api_key")
    private val selectedModelId: String get() = storage.getString("openrouter_model") ?: "openrouter/free"

    override suspend fun getStatus(): ProviderStatus = withContext(Dispatchers.IO) {
        val current = if (apiKey.isNullOrBlank()) {
            ProviderStatus.UNCONFIGURED
        } else {
            statusFromLastErrorOrReady()
        }
        _status.value = current
        current
    }

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        val key = apiKey ?: return@withContext false
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/auth/key")
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val ok = response.isSuccessful
                _status.value = if (ok) {
                    ProviderStatus.READY
                } else {
                    providerStatus(httpError(response.code, response.body?.string()))
                }
                ok
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: SocketTimeoutException) {
            _status.value = providerStatus(AiProviderException(AiErrorType.TIMEOUT, "OpenRouter auth check timed out", error))
            false
        } catch (error: UnknownHostException) {
            _status.value = providerStatus(AiProviderException(AiErrorType.NETWORK_UNAVAILABLE, "OpenRouter host unavailable", error))
            false
        } catch (error: Exception) {
            _status.value = providerStatus(AiProviderException(AiErrorType.NETWORK_ERROR, "OpenRouter auth check failed", error))
            false
        }
    }

    override suspend fun getCapabilities(): Set<AiCapability> = setOf(
        AiCapability.TEXT, AiCapability.IMAGE, AiCapability.STREAMING, AiCapability.TOOLS
    )

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = withContext(Dispatchers.IO) {
        val key = apiKey ?: throw AiProviderException(AiErrorType.AUTHENTICATION_FAILED, "OpenRouter API key not configured")
        val actualModel = resolveModel(request.modelId ?: selectedModelId)
        val body = buildRequestBody(request, context, actualModel, false).toString()
            .toRequestBody("application/json".toMediaType())
        val httpRequest = buildChatRequest(key, body)
        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw httpError(response.code, response.body?.string())
                }
                val responseJson = JSONObject(
                    response.body?.string()
                        ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter returned an empty response body")
                )
                val choices = responseJson.optJSONArray("choices")
                    ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter returned no choices")
                if (choices.length() == 0) throw AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter returned no choices")
                val first = choices.getJSONObject(0)
                val message = first.getJSONObject("message")
                val result = AiResponse(
                    message.optString("content"),
                    parseToolCalls(message.optJSONArray("tool_calls")),
                    first.optString("finish_reason"),
                    id,
                    responseJson.optString("model", actualModel)
                )
                _status.value = ProviderStatus.READY
                result
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiProviderException) {
            _status.value = providerStatus(e)
            throw e
        } catch (e: SocketTimeoutException) {
            val failure = AiProviderException(AiErrorType.TIMEOUT, "OpenRouter request timed out (model=$actualModel)", e)
            _status.value = providerStatus(failure)
            throw failure
        } catch (e: UnknownHostException) {
            val failure = AiProviderException(AiErrorType.NETWORK_UNAVAILABLE, "OpenRouter host unavailable", e)
            _status.value = providerStatus(failure)
            throw failure
        } catch (e: org.json.JSONException) {
            val failure = AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter returned invalid JSON", e)
            _status.value = providerStatus(failure)
            throw failure
        } catch (e: Exception) {
            val failure = AiProviderException(AiErrorType.NETWORK_ERROR, "OpenRouter request failed: ${e.message ?: e::class.simpleName}", e)
            _status.value = providerStatus(failure)
            throw failure
        }
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
        val key = apiKey ?: throw AiProviderException(AiErrorType.AUTHENTICATION_FAILED, "OpenRouter API key not configured")
        emit(AiStreamEvent.Started)
        val actualModel = resolveModel(request.modelId ?: selectedModelId)
        val body = buildRequestBody(request, context, actualModel, true).toString()
            .toRequestBody("application/json".toMediaType())
        val httpRequest = buildChatRequest(key, body)
        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) throw httpError(response.code, response.body?.string())
                val source = response.body?.source() ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter returned no response body")
                val text = StringBuilder()
                val tools = linkedMapOf<Int, StreamToolCall>()
                var finish: String? = null

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data: ")) continue
                    val data = line.substring(6)
                    if (data == "[DONE]") break
                    val choices = JSONObject(data).optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val choice = choices.getJSONObject(0)
                    val delta = choice.optJSONObject("delta") ?: continue
                    val token = delta.optString("content")
                    if (token.isNotEmpty()) {
                        text.append(token)
                        emit(AiStreamEvent.Token(token))
                    }
                    finish = choice.optString("finish_reason").takeIf { it.isNotBlank() } ?: finish
                    delta.optJSONArray("tool_calls")?.let { calls ->
                        for (i in 0 until calls.length()) {
                            val tc = calls.getJSONObject(i)
                            val fn = tc.optJSONObject("function")
                            val index = tc.optInt("index", i)
                            val assembled = tools.getOrPut(index) {
                                StreamToolCall(tc.optString("id").ifBlank { "tool-$index" })
                            }
                            fn?.optString("name")?.takeIf { it.isNotBlank() }?.let { assembled.name = it }
                            fn?.optString("arguments")?.let { assembled.arguments.append(it) }
                            emit(AiStreamEvent.ToolCallDelta(index, fn?.optString("name"), fn?.optString("arguments")))
                        }
                    }
                }

                _status.value = ProviderStatus.READY
                emit(
                    AiStreamEvent.Completed(
                        AiResponse(
                            text = text.toString(),
                            toolCalls = tools.values.mapNotNull { it.name?.let { name ->
                                ToolCall(it.id, name, parseArguments(it.arguments.toString()))
                            } },
                            finishReason = finish,
                            providerId = id,
                            modelId = actualModel
                        )
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiProviderException) {
            _status.value = providerStatus(e)
            emit(AiStreamEvent.Failed(e))
        } catch (e: SocketTimeoutException) {
            val failure = AiProviderException(AiErrorType.TIMEOUT, "OpenRouter stream timed out (model=$actualModel)", e)
            _status.value = providerStatus(failure)
            emit(AiStreamEvent.Failed(failure))
        } catch (e: UnknownHostException) {
            val failure = AiProviderException(AiErrorType.NETWORK_UNAVAILABLE, "OpenRouter host unavailable", e)
            _status.value = providerStatus(failure)
            emit(AiStreamEvent.Failed(failure))
        } catch (e: org.json.JSONException) {
            val failure = AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter returned invalid stream JSON", e)
            _status.value = providerStatus(failure)
            emit(AiStreamEvent.Failed(failure))
        } catch (e: Exception) {
            val failure = AiProviderException(AiErrorType.NETWORK_ERROR, "OpenRouter stream failed: ${e.message ?: e::class.simpleName}", e)
            _status.value = providerStatus(failure)
            emit(AiStreamEvent.Failed(failure))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): List<AiModel> = withContext(Dispatchers.IO) {
        val key = apiKey ?: return@withContext emptyList()
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/models")
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw httpError(response.code, response.body?.string())
                val body = response.body?.string()
                    ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Empty response body from OpenRouter")
                val data = JSONObject(body).getJSONArray("data")
                val models = buildList {
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        add(
                            AiModel(
                                id = item.getString("id"),
                                displayName = item.optString("name", item.getString("id")),
                                providerId = id,
                                inputCapabilities = mapCapabilities(item),
                                contextLength = item.optLong("context_length").takeIf { it > 0L },
                                supportsStreaming = supportsParameter(item, "stream"),
                                supportsTools = supportsParameter(item, "tools"),
                                isFree = isFreeModel(item)
                            )
                        )
                    }
                }
                _status.value = ProviderStatus.READY
                models
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiProviderException) {
            _status.value = providerStatus(e)
            throw e
        } catch (e: SocketTimeoutException) {
            val failure = AiProviderException(AiErrorType.TIMEOUT, "OpenRouter model discovery timed out", e)
            _status.value = providerStatus(failure)
            throw failure
        } catch (e: UnknownHostException) {
            val failure = AiProviderException(AiErrorType.NETWORK_UNAVAILABLE, "OpenRouter model discovery: network unavailable", e)
            _status.value = providerStatus(failure)
            throw failure
        } catch (e: org.json.JSONException) {
            val failure = AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter returned invalid model metadata", e)
            _status.value = providerStatus(failure)
            throw failure
        } catch (e: Exception) {
            val failure = AiProviderException(AiErrorType.NETWORK_ERROR, "OpenRouter model discovery failed: ${e.message ?: e::class.simpleName}", e)
            _status.value = providerStatus(failure)
            throw failure
        }
    }

    private suspend fun resolveModel(modelId: String): String {
        // Legacy shorthand maps to the same router used by the current UI/default.
        return if (modelId == "free") "openrouter/free" else modelId
    }

    private fun buildRequestBody(request: AiRequest, context: AiContext, model: String, stream: Boolean): JSONObject {
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", storage.getString("openrouter_system_prompt") ?: "You are Halo, a helpful AI assistant.")
        })
        context.conversation.forEach { msg ->
            messages.put(JSONObject().apply {
                put("role", msg.role.name.lowercase())
                put("content", msg.content)
                msg.toolCallId?.let { put("tool_call_id", it) }
                if (msg.toolCalls.isNotEmpty()) {
                    put("tool_calls", JSONArray().apply {
                        msg.toolCalls.forEach { call ->
                            put(JSONObject().apply {
                                put("id", call.id)
                                put("type", "function")
                                put("function", JSONObject().apply {
                                    put("name", call.functionName)
                                    put("arguments", JSONObject(call.arguments).toString())
                                })
                            })
                        }
                    })
                }
            })
        }
        request.text?.let { text ->
            val content: Any = if (request.image != null) {
                JSONArray().apply {
                    put(JSONObject().apply { put("type", "text"); put("text", text) })
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            val mimeType = request.mimeTypes.firstOrNull() ?: "image/jpeg"
                            put("url", "data:$mimeType;base64,${android.util.Base64.encodeToString(request.image, android.util.Base64.NO_WRAP)}")
                        })
                    })
                }
            } else text
            messages.put(JSONObject().apply { put("role", "user"); put("content", content) })
        }
        return JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("stream", stream)
            if (context.availableTools.isNotEmpty()) {
                put("parallel_tool_calls", false)
                put("tools", JSONArray().apply {
                    context.availableTools.forEach { tool ->
                        put(JSONObject().apply {
                            put("type", "function")
                            put("function", JSONObject().apply {
                                put("name", tool.id)
                                put("description", tool.description)
                                put("parameters", JSONObject(tool.parameters))
                            })
                        })
                    }
                })
            }
        }
    }

    private fun buildChatRequest(key: String, body: okhttp3.RequestBody): Request =
        Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-OpenRouter-Title", "Halo")
            .post(body)
            .build()

    private fun jsonStringArray(json: JSONObject, key: String): Set<String> {
        val array = json.optJSONArray(key) ?: return emptySet()
        return buildSet {
            for (i in 0 until array.length()) {
                array.optString(i).trim().takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }

    private fun mapCapabilities(modelJson: JSONObject): Set<AiCapability> {
        val modalities = modelJson.optJSONObject("architecture")
            ?.let { jsonStringArray(it, "input_modalities") }
            .orEmpty()
        return buildSet {
            if ("text" in modalities || modalities.isEmpty()) add(AiCapability.TEXT)
            if ("image" in modalities) add(AiCapability.IMAGE)
            if ("audio" in modalities) add(AiCapability.AUDIO)
            if ("video" in modalities) add(AiCapability.VIDEO)
        }
    }

    private fun supportsParameter(modelJson: JSONObject, parameter: String): Boolean =
        parameter in jsonStringArray(modelJson, "supported_parameters")

    private fun isFreeModel(modelJson: JSONObject): Boolean =
        modelJson.optJSONObject("pricing")?.let {
            it.optString("prompt") == "0" && it.optString("completion") == "0"
        } ?: false

    private fun parseToolCalls(jsonToolCalls: JSONArray?): List<ToolCall> {
        if (jsonToolCalls == null) return emptyList()
        return buildList {
            for (i in 0 until jsonToolCalls.length()) {
                val tc = jsonToolCalls.getJSONObject(i)
                val function = tc.getJSONObject("function")
                add(
                    ToolCall(
                        tc.getString("id"),
                        function.getString("name"),
                        parseArguments(function.getString("arguments"))
                    )
                )
            }
        }
    }

    private fun parseArguments(json: String): Map<String, Any> = try {
        val obj = JSONObject(json)
        buildMap {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, obj.get(key))
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }

    private fun httpError(statusCode: Int, responseBody: String? = null): AiProviderException {
        val normalizedBody = responseBody?.trim().orEmpty()
        val detail = parseProviderMessage(normalizedBody)
        val type = when (statusCode) {
            400 -> AiErrorType.INVALID_REQUEST
            401, 403 -> AiErrorType.AUTHENTICATION_FAILED
            402 -> AiErrorType.AUTHENTICATION_FAILED
            408 -> AiErrorType.TIMEOUT
            409 -> AiErrorType.PROVIDER_UNAVAILABLE
            429 -> AiErrorType.RATE_LIMITED
            500, 502, 503, 504 -> AiErrorType.PROVIDER_UNAVAILABLE
            else -> AiErrorType.NETWORK_ERROR
        }
        return AiProviderException(
            type,
            buildString {
                append("OpenRouter API error: ")
                append(statusCode)
                if (detail != null) append(" — ").append(detail)
            }
        )
    }

    private fun parseProviderMessage(body: String): String? = runCatching {
        val root = JSONObject(body)
        root.optJSONObject("error")?.let { error ->
            error.optString("message").takeIf { it.isNotBlank() }
                ?: error.optString("code").takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun providerStatus(error: AiProviderException): ProviderStatus =
        ProviderStatus(ProviderStatus.State.ERROR, error.message ?: error.type.name)

    private fun statusFromLastErrorOrReady(): ProviderStatus =
        status.value.takeIf { it.state == ProviderStatus.State.ERROR } ?: ProviderStatus.READY

    private companion object {
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build()
    }

    private class StreamToolCall(val id: String) {
        var name: String? = null
        val arguments = StringBuilder()
    }
}
