package com.halo.providers.gemini

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
import com.halo.sdk.provider.Role
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

enum class GeminiAuthenticationMode { OAUTH, API_KEY, NONE }

/** Gemini GenerateContent adapter. OAuth tokens are intentionally never persisted. */
class GeminiProvider(
    private val storage: Storage,
    private val client: OkHttpClient = defaultClient(),
) : com.halo.sdk.provider.AiImageProvider, com.halo.sdk.provider.CredentialProvider, com.halo.sdk.provider.ProviderConfigurationReporting {

    override val configurationDescriptor: com.halo.sdk.provider.ProviderConfigurationDescriptor get() =
        com.halo.sdk.provider.ProviderConfigurationDescriptor(id, displayName, com.halo.sdk.provider.AiExecutionLocation.REMOTE,
            listOf(com.halo.sdk.provider.ProviderConfigurationField("credential", "API key", com.halo.sdk.provider.ProviderFieldKind.SECRET), com.halo.sdk.provider.ProviderConfigurationField("gemini_auth_mode", "Authentication", com.halo.sdk.provider.ProviderFieldKind.ENUM, listOf("API_KEY", "OAUTH")), com.halo.sdk.provider.ProviderConfigurationField("gemini_project_id", "Google Cloud project ID", com.halo.sdk.provider.ProviderFieldKind.TEXT), com.halo.sdk.provider.ProviderConfigurationField("google_oauth", "Google account", com.halo.sdk.provider.ProviderFieldKind.OAUTH_ACTION)), "GEMINI_GENERATE_CONTENT", "ACCOUNT_DEPENDENT")
    override val id = ID
    override val displayName = "Google Gemini"
    override val isLocal = false

    private val _status = MutableStateFlow(ProviderStatus.UNCONFIGURED)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()

    @Volatile private var oauthAccessToken: String? = null
    @Volatile private var oauthProjectId: String? = null

    val authenticationMode: GeminiAuthenticationMode
        get() = when (storage.getString("gemini_auth_mode")) {
            "OAUTH" -> if (!oauthAccessToken.isNullOrBlank() && !projectId.isNullOrBlank()) GeminiAuthenticationMode.OAUTH else GeminiAuthenticationMode.NONE
            "API_KEY", null -> if (!apiKey.isNullOrBlank()) GeminiAuthenticationMode.API_KEY else GeminiAuthenticationMode.NONE
            else -> GeminiAuthenticationMode.NONE
        }

    override fun hasCredential(): Boolean = !apiKey.isNullOrBlank()
    override fun credential(): com.halo.sdk.provider.Credential? = apiKey?.let { com.halo.sdk.provider.Credential(id, it) }
    override fun setCredential(credential: com.halo.sdk.provider.Credential) {
        storage.putString("gemini_api_key", credential.value.trim())
        clearOAuthSession()
        _status.value = ProviderStatus(ProviderStatus.State.STANDBY, "Configured; not checked")
    }
    override fun clearCredential() {
        storage.remove("gemini_api_key")
        clearOAuthSession()
        _status.value = ProviderStatus.UNCONFIGURED
    }

    private val apiKey: String?
        get() = storage.getString(API_KEY_STORAGE)?.trim()?.ifBlank { null }
    private val projectId: String?
        get() = oauthProjectId ?: storage.getString(PROJECT_ID_STORAGE)?.trim()?.ifBlank { null }

    fun acceptOAuthAccessToken(token: String, projectId: String) {
        storage.putString("gemini_auth_mode", "OAUTH")
        oauthAccessToken = token.trim().ifBlank { null }
        oauthProjectId = projectId.trim().ifBlank { null }
        _status.value = if (authenticationMode == GeminiAuthenticationMode.NONE) {
            ProviderStatus.UNCONFIGURED
        } else configuredCold()
    }

    fun clearOAuthSession() {
        storage.putString("gemini_auth_mode", "API_KEY")
        oauthAccessToken = null
        oauthProjectId = null
        _status.value = if (apiKey == null) ProviderStatus.UNCONFIGURED else configuredCold()
    }

    override suspend fun getCapabilities(): Set<AiCapability> = setOf(
        AiCapability.TEXT,
        AiCapability.IMAGE,
        AiCapability.TOOLS,
    )

    override fun configurationChanged() {
        _status.value = if (authenticationMode != GeminiAuthenticationMode.NONE) configuredCold() else ProviderStatus.UNCONFIGURED
    }
    override suspend fun getStatus(): ProviderStatus {
        val current = if (authenticationMode == GeminiAuthenticationMode.NONE) {
            ProviderStatus.UNCONFIGURED
        } else if (_status.value.state == ProviderStatus.State.UNCONFIGURED) {
            configuredCold()
        } else {
            _status.value
        }
        _status.value = current
        return current
    }

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        execute(authenticatedRequest("$API_BASE/models?pageSize=100").get().build()) { body -> JSONObject(body).getJSONArray("models"); true }
    }

    private suspend fun modelCatalogue(imageMetadata: Boolean = false): List<JSONObject> {
        if (authenticationMode == GeminiAuthenticationMode.NONE) return emptyList()
        val result = mutableListOf<JSONObject>()
        val seen = mutableSetOf<String>()
        var page: String? = null
        do {
            val url = "$API_BASE/models".toHttpUrl().newBuilder().addQueryParameter("pageSize", "100")
                .apply { page?.let { addQueryParameter("pageToken", it) } }.build()
            val response = execute(authenticatedRequest(url.toString()).get().build(), imageRequest = imageMetadata) { JSONObject(it) }
            val models = response.getJSONArray("models")
            (0 until models.length()).forEach { index -> models.optJSONObject(index)?.let { result += it } }
            page = response.optString("nextPageToken").takeIf { it.isNotBlank() }
            if (page != null) check(seen.add(page!!) && seen.size <= 30) { "Model catalogue pagination did not finish" }
        } while (page != null)
        return result
    }

    override suspend fun listModels(): List<AiModel> = withContext(Dispatchers.IO) {
        modelCatalogue().mapNotNull { item ->
            val methods = item.optJSONArray("supportedGenerationMethods") ?: JSONArray()
            if ((0 until methods.length()).none { methods.optString(it) == "generateContent" }) return@mapNotNull null
            val modelId = item.optString("name").removePrefix("models/").trim()
            if (!modelId.startsWith("gemini-") || modelId.contains("image", true) || modelId.contains("tts", true)) return@mapNotNull null
            AiModel(modelId, item.optString("displayName", modelId), id, setOf(AiCapability.TEXT, AiCapability.IMAGE),
                item.optLong("inputTokenLimit").takeIf { it > 0L }, supportsStreaming = false, supportsTools = true,
                capabilityEvidence = mapOf(AiCapability.IMAGE to com.halo.sdk.provider.CapabilityEvidence.HEURISTIC))
        }.distinctBy { it.id }.sortedBy { it.displayName.lowercase() }
    }

    override suspend fun listImageModels(): List<com.halo.sdk.provider.AiImageModel> = withContext(Dispatchers.IO) {
        modelCatalogue(imageMetadata = true).mapNotNull { item ->
            val modelId = item.optString("name").removePrefix("models/")
            val methods = item.optJSONArray("supportedGenerationMethods") ?: JSONArray()
            if (modelId.startsWith("gemini-") && modelId.contains("image", true) &&
                (0 until methods.length()).any { methods.optString(it) == "generateContent" })
                com.halo.sdk.provider.AiImageModel(modelId, item.optString("displayName", modelId)) else null
        }.distinctBy { it.id }
    }

    override suspend fun generateImage(request: com.halo.sdk.provider.AiImageRequest): com.halo.sdk.provider.AiImageResult = withContext(Dispatchers.IO) {
        require(request.modelId.contains("image", true) && request.prompt.isNotBlank())
        val parts = JSONArray().put(JSONObject().put("text", request.prompt))
        request.reference?.let { bytes -> parts.put(JSONObject().put("inlineData", JSONObject()
            .put("mimeType", request.referenceMime).put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)))) }
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE")))
        execute(authenticatedRequest("$API_BASE/models/${request.modelId}:generateContent").post(body.toString().toRequestBody(JSON)).build(), imageRequest = true) { response ->
            val output = JSONObject(response).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            val image = (0 until output.length()).firstNotNullOfOrNull { index ->
                output.optJSONObject(index)?.let { it.optJSONObject("inlineData") ?: it.optJSONObject("inline_data") }
            } ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "No image was returned; try another prompt or image model")
            val encoded = image.optString("data")
            require(encoded.isNotBlank() && encoded.length <= 45_000_000) { "Image response was empty or too large" }
            com.halo.sdk.provider.AiImageResult(Base64.decode(encoded, Base64.DEFAULT), image.optString("mimeType", image.optString("mime_type", "image/png")), id, request.modelId)
        }
    }

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = withContext(Dispatchers.IO) {
        val model = request.modelId?.takeIf { it.isNotBlank() }
            ?: throw AiProviderException(AiErrorType.MODEL_NOT_SELECTED, "Select a Gemini model first")
        val body = buildRequestBody(request, context).toString().toRequestBody(JSON)
        val url = "$API_BASE/models/${model.removePrefix("models/")}:generateContent"
        execute(authenticatedRequest(url).post(body).build()) { response -> parseResponse(response, model) }
    }

    /** Buffered completion keeps capabilities honest until Gemini SSE is implemented. */
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

    private fun authenticatedRequest(url: String): Request.Builder {
        val builder = Request.Builder().url(url).header("content-type", "application/json")
        return when (authenticationMode) {
            GeminiAuthenticationMode.OAUTH -> builder
                .header("Authorization", "Bearer ${oauthAccessToken.orEmpty()}")
                .header("x-goog-user-project", projectId.orEmpty())
            GeminiAuthenticationMode.API_KEY -> builder.header("x-goog-api-key", apiKey.orEmpty())
            GeminiAuthenticationMode.NONE -> throw missingCredential()
        }
    }

    private fun buildRequestBody(request: AiRequest, context: AiContext): JSONObject {
        val toolNames = context.conversation
            .flatMap(AIMessage::toolCalls)
            .associate { it.id to it.functionName }
        val contents = JSONArray()
        context.conversation.filterNot { it.role == Role.SYSTEM }.forEach { message ->
            contents.put(message.toGeminiContent(toolNames))
        }
        context.screenContext?.let { screen -> contents.put(textContent("user", screen.asUntrustedPromptSection())) }
        request.text?.takeIf { request.appendTextAsUserMessage }?.let { text ->
            val parts = JSONArray()
            request.image?.let { image ->
                parts.put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", request.imageMimeType())
                            .put("data", Base64.encodeToString(image, Base64.NO_WRAP)),
                    ),
                )
            }
            parts.put(JSONObject().put("text", text))
            contents.put(JSONObject().put("role", "user").put("parts", parts))
        }

        val systemText = buildList {
            add(storage.getString("gemini_system_prompt") ?: "You are Halo, a helpful AI assistant.")
            context.conversation.filter { it.role == Role.SYSTEM }.mapTo(this) { it.content }
        }.filter { it.isNotBlank() }.joinToString("\n\n")

        return JSONObject()
            .put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemText))))
            .apply {
                request.maxOutputTokens?.takeIf { it > 0 }?.let { maxTokens ->
                    put("generationConfig", JSONObject().put("maxOutputTokens", maxTokens))
                }
                if (request.toolUseMode != com.halo.sdk.provider.ToolUseMode.NONE && context.availableTools.isNotEmpty()) {
                    put(
                        "tools",
                        JSONArray().put(
                            JSONObject().put("functionDeclarations", JSONArray().apply {
                                context.availableTools.forEach { tool ->
                                    put(
                                        JSONObject()
                                            .put("name", tool.id)
                                            .put("description", tool.description)
                                            .put("parameters", JSONObject(tool.parameters)),
                                    )
                                }
                            }),
                        ),
                    )
                }
            }
    }

    private fun AIMessage.toGeminiContent(toolNames: Map<String, String>): JSONObject {
        if (role == Role.TOOL) {
            val callId = toolCallId?.takeIf { it.isNotBlank() }
                ?: throw AiProviderException(AiErrorType.INVALID_REQUEST, "Gemini tool result is missing its call identity")
            val functionName = toolNames[callId]
                ?: throw AiProviderException(AiErrorType.INVALID_REQUEST, "Gemini tool result has no matching function call")
            val response = runCatching { JSONObject(content) }
                .getOrElse { JSONObject().put("result", content) }
            return JSONObject().put("role", "user").put(
                "parts",
                JSONArray().put(
                    JSONObject().put(
                        "functionResponse",
                        JSONObject()
                            .put("id", callId)
                            .put("name", functionName)
                            .put("response", response),
                    ),
                ),
            )
        }
        val parts = JSONArray()
        if (content.isNotBlank()) parts.put(JSONObject().put("text", content))
        if (role == Role.ASSISTANT) {
            toolCalls.forEach { call ->
                parts.put(
                    JSONObject().put(
                        "functionCall",
                        JSONObject()
                            .put("id", call.id)
                            .put("name", call.functionName)
                            .put("args", JSONObject(call.arguments)),
                    ),
                )
            }
        }
        return JSONObject()
            .put("role", if (role == Role.ASSISTANT) "model" else "user")
            .put("parts", parts)
    }

    private fun textContent(role: String, text: String): JSONObject = JSONObject()
        .put("role", role)
        .put("parts", JSONArray().put(JSONObject().put("text", text)))

    private fun parseResponse(body: String, requestedModel: String): AiResponse {
        val json = JSONObject(body)
        val candidates = json.optJSONArray("candidates")
            ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Gemini returned no candidates")
        if (candidates.length() == 0) {
            val reason = json.optJSONObject("promptFeedback")?.optString("blockReason")
            throw AiProviderException(AiErrorType.EMPTY_RESPONSE, "Gemini returned no candidate${reason?.let { ": $it" }.orEmpty()}")
        }
        val candidate = candidates.getJSONObject(0)
        val parts = candidate.getJSONObject("content").optJSONArray("parts") ?: JSONArray()
        val text = StringBuilder()
        val toolCalls = mutableListOf<ToolCall>()
        for (index in 0 until parts.length()) {
            val part = parts.optJSONObject(index) ?: continue
            part.optString("text").takeIf { it.isNotEmpty() }?.let(text::append)
            part.optJSONObject("functionCall")?.let { call ->
                val callId = call.optString("id").takeIf { it.isNotBlank() }
                    ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Gemini returned a function call without an identity")
                toolCalls += ToolCall(
                    id = callId,
                    functionName = call.getString("name"),
                    arguments = (call.optJSONObject("args") ?: JSONObject()).toMap(),
                )
            }
        }
        if (text.isEmpty() && toolCalls.isEmpty()) {
            throw AiProviderException(AiErrorType.EMPTY_RESPONSE, "Gemini returned an empty response")
        }
        return AiResponse(
            text = text.toString(),
            toolCalls = toolCalls,
            finishReason = candidate.optString("finishReason").takeIf { it.isNotBlank() },
            providerId = id,
            modelId = requestedModel,
            requestedModelId = requestedModel,
            resolvedModelId = requestedModel,
            executionBackend = "GEMINI_${authenticationMode.name}",
        )
    }

    private fun JSONObject.toMap(): Map<String, Any> = buildMap {
        keys().forEach { key -> put(key, get(key)) }
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
            throw record(AiProviderException(AiErrorType.TIMEOUT, "Gemini request timed out", timeout))
        } catch (offline: UnknownHostException) {
            throw record(AiProviderException(AiErrorType.NETWORK_UNAVAILABLE, "Gemini is unreachable", offline))
        } catch (invalid: org.json.JSONException) {
            throw record(AiProviderException(AiErrorType.INVALID_RESPONSE, "Gemini returned invalid JSON", invalid))
        } catch (error: Exception) {
            throw record(AiProviderException(AiErrorType.NETWORK_ERROR, "Gemini request failed", error))
        }
    }

    private fun record(error: AiProviderException): AiProviderException {
        _status.value = statusFor(error)
        return error
    }

    private fun statusFor(error: AiProviderException): ProviderStatus = ProviderStatus(
        state = when (error.type) {
            AiErrorType.NETWORK_UNAVAILABLE, AiErrorType.TIMEOUT -> ProviderStatus.State.OFFLINE
            AiErrorType.AUTHENTICATION_FAILED -> ProviderStatus.State.UNCONFIGURED
            else -> ProviderStatus.State.ERROR
        },
        message = error.message,
        readiness = ProviderReadiness.Failed(error.message ?: "Gemini request failed"),
        rejectionReason = if (error.type == AiErrorType.AUTHENTICATION_FAILED) {
            TargetRejectionReason.CONFIGURATION_MISSING
        } else null,
    )

    private fun httpError(code: Int, body: String, retryAfterHeader: String? = null): AiProviderException {
        val error = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        val message = error?.optString("message")?.takeIf { it.isNotBlank() }
        val providerCode = error?.optString("status")?.takeIf { it.isNotBlank() }
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
        if (code == 401 && authenticationMode == GeminiAuthenticationMode.OAUTH) {
            oauthAccessToken = null
        }
        return AiProviderException(
            type = type,
            message = "Gemini API error $code",
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

    private fun missingCredential() = AiProviderException(
        AiErrorType.AUTHENTICATION_FAILED,
        "Connect Google OAuth or configure a Gemini API key",
        AiFailureDetails(recoverability = Recoverability.USER_ACTION_REQUIRED),
    )

    private fun configuredCold() = ProviderStatus(
        ProviderStatus.State.STANDBY,
        when (authenticationMode) {
            GeminiAuthenticationMode.OAUTH -> "Google OAuth connected; check connection to verify access"
            GeminiAuthenticationMode.API_KEY -> "API key configured; check connection to verify access"
            GeminiAuthenticationMode.NONE -> "Gemini is not configured"
        },
        ProviderReadiness.ConfiguredCold,
    )

    private companion object {
        const val ID = "gemini"
        const val API_KEY_STORAGE = "gemini_api_key"
        const val PROJECT_ID_STORAGE = "gemini_project_id"
        const val API_BASE = "https://generativelanguage.googleapis.com/v1beta"
        val JSON = "application/json".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
