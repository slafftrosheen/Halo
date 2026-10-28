package com.halo.providers.openrouter

import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiProviderException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import java.io.ByteArrayOutputStream

/** Bounds apply before JSON/SSE allocation. Image payloads retain their separate existing owner. */
internal object OpenRouterTransportBounds {
    const val JSON_BYTES = 8 * 1024 * 1024
    const val CATALOG_BYTES = 16 * 1024 * 1024
    const val ERROR_BYTES = 64 * 1024
    const val STREAM_LINE_BYTES = 1024 * 1024L
    const val STREAM_BYTES = 32 * 1024 * 1024L
    const val TEXT_CHARS = 4 * 1024 * 1024
    const val TOOL_CALLS = 32
    const val TOOL_ARGUMENT_CHARS = 1024 * 1024
    const val TOOL_ARGUMENT_TOTAL_CHARS = 4 * 1024 * 1024

    fun exceeded(): Nothing = throw AiProviderException(AiErrorType.INVALID_RESPONSE,
        "OpenRouter response exceeded the supported size limit")
}

/** The child is installed before execute, and remains alive through body consumption.
 * Cancellation closes both blocked header and body reads; no retry or dispatch owner is added.
 */
internal suspend fun <T> Call.consumeCancellable(block: suspend (Response) -> T): T = coroutineScope {
    val cancellation = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() } finally { this@consumeCancellable.cancel() }
    }
    try {
        ensureActive()
        execute().use { response -> ensureActive(); block(response) }
    } catch (error: Exception) {
        // A cancelled socket often throws IOException. Preserve cancellation semantics for Stop.
        ensureActive()
        throw error
    } finally {
        cancellation.cancel()
    }
}

internal fun ResponseBody.boundedText(limit: Int = OpenRouterTransportBounds.JSON_BYTES, truncate: Boolean = false): String {
    if (!truncate && contentLength() > limit) OpenRouterTransportBounds.exceeded()
    val output = ByteArrayOutputStream()
    byteStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val remaining = limit - output.size()
            val size = input.read(buffer, 0, minOf(buffer.size, remaining + 1))
            if (size < 0) break
            if (size > remaining) {
                if (!truncate) OpenRouterTransportBounds.exceeded()
                output.write(buffer, 0, remaining)
                break
            }
            output.write(buffer, 0, size)
        }
    }
    return output.toString("UTF-8")
}

internal fun BufferedSource.boundedSseLine(): String? {
    val newline = indexOf('\n'.code.toByte(), 0, OpenRouterTransportBounds.STREAM_LINE_BYTES + 1)
    if (newline >= 0) {
        val line = readUtf8(newline)
        skip(1)
        return line.removeSuffix("\r")
    }
    if (buffer.size > OpenRouterTransportBounds.STREAM_LINE_BYTES) OpenRouterTransportBounds.exceeded()
    // Preserve a final unterminated line at EOF without permitting an unbounded scan.
    return readUtf8().takeIf { it.isNotEmpty() }
}
