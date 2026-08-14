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
import android.util.Log

class OpenRouterProvider(
    private val storage: Storage,
    private val client: OkHttpClient = OkHttpClient()
) : AiProvider {

    override val id: String = "openrouter"
    override val displayName: String = "OpenRouter"
    override val isLocal: Boolean = false

    private val _status = MutableStateFlow(ProviderStatus.UNCONFIGURED)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()

    private val apiKey: String? get() = storage.getString("openrouter_api_key")
    private val selectedModelId: String get() = storage.getString("openrouter_model") ?: "openrouter/auto"

    override suspend fun getStatus(): ProviderStatus {
        val current = if (apiKey.isNullOrBlank()) {
            ProviderStatus.UNCONFIGURED
        } else {
            ProviderStatus.READY
        }
        _status.value = current
        return current
    }

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        val key = apiKey ?: return@withContext false
        val httpRequest = Request.Builder()
            .url("https://openrouter.ai/api/v1/auth/key")
            .header("Authorization", "Bearer $key")
            .get()
            .build()
        try {
            client.newCall(httpRequest).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getCapabilities(): Set<AiCapability> = setOf(
        AiCapability.TEXT,
        AiCapability.IMAGE,
        AiCapability.STREAMING,
        AiCapability.TOOLS
    )

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = withContext(Dispatchers.IO) {
        val key = apiKey ?: throw AiProviderException(AiErrorType.AUTHENTICATION_FAILED, "OpenRouter API key not configured")
        
        val modelId = request.modelId ?: selectedModelId
        val actualModel = resolveModel(modelId)
        
        val json = buildRequestBody(request, context, actualModel, stream = false)
        val body = json.toString().toRequestBody("application/json".toMediaType())
        
        val httpRequest = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("X-OpenRouter-Title", "Halo")
            .post(body)
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorType = when (response.code) {
                        401, 403 -> AiErrorType.AUTHENTICATION_FAILED
                        429 -> AiErrorType.RATE_LIMITED
                        500, 502, 503, 504 -> AiErrorType.PROVIDER_UNAVAILABLE
                        else -> AiErrorType.UNKNOWN_ERROR
                    }
                    throw AiProviderException(errorType, "OpenRouter API error: ${response.code}")
                }
                
                val responseBody = response.body?.string() ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Empty response")
            val responseJson = JSONObject(responseBody)
            val choices = responseJson.getJSONArray("choices")
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            val content = message.optString("content")
            
            // Tool calls
            val toolCalls = mutableListOf<ToolCall>()
            val jsonToolCalls = message.optJSONArray("tool_calls")
            if (jsonToolCalls != null) {
                for (i in 0 until jsonToolCalls.length()) {
                    val tc = jsonToolCalls.getJSONObject(i)
                    val function = tc.getJSONObject("function")
                    toolCalls.add(
                        ToolCall(
                            id = tc.getString("id"),
                            functionName = function.getString("name"),
                            arguments = parseArguments(function.getString("arguments"))
                        )
                    )
                }
            }

            AiResponse(
                text = content,
                toolCalls = toolCalls,
                finishReason = firstChoice.optString("finish_reason"),
                providerId = id,
                modelId = responseJson.optString("model", actualModel)
            )
        }
        } catch (e: java.net.SocketTimeoutException) {
            throw AiProviderException(AiErrorType.TIMEOUT, "Request timed out", e)
        } catch (e: java.net.UnknownHostException) {
            throw AiProviderException(AiErrorType.NETWORK_UNAVAILABLE, "Network unavailable", e)
        } catch (e: org.json.JSONException) {
            throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Failed to parse JSON response", e)
        }
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
        val key = apiKey ?: throw AiProviderException(AiErrorType.AUTHENTICATION_FAILED, "OpenRouter API key not configured")
        emit(AiStreamEvent.Started)
        
        val modelId = request.modelId ?: selectedModelId
        val actualModel = resolveModel(modelId)
        val json = buildRequestBody(request, context, actualModel, stream = true)
        val body = json.toString().toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("X-OpenRouter-Title", "Halo")
            .post(body)
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) throw RuntimeException("Stream failed: ${response.code}")
                
                val source = response.body?.source() ?: throw RuntimeException("No body")
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6)
                        if (data == "[DONE]") break
                        
                        val chunk = JSONObject(data)
                        val choices = chunk.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).getJSONObject("delta")
                            val text = delta.optString("content")
                            if (text.isNotEmpty()) {
                                emit(AiStreamEvent.Token(text))
                            }
                            
                            val toolCalls = delta.optJSONArray("tool_calls")
                            if (toolCalls != null) {
                                for (i in 0 until toolCalls.length()) {
                                    val tc = toolCalls.getJSONObject(i)
                                    val func = tc.optJSONObject("function")
                                    emit(AiStreamEvent.ToolCallDelta(
                                        index = tc.getInt("index"),
                                        name = func?.optString("name"),
                                        argumentsDelta = func?.optString("arguments")
                                    ))
                                }
                            }
                        }
                    }
                }
            }
            // Logic for completing the stream would go here (assembling the full response)
        } catch (e: Exception) {
            emit(AiStreamEvent.Failed(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): List<AiModel> = withContext(Dispatchers.IO) {
        val key = apiKey ?: return@withContext emptyList()

        val httpRequest = Request.Builder()
            .url("https://openrouter.ai/api/v1/models")
            .header("Authorization", "Bearer $key")
            .get()
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                
                val responseBody = response.body?.string() ?: return@withContext emptyList()
                val responseJson = JSONObject(responseBody)
                val data = responseJson.getJSONArray("data")
                val models = mutableListOf<AiModel>()
                
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val pricing = item.optJSONObject("pricing")
                    val isFree = pricing?.optString("prompt") == "0" && pricing.optString("completion") == "0"
                    
                    models.add(
                        AiModel(
                            id = item.getString("id"),
                            displayName = item.getString("name"),
                            providerId = id,
                            inputCapabilities = mapCapabilities(item),
                            contextLength = item.optLong("context_length"),
                            supportsStreaming = true, // OpenRouter generally supports streaming
                            supportsTools = item.optJSONObject("architecture")?.optBoolean("instruct_type") ?: false,
                            isFree = isFree
                        )
                    )
                }
                models
            }
        } catch (e: Exception) {
            Log.e("OpenRouter", "Failed to list models", e)
            emptyList()
        }
    }

    private suspend fun resolveModel(modelId: String): String {
        if (modelId != "free") return modelId
        
        val models = listModels()
        return models.firstOrNull { it.isFree }?.id ?: "openrouter/auto"
    }

    private fun buildRequestBody(request: AiRequest, context: AiContext, model: String, stream: Boolean): JSONObject {
        val messages = JSONArray()
        
        // System prompt from storage
        val systemPrompt = storage.getString("openrouter_system_prompt") ?: "You are Halo, a helpful AI assistant."
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        // Conversation history
        context.conversation.forEach { msg ->
            messages.put(JSONObject().apply {
                put("role", msg.role.name.lowercase())
                put("content", msg.content)
            })
        }

        // Current user input
        val text = request.text
        if (text != null) {
            val userContent: Any = if (request.image != null) {
                val array = JSONArray()
                array.put(JSONObject().apply {
                    put("type", "text")
                    put("text", text)
                })
                array.put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,${android.util.Base64.encodeToString(request.image, android.util.Base64.NO_WRAP)}")
                    })
                })
                array
            } else {
                text
            }
            
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userContent)
            })
        }

        return JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("stream", stream)
            
            // Tools
            if (context.availableTools.isNotEmpty()) {
                val tools = JSONArray()
                context.availableTools.forEach { tool ->
                    tools.put(JSONObject().apply {
                        put("type", "function")
                        put("function", JSONObject().apply {
                            put("name", tool.id)
                            put("description", tool.description)
                            // Map parameters if needed
                        })
                    })
                }
                put("tools", tools)
            }
        }
    }

    private fun mapCapabilities(modelJson: JSONObject): Set<AiCapability> {
        val caps = mutableSetOf(AiCapability.TEXT)
        // Extract vision/audio/etc from description or metadata if available
        // OpenRouter's /models doesn't have a clean modality flag in the data root, 
        // but often mentions it in description or pricing fields.
        return caps
    }

    private fun parseArguments(json: String): Map<String, Any> {
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, Any>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.get(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
