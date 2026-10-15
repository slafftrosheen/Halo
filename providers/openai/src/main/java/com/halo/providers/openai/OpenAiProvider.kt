package com.halo.providers.openai

import android.util.Base64
import com.halo.sdk.provider.AIMessage
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.awaitImagePayload
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiFailureDetails
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.imageMimeType
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.ContextOverflow
import com.halo.sdk.provider.ProviderReadiness
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.Recoverability
import com.halo.sdk.provider.TargetRejectionReason
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.asUntrustedPromptSection
import com.halo.sdk.provider.providerRetryAfterMs
import com.halo.sdk.provider.safeProviderErrorCode
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

/** Direct OpenAI inference. Selection still belongs exclusively to AiProviderRouter. */
class OpenAiProvider(
    private val storage: Storage,
    private val client: OkHttpClient = defaultClient(),
) : com.halo.sdk.provider.AiImageProvider, com.halo.sdk.provider.CredentialProvider, com.halo.sdk.provider.ProviderConfigurationReporting {

    override val configurationDescriptor: com.halo.sdk.provider.ProviderConfigurationDescriptor get() =
        com.halo.sdk.provider.ProviderConfigurationDescriptor(id, displayName, com.halo.sdk.provider.AiExecutionLocation.REMOTE,
            listOf(com.halo.sdk.provider.ProviderConfigurationField("credential", "API key", com.halo.sdk.provider.ProviderFieldKind.SECRET)), "OPENAI_CHAT_COMPLETIONS", "ACCOUNT_DEPENDENT")
    override val id = ID
    override val displayName = "OpenAI"
    override val isLocal = false

    private val _status = MutableStateFlow(ProviderStatus.UNCONFIGURED)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()

    override fun hasCredential(): Boolean = !apiKey.isNullOrBlank()
    override fun credential(): com.halo.sdk.provider.Credential? = apiKey?.let { com.halo.sdk.provider.Credential(id, it) }
    override fun setCredential(credential: com.halo.sdk.provider.Credential) {
        storage.putString("openai_api_key", credential.value.trim())

        _status.value = ProviderStatus(ProviderStatus.State.STANDBY, "Configured; not checked")
    }
    override fun clearCredential() {
        storage.remove("openai_api_key")

        _status.value = ProviderStatus.UNCONFIGURED
    }

    private val apiKey: String?
        get() = storage.getString(API_KEY_STORAGE)?.trim()?.ifBlank { null }

    override suspend fun getCapabilities(): Set<AiCapability> = setOf(
        AiCapability.TEXT,
        AiCapability.IMAGE,
        AiCapability.TOOLS,
    )

    override fun configurationChanged() {
        _status.value = if (hasCredential()) configuredCold() else ProviderStatus.UNCONFIGURED
    }
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
        execute(
            Request.Builder()
                .url("$API_BASE/models")
                .header("Authorization", "Bearer $key")
                .get()
                .build(),
        ) { body -> JSONObject(body).getJSONArray("data"); true }
    }

    override suspend fun listModels(): List<AiModel> = withContext(Dispatchers.IO) {
        val key = apiKey ?: return@withContext emptyList()
        execute(
            Request.Builder()
                .url("$API_BASE/models")
                .header("Authorization", "Bearer $key")
                .get()
                .build(),
        ) { body ->
            val data = JSONObject(body).getJSONArray("data")
            buildList {
                for (index in 0 until data.length()) {
                    val modelId = data.optJSONObject(index)?.optString("id")?.trim().orEmpty()
                    if (!isGenerationModel(modelId) || modelId.startsWith("gpt-image-") || modelId.contains("audio", true) || modelId.contains("realtime", true)) continue
                    add(
                        AiModel(
                            id = modelId,
                            displayName = modelId,
                            providerId = id,
                            inputCapabilities = buildSet {
                                add(AiCapability.TEXT)
                                if (supportsVision(modelId)) add(AiCapability.IMAGE)
                            },
                            capabilityEvidence = mapOf(AiCapability.IMAGE to com.halo.sdk.provider.CapabilityEvidence.HEURISTIC),
                            contextLength = null,
                            supportsStreaming = false,
                            supportsTools = supportsTools(modelId),
                        ),
                    )
                }
            }.sortedBy { it.displayName.lowercase() }
        }
    }

    override suspend fun listImageModels(): List<com.halo.sdk.provider.AiImageModel> = withContext(Dispatchers.IO) {
        val key = apiKey ?: return@withContext emptyList()
        execute(Request.Builder().url("$API_BASE/models").header("Authorization", "Bearer $key").get().build(), imageRequest = true) { body ->
            val data = JSONObject(body).getJSONArray("data")
            (0 until data.length()).mapNotNull { index ->
                data.optJSONObject(index)?.optString("id")?.takeIf { it.startsWith("gpt-image-") }
                    ?.let { com.halo.sdk.provider.AiImageModel(it) }
            }.distinctBy { it.id }.sortedBy { it.id }
        }
    }

    override suspend fun generateImage(request: com.halo.sdk.provider.AiImageRequest): com.halo.sdk.provider.AiImageResult = withContext(Dispatchers.IO) {
        val key = apiKey ?: throw missingKey()
        require(request.modelId.startsWith("gpt-image-") && request.prompt.isNotBlank())
        val reference = request.reference
        val body = if (reference == null) JSONObject().put("model", request.modelId).put("prompt", request.prompt)
            .put("n", 1).put("size", "1024x1024").put("output_format", "png").toString().toRequestBody(JSON)
        else okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("model", request.modelId).addFormDataPart("prompt", request.prompt)
            .addFormDataPart("n", "1").addFormDataPart("size", "1024x1024").addFormDataPart("output_format", "png")
            .addFormDataPart("image", "reference.${request.referenceMime.substringAfter('/')}", reference.toRequestBody(request.referenceMime.toMediaType())).build()
        execute(Request.Builder().url("$API_BASE/images/${if (reference == null) "generations" else "edits"}")
            .header("Authorization", "Bearer $key").post(body).build(), imageRequest = true) { response ->
            val encoded = JSONObject(response).optJSONArray("data")?.optJSONObject(0)?.optString("b64_json").orEmpty()
            if (encoded.isBlank() || encoded.length > 45_000_000) throw AiProviderException(AiErrorType.INVALID_RESPONSE, "No usable image was returned")
            com.halo.sdk.provider.AiImageResult(Base64.decode(encoded, Base64.DEFAULT), "image/png", id, request.modelId)
        }
    }

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = withContext(Dispatchers.IO) {
        val key = apiKey ?: throw missingKey()
        val model = request.modelId?.takeIf { it.isNotBlank() }
            ?: throw AiProviderException(AiErrorType.MODEL_NOT_SELECTED, "Select an OpenAI model first")
        val body = buildRequestBody(request, context, model)
            .toString()
            .toRequestBody(JSON)
        execute(
            Request.Builder()
                .url("$API_BASE/chat/completions")
                .header("Authorization", "Bearer $key")
                .header("Content-Type", "application/json")
                .post(body)
                .build(),
        ) { responseBody ->
            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
                ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenAI returned no choices")
            if (choices.length() == 0) {
                throw AiProviderException(AiErrorType.EMPTY_RESPONSE, "OpenAI returned an empty response")
            }
            val choice = choices.getJSONObject(0)
            val message = choice.getJSONObject("message")
            AiResponse(
                text = message.optString("content"),
                toolCalls = parseToolCalls(message.optJSONArray("tool_calls")),
                finishReason = choice.optString("finish_reason").takeIf { it.isNotBlank() && it != "null" },
                providerId = id,
                modelId = model,
                requestedModelId = model,
                resolvedModelId = json.optString("model", model),
                executionBackend = "OPENAI",
            )
        }
    }

    /** Buffered fallback keeps the provider operational without pretending token streaming. */
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

    private fun buildRequestBody(request: AiRequest, context: AiContext, model: String): JSONObject {
        val messages = JSONArray().put(
            JSONObject()
                .put("role", "system")
                .put("content", storage.getString("openai_system_prompt") ?: "You are Halo, a helpful AI assistant."),
        )
        context.conversation.forEach { messages.put(it.toJson()) }
        context.screenContext?.let { screen ->
            messages.put(JSONObject().put("role", "user").put("content", screen.asUntrustedPromptSection()))
        }
        request.text?.takeIf { request.appendTextAsUserMessage }?.let { text ->
            val content: Any = request.image?.let { image ->
                JSONArray()
                    .put(JSONObject().put("type", "text").put("text", text))
                    .put(
                        JSONObject().put("type", "image_url").put(
                            "image_url",
                            JSONObject().put(
                                "url",
                                "data:${request.imageMimeType()};base64,${Base64.encodeToString(image, Base64.NO_WRAP)}",
                            ),
                        ),
                    )
            } ?: text
            messages.put(JSONObject().put("role", "user").put("content", content))
        }
        return JSONObject()
            .put("model", model)
            .put("messages", messages)
            .apply {
                request.maxOutputTokens?.takeIf { it > 0 }?.let { put("max_completion_tokens", it) }
                if (request.toolUseMode != com.halo.sdk.provider.ToolUseMode.NONE && context.availableTools.isNotEmpty()) {
                    put("tools", JSONArray().apply {
                        context.availableTools.forEach { tool ->
                            put(
                                JSONObject().put("type", "function").put(
                                    "function",
                                    JSONObject()
                                        .put("name", tool.id)
                                        .put("description", tool.description)
                                        .put("parameters", JSONObject(tool.parameters)),
                                ),
                            )
                        }
                    })
                }
            }
    }

    private fun AIMessage.toJson(): JSONObject = JSONObject()
        .put("role", role.name.lowercase())
        .put("content", content)
        .apply {
            toolCallId?.let { put("tool_call_id", it) }
            if (toolCalls.isNotEmpty()) {
                put("tool_calls", JSONArray().apply {
                    toolCalls.forEach { call ->
                        put(
                            JSONObject()
                                .put("id", call.id)
                                .put("type", "function")
                                .put(
                                    "function",
                                    JSONObject()
                                        .put("name", call.functionName)
                                        .put("arguments", JSONObject(call.arguments).toString()),
                                ),
                        )
                    }
                })
            }
        }

    private fun parseToolCalls(calls: JSONArray?): List<ToolCall> = buildList {
        if (calls == null) return@buildList
        for (index in 0 until calls.length()) {
            val call = calls.getJSONObject(index)
            val function = call.getJSONObject("function")
            val arguments = JSONObject(function.optString("arguments", "{}"))
            add(
                ToolCall(
                    id = call.getString("id"),
                    functionName = function.getString("name"),
                    arguments = buildMap {
                        arguments.keys().forEach { key -> put(key, arguments.get(key)) }
                    },
                ),
            )
        }
    }

    private suspend fun <T> execute(request: Request, imageRequest: Boolean = false, read: (String) -> T): T {
        try {
            if (imageRequest) {
                val response = client.newBuilder().readTimeout(180, TimeUnit.SECONDS).callTimeout(180, TimeUnit.SECONDS).build().newCall(request).awaitImagePayload()
                if (response.code !in 200..299) throw httpError(response.code, response.body)
                val result = read(response.body)
                _status.value = ProviderStatus.READY
                return result
            }
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw httpError(response.code, body, response.header("Retry-After"))
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
            throw record(AiProviderException(AiErrorType.TIMEOUT, "OpenAI request timed out", timeout))
        } catch (offline: UnknownHostException) {
            throw record(AiProviderException(AiErrorType.NETWORK_UNAVAILABLE, "OpenAI is unreachable", offline))
        } catch (invalid: org.json.JSONException) {
            throw record(AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenAI returned invalid JSON", invalid))
        } catch (error: Exception) {
            throw record(AiProviderException(AiErrorType.NETWORK_ERROR, "OpenAI request failed", error))
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
        readiness = ProviderReadiness.Failed(error.message ?: "OpenAI request failed"),
        rejectionReason = if (error.type == AiErrorType.AUTHENTICATION_FAILED) {
            TargetRejectionReason.CONFIGURATION_MISSING
        } else null,
    )

    private fun httpError(code: Int, body: String, retryAfterHeader: String? = null): AiProviderException {
        val errorObject = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        val message = errorObject?.optString("message")?.takeIf { it.isNotBlank() }
        val providerCode = errorObject?.opt("code")?.toString()
        val type = when {
            code == 400 && ContextOverflow.matches(providerCode, message) -> AiErrorType.CONTEXT_TOO_LARGE
            code == 400 -> AiErrorType.INVALID_REQUEST
            code == 401 || code == 403 -> AiErrorType.AUTHENTICATION_FAILED
            code == 404 -> AiErrorType.MODEL_UNAVAILABLE
            code == 408 -> AiErrorType.TIMEOUT
            code == 429 -> AiErrorType.RATE_LIMITED
            code >= 500 -> AiErrorType.PROVIDER_UNAVAILABLE
            else -> AiErrorType.NETWORK_ERROR
        }
        return AiProviderException(
            type = type,
            message = "OpenAI API error $code",
            details = AiFailureDetails(
                providerCode = safeProviderErrorCode(providerCode),
                retryAfterMs = if (code == 429) providerRetryAfterMs(retryAfterHeader) else null,
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
        "OpenAI API key is not configured",
        AiFailureDetails(recoverability = Recoverability.USER_ACTION_REQUIRED),
    )

    private fun configuredCold() = ProviderStatus(
        ProviderStatus.State.STANDBY,
        "Configured; check connection to verify access",
        ProviderReadiness.ConfiguredCold,
    )

    private companion object {
        const val ID = "openai"
        const val API_KEY_STORAGE = "openai_api_key"
        const val API_BASE = "https://api.openai.com/v1"
        val JSON = "application/json".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        fun isGenerationModel(id: String): Boolean = listOf(
            "gpt-",
            "o1",
            "o3",
            "o4",
            "chatgpt-",
        ).any(id::startsWith)

        fun supportsVision(id: String): Boolean = listOf(
            "gpt-4o",
            "gpt-4.1",
            "gpt-5",
            "o3",
            "o4",
        ).any(id::startsWith)

        fun supportsTools(id: String): Boolean = listOf(
            "gpt-4",
            "gpt-5",
            "o3",
            "o4",
        ).any(id::startsWith)
    }
}
