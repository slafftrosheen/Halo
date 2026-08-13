package com.halo.providers.llamacpp

import android.util.Log
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
import java.io.IOException
import java.util.concurrent.TimeUnit

class OllamaProvider(
    private val storage: Storage
) : InferenceProvider {

    private val TAG = "OllamaProvider"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val _status = MutableStateFlow(ProviderStatus.UNAVAILABLE)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()

    private val baseUrl: String get() = storage.getString("ollama_endpoint") ?: "http://10.0.2.2:11434"
    private val modelId: String get() = storage.getString("ollama_model") ?: "llama3"

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor { Log.d(TAG, it) }
        logging.level = HttpLoggingInterceptor.Level.BASIC

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val generateRequestAdapter = moshi.adapter(OllamaGenerateRequest::class.java)
    private val generateResponseAdapter = moshi.adapter(OllamaGenerateResponse::class.java)

    override val metadata = ProviderMetadata(
        id = "provider.ollama",
        displayName = "Ollama",
        local = true,
        supportsStreaming = true
    )

    override suspend fun getStatus(): ProviderStatus {
        val current = if (health()) ProviderStatus.READY else ProviderStatus.UNAVAILABLE
        _status.value = current
        return current
    }

    override suspend fun health(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/tags")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
        val conversation = AIConversation(
            id = "legacy",
            messages = context.conversation.toMutableList()
        )
        val legacyRequest = AIRequestLegacy(conversation)
        val model = request.modelId ?: modelId
        val legacyResponse = completeWithModel(legacyRequest, model)
        return AiResponse(text = legacyResponse.text, providerId = id, modelId = model)
    }

    override suspend fun complete(request: AIRequestLegacy): AIResponseLegacy {
        return completeWithModel(request, modelId)
    }

    private suspend fun completeWithModel(request: AIRequestLegacy, model: String): AIResponseLegacy {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val prompt = request.conversation.messages.joinToString("\n") {
                "${it.role.name.lowercase()}: ${it.content}"
            }
            
            val ollamaRequest = OllamaGenerateRequest(
                model = model,
                prompt = prompt,
                stream = false,
                options = mapOf(
                    "temperature" to request.temperature,
                    "num_predict" to request.maxTokens
                )
            )

            val body = generateRequestAdapter.toJson(ollamaRequest).toRequestBody(JSON)
            val httpRequest = Request.Builder()
                .url("$baseUrl/api/generate")
                .post(body)
                .build()

            try {
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorType = when (response.code) {
                            404 -> AiErrorType.MODEL_UNAVAILABLE
                            else -> AiErrorType.LOCAL_MODEL_UNAVAILABLE
                        }
                        throw AiProviderException(errorType, "Ollama error: ${response.code}")
                    }
                    val json = response.body?.string() ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Empty body")
                    val ollamaResponse = generateResponseAdapter.fromJson(json) 
                        ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Failed to parse response")

                    AIResponseLegacy(
                        text = ollamaResponse.response,
                        finishReason = if (ollamaResponse.done) "stop" else "length",
                        latencyMs = System.currentTimeMillis() - startTime
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e is AiProviderException) throw e
                throw AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Ollama completion failed", e)
            }
        }
    }

    override fun stream(prompt: String): Flow<String> = flow {
        val ollamaRequest = OllamaGenerateRequest(
            model = modelId,
            prompt = prompt,
            stream = true
        )

        val body = generateRequestAdapter.toJson(ollamaRequest).toRequestBody(JSON)
        val request = Request.Builder()
            .url("$baseUrl/api/generate")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorType = when (response.code) {
                        404 -> AiErrorType.MODEL_UNAVAILABLE
                        else -> AiErrorType.LOCAL_MODEL_UNAVAILABLE
                    }
                    throw AiProviderException(errorType, "Ollama error: ${response.code}")
                }

                val source = response.body?.source() ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Empty body")
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8LineStrict()
                    if (line.isNotBlank()) {
                        try {
                            val ollamaResponse = generateResponseAdapter.fromJson(line)
                            if (ollamaResponse != null) {
                                emit(ollamaResponse.response)
                                if (ollamaResponse.done) {
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "JSON parsing error on line: $line", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (e is AiProviderException) throw e
            throw AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Ollama stream failed", e)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): List<AiModel> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/tags")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val json = response.body?.string() ?: return@withContext emptyList()
                val obj = JSONObject(json)
                val models = obj.getJSONArray("models")
                val result = mutableListOf<AiModel>()
                for (i in 0 until models.length()) {
                    val m = models.getJSONObject(i)
                    val name = m.getString("name")
                    result.add(AiModel(
                        id = name,
                        displayName = name,
                        providerId = id,
                        inputCapabilities = setOf(AiCapability.TEXT),
                        contextLength = 4096,
                        supportsStreaming = true,
                        supportsTools = false
                    ))
                }
                result
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun close() {
        Log.i(TAG, "Disconnecting from Ollama")
        client.connectionPool.evictAll()
    }
}
