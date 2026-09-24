package com.halo.providers.llamacpp

import android.os.Build
import android.util.Log
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Local Ollama inference backend.
 *
 * The runtime-facing contract is [AiInferenceProvider]. Legacy completion/stream
 * helpers remain as concrete methods for callers that have not migrated yet.
 *
 * The emulator host alias 10.0.2.2 is only a development convenience. A physical
 * Android device must use an explicit LAN/local endpoint configured by the user.
 */
class OllamaProvider(
    private val storage: Storage
) : AiInferenceProvider {
    private val TAG = "OllamaProvider"
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val defaultEndpoint: String?
        get() = if (isAndroidEmulator()) "http://10.0.2.2:11434" else null
    private val _status = MutableStateFlow(ProviderStatus.UNAVAILABLE)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()
    private val baseUrl: String
        get() = storage.getString("ollama_endpoint")?.trim()?.trimEnd('/')?.ifBlank { null } ?: defaultEndpoint.orEmpty()
    private val modelId: String
        get() = storage.getString("ollama_model")?.trim()?.ifBlank { null } ?: "llama3"
    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor { Log.d(TAG, it) }
        logging.level = HttpLoggingInterceptor.Level.BASIC
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    private val moshi = com.squareup.moshi.Moshi.Builder()
        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
    private val chatRequestAdapter = moshi.adapter(OllamaChatRequest::class.java)
    private val chatResponseAdapter = moshi.adapter(OllamaChatResponse::class.java)

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
            probe.error != null -> ProviderStatus(ProviderStatus.State.OFFLINE, "Ollama unreachable at $endpoint: ${probe.error}")
            probe.models.isEmpty() -> ProviderStatus(ProviderStatus.State.OFFLINE, "Ollama responded at $endpoint but returned no models")
            probe.models.none { sameModel(it.id, modelId) } -> ProviderStatus(ProviderStatus.State.MODEL_MISSING, "Connected to $endpoint, but model '$modelId' is not pulled")
            else -> ProviderStatus(ProviderStatus.State.READY, "Connected to $endpoint • $modelId")
        }
        _status.value = status
        status
    }

    override suspend fun getCapabilities(): Set<AiCapability> = setOf(AiCapability.TEXT, AiCapability.STREAMING)

    override suspend fun testConnection(): Boolean = getStatus().state == ProviderStatus.State.READY
    suspend fun health(): Boolean = normalizedEndpointOrNull()?.let { fetchModels(it).error == null } == true

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
        request.validateForTextGeneration()
        val model = request.modelId?.trim()?.ifBlank { null } ?: modelId
        val messages = context.conversation.toMutableList().apply {
            context.screenContext?.let { add(AIMessage(Role.USER, it.asUntrustedPromptSection())) }
            add(AIMessage(Role.USER, request.text!!))
        }
        val legacy = completeWithModel(AIRequestLegacy(AIConversation("runtime", messages)), model)
        return AiResponse(text = legacy.text, providerId = id, modelId = model)
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
        request.validateForTextGeneration()
        emit(AiStreamEvent.Started)
        val model = request.modelId?.trim()?.ifBlank { null } ?: modelId
        val messages = context.conversation.toMutableList().apply {
            context.screenContext?.let { add(AIMessage(Role.USER, it.asUntrustedPromptSection())) }
            add(AIMessage(Role.USER, request.text!!))
        }
            .map(::toOllamaMessage)
        val endpoint = normalizedEndpointOrNull()
            ?: throw AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Ollama endpoint is not configured")
        val body = chatRequestAdapter.toJson(
            OllamaChatRequest(model = model, messages = messages, stream = true)
        ).toRequestBody(JSON)
        val output = StringBuilder()
        try {
            client.newCall(Request.Builder().url("$endpoint/api/chat").post(body).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw AiProviderException(
                        if (response.code == 404) AiErrorType.MODEL_UNAVAILABLE else AiErrorType.LOCAL_MODEL_UNAVAILABLE,
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
                            val token = it.message?.content.orEmpty()
                            if (token.isNotEmpty()) {
                                output.append(token)
                                emit(AiStreamEvent.Token(token))
                            }
                            if (it.done) return@use
                        }
                    }
                }
            }
            val text = output.toString().trim()
            if (text.isBlank()) throw AiProviderException(AiErrorType.EMPTY_RESPONSE, "Ollama produced no response text")
            emit(AiStreamEvent.Completed(AiResponse(text = text, providerId = id, modelId = model, finishReason = "stop")))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AiProviderException) {
            emit(AiStreamEvent.Failed(e))
        } catch (e: Exception) {
            emit(AiStreamEvent.Failed(AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Ollama stream failed: ${e.message ?: "network error"}", e)))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun complete(request: AIRequestLegacy): AIResponseLegacy = completeWithModel(request, modelId)

    private suspend fun completeWithModel(request: AIRequestLegacy, model: String): AIResponseLegacy = withContext(Dispatchers.IO) {
        val endpoint = normalizedEndpointOrNull() ?: throw AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Ollama endpoint is not configured")
        val startTime = System.currentTimeMillis()
        val messages = request.conversation.messages.map(::toOllamaMessage)
        val body = chatRequestAdapter.toJson(OllamaChatRequest(model = model, messages = messages, stream = false, options = mapOf("temperature" to request.temperature, "num_predict" to request.maxTokens))).toRequestBody(JSON)
        try {
            client.newCall(Request.Builder().url("$endpoint/api/chat").post(body).build()).execute().use { response ->
                if (!response.isSuccessful) throw AiProviderException(if (response.code == 404) AiErrorType.MODEL_UNAVAILABLE else AiErrorType.LOCAL_MODEL_UNAVAILABLE, ollamaHttpError(response.code, response.body?.string()))
                val json = response.body?.string() ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Empty body")
                val result = chatResponseAdapter.fromJson(json) ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Failed to parse response")
                AIResponseLegacy(result.message?.content.orEmpty(), if (result.done) "stop" else "length", System.currentTimeMillis() - startTime)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: AiProviderException) { throw e }
        catch (e: Exception) { throw AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Ollama completion failed: ${e.message ?: "network error"}", e) }
    }

    fun stream(prompt: String): Flow<String> = flow {
        val endpoint = normalizedEndpointOrNull() ?: throw AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Ollama endpoint is not configured")
        val body = chatRequestAdapter.toJson(OllamaChatRequest(model = modelId, messages = listOf(OllamaChatMessage("user", prompt)), stream = true)).toRequestBody(JSON)
        try {
            client.newCall(Request.Builder().url("$endpoint/api/chat").post(body).build()).execute().use { response ->
                if (!response.isSuccessful) throw AiProviderException(if (response.code == 404) AiErrorType.MODEL_UNAVAILABLE else AiErrorType.LOCAL_MODEL_UNAVAILABLE, ollamaHttpError(response.code, response.body?.string()))
                val source = response.body?.source() ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Empty body")
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
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: AiProviderException) { throw e }
        catch (e: Exception) { throw AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Ollama stream failed: ${e.message ?: "network error"}", e) }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): List<AiModel> = withContext(Dispatchers.IO) {
        fetchModels(normalizedEndpointOrNull()).models
    }

    private data class ModelProbe(val models: List<AiModel>, val error: String?)

    private suspend fun fetchModels(endpoint: String?): ModelProbe = withContext(Dispatchers.IO) {
        if (endpoint == null) return@withContext ModelProbe(emptyList(), "endpoint is not configured")
        try {
            client.newCall(Request.Builder().url("$endpoint/api/tags").get().build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext ModelProbe(emptyList(), "HTTP ${response.code}")
                val json = response.body?.string() ?: return@withContext ModelProbe(emptyList(), "empty response")
                val models = JSONObject(json).optJSONArray("models") ?: return@withContext ModelProbe(emptyList(), "missing models list")
                val result = buildList {
                    for (i in 0 until models.length()) {
                        val name = models.getJSONObject(i).optString("name").trim()
                        if (name.isNotEmpty()) add(AiModel(name, name, id, setOf(AiCapability.TEXT), 4096, true, false))
                    }
                }
                ModelProbe(result, null)
            }
        } catch (e: Exception) { ModelProbe(emptyList(), e.message ?: e.javaClass.simpleName) }
    }

    private fun normalizedEndpointOrNull(): String? {
        val configured = storage.getString("ollama_endpoint")?.trim()?.trimEnd('/')
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

    private fun toOllamaMessage(message: AIMessage): OllamaChatMessage = OllamaChatMessage(
        role = when (message.role) {
            Role.SYSTEM -> "system"
            Role.USER -> "user"
            Role.ASSISTANT -> "assistant"
            Role.TOOL -> "tool"
        },
        content = message.content
    )

    private fun sameModel(advertised: String, selected: String): Boolean =
        advertised == selected || advertised.removeSuffix(":latest") == selected.removeSuffix(":latest")

    private fun ollamaHttpError(code: Int, body: String?): String {
        val detail = body?.let { runCatching { JSONObject(it).optString("error") }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
        return if (detail == null) "Ollama returned HTTP $code" else "Ollama returned HTTP $code: $detail"
    }

    private fun isAndroidEmulator(): Boolean =
        Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true)

    suspend fun close() {
        Log.i(TAG, "Disconnecting from Ollama")
        client.connectionPool.evictAll()
    }
}
