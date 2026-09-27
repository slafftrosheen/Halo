package com.halo.providers.litert.models

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Reasons a catalog artifact can fail to reach the device.
 *
 * Callers must never invent these from an arbitrary message string: the downloader owns the
 * classification so the UI can render a stable, user-safe message.
 */
enum class ModelDownloadFailure {
    NETWORK,
    HTTP_NOT_FOUND,
    AUTH_REQUIRED,
    DISK_FULL,
    SIZE_MISMATCH,
    HASH_MISMATCH,
    IO_FAILURE,
    CANCELLED,
}

/** Progress for a single in-flight artifact transfer. */
data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val progress: Float?,
) {
    companion object {
        fun of(downloadedBytes: Long, totalBytes: Long?): DownloadProgress {
            val expected = totalBytes?.takeIf { it > 0L }
            return DownloadProgress(
                downloadedBytes = downloadedBytes,
                totalBytes = expected,
                progress = expected?.let { (downloadedBytes.toFloat() / it.toFloat()).coerceIn(0f, 1f) },
            )
        }
    }
}

/** Outcome of a completed artifact transfer. Bytes and SHA are always computed from the stream. */
sealed interface DownloadResult {
    data class Success(
        val file: File,
        val bytes: Long,
        val sha256: String,
    ) : DownloadResult

    data class Failure(
        val kind: ModelDownloadFailure,
        val userMessage: String,
        val technicalMessage: String? = null,
        val cause: Throwable? = null,
    ) : DownloadResult
}

/** Observable lifecycle for a single catalog artifact. */
sealed interface DownloadState {
    data object NotInstalled : DownloadState

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long?,
        val progress: Float?,
    ) : DownloadState

    data class Verifying(
        val downloadedBytes: Long,
        val totalBytes: Long?,
    ) : DownloadState

    data object Installed : DownloadState

    data class Failed(
        val kind: ModelDownloadFailure,
        val userMessage: String,
        val technicalMessage: String? = null,
    ) : DownloadState
}

/**
 * Streams a pinned catalog artifact to a `.part` file and proves its integrity before returning.
 *
 * The integrity authority is the byte stream, never an HTTP header: `Content-Length` may be
 * absent or wrong behind a CDN/redirect and is used for progress reporting only.
 */
interface LiteRtModelDownloader {
    suspend fun download(
        artifact: LiteRtCatalogModel,
        destinationPart: File,
        onProgress: (DownloadProgress) -> Unit,
    ): DownloadResult
}

class OkHttpLiteRtModelDownloader(
    private val client: OkHttpClient = defaultClient(),
    private val userAgent: String = DEFAULT_USER_AGENT,
    /**
     * Test seam for pointing at a local server (MockWebServer) or an operator mirror.
     * Production always uses the pinned [LiteRtCatalogModel.downloadUrl].
     */
    private val resolveUrl: (LiteRtCatalogModel) -> String = { it.downloadUrl },
) : LiteRtModelDownloader {

    override suspend fun download(
        artifact: LiteRtCatalogModel,
        destinationPart: File,
        onProgress: (DownloadProgress) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val url = resolveUrl(artifact)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .header("Accept-Encoding", "identity")
            .header("User-Agent", userAgent)
            .build()

        val call = client.newCall(request)
        val cancellationHandle = kotlinx.coroutines.currentCoroutineContext()[Job]
            ?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }

        Log.i(TAG, "$EVENT_START id=${artifact.id} revision=${artifact.revision} url=$url")

        val destinationParent = destinationPart.parentFile
        if (destinationParent != null && !destinationParent.exists() && !destinationParent.mkdirs()) {
            return@withContext failure(
                ModelDownloadFailure.IO_FAILURE,
                "Could not prepare the model directory.",
                "mkdirs failed for ${destinationParent.absolutePath}",
            )
        }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext failure(
                        httpFailureKind(response.code),
                        httpFailureMessage(response.code),
                        "HTTP ${response.code} ${response.message} for $url",
                    )
                }
                val body = response.body ?: return@withContext failure(
                    ModelDownloadFailure.IO_FAILURE,
                    "The download server returned no data.",
                    "HTTP ${response.code} with an empty body",
                )

                // Header value: used for progress only. Never an integrity authority.
                val headerLength = body.contentLength().takeIf { it >= 0L }
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L

                body.byteStream().use { input ->
                    destinationPart.outputStream().use { output ->
                        digest.reset()
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(DownloadProgress.of(downloaded, headerLength ?: artifact.expectedBytes))
                        }
                        output.flush()
                        output.fd.sync()
                    }
                }

                val actualBytes = destinationPart.length()
                val actualSha = digest.digest().joinToString("") { "%02x".format(it) }

                if (actualBytes != artifact.expectedBytes) {
                    Log.e(
                        TAG,
                        "$EVENT_FAILED id=${artifact.id} reason=SIZE_MISMATCH expectedBytes=${artifact.expectedBytes} " +
                            "actualBytes=$actualBytes httpStatus=${response.code} revision=${artifact.revision}",
                    )
                    return@withContext failure(
                        ModelDownloadFailure.SIZE_MISMATCH,
                        INTEGRITY_USER_MESSAGE,
                        "Expected ${artifact.expectedBytes} bytes, received $actualBytes " +
                            "(HTTP ${response.code}, revision ${artifact.revision})",
                    )
                }

                if (!actualSha.equals(artifact.sha256, ignoreCase = true)) {
                    Log.e(
                        TAG,
                        "$EVENT_FAILED id=${artifact.id} reason=HASH_MISMATCH expectedSha=${artifact.sha256} " +
                            "actualSha=$actualSha bytes=$actualBytes revision=${artifact.revision}",
                    )
                    return@withContext failure(
                        ModelDownloadFailure.HASH_MISMATCH,
                        INTEGRITY_USER_MESSAGE,
                        "Expected SHA-256 ${artifact.sha256}, received $actualSha " +
                            "(HTTP ${response.code}, revision ${artifact.revision})",
                    )
                }

                Log.i(
                    TAG,
                    "$EVENT_VERIFIED id=${artifact.id} bytes=$actualBytes sha256=$actualSha revision=${artifact.revision}",
                )
                DownloadResult.Success(destinationPart, actualBytes, actualSha)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (io: IOException) {
            val cancelled = call.isCanceled() || io is InterruptedIOException && io.message?.contains("Canceled") == true
            when {
                cancelled -> failure(
                    ModelDownloadFailure.CANCELLED,
                    "Download cancelled.",
                    io.message,
                    userCancelled = true,
                )
                isDiskFull(io) -> failure(
                    ModelDownloadFailure.DISK_FULL,
                    "Not enough free space to install this model.",
                    io.message,
                )
                else -> {
                    Log.e(TAG, "$EVENT_FAILED id=${artifact.id} reason=NETWORK", io)
                    failure(
                        ModelDownloadFailure.NETWORK,
                        "Download failed. Check your connection and try again.",
                        io.message,
                    )
                }
            }
        } catch (error: Throwable) {
            Log.e(TAG, "$EVENT_FAILED id=${artifact.id} reason=IO_FAILURE", error)
            failure(
                ModelDownloadFailure.IO_FAILURE,
                "The model could not be saved to this device.",
                "${error::class.java.simpleName}: ${error.message}",
            )
        } finally {
            cancellationHandle?.dispose()
            call.cancel()
        }
    }

    private fun httpFailureKind(code: Int): ModelDownloadFailure = when (code) {
        401, 403 -> ModelDownloadFailure.AUTH_REQUIRED
        404 -> ModelDownloadFailure.HTTP_NOT_FOUND
        else -> ModelDownloadFailure.NETWORK
    }

    private fun httpFailureMessage(code: Int): String = when (code) {
        401, 403 -> "This model repository requires authentication."
        404 -> "This model artifact is no longer available at the pinned revision."
        else -> "The download server rejected the request (HTTP $code)."
    }

    private fun isDiskFull(error: IOException): Boolean {
        val message = error.message?.lowercase() ?: return false
        return message.contains("enospc") || message.contains("no space left") || message.contains("disk full")
    }

    private fun failure(
        kind: ModelDownloadFailure,
        userMessage: String,
        technicalMessage: String?,
        cause: Throwable? = null,
        userCancelled: Boolean = false,
    ): DownloadResult.Failure {
        if (!userCancelled) {
            Log.w(TAG, "$EVENT_FAILED kind=$kind detail=$technicalMessage")
        }
        return DownloadResult.Failure(kind, userMessage, technicalMessage, cause)
    }

    companion object {
        private const val TAG = "HALO_LITERT_DOWNLOAD"

        const val EVENT_START = "HALO_LITERT_DOWNLOAD_START"
        const val EVENT_PROGRESS = "HALO_LITERT_DOWNLOAD_PROGRESS"
        const val EVENT_VERIFIED = "HALO_LITERT_DOWNLOAD_VERIFY"
        const val EVENT_FAILED = "HALO_LITERT_DOWNLOAD_FAILED"

        const val INTEGRITY_USER_MESSAGE = "Downloaded model failed integrity verification."

        const val DEFAULT_USER_AGENT = "Halo-Android/1.0 (LiteRT model downloader)"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build()
    }
}
