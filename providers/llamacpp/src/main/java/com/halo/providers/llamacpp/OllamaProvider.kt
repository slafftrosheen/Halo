package com.halo.providers.llamacpp

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

class OllamaProvider(
    private val storage: Storage
) : InferenceProvider {
    private val TAG = "OllamaProvider"
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val defaultEndpoint = "http://10.0.2.2:11434"
    private val _status = MutableStateFlow(ProviderStatus.UNAVAILABLE)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()
    private val baseUrl: String
        get() = storage.getString("ollama_endpoint")?.trim()?.trimEnd('/')?.ifBlank { null } ?: defaultEndpoint
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
    private val generateRequestAdapter = moshi.adapter(OllamaGenerateRequest::class.java)
    private val generateResponseAdapter = moshi.adapter(OllamaGenerateResponse::class.java)

    override val metadata = ProviderMetadata(id = "provider.ollama", displayName = "Ollama", local = true, supportsStreaming = true)

    override suspend fun getStatus(): ProviderStatus {
        val endpoint = normalizedEndpointOrNull() ?: return ProviderStatus(ProviderStatus.State.UNCONFIGURED, "Ollama endpoint is not configured").also { _status.value = it }
        val probe = fetchModels(endpoint)
        val status = when {
            probe.error != null -> ProviderStatus(ProviderStatus.State.OFFLINE, "Ollama unreachable at $endpoint: ${probe.error}")
            probe.models.isEmpty() -> ProviderStatus(ProviderStatus.State.OFFLINE, "Ollama responded at $endpoint but returned no models")
            probe.models.none { it.id == modelId } -> ProviderStatus(ProviderStatus.State.MODEL_MISSING, "Connected to $endpoint, but model '$modelId' is not pulled")
            else -> ProviderStatus(ProviderStatus.State.READY, "Connected to $endpoint • $modelId")
        }
        _status.value = status
        return status
    }

    override suspend fun testConnection(): Boolean = getStatus().state == ProviderStatus.State.READY
    override suspend fun health(): Boolean = fetchModels(normalizedEndpointOrNull()).error == null

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
        request.validateForTextGeneration()
        val model = request.modelId?.trim()?.ifBlank { null } ?: modelId
        val messages = context.conversation.toMutableList().apply { add(AIMessage(Role.USER, request.text!!)) }
        val legacy = completeWithModel(AIRequestLegacy(AIConversation("runtime", messages)), model)
        return AiResponse(text = legacy.text, providerId = id, modelId = model)
    }

    override suspend fun complete(request: AIRequestLegacy): AIResponseLegacy = completeWithModel(request, modelId)

    private suspend fun completeWithModel(request: AIRequestLegacy, model: String): AIResponseLegacy = withContext(Dispatchers.IO) {
        val endpoint = normalizedEndpointOrNull() ?: throw AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Ollama endpoint is not configured")
        val startTime = System.currentTimeMillis()
        val prompt = request.conversation.messages.joinToString("\n") { "${it.role.name.lowercase()}: ${it.content}" }
        val body = generateRequestAdapter.toJson(OllamaGenerateRequest(model = model, prompt = prompt, stream = false, options = mapOf("temperature" to request.temperature, "num_predict" to request.maxTokens))).toRequestBody(JSON)
        try {
            client.newCall(Request.Builder().url("$endpoint/api/generate").post(body).build()).execute().use { response ->
                if (!response.isSuccessful) throw AiProviderException(if (response.code == 404) AiErrorType.MODEL_UNAVAILABLE else AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Ollama error: ${response.code}")
                val json = response.body?.string() ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Empty body")
                val result = generateResponseAdapter.fromJson(json) ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Failed to parse response")
                AIResponseLegacy(result.response, if (result.done) "stop" else "length", System.currentTimeMillis() - startTime)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: AiProviderException) { throw e }
        catch (e: Exception) { throw AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Ollama completion failed: ${e.message ?: "network error"}", e) }
    }

    override fun stream(prompt: String): Flow<String> = flow {
        val endpoint = normalizedEndpointOrNull() ?: throw AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Ollama endpoint is not configured")
        val body = generateRequestAdapter.toJson(OllamaGenerateRequest(model = modelId, prompt = prompt, stream = true)).toRequestBody(JSON)
        try {
            client.newCall(Request.Builder().url("$endpoint/api/generate").post(body).build()).execute().use { response ->
                if (!response.isSuccessful) throw AiProviderException(if (response.code == 404) AiErrorType.MODEL_UNAVAILABLE else AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Ollama error: ${response.code}")
                val source = response.body?.source() ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Empty body")
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8LineStrict()
                    if (line.isNotBlank()) {
                        generateResponseAdapter.fromJson(line)?.let {
                            emit(it.response)
                            if (it.done) return@use
                        }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: AiProviderException) { throw e }
        catch (e: Exception) { throw AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Ollama stream failed: ${e.message ?: "network error"}", e) }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): List<AiModel> = fetchModels(normalizedEndpointOrNull()).models

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
        val raw = storage.getString("ollama_endpoint")?.trim()?.trimEnd('/') ?: defaultEndpoint
        if (raw.isBlank()) return null
        return runCatching {
            val uri = URI(raw)
            require(uri.scheme == "http" || uri.scheme == "https")
            require(!uri.host.isNullOrBlank())
            raw
        }.getOrNull()
    }

    override suspend fun close() {
        Log.i(TAG, "Disconnecting from Ollama")
        client.connectionPool.evictAll()
    }
}
