package com.halo.providers.llamacpp

import android.util.Log
import com.halo.sdk.provider.AIRequest
import com.halo.sdk.provider.AIResponse

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

import com.halo.sdk.provider.InferenceProvider

class OllamaProvider(
    private val baseUrl: String = "http://10.0.2.2:11434",
    private val modelId: String = "llama3"
) : InferenceProvider {

    private val TAG = "OllamaProvider"
    private val JSON = "application/json; charset=utf-8".toMediaType()

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

    override val metadata = com.halo.sdk.provider.ProviderMetadata(
        id = "provider.ollama",
        displayName = "Ollama",
        local = true,
        supportsStreaming = true
    )

    suspend fun connect() {
        Log.i(TAG, "Connecting to Ollama at $baseUrl")
        // Just warm up the client
        client.connectionPool.idleConnectionCount() 
    }

    override suspend fun health(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/tags")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val isHealthy = response.isSuccessful
                    Log.i(TAG, "Health check result: $isHealthy")
                    isHealthy
                }
            } catch (e: Exception) {
                Log.e(TAG, "Health check failed: ${e.message}")
                false
            }
        }
    }

    override suspend fun complete(request: AIRequest): AIResponse {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val prompt = request.conversation.messages.joinToString("\n") {
                "${it.role.name.lowercase()}: ${it.content}"
            }
            
            val ollamaRequest = OllamaGenerateRequest(
                model = modelId,
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
                        throw IOException("Unexpected code $response")
                    }
                    val json = response.body?.string() ?: throw IOException("Empty body")
                    val ollamaResponse = generateResponseAdapter.fromJson(json) 
                        ?: throw IOException("Failed to parse response")

                    AIResponse(
                        text = ollamaResponse.response,
                        finishReason = if (ollamaResponse.done) "stop" else "length",
                        latencyMs = System.currentTimeMillis() - startTime
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Completion failed", e)
                AIResponse(
                    text = "Error: ${e.message}",
                    finishReason = "error",
                    latencyMs = System.currentTimeMillis() - startTime
                )
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
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val source = response.body?.source() ?: throw IOException("Empty body")
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
            Log.e(TAG, "Streaming failed", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun close() {
        Log.i(TAG, "Disconnecting from Ollama")
        client.connectionPool.evictAll()
    }

    fun disconnect() {
        client.connectionPool.evictAll()
    }
}
