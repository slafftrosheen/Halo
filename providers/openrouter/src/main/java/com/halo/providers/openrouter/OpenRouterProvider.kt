package com.halo.providers.openrouter

import com.halo.sdk.provider.remoteImageContext
import com.halo.sdk.provider.remoteAiContext

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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.math.BigDecimal

data class OpenRouterModelMetadata(
    val modelId: String,
    val description: String?,
    val promptPrice: BigDecimal?,
    val completionPrice: BigDecimal?,
    val supportedParameters: Set<String>,
    val inputModalities: Set<String>,
    val routingModel: Boolean = false,
)

class OpenRouterProvider(
    private val storage: Storage,
    private val client: OkHttpClient = defaultClient(),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val logger: com.halo.sdk.runtime.RuntimeLogger? = null,
) : AiImageProvider, com.halo.sdk.provider.CredentialProvider, com.halo.sdk.provider.ProviderConfigurationReporting {

    override val configurationDescriptor: com.halo.sdk.provider.ProviderConfigurationDescriptor get() =
        com.halo.sdk.provider.ProviderConfigurationDescriptor(id, displayName, com.halo.sdk.provider.AiExecutionLocation.REMOTE,
            listOf(com.halo.sdk.provider.ProviderConfigurationField("credential", "API key", com.halo.sdk.provider.ProviderFieldKind.SECRET)), "OPENAI_CHAT_COMPLETIONS", "ACCOUNT_DEPENDENT")

    override val id: String = "openrouter"
    override val displayName: String = "OpenRouter"
    override val isLocal: Boolean = false

    private val _status = MutableStateFlow(ProviderStatus.UNCONFIGURED)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()

    override fun hasCredential(): Boolean = !apiKey.isNullOrBlank()
    override fun credential(): com.halo.sdk.provider.Credential? = apiKey?.let { com.halo.sdk.provider.Credential(id, it) }
    override fun setCredential(credential: com.halo.sdk.provider.Credential) {
        storage.putString("openrouter_api_key", credential.value.trim())
        configurationChanged()
    }
    override fun clearCredential() {
        storage.remove("openrouter_api_key")
        configurationChanged()
    }

    private val apiKey: String?
        get() = storage.getString("openrouter_api_key")?.trim()?.ifBlank { null }
    private val selectedModelId: String
        get() = storage.getString("active_ai_provider")
            ?.takeIf { it == id }
            ?.let { storage.getString("active_ai_model") }
            ?.trim()
            ?.ifBlank { null }
            ?: FREE_ROUTER_ID
    private val configurationRevision = java.util.concurrent.atomic.AtomicLong()
    @Volatile private var cooldown: ProviderCooldown? = null
    /** Only credential failures are sticky. Request/model/network failures must not brick routing. */
    @Volatile private var rejectedApiKey: String? = null
    @Volatile var modelMetadata: Map<String, OpenRouterModelMetadata> = emptyMap()
        private set
    override fun configurationChanged() {
        configurationRevision.incrementAndGet()
        storage.remove(MODEL_CATALOG_CACHE_KEY); storage.remove(MODEL_CATALOG_REFRESHED_AT_KEY)
        modelMetadata = emptyMap()
        rejectedApiKey = null; cooldown = null
        _status.value = if (hasCredential()) ProviderStatus(ProviderStatus.State.STANDBY, "Configured; not checked") else ProviderStatus.UNCONFIGURED
    }
    override suspend fun getStatus(): ProviderStatus = withContext(Dispatchers.IO) {
        val current = if (apiKey.isNullOrBlank()) {
            ProviderStatus.UNCONFIGURED
        } else if (rejectedApiKey == apiKey) {
            ProviderStatus(
                ProviderStatus.State.ERROR,
                "OpenRouter rejected the configured API key",
                ProviderReadiness.Failed("Authentication failed"),
                rejectionReason = TargetRejectionReason.CONFIGURATION_MISSING,
            )
        } else if (activeCooldown() != null) {
            cooldownStatus(activeCooldown()!!)
        } else {
            statusFromLastErrorOrReady()
        }
        _status.value = current
        current
    }

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        throwIfCoolingDown()
        val key = apiKey ?: return@withContext false
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/auth/key")
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .get()
            .build()
        val revision = configurationRevision.get()
        try {
            client.newCall(request).consumeCancellable { response ->
                if (!response.isSuccessful) throw httpError(response.code, response.body?.boundedText(OpenRouterTransportBounds.ERROR_BYTES, truncate = true), response.header("Retry-After"))
                JSONObject(response.body?.boundedText().orEmpty()).getJSONObject("data")
                if (configurationRevision.get() == revision) {
                    cooldown = null; rejectedApiKey = null; _status.value = ProviderStatus.READY
                }
                true
            }
        } catch (error: CancellationException) { throw error }
        catch (error: AiProviderException) {
            if (configurationRevision.get() == revision) _status.value = providerStatus(error)
            throw error
        } catch (_: org.json.JSONException) {
            throw AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter returned invalid authentication metadata")
        } catch (error: SocketTimeoutException) {
            throw AiProviderException(AiErrorType.TIMEOUT, "OpenRouter auth check timed out", error)
        } catch (error: UnknownHostException) {
            throw AiProviderException(AiErrorType.NETWORK_UNAVAILABLE, "OpenRouter host unavailable", error)
        } catch (error: Exception) {
            throw AiProviderException(AiErrorType.NETWORK_ERROR, "OpenRouter auth check failed", error)
        }
    }

    override suspend fun getCapabilities(): Set<AiCapability> = setOf(
        AiCapability.TEXT, AiCapability.IMAGE, AiCapability.STREAMING, AiCapability.TOOLS
    )

    override suspend fun listImageModels(): List<AiImageModel> = withContext(Dispatchers.IO) {
        val key = apiKey ?: return@withContext emptyList()
        val request = Request.Builder().url("https://openrouter.ai/api/v1/images/models")
            .header("Authorization", "Bearer $key").get().build()
        val response = client.newCall(request).awaitImagePayload()
        run {
            if (response.code !in 200..299) throw httpError(response.code, response.body, response.retryAfter)
            val data = JSONObject(response.body).optJSONArray("data") ?: JSONArray()
            (0 until data.length()).mapNotNull { index ->
                val item = data.optJSONObject(index) ?: return@mapNotNull null
                val modelId = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                // SVG generators are not supported by Android's raster attachment decoder.
                if (modelId.contains("vector", true)) return@mapNotNull null
                val inputs = item.optJSONObject("architecture")?.optJSONArray("input_modalities") ?: JSONArray()
                val references = item.optJSONObject("supported_parameters")?.optJSONObject("input_references")
                val edits = (0 until inputs.length()).any { inputs.optString(it) == "image" } &&
                    references != null && references.optInt("max", 0) >= 1
                AiImageModel(modelId, item.optString("name", modelId), edits,
                    requiresReference = references?.optInt("min", 0)?.let { it > 0 } == true)
            }.sortedBy { it.displayName }
        }
    }

    override suspend fun generateImage(request: AiImageRequest): AiImageResult = withContext(Dispatchers.IO + storage.remoteImageContext(id, request)) {
        throwIfCoolingDown()
        val key = apiKey ?: throw missingApiKey()
        // The endpoint defaults to one image; some edit models do not accept the optional n parameter.
        val body = JSONObject().put("model", request.modelId).put("prompt", request.prompt)
        request.reference?.let { bytes ->
            body.put("input_references", JSONArray().put(JSONObject().put("type", "image_url").put("image_url",
                JSONObject().put("url", "data:${request.referenceMime};base64,${android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)}"))))
        }
        val http = Request.Builder().url("https://openrouter.ai/api/v1/images").header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        try {
            val response = client.newBuilder().readTimeout(180, TimeUnit.SECONDS).callTimeout(180, TimeUnit.SECONDS).build().newCall(http).awaitImagePayload()
            run {
                if (response.code !in 200..299) throw httpError(response.code, response.body, response.retryAfter)
                val output = JSONObject(response.body).optJSONArray("data")?.optJSONObject(0)
                    ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "No image returned")
                val encoded = output.optString("b64_json")
                require(encoded.isNotBlank() && encoded.length <= 45_000_000) { "Image response was empty or too large" }
                val result = AiImageResult(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), output.optString("media_type", "image/png"), id, request.modelId)
                _status.value = ProviderStatus.READY; cooldown = null; rejectedApiKey = null
                result
            }
        } catch (error: AiProviderException) { _status.value = providerStatus(error); throw error }
    }

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = withContext(Dispatchers.IO + storage.remoteAiContext(id, request, context)) {
        throwIfCoolingDown()
        val key = apiKey ?: throw missingApiKey()
        val actualModel = resolveModel(request.modelId ?: selectedModelId)
        val body = buildRequestBody(request, context, actualModel, false).toString()
            .toRequestBody("application/json".toMediaType())
        val httpRequest = buildChatRequest(key, body)
        try {
            client.newCall(httpRequest).consumeCancellable { response ->
                if (!response.isSuccessful) {
                    throw httpError(response.code, response.body?.boundedText(OpenRouterTransportBounds.ERROR_BYTES, truncate = true), response.header("Retry-After"))
                }
                val responseJson = JSONObject(
                    response.body?.boundedText()
                        ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter returned an empty response body")
                )
                responseJson.optJSONObject("error")?.let { error ->
                    throw streamError(error)
                }
                val choices = responseJson.optJSONArray("choices")
                    ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter returned no choices")
                if (choices.length() == 0) throw AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter returned no choices")
                val first = choices.getJSONObject(0)
                val message = first.getJSONObject("message")
                val text = message.optionalString("content").orEmpty()
                if (text.length > OpenRouterTransportBounds.TEXT_CHARS) OpenRouterTransportBounds.exceeded()
                val resolvedModel = responseJson.optionalString("model") ?: actualModel
                val result = AiResponse(
                    text = text,
                    toolCalls = parseToolCalls(message.optJSONArray("tool_calls")),
                    finishReason = first.optionalString("finish_reason"),
                    providerId = id,
                    modelId = actualModel,
                    requestedModelId = actualModel,
                    resolvedModelId = resolvedModel,
                    executionBackend = "OPENROUTER",
                )
                if (result.text.isBlank() && result.toolCalls.isEmpty()) {
                    val usage = responseJson.optJSONObject("usage")
                    logEmptyCompletion(request, actualModel, resolvedModel, result.finishReason,
                        tokenCount(usage, "completion_tokens"),
                        tokenCount(usage?.optJSONObject("completion_tokens_details"), "reasoning_tokens"))
                }
                _status.value = ProviderStatus.READY
                cooldown = null
                rejectedApiKey = null
                result
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiProviderException) {
            logRequestFailure(request, actualModel, e)
            _status.value = providerStatus(e)
            throw e
        } catch (e: SocketTimeoutException) {
            val failure = AiProviderException(AiErrorType.TIMEOUT, "OpenRouter request timed out (model=$actualModel)", e)
            logRequestFailure(request, actualModel, failure)
            _status.value = providerStatus(failure)
            throw failure
        } catch (e: UnknownHostException) {
            val failure = AiProviderException(AiErrorType.NETWORK_UNAVAILABLE, "OpenRouter host unavailable", e)
            logRequestFailure(request, actualModel, failure)
            _status.value = providerStatus(failure)
            throw failure
        } catch (e: org.json.JSONException) {
            val failure = AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter returned invalid JSON", e)
            logRequestFailure(request, actualModel, failure)
            _status.value = providerStatus(failure)
            throw failure
        } catch (e: Exception) {
            val failure = AiProviderException(AiErrorType.NETWORK_ERROR, "OpenRouter request failed: ${e.message ?: e::class.simpleName}", e)
            logRequestFailure(request, actualModel, failure)
            _status.value = providerStatus(failure)
            throw failure
        }
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
        throwIfCoolingDown()
        val key = apiKey ?: throw missingApiKey()
        emit(AiStreamEvent.Started)
        val actualModel = resolveModel(request.modelId ?: selectedModelId)
        val body = buildRequestBody(request, context, actualModel, true).toString()
            .toRequestBody("application/json".toMediaType())
        val httpRequest = buildChatRequest(key, body)
        try {
            client.newCall(httpRequest).consumeCancellable { response ->
                if (!response.isSuccessful) throw httpError(response.code, response.body?.boundedText(OpenRouterTransportBounds.ERROR_BYTES, truncate = true), response.header("Retry-After"))
                val source = response.body?.source() ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter returned no response body")
                val text = StringBuilder()
                val tools = linkedMapOf<Int, StreamToolCall>()
                var finish: String? = null
                var resolvedModel: String? = null
                var outputTokens: Int? = null
                var reasoningTokens: Int? = null
                var streamBytes = 0L
                var argumentChars = 0L

                var streamFinishedCleanly = false
                while (!source.exhausted()) {
                    val line = source.boundedSseLine() ?: break
                    streamBytes += line.toByteArray(Charsets.UTF_8).size + 2L
                    if (streamBytes > OpenRouterTransportBounds.STREAM_BYTES) OpenRouterTransportBounds.exceeded()
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trimStart()
                    if (data.isBlank()) continue
                    if (data == "[DONE]") {
                        streamFinishedCleanly = true
                        break
                    }
                    val event = try {
                        JSONObject(data)
                    } catch (error: org.json.JSONException) {
                        throw AiProviderException(
                            AiErrorType.INVALID_RESPONSE,
                            "OpenRouter returned malformed stream data",
                            error,
                        )
                    }
                    event.optJSONObject("error")?.let { error ->
                        throw streamError(error)
                    }
                    event.optionalString("model")?.takeIf { it.isNotBlank() }?.let { resolvedModel = it }
                    event.optJSONObject("usage")?.let { usage ->
                        outputTokens = tokenCount(usage, "completion_tokens") ?: outputTokens
                        reasoningTokens = tokenCount(usage.optJSONObject("completion_tokens_details"), "reasoning_tokens") ?: reasoningTokens
                    }
                    val choices = event.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val choice = choices.getJSONObject(0)
                    finish = choice.optString("finish_reason").takeIf {
                        it.isNotBlank() && it != "null"
                    } ?: finish
                    val delta = choice.optJSONObject("delta") ?: continue
                    val token = delta.optionalString("content").orEmpty()
                    if (token.isNotEmpty()) {
                        if (text.length.toLong() + token.length > OpenRouterTransportBounds.TEXT_CHARS) OpenRouterTransportBounds.exceeded()
                        text.append(token)
                        emit(AiStreamEvent.Token(token))
                    }
                    delta.optJSONArray("tool_calls")?.let { calls ->
                        for (i in 0 until calls.length()) {
                            val tc = calls.getJSONObject(i)
                            val fn = tc.optJSONObject("function")
                            val index = tc.optInt("index", i)
                            if (index !in 0 until OpenRouterTransportBounds.TOOL_CALLS ||
                                index !in tools && tools.size >= OpenRouterTransportBounds.TOOL_CALLS) OpenRouterTransportBounds.exceeded()
                            val assembled = tools.getOrPut(index) { StreamToolCall() }
                            tc.optionalString("id")?.takeIf { it.isNotBlank() }?.let {
                                if (it.length > 512) OpenRouterTransportBounds.exceeded()
                                assembled.id = it
                            }
                            fn?.optionalString("name")?.takeIf { it.isNotBlank() }?.let {
                                if (it.length > 240) OpenRouterTransportBounds.exceeded()
                                assembled.name = it
                            }
                            fn?.optionalString("arguments")?.let {
                                argumentChars += it.length
                                if (assembled.arguments.length.toLong() + it.length > OpenRouterTransportBounds.TOOL_ARGUMENT_CHARS ||
                                    argumentChars > OpenRouterTransportBounds.TOOL_ARGUMENT_TOTAL_CHARS) OpenRouterTransportBounds.exceeded()
                                assembled.arguments.append(it)
                            }
                            emit(AiStreamEvent.ToolCallDelta(
                                index = index,
                                name = fn?.optionalString("name"),
                                argumentsDelta = fn?.optionalString("arguments"),
                                id = tc.optionalString("id")?.takeIf { it.isNotBlank() },
                            ))
                        }
                    }
                }

                if (!streamFinishedCleanly && finish == null) {
                    throw AiProviderException(
                        AiErrorType.NETWORK_ERROR,
                        "OpenRouter stream ended prematurely without [DONE] or finish_reason"
                    )
                }

                if (text.isBlank() && tools.isEmpty()) {
                    logEmptyCompletion(request, actualModel, resolvedModel ?: actualModel, finish,
                        outputTokens, reasoningTokens)
                }
                _status.value = ProviderStatus.READY
                cooldown = null
                rejectedApiKey = null
                emit(
                    AiStreamEvent.Completed(
                        AiResponse(
                            text = text.toString(),
                            toolCalls = tools.entries.map { (index, call) ->
                                val callId = call.id?.takeIf { it.isNotBlank() }
                                    ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter tool call $index has no id")
                                val name = call.name?.takeIf { it.isNotBlank() }
                                    ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter tool call $index has no function name")
                                ToolCall(callId, name, parseArguments(call.arguments.toString()))
                            },
                            finishReason = finish,
                            providerId = id,
                            modelId = actualModel,
                            requestedModelId = actualModel,
                            resolvedModelId = resolvedModel ?: actualModel,
                            executionBackend = "OPENROUTER",
                        )
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiProviderException) {
            logRequestFailure(request, actualModel, e)
            _status.value = providerStatus(e)
            emit(AiStreamEvent.Failed(e))
        } catch (e: SocketTimeoutException) {
            val failure = AiProviderException(AiErrorType.TIMEOUT, "OpenRouter stream timed out (model=$actualModel)", e)
            logRequestFailure(request, actualModel, failure)
            _status.value = providerStatus(failure)
            emit(AiStreamEvent.Failed(failure))
        } catch (e: UnknownHostException) {
            val failure = AiProviderException(AiErrorType.NETWORK_UNAVAILABLE, "OpenRouter host unavailable", e)
            logRequestFailure(request, actualModel, failure)
            _status.value = providerStatus(failure)
            emit(AiStreamEvent.Failed(failure))
        } catch (e: org.json.JSONException) {
            val failure = AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter returned invalid stream JSON", e)
            logRequestFailure(request, actualModel, failure)
            _status.value = providerStatus(failure)
            emit(AiStreamEvent.Failed(failure))
        } catch (e: Exception) {
            val failure = AiProviderException(AiErrorType.NETWORK_ERROR, "OpenRouter stream failed: ${e.message ?: e::class.simpleName}", e)
            logRequestFailure(request, actualModel, failure)
            _status.value = providerStatus(failure)
            emit(AiStreamEvent.Failed(failure))
        }
    }.flowOn(Dispatchers.IO + storage.remoteAiContext(id, request, context))

    override suspend fun refreshModels(): List<AiModel> {
        storage.remove(MODEL_CATALOG_REFRESHED_AT_KEY)
        return listModels()
    }

    override suspend fun listModels(): List<AiModel> = withContext(Dispatchers.IO) {
        val revision = configurationRevision.get()
        throwIfCoolingDown()
        val key = apiKey ?: return@withContext readCachedModels()
        val cached = readCachedModels()
        val refreshedAt = storage.getString(MODEL_CATALOG_REFRESHED_AT_KEY)?.toLongOrNull()
        if (cached.isNotEmpty() && refreshedAt != null &&
            nowEpochMs() - refreshedAt in 0 until MODEL_CATALOG_TTL_MS
        ) {
            return@withContext cached
        }
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/models")
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(request).consumeCancellable { response ->
                if (!response.isSuccessful) throw httpError(response.code, response.body?.boundedText(OpenRouterTransportBounds.ERROR_BYTES, truncate = true), response.header("Retry-After"))
                val body = response.body?.boundedText(OpenRouterTransportBounds.CATALOG_BYTES)
                    ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Empty response body from OpenRouter")
                if (configurationRevision.get() != revision) throw AiProviderException(AiErrorType.INVALID_REQUEST, "Provider configuration changed; refresh again")
                val data = JSONObject(body).getJSONArray("data")
                val models = parseModelCatalog(data)
                if (configurationRevision.get() != revision) throw AiProviderException(AiErrorType.INVALID_REQUEST, "Provider configuration changed; refresh again")
                storage.putString(MODEL_CATALOG_CACHE_KEY, body)
                storage.putString(MODEL_CATALOG_REFRESHED_AT_KEY, nowEpochMs().toString())
                _status.value = ProviderStatus.READY
                cooldown = null
                rejectedApiKey = null
                models
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiProviderException) {
            if (configurationRevision.get() == revision) _status.value = providerStatus(e)
            cachedModelsOrThrow(e)
        } catch (e: SocketTimeoutException) {
            val failure = AiProviderException(AiErrorType.TIMEOUT, "OpenRouter model discovery timed out", e)
            if (configurationRevision.get() == revision) _status.value = providerStatus(failure)
            cachedModelsOrThrow(failure)
        } catch (e: UnknownHostException) {
            val failure = AiProviderException(AiErrorType.NETWORK_UNAVAILABLE, "OpenRouter model discovery: network unavailable", e)
            if (configurationRevision.get() == revision) _status.value = providerStatus(failure)
            cachedModelsOrThrow(failure)
        } catch (e: org.json.JSONException) {
            val failure = AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter returned invalid model metadata", e)
            if (configurationRevision.get() == revision) _status.value = providerStatus(failure)
            cachedModelsOrThrow(failure)
        } catch (e: Exception) {
            val failure = AiProviderException(AiErrorType.NETWORK_ERROR, "OpenRouter model discovery failed: ${e.message ?: e::class.simpleName}", e)
            if (configurationRevision.get() == revision) _status.value = providerStatus(failure)
            cachedModelsOrThrow(failure)
        }
    }

    private fun parseModelCatalog(data: JSONArray): List<AiModel> {
        val metadata = linkedMapOf<String, OpenRouterModelMetadata>()
        val models = buildList {
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val modelId = item.getString("id")
                val parameters = jsonStringArray(item, "supported_parameters")
                val outputs = item.optJSONObject("architecture")?.let { jsonStringArray(it, "output_modalities") }.orEmpty()
                if (outputs.isNotEmpty() && "text" !in outputs) continue
                val modalities = item.optJSONObject("architecture")
                    ?.let { jsonStringArray(it, "input_modalities") }.orEmpty()
                val pricing = item.optJSONObject("pricing")
                metadata[modelId] = OpenRouterModelMetadata(
                    modelId = modelId,
                    description = item.optString("description").takeIf { it.isNotBlank() },
                    promptPrice = pricing?.optString("prompt")?.toBigDecimalOrNull(),
                    completionPrice = pricing?.optString("completion")?.toBigDecimalOrNull(),
                    supportedParameters = parameters,
                    inputModalities = modalities,
                )
                add(AiModel(
                    id = modelId,
                    displayName = item.optString("name", modelId),
                    providerId = id,
                    inputCapabilities = mapCapabilities(item),
                    capabilityEvidence = if (modelId != FREE_ROUTER_ID && "image" in modalities)
                        mapOf(AiCapability.IMAGE to com.halo.sdk.provider.CapabilityEvidence.AUTHORITATIVE_METADATA) else emptyMap(),
                    contextLength = item.optLong("context_length").takeIf { it > 0L },
                    // Streaming is an OpenRouter chat-completions transport capability, not a
                    // consistently advertised per-model supported_parameter.
                    supportsStreaming = true,
                    supportsTools = modelId != FREE_ROUTER_ID && "tools" in parameters,
                    isFree = isFreeModel(item),
                ))
            }
        }.toMutableList()
        if (models.none { it.id == FREE_ROUTER_ID }) {
            models.add(0, AiModel(
                id = FREE_ROUTER_ID,
                displayName = "OpenRouter Free",
                providerId = id,
                // The router chooses a concrete free model per request. Capabilities vary, so the
                // pseudo-model must advertise only the portable guarantee.
                inputCapabilities = setOf(AiCapability.TEXT),
                contextLength = null,
                supportsStreaming = true,
                supportsTools = false,
                isFree = true,
            ))
        }
        metadata[FREE_ROUTER_ID] = OpenRouterModelMetadata(
            modelId = FREE_ROUTER_ID,
            description = "Free cloud router; the concrete model may vary by request.",
            promptPrice = BigDecimal.ZERO,
            completionPrice = BigDecimal.ZERO,
            supportedParameters = setOf("stream"),
            inputModalities = setOf("text"),
            routingModel = true,
        )
        modelMetadata = metadata
        return models
    }

    private fun readCachedModels(): List<AiModel> = runCatching {
        val body = storage.getString(MODEL_CATALOG_CACHE_KEY) ?: return@runCatching emptyList()
        if (body.length > OpenRouterTransportBounds.CATALOG_BYTES) return@runCatching emptyList()
        parseModelCatalog(JSONObject(body).getJSONArray("data"))
    }.getOrDefault(emptyList())

    private fun cachedModelsOrThrow(error: AiProviderException): List<AiModel> =
        readCachedModels().takeIf { it.isNotEmpty() } ?: throw error

    private suspend fun resolveModel(modelId: String): String {
        // Legacy shorthand maps to the same router used by the current UI/default.
        return if (modelId == "free") "openrouter/free" else modelId
    }

    private fun tokenCount(json: JSONObject?, key: String): Int? =
        (json?.opt(key) as? Number)?.toString()?.toLongOrNull()
            ?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()

    private fun diagnosticIdentifier(value: String?): String {
        val normalized = value?.trim() ?: return "missing"
        return if (normalized.length in 1..160 && normalized.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:/-]*")))
            normalized else "invalid"
    }

    private fun logRequestFailure(request: AiRequest, selectedModel: String, failure: AiProviderException) {
        val sink = logger ?: return
        fun exceptionClass(error: Throwable?): String {
            val name = error?.javaClass?.name ?: return "missing"
            return if (name.length in 1..160 && name.matches(Regex("[A-Za-z_][A-Za-z0-9_.$]*"))) name else "invalid"
        }
        runCatching {
            sink.warn("OpenRouter", "request_failure type=${failure.type.name} " +
                "httpStatus=${failure.details.httpStatus?.takeIf { it in 100..599 } ?: "missing"} " +
                "exceptionClass=${exceptionClass(failure)} causeClass=${exceptionClass(failure.cause)} " +
                "selectedModel=${diagnosticIdentifier(selectedModel)} interactionId=${diagnosticIdentifier(request.interactionId)}")
        }
    }

    /** Emit evidence before router validation rejects blank completions; never include content. */
    private fun logEmptyCompletion(request: AiRequest, requestedModel: String, resolvedModel: String,
        finishReason: String?, outputTokens: Int?, reasoningTokens: Int?) {
        val sink = logger ?: return
        val normalizedFinish = finishReason?.trim()?.lowercase(Locale.ROOT)?.let {
            if (it in setOf("stop", "length", "tool_calls", "function_call", "content_filter", "error",
                    "cancelled", "end_turn", "max_tokens", "stop_sequence")) it else "unknown"
        } ?: "missing"
        // A diagnostic sink failure must not alter the provider/router outcome.
        runCatching {
            sink.warn("OpenRouter", "empty_completion finishReason=$normalizedFinish " +
                "interactionId=${diagnosticIdentifier(request.interactionId)} " +
                "selectedProvider=openrouter selectedModel=${diagnosticIdentifier(requestedModel)} " +
                "resolvedProvider=openrouter resolvedModel=${diagnosticIdentifier(resolvedModel)} " +
                "requestedOutputTokens=${request.maxOutputTokens?.takeIf { it > 0 } ?: "missing"} " +
                "outputTokens=${outputTokens ?: "missing"} reasoningTokens=${reasoningTokens ?: "missing"}")
        }
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
        context.screenContext?.let { screen ->
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", screen.asUntrustedPromptSection())
            })
        }
        request.text?.takeIf { request.appendTextAsUserMessage }?.let { text ->
            val content: Any = if (request.image != null) {
                JSONArray().apply {
                    put(JSONObject().apply { put("type", "text"); put("text", text) })
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            val mimeType = request.imageMimeType()
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
            request.maxOutputTokens?.takeIf { it > 0 }?.let { put("max_tokens", it) }
            when (request.reasoningMode) {
                AiReasoningMode.DISABLED -> put("reasoning", JSONObject().put("enabled", false))
                AiReasoningMode.ENABLED -> put("reasoning", JSONObject().put("enabled", true))
                AiReasoningMode.DEFAULT -> Unit // Preserve the selected model's default.
            }
            if (request.toolUseMode != com.halo.sdk.provider.ToolUseMode.NONE && context.availableTools.isNotEmpty()) {
                put("tool_choice", if (request.toolUseMode == com.halo.sdk.provider.ToolUseMode.REQUIRED &&
                    request.turnKind == com.halo.sdk.provider.ProviderTurnKind.INITIAL) "required" else "auto")
                put("provider", JSONObject().put("require_parameters", true))
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
        if (jsonToolCalls.length() > OpenRouterTransportBounds.TOOL_CALLS) OpenRouterTransportBounds.exceeded()
        var argumentChars = 0L
        return buildList {
            for (i in 0 until jsonToolCalls.length()) {
                val tc = jsonToolCalls.getJSONObject(i)
                val function = tc.getJSONObject("function")
                val callId = tc.getString("id")
                val name = function.getString("name")
                val arguments = function.getString("arguments")
                argumentChars += arguments.length
                if (callId.length > 512 || name.length > 240 ||
                    argumentChars > OpenRouterTransportBounds.TOOL_ARGUMENT_TOTAL_CHARS) OpenRouterTransportBounds.exceeded()
                add(
                    ToolCall(
                        callId,
                        name,
                        parseArguments(arguments)
                    )
                )
            }
        }
    }

    // Android org.json stringifies JSONObject.NULL as "null"; SSE null fields
    // carry no new text/identity and must not overwrite an assembled tool call.
    private fun JSONObject.optionalString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key)

    private fun parseArguments(json: String): Map<String, Any> = try {
        if (json.length > OpenRouterTransportBounds.TOOL_ARGUMENT_CHARS) OpenRouterTransportBounds.exceeded()
        val obj = JSONObject(json)
        buildMap {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, obj.get(key))
            }
        }
    } catch (error: Exception) {
        throw AiProviderException(AiErrorType.INVALID_RESPONSE, "OpenRouter returned invalid tool arguments", error)
    }

    private fun httpError(
        statusCode: Int,
        responseBody: String? = null,
        retryAfterHeader: String? = null,
    ): AiProviderException {
        val normalizedBody = responseBody?.trim().orEmpty()
        val detail = parseProviderMessage(normalizedBody)
        val providerCode = parseProviderCode(normalizedBody)
        val retryAfterMs = if (statusCode == 429) {
            parseRetryAfterMs(retryAfterHeader) ?: parseBodyRetryAfterMs(normalizedBody)
        } else null
        val type = when (statusCode) {
            400 -> if (ContextOverflow.matches(providerCode, detail)) AiErrorType.CONTEXT_TOO_LARGE else AiErrorType.INVALID_REQUEST
            401, 403 -> AiErrorType.AUTHENTICATION_FAILED
            402 -> AiErrorType.PROVIDER_UNAVAILABLE
            404 -> AiErrorType.MODEL_UNAVAILABLE
            408 -> AiErrorType.TIMEOUT
            409 -> AiErrorType.PROVIDER_UNAVAILABLE
            429 -> AiErrorType.RATE_LIMITED
            500, 502, 503, 504 -> AiErrorType.PROVIDER_UNAVAILABLE
            else -> AiErrorType.NETWORK_ERROR
        }
        return AiProviderException(
            type,
            "OpenRouter API error $statusCode",
            details = AiFailureDetails(
                retryAfterMs = retryAfterMs,
                providerCode = safeProviderErrorCode(providerCode),
                httpStatus = statusCode,
                recoverability = when {
                    statusCode in setOf(401, 402, 403) -> Recoverability.USER_ACTION_REQUIRED
                    statusCode == 429 && retryAfterMs != null -> Recoverability.RETRY_AFTER
                    statusCode == 429 -> Recoverability.UNKNOWN
                    statusCode in setOf(408, 409, 500, 502, 503, 504) -> Recoverability.RETRY_IMMEDIATELY
                    statusCode in 400..499 -> Recoverability.PERMANENT
                    else -> Recoverability.UNKNOWN
                },
            ),
        )
    }

    private fun streamError(error: JSONObject): AiProviderException {
        val message = error.optString("message").takeIf { it.isNotBlank() }
            ?: "OpenRouter stream failed"
        val providerCode = error.opt("code")?.toString()?.takeIf { it.isNotBlank() }
        val numericCode = providerCode?.toIntOrNull()
        val type = when {
            ContextOverflow.matches(providerCode, message) -> AiErrorType.CONTEXT_TOO_LARGE
            numericCode == 401 || numericCode == 403 -> AiErrorType.AUTHENTICATION_FAILED
            numericCode == 404 -> AiErrorType.MODEL_UNAVAILABLE
            numericCode == 408 -> AiErrorType.TIMEOUT
            numericCode == 429 -> AiErrorType.RATE_LIMITED
            numericCode in setOf(402, 409, 500, 502, 503, 504) -> AiErrorType.PROVIDER_UNAVAILABLE
            numericCode != null && numericCode in 400..499 -> AiErrorType.INVALID_REQUEST
            else -> AiErrorType.PROVIDER_UNAVAILABLE
        }
        return AiProviderException(
            type = type,
            message = "OpenRouter stream error",
            details = AiFailureDetails(
                providerCode = safeProviderErrorCode(providerCode),
                httpStatus = numericCode,
                recoverability = when (type) {
                    AiErrorType.AUTHENTICATION_FAILED -> Recoverability.USER_ACTION_REQUIRED
                    AiErrorType.RATE_LIMITED -> Recoverability.UNKNOWN
                    AiErrorType.INVALID_REQUEST, AiErrorType.CONTEXT_TOO_LARGE, AiErrorType.MODEL_UNAVAILABLE -> Recoverability.PERMANENT
                    else -> Recoverability.RETRY_IMMEDIATELY
                },
            ),
        )
    }

    private fun parseProviderMessage(body: String): String? = runCatching {
        val root = JSONObject(body)
        root.optJSONObject("error")?.let { error ->
            error.optString("message").takeIf { it.isNotBlank() }
                ?: error.optString("code").takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun parseProviderCode(body: String): String? = runCatching {
        JSONObject(body).optJSONObject("error")?.optString("code")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun parseBodyRetryAfterMs(body: String): Long? = runCatching {
        val root = JSONObject(body)
        val error = root.optJSONObject("error")
        sequenceOf(root, error).filterNotNull().firstNotNullOfOrNull { json ->
            when {
                json.has("retry_after_ms") -> json.optLong("retry_after_ms").takeIf { it > 0L }
                json.has("retry_after") -> (json.optDouble("retry_after") * 1_000.0).toLong().takeIf { it > 0L }
                else -> null
            }
        }
    }.getOrNull()

    private fun parseRetryAfterMs(value: String?): Long? {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        normalized.toDoubleOrNull()?.let { seconds ->
            if (seconds >= 0.0) return (seconds * 1_000.0).toLong()
        }
        val date = runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("GMT")
            }.parse(normalized)
        }.getOrNull() ?: return null
        return (date.time - nowEpochMs()).coerceAtLeast(0L)
    }

    private fun activeCooldown(): ProviderCooldown? {
        val current = cooldown ?: return null
        if (current.isActive(nowEpochMs())) return current
        cooldown = null
        return null
    }

    private fun missingApiKey() = AiProviderException(
        type = AiErrorType.AUTHENTICATION_FAILED,
        message = "OpenRouter API key not configured",
        details = AiFailureDetails(recoverability = Recoverability.USER_ACTION_REQUIRED),
    )

    private fun throwIfCoolingDown() {
        val current = activeCooldown() ?: return
        val remaining = current.remainingMs(nowEpochMs())
        throw AiProviderException(
            type = current.reason,
            message = "OpenRouter is rate limited. Try again in about ${((remaining + 999L) / 1_000L).coerceAtLeast(1L)} seconds.",
            details = AiFailureDetails(
                retryAfterMs = remaining,
                recoverability = Recoverability.RETRY_AFTER,
            ),
        )
    }

    @Volatile private var lastFailure: AiProviderException? = null
    @Volatile private var lastFailedModel: String? = null

    private fun providerStatus(error: AiProviderException, modelId: String? = null): ProviderStatus {
        lastFailure = error
        if (modelId != null) lastFailedModel = modelId
        val targetModel = modelId ?: lastFailedModel ?: selectedModelId
        val sanitizedMsg = error.sanitizedSummary(targetModel)
        if (error.type == AiErrorType.AUTHENTICATION_FAILED && !apiKey.isNullOrBlank()) {
            rejectedApiKey = apiKey
        }
        if (error.type == AiErrorType.RATE_LIMITED) {
            error.details.retryAfterMs?.takeIf { it > 0L }?.let { retryAfter ->
                cooldown = ProviderCooldown(nowEpochMs() + retryAfter, AiErrorType.RATE_LIMITED)
            }
        }
        return activeCooldown()?.let(::cooldownStatus) ?: when (error.type) {
            AiErrorType.AUTHENTICATION_FAILED -> ProviderStatus(
                ProviderStatus.State.ERROR,
                sanitizedMsg,
                ProviderReadiness.Failed("Authentication failed"),
                rejectionReason = TargetRejectionReason.CONFIGURATION_MISSING,
            )
            AiErrorType.NETWORK_UNAVAILABLE -> ProviderStatus(
                ProviderStatus.State.OFFLINE,
                sanitizedMsg,
                ProviderReadiness.ConfiguredCold,
                rejectionReason = TargetRejectionReason.NETWORK_UNAVAILABLE,
            )
            else -> ProviderStatus(
                ProviderStatus.State.ERROR,
                sanitizedMsg,
                ProviderReadiness.ConfiguredCold,
            )
        }
    }

    private fun cooldownStatus(value: ProviderCooldown): ProviderStatus {
        val seconds = ((value.remainingMs(nowEpochMs()) + 999L) / 1_000L).coerceAtLeast(1L)
        return ProviderStatus(
            state = ProviderStatus.State.STANDBY,
            message = "OpenRouter is rate limited. Try again in about $seconds seconds.",
            readiness = ProviderReadiness.Failed("Provider cooldown"),
            rejectionReason = TargetRejectionReason.PROVIDER_COOLDOWN,
            cooldown = value,
        )
    }

    private fun statusFromLastErrorOrReady(): ProviderStatus = ProviderStatus(
        ProviderStatus.State.STANDBY,
        message = "OpenRouter is configured; connectivity has not been proven in this process",
        readiness = ProviderReadiness.ConfiguredCold,
    )

    companion object {
        const val FREE_ROUTER_ID = "openrouter/free"
        const val MODEL_CATALOG_CACHE_KEY = "openrouter_model_catalog_v1"
        const val MODEL_CATALOG_REFRESHED_AT_KEY = "openrouter_model_catalog_refreshed_at_ms"
        const val MODEL_CATALOG_TTL_MS = 15 * 60 * 1_000L
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
    }

    private class StreamToolCall {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()
    }
}
