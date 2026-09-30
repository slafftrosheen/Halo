package com.halo.app.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * MINING-P1-001 MCP transport.
 *
 * One small interface so `McpClient` never knows about HTTP, and so the whole fabric can be tested
 * without a network. The HTTP implementation speaks MCP's streamable HTTP transport: a JSON-RPC
 * POST that may answer `application/json` or `text/event-stream`.
 */
interface McpTransport {
    suspend fun post(
        endpoint: String,
        body: String,
        bearerToken: String?,
        sessionId: String?,
        protocolVersion: String?,
    ): McpHttpResponse
}

data class McpHttpResponse(
    val status: Int,
    /** Response body for `application/json`, or the last `data:` event for SSE. */
    val body: String,
    val sessionId: String?,
    val contentType: String?,
) {
    val isSuccess: Boolean get() = status in 200..299

    /** 202 means "notification accepted, no payload" in streamable HTTP. */
    val isAcceptedNotification: Boolean get() = status == 202 && body.isBlank()
}

class HttpMcpTransport(
    private val client: OkHttpClient = defaultClient(),
) : McpTransport {

    override suspend fun post(
        endpoint: String,
        body: String,
        bearerToken: String?,
        sessionId: String?,
        protocolVersion: String?,
    ): McpHttpResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
            .apply {
                sessionId?.takeIf { it.isNotBlank() }?.let { header("Mcp-Session-Id", it) }
                protocolVersion?.takeIf { it.isNotBlank() }?.let { header("MCP-Protocol-Version", it) }
                bearerToken?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
            }
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, io: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(
                        McpProtocolException(McpStage.TRANSPORT, null, "Transport failure (${io.javaClass.simpleName})"))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            val payload = response.body?.byteStream()?.use { input ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (output.size() + count > MAX_RESPONSE_BYTES) throw IOException("Response too large")
                                    output.write(buffer, 0, count)
                                }
                                output.toByteArray()
                            }
                            val text = payload?.toString(Charsets.UTF_8).orEmpty()
                            val contentType = response.header("Content-Type")
                            val result = McpHttpResponse(
                                status = response.code,
                                body = if (contentType?.contains("text/event-stream") == true) lastSseData(text) else text,
                                sessionId = response.header("Mcp-Session-Id"),
                                contentType = contentType,
                            )
                            if (continuation.isActive) continuation.resume(result)
                        }
                    } catch (_: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(
                            McpProtocolException(McpStage.TRANSPORT, null, "Response could not be read within limits"))
                    }
                }
            })
        }
    }

    /** SSE frames are `data: {...}` lines separated by blank lines; we need the last message. */
    private fun lastSseData(raw: String): String {
        val events = raw.replace("\r\n", "\n").split("\n\n")
        for (event in events.asReversed()) {
            val dataLines = event.lineSequence()
                .filter { it.startsWith("data:") }
                .map { it.removePrefix("data:").trim() }
                .toList()
            if (dataLines.isNotEmpty()) return dataLines.joinToString("\n")
        }
        return ""
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 1024 * 1024
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * Deliberately short timeouts: an MCP call is bounded work inside a bounded agent run. A
         * stalled server must not hold a native execution lease open.
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
