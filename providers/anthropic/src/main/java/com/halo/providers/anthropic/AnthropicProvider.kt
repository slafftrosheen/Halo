package com.halo.providers.anthropic

import android.util.Base64
import com.halo.sdk.provider.AIMessage
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiFailureDetails
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.ContextOverflow
import com.halo.sdk.provider.ProviderReadiness
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.Recoverability
import com.halo.sdk.provider.Role
import com.halo.sdk.provider.TargetRejectionReason
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.asUntrustedPromptSection
import com.halo.sdk.runtime.Storage
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Native Anthropic Messages API adapter. Provider/model selection remains router-owned. */
class AnthropicProvider(
    private val storage: Storage,
    private val client: OkHttpClient = defaultClient(),
) : AiInferenceProvider {
    override val id = ID
    override val displayName = "Anthropic"
    override val isLocal = false

    private val _status = MutableStateFlow(ProviderStatus.UNCONFIGURED)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()

    private val apiKey: String?
        get() = storage.getString(API_KEY_STORAGE)?.trim()?.ifBlank { null }

    override suspend fun getCapabilities(): Set<AiCapability> = setOf(
        AiCapability.TEXT,
        AiCapability.IMAGE,
        AiCapability.TOOLS,
    )

    override suspend fun getStatus(): ProviderStatus {
        val value = if (apiKey == null) ProviderStatus.UNCONFIGURED else _status.value.let {
            if (it.state == ProviderStatus.State.UNCONFIGURED) configuredCold() else it
        }
        _status.value = value
        return value
    }

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        val key = apiKey ?: run {
            _status.value = ProviderStatus.UNCONFIGURED
            return@withContext false
        }
        execute(modelsRequest(key)) { _ -> true }
    }

    override suspend fun listModels(): List<AiModel> = withContext(Dispatchers.IO) {
        val key = apiKey ?: return@withContext emptyList()
        execute(modelsRequest(key)) { body ->
            val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
            buildList {
                for (index in 0 until data.length()) {
                    val item = data.optJSONObject(index) ?: continue
                    val modelId = item.optString("id").trim()
                    if (!modelId.startsWith("claude-")) continue
                    add(
                        AiModel(
                            id = modelId,
                            displayName = item.optString("display_name", modelId),
                            providerId = id,
                            inputCapabilities = setOf(AiCapability.TEXT, AiCapability.IMAGE),
                            contextLength = null,
                            supportsStreaming = false,
                            supportsTools = true,
                        ),
                    )
                }
            }.sortedBy { it.displayName.lowercase() }
        }
    }

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = withContext(Dispatchers.IO) {
        val key = apiKey ?: throw missingKey()
        val model = request.modelId?.takeIf { it.isNotBlank() }
            ?: throw AiProviderException(AiErrorType.MODEL_NOT_SELECTED, "Select an Anthropic model first")
        val body = buildRequestBody(request, context, model).toString().toRequestBody(JSON)
        execute(
            Request.Builder()
                .url("$API_BASE/messages")
                .anthropicHeaders(key)
                .post(body)
                .build(),
        ) { responseBody -> parseResponse(responseBody, model) }
    }

    /** Buffered completion is intentional until token SSE is implemented and advertised. */
    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
        emit(AiStreamEvent.Started)
        try {
            val response = generate(request, context)
            if (response.text.isNotEmpty()) emit(AiStreamEvent.Token(response.text))
            emit(AiStreamEvent.Completed(response))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            emit(AiStreamEvent.Failed(error))
        }
    }.flowOn(Dispatchers.IO)

    private fun modelsRequest(key: String): Request = Request.Builder()
        .url("$API_BASE/models?limit=100")
        .anthropicHeaders(key)
        .get()
        .build()

    private fun Request.Builder.anthropicHeaders(key: String): Request.Builder =
        header("x-api-key", key)
            .header("anthropic-version", API_VERSION)
            .header("content-type", "application/json")

    private fun buildRequestBody(request: AiRequest, context: AiContext, model: String): JSONObject {
        val system = buildList {
            add(storage.getString("anthropic_system_prompt") ?: "You are Halo, a helpful AI assistant.")
            context.conversation.filter { it.role == Role.SYSTEM }.mapTo(this) { it.content }
        }.filter { it.isNotBlank() }.joinToString("\n\n")

        val messages = JSONArray()
        context.conversation.filterNot { it.role == Role.SYSTEM }.forEach { message ->
            messages.put(message.toAnthropicJson())
        }
        context.screenContext?.let { screen ->
            messages.put(JSONObject().put("role", "user").put("content", screen.asUntrustedPromptSection()))
        }
        request.text?.takeIf { request.appendTextAsUserMessage }?.let { text ->
            val content: Any = request.image?.let { image ->
                JSONArray()
                    .put(
                        JSONObject()
                            .put("type", "image")
                            .put(
                                "source",
                                JSONObject()
                                    .put("type", "base64")
                                    .put("media_type", request.mimeTypes.firstOrNull() ?: "image/jpeg")
                                    .put("data", Base64.encodeToString(image, Base64.NO_WRAP)),
                            ),
                    )
                    .put(JSONObject().put("type", "text").put("text", text))
            } ?: text
            messages.put(JSONObject().put("role", "user").put("content", content))
        }

        return JSONObject()
            .put("model", model)
            .put("system", system)
            .put("max_tokens", request.maxOutputTokens?.coerceAtLeast(1) ?: DEFAULT_MAX_TOKENS)
            .put("messages", messages)
            .apply {
                if (context.availableTools.isNotEmpty()) {
                    put("tools", JSONArray().apply {
                        context.availableTools.forEach { tool ->
                            put(
                                JSONObject()
                                    .put("name", tool.id)
                                    .put("description", tool.description)
                                    .put("input_schema", JSONObject(tool.parameters)),
                            )
                        }
                    })
                }
            }
    }

    private fun AIMessage.toAnthropicJson(): JSONObject {
        if (role == Role.TOOL) {
            val resultFor = toolCallId?.takeIf { it.isNotBlank() }
                ?: throw AiProviderException(
                    AiErrorType.INVALID_REQUEST,
                    "Anthropic tool result is missing its tool-use identity",
                )
            return JSONObject().put("role", "user").put(
                "content",
                JSONArray().put(
                    JSONObject()
                        .put("type", "tool_result")
                        .put("tool_use_id", resultFor)
                        .put("content", content),
                ),
            )
        }
        if (role == Role.ASSISTANT && toolCalls.isNotEmpty()) {
            val blocks = JSONArray()
            if (content.isNotBlank()) blocks.put(JSONObject().put("type", "text").put("text", content))
            toolCalls.forEach { call ->
                blocks.put(
                    JSONObject()
                        .put("type", "tool_use")
                        .put("id", call.id)
                        .put("name", call.functionName)
                        .put("input", JSONObject(call.arguments)),
                )
            }
            return JSONObject().put("role", "assistant").put("content", blocks)
        }
        return JSONObject()
            .put("role", if (role == Role.ASSISTANT) "assistant" else "user")
            .put("content", content)
    }

    private fun parseResponse(body: String, requestedModel: String): AiResponse {
        val json = JSONObject(body)
        val content = json.optJSONArray("content")
            ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Anthropic returned no content")
        val text = StringBuilder()
        val toolCalls = mutableListOf<ToolCall>()
        for (index in 0 until content.length()) {
            val block = content.optJSONObject(index) ?: continue
            when (block.optString("type")) {
                "text" -> text.append(block.optString("text"))
                "tool_use" -> {
                    val input = block.optJSONObject("input") ?: JSONObject()
                    toolCalls += ToolCall(
                        id = block.getString("id"),
                        functionName = block.getString("name"),
                        arguments = input.toMap(),
                    )
                }
            }
        }
        if (text.isEmpty() && toolCalls.isEmpty()) {
            throw AiProviderException(AiErrorType.EMPTY_RESPONSE, "Anthropic returned an empty response")
        }
        return AiResponse(
            text = text.toString(),
            toolCalls = toolCalls,
            finishReason = json.optString("stop_reason").takeIf { it.isNotBlank() && it != "null" },
            providerId = id,
            modelId = requestedModel,
            requestedModelId = requestedModel,
            resolvedModelId = json.optString("model", requestedModel),
            executionBackend = "ANTHROPIC",
        )
    }

    private fun JSONObject.toMap(): Map<String, Any> = buildMap {
        keys().forEach { key -> put(key, get(key)) }
    }

    private suspend fun <T> execute(request: Request, read: (String) -> T): T {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw httpError(response.code, body)
                val result = read(body)
                _status.value = ProviderStatus.READY
                return result
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (known: AiProviderException) {
            _status.value = statusFor(known)
            throw known
        } catch (timeout: SocketTimeoutException) {
            throw record(AiProviderException(AiErrorType.TIMEOUT, "Anthropic request timed out", timeout))
        } catch (offline: UnknownHostException) {
            throw record(AiProviderException(AiErrorType.NETWORK_UNAVAILABLE, "Anthropic is unreachable", offline))
        } catch (invalid: org.json.JSONException) {
            throw record(AiProviderException(AiErrorType.INVALID_RESPONSE, "Anthropic returned invalid JSON", invalid))
        } catch (error: Exception) {
            throw record(AiProviderException(AiErrorType.NETWORK_ERROR, "Anthropic request failed", error))
        }
    }

    private fun record(error: AiProviderException): AiProviderException {
        _status.value = statusFor(error)
        return error
    }

    private fun statusFor(error: AiProviderException): ProviderStatus = ProviderStatus(
        state = when (error.type) {
            AiErrorType.NETWORK_UNAVAILABLE, AiErrorType.TIMEOUT -> ProviderStatus.State.OFFLINE
            else -> ProviderStatus.State.ERROR
        },
        message = error.message,
        readiness = ProviderReadiness.Failed(error.message ?: "Anthropic request failed"),
        rejectionReason = if (error.type == AiErrorType.AUTHENTICATION_FAILED) {
            TargetRejectionReason.CONFIGURATION_MISSING
        } else null,
    )

    private fun httpError(code: Int, body: String): AiProviderException {
        val error = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        val message = error?.optString("message")?.takeIf { it.isNotBlank() }
        val providerCode = error?.optString("type")?.takeIf { it.isNotBlank() }
        val type = when {
            code == 400 && ContextOverflow.matches(providerCode, message) -> AiErrorType.CONTEXT_TOO_LARGE
            code == 400 -> AiErrorType.INVALID_REQUEST
            code == 401 || code == 403 -> AiErrorType.AUTHENTICATION_FAILED
            code == 404 -> AiErrorType.MODEL_UNAVAILABLE
            code == 408 -> AiErrorType.TIMEOUT
            code == 429 -> AiErrorType.RATE_LIMITED
            code == 529 || code >= 500 -> AiErrorType.PROVIDER_UNAVAILABLE
            else -> AiErrorType.NETWORK_ERROR
        }
        return AiProviderException(
            type = type,
            message = "Anthropic API error $code${message?.let { ": $it" }.orEmpty()}",
            details = AiFailureDetails(
                providerCode = providerCode,
                httpStatus = code,
                recoverability = when (type) {
                    AiErrorType.AUTHENTICATION_FAILED -> Recoverability.USER_ACTION_REQUIRED
                    AiErrorType.RATE_LIMITED -> Recoverability.RETRY_AFTER
                    AiErrorType.PROVIDER_UNAVAILABLE, AiErrorType.TIMEOUT -> Recoverability.RETRY_IMMEDIATELY
                    else -> Recoverability.PERMANENT
                },
            ),
        )
    }

    private fun missingKey() = AiProviderException(
        AiErrorType.AUTHENTICATION_FAILED,
        "Anthropic API key is not configured",
        AiFailureDetails(recoverability = Recoverability.USER_ACTION_REQUIRED),
    )

    private fun configuredCold() = ProviderStatus(
        ProviderStatus.State.STANDBY,
        "Configured; check connection to verify access",
        ProviderReadiness.ConfiguredCold,
    )

    private companion object {
        const val ID = "anthropic"
        const val API_KEY_STORAGE = "anthropic_api_key"
        const val API_BASE = "https://api.anthropic.com/v1"
        const val API_VERSION = "2023-06-01"
        const val DEFAULT_MAX_TOKENS = 1024
        val JSON = "application/json".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
