package com.halo.providers.llamacpp

import android.os.Build
import android.util.Base64
import android.util.Log
import com.halo.sdk.provider.AIConversation
import com.halo.sdk.provider.AIMessage
import com.halo.sdk.provider.AIRequestLegacy
import com.halo.sdk.provider.AIResponseLegacy
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.Role
import com.halo.sdk.provider.asUntrustedPromptSection
import com.halo.sdk.provider.validateAgainst
import com.halo.sdk.provider.validateForTextGeneration
import com.halo.sdk.runtime.Storage
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject

/**
 * Local Ollama inference backend.
 *
 * Adapter capabilities describe what Halo's Ollama bridge can serialize and
 * consume. Per-model capabilities come from /api/show and remain conservative:
 * tool support is not exposed until this adapter implements the complete
 * tool-call/result round trip.
 */
class OllamaProvider(
    private val storage: Storage
) : AiInferenceProvider {

    private val tag = "OllamaProvider"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val defaultEndpoint: String?
        get() = if (isAndroidEmulator()) "http://10.0.2.2:11434" else null

    private val configuredModelId: String
        get() = storage.getString("ollama_model")?.trim()?.ifBlank { null } ?: "llama3"

    private val _status = MutableStateFlow(ProviderStatus.UNAVAILABLE)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor { Log.d(tag, it) }
        logging.level = HttpLoggingInterceptor.Level.BASIC
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val moshi = com.squareup.moshi.Moshi.Builder()
        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()
    private val chatRequestAdapter = moshi.adapter(OllamaChatRequest::class.java)
    private val chatResponseAdapter = moshi.adapter(OllamaChatResponse::class.java)

    private data class CachedModel(
        val model: AiModel,
        val cachedAtMs: Long,
    )

    private val modelMetadataCache = ConcurrentHashMap<String, CachedModel>()

    override val id: String = "provider.ollama"
    override val displayName: String = "Ollama"
    override val isLocal: Boolean = true

    override suspend fun getStatus(): ProviderStatus = withContext(Dispatchers.IO) {
        val endpoint = normalizedEndpointOrNull()
            ?: return@withContext ProviderStatus(
                ProviderStatus.State.UNCONFIGURED,
                "Ollama endpoint is not configured"
            ).also { _status.value = it }

        val probe = fetchModels(endpoint)
        val status = when {
            probe.error != null ->
                ProviderStatus(
                    ProviderStatus.State.OFFLINE,
                    "Ollama unreachable at $endpoint: ${probe.error}"
                )
            probe.models.isEmpty() ->
                ProviderStatus(
                    ProviderStatus.State.OFFLINE,
                    "Ollama responded at $endpoint but returned no models"
                )
            probe.models.none { sameModel(it.id, configuredModelId) } ->
                ProviderStatus(
                    ProviderStatus.State.MODEL_MISSING,
                    "Connected to $endpoint, but model '$configuredModelId' is not pulled"
                )
            else ->
                ProviderStatus(
                    ProviderStatus.State.READY,
                    "Connected to $endpoint • $configuredModelId"
                )
        }
        _status.value = status
        status
    }

    /**
     * Provider/adapter-level capability. Individual models are intersected by
     * AiExecutionTarget, so a text-only model never becomes a vision target.
     */
    override suspend fun getCapabilities(): Set<AiCapability> = setOf(
        AiCapability.TEXT,
        AiCapability.IMAGE,
        AiCapability.STREAMING,
    )

    override suspend fun testConnection(): Boolean =
        getStatus().state == ProviderStatus.State.READY

    suspend fun health(): Boolean =
        normalizedEndpointOrNull()?.let { fetchModels(it).error == null } == true

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse =
        withContext(Dispatchers.IO) {
            request.validateForTextGeneration()
            request.validateAgainst(getCapabilities())
            val endpoint = normalizedEndpointOrNull()
                ?: throw AiProviderException(
                    AiErrorType.LOCAL_MODEL_UNAVAILABLE,
                    "Ollama endpoint is not configured"
                )
            val model = request.modelId?.trim()?.ifBlank { null } ?: configuredModelId
            val messages = buildMessages(request, context)
            val body = chatRequestAdapter.toJson(
                OllamaChatRequest(
                    model = model,
                    messages = messages,
                    stream = false,
                )
            ).toRequestBody(jsonMediaType)

            try {
                client.newCall(
                    Request.Builder().url("$endpoint/api/chat").post(body).build()
                ).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw AiProviderException(
                            if (response.code == 404) {
                                AiErrorType.MODEL_UNAVAILABLE
                            } else {
                                AiErrorType.LOCAL_MODEL_UNAVAILABLE
                            },
                            ollamaHttpError(response.code, response.body?.string())
                        )
                    }
                    val json = response.body?.string()
                        ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Empty body")
                    val result = chatResponseAdapter.fromJson(json)
                        ?: throw AiProviderException(
                            AiErrorType.INVALID_RESPONSE,
                            "Failed to parse response"
                        )
                    val text = result.message?.content.orEmpty().trim()
                    if (text.isBlank()) {
                        throw AiProviderException(
                            AiErrorType.EMPTY_RESPONSE,
                            "Ollama produced no response text"
                        )
                    }
                    AiResponse(
                        text = text,
                        providerId = id,
                        modelId = model,
                        finishReason = if (result.done) "stop" else "length",
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: AiProviderException) {
                throw error
            } catch (error: Exception) {
                throw AiProviderException(
                    AiErrorType.LOCAL_MODEL_UNAVAILABLE,
                    "Ollama completion failed: ${error.message ?: "network error"}",
                    error,
                )
            }
        }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
        request.validateForTextGeneration()
        request.validateAgainst(getCapabilities())
        emit(AiStreamEvent.Started)

        val endpoint = normalizedEndpointOrNull()
            ?: throw AiProviderException(
                AiErrorType.LOCAL_MODEL_UNAVAILABLE,
                "Ollama endpoint is not configured"
            )
        val model = request.modelId?.trim()?.ifBlank { null } ?: configuredModelId
        val body = chatRequestAdapter.toJson(
            OllamaChatRequest(
                model = model,
                messages = buildMessages(request, context),
                stream = true,
            )
        ).toRequestBody(jsonMediaType)

        val output = StringBuilder()
        try {
            client.newCall(
                Request.Builder().url("$endpoint/api/chat").post(body).build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    throw AiProviderException(
                        if (response.code == 404) {
                            AiErrorType.MODEL_UNAVAILABLE
                        } else {
                            AiErrorType.LOCAL_MODEL_UNAVAILABLE
                        },
                        ollamaHttpError(response.code, response.body?.string())
                    )
                }

                val source = response.body?.source()
                    ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Empty body")
                var streamFinishedCleanly = false
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    chatResponseAdapter.fromJson(line)?.let { parsed ->
                        val token = parsed.message?.content.orEmpty()
                        if (token.isNotEmpty()) {
                            output.append(token)
                            emit(AiStreamEvent.Token(token))
                        }
                        if (parsed.done) {
                            streamFinishedCleanly = true
                            break
                        }
                    }
                }
                
                if (!streamFinishedCleanly) {
                    throw AiProviderException(
                        AiErrorType.NETWORK_ERROR,
                        "Ollama stream ended prematurely without 'done' signal"
                    )
                }
            }

            val text = output.toString().trim()
            if (text.isBlank()) {
                throw AiProviderException(
                    AiErrorType.EMPTY_RESPONSE,
                    "Ollama produced no response text"
                )
            }
            emit(
                AiStreamEvent.Completed(
                    AiResponse(
                        text = text,
                        providerId = id,
                        modelId = model,
                        finishReason = "stop",
                    )
                )
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: AiProviderException) {
            emit(AiStreamEvent.Failed(error))
        } catch (error: Exception) {
            emit(
                AiStreamEvent.Failed(
                    AiProviderException(
                        AiErrorType.LOCAL_MODEL_UNAVAILABLE,
                        "Ollama stream failed: ${error.message ?: "network error"}",
                        error,
                    )
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun buildMessages(
        request: AiRequest,
        context: AiContext,
    ): List<OllamaChatMessage> {
        val messages = context.conversation.map(::toOllamaMessage).toMutableList()
        context.screenContext?.let {
            messages += OllamaChatMessage(
                role = "user",
                content = it.asUntrustedPromptSection(),
            )
        }
        if (request.appendTextAsUserMessage) {
            messages += OllamaChatMessage(
                role = "user",
                content = requireNotNull(request.text),
                images = request.image?.let { image ->
                    listOf(Base64.encodeToString(image, Base64.NO_WRAP))
                },
            )
        }
        return messages
    }

    /*
     * Legacy text-only helpers remain for callers not yet migrated to AiRequest.
     */
    suspend fun complete(request: AIRequestLegacy): AIResponseLegacy =
        completeWithModel(request, configuredModelId)

    private suspend fun completeWithModel(
        request: AIRequestLegacy,
        model: String,
    ): AIResponseLegacy = withContext(Dispatchers.IO) {
        val endpoint = normalizedEndpointOrNull()
            ?: throw AiProviderException(
                AiErrorType.LOCAL_MODEL_UNAVAILABLE,
                "Ollama endpoint is not configured"
            )
        val startTime = System.currentTimeMillis()
        val body = chatRequestAdapter.toJson(
            OllamaChatRequest(
                model = model,
                messages = request.conversation.messages.map(::toOllamaMessage),
                stream = false,
                options = mapOf(
                    "temperature" to request.temperature,
                    "num_predict" to request.maxTokens,
                ),
            )
        ).toRequestBody(jsonMediaType)

        try {
            client.newCall(
                Request.Builder().url("$endpoint/api/chat").post(body).build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    throw AiProviderException(
                        if (response.code == 404) {
                            AiErrorType.MODEL_UNAVAILABLE
                        } else {
                            AiErrorType.LOCAL_MODEL_UNAVAILABLE
                        },
                        ollamaHttpError(response.code, response.body?.string())
                    )
                }
                val json = response.body?.string()
                    ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Empty body")
                val result = chatResponseAdapter.fromJson(json)
                    ?: throw AiProviderException(
                        AiErrorType.INVALID_RESPONSE,
                        "Failed to parse response"
                    )
                AIResponseLegacy(
                    result.message?.content.orEmpty(),
                    if (result.done) "stop" else "length",
                    System.currentTimeMillis() - startTime,
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: AiProviderException) {
            throw error
        } catch (error: Exception) {
            throw AiProviderException(
                AiErrorType.LOCAL_MODEL_UNAVAILABLE,
                "Ollama completion failed: ${error.message ?: "network error"}",
                error,
            )
        }
    }

    fun stream(prompt: String): Flow<String> = flow {
        val endpoint = normalizedEndpointOrNull()
            ?: throw AiProviderException(
                AiErrorType.LOCAL_MODEL_UNAVAILABLE,
                "Ollama endpoint is not configured"
            )
        val body = chatRequestAdapter.toJson(
            OllamaChatRequest(
                model = configuredModelId,
                messages = listOf(OllamaChatMessage("user", prompt)),
                stream = true,
            )
        ).toRequestBody(jsonMediaType)

        try {
            client.newCall(
                Request.Builder().url("$endpoint/api/chat").post(body).build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    throw AiProviderException(
                        if (response.code == 404) {
                            AiErrorType.MODEL_UNAVAILABLE
                        } else {
                            AiErrorType.LOCAL_MODEL_UNAVAILABLE
                        },
                        ollamaHttpError(response.code, response.body?.string())
                    )
                }
                val source = response.body?.source()
                    ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Empty body")
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (line.isNotBlank()) {
                        chatResponseAdapter.fromJson(line)?.let {
                            emit(it.message?.content.orEmpty())
                            if (it.done) return@use
                        }
                    }
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: AiProviderException) {
            throw error
        } catch (error: Exception) {
            throw AiProviderException(
                AiErrorType.LOCAL_MODEL_UNAVAILABLE,
                "Ollama stream failed: ${error.message ?: "network error"}",
                error,
            )
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): List<AiModel> = withContext(Dispatchers.IO) {
        fetchModels(normalizedEndpointOrNull()).models
    }

    private data class ModelProbe(
        val models: List<AiModel>,
        val error: String?,
    )

    private suspend fun fetchModels(endpoint: String?): ModelProbe = withContext(Dispatchers.IO) {
        if (endpoint == null) {
            return@withContext ModelProbe(emptyList(), "endpoint is not configured")
        }

        try {
            client.newCall(
                Request.Builder().url("$endpoint/api/tags").get().build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ModelProbe(emptyList(), "HTTP ${response.code}")
                }
                val body = response.body?.string()
                    ?: return@withContext ModelProbe(emptyList(), "empty response")
                val models = JSONObject(body).optJSONArray("models")
                    ?: return@withContext ModelProbe(emptyList(), "missing models list")

                val result = buildList {
                    for (index in 0 until models.length()) {
                        val name = models.getJSONObject(index).optString("name").trim()
                        if (name.isBlank()) continue
                        add(inspectModel(endpoint, name))
                    }
                }
                ModelProbe(result, null)
            }
        } catch (error: Exception) {
            ModelProbe(emptyList(), error.message ?: error.javaClass.simpleName)
        }
    }

    /**
     * /api/tags does not contain enough model capability information for
     * workload routing. /api/show metadata is cached briefly and failure falls
     * back conservatively to text-only instead of guessing from model names.
     */
    private fun inspectModel(endpoint: String, name: String): AiModel {
        val key = "$endpoint|$name"
        val now = System.currentTimeMillis()
        modelMetadataCache[key]?.takeIf {
            now - it.cachedAtMs <= MODEL_METADATA_TTL_MS
        }?.let { return it.model }

        val conservative = AiModel(
            id = name,
            displayName = name,
            providerId = id,
            inputCapabilities = setOf(AiCapability.TEXT),
            contextLength = null,
            supportsStreaming = true,
            supportsTools = false,
        )

        val inspected = runCatching {
            val requestBody = JSONObject()
                .put("model", name)
                .toString()
                .toRequestBody(jsonMediaType)

            client.newCall(
                Request.Builder().url("$endpoint/api/show").post(requestBody).build()
            ).execute().use { response ->
                if (!response.isSuccessful) return@use conservative
                val body = response.body?.string() ?: return@use conservative
                val json = JSONObject(body)

                val advertised = buildSet {
                    val array = json.optJSONArray("capabilities")
                    if (array != null) {
                        for (index in 0 until array.length()) {
                            add(array.optString(index).lowercase())
                        }
                    }
                }

                val inputCapabilities = buildSet {
                    add(AiCapability.TEXT)
                    if ("vision" in advertised) add(AiCapability.IMAGE)
                }

                val modelInfo = json.optJSONObject("model_info")
                val contextLength = modelInfo?.let { info ->
                    val keys = info.keys()
                    var found: Long? = null
                    while (keys.hasNext()) {
                        val field = keys.next()
                        if (field.endsWith(".context_length") || field == "context_length") {
                            val value = info.optLong(field, 0L)
                            if (value > 0L) found = maxOf(found ?: 0L, value)
                        }
                    }
                    found
                }

                AiModel(
                    id = name,
                    displayName = name,
                    providerId = id,
                    inputCapabilities = inputCapabilities,
                    contextLength = contextLength,
                    supportsStreaming = true,
                    // Deliberately false even when Ollama reports "tools": the
                    // Halo adapter does not yet implement the complete tool loop.
                    supportsTools = false,
                )
            }
        }.getOrDefault(conservative)

        modelMetadataCache[key] = CachedModel(inspected, now)
        return inspected
    }

    private fun normalizedEndpointOrNull(): String? {
        val configured = storage.getString("ollama_endpoint")
            ?.trim()
            ?.trimEnd('/')
        val raw = configured?.takeIf { it.isNotBlank() }?.let {
            val withScheme = if (it.contains("://")) it else "http://$it"
            withScheme.removeSuffix("/api")
        } ?: defaultEndpoint

        if (raw.isNullOrBlank()) return null

        return runCatching {
            val uri = URI(raw)
            require(uri.scheme == "http" || uri.scheme == "https")
            require(!uri.host.isNullOrBlank())
            raw
        }.getOrNull()
    }

    private fun toOllamaMessage(message: AIMessage): OllamaChatMessage =
        OllamaChatMessage(
            role = when (message.role) {
                Role.SYSTEM -> "system"
                Role.USER -> "user"
                Role.ASSISTANT -> "assistant"
                Role.TOOL -> "tool"
            },
            content = message.content,
        )

    private fun sameModel(advertised: String, selected: String): Boolean =
        advertised == selected ||
            advertised.removeSuffix(":latest") == selected.removeSuffix(":latest")

    private fun ollamaHttpError(code: Int, body: String?): String {
        val detail = body?.let {
            runCatching { JSONObject(it).optString("error") }.getOrNull()
        }?.takeIf { it.isNotBlank() }

        return if (detail == null) {
            "Ollama returned HTTP $code"
        } else {
            "Ollama returned HTTP $code: $detail"
        }
    }

    private fun isAndroidEmulator(): Boolean =
        Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true)

    suspend fun close() {
        Log.i(tag, "Disconnecting from Ollama")
        client.connectionPool.evictAll()
        modelMetadataCache.clear()
    }

    private companion object {
        const val MODEL_METADATA_TTL_MS = 5 * 60_000L
    }
}
