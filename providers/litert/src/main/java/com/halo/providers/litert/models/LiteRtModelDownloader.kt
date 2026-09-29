package com.halo.providers.litert.models

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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

/** True when a partial download is worth keeping for a later resume. */
val ModelDownloadFailure.isResumable: Boolean
    get() = when (this) {
        ModelDownloadFailure.NETWORK,
        ModelDownloadFailure.DISK_FULL,
        ModelDownloadFailure.CANCELLED,
        ModelDownloadFailure.IO_FAILURE,
        -> true
        ModelDownloadFailure.SIZE_MISMATCH,
        ModelDownloadFailure.HASH_MISMATCH,
        ModelDownloadFailure.HTTP_NOT_FOUND,
        ModelDownloadFailure.AUTH_REQUIRED,
        -> false
    }

/** Progress for a single in-flight artifact transfer. [downloadedBytes] includes a resumed prefix. */
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
        /** Bytes that came from a previously stored partial, or 0 when the transfer started fresh. */
        val resumedFrom: Long = 0L,
        val etag: String? = null,
        val lastModified: String? = null,
    ) : DownloadResult

    data class Failure(
        val kind: ModelDownloadFailure,
        val userMessage: String,
        val technicalMessage: String? = null,
        val cause: Throwable? = null,
        /** Bytes already on disk when the transfer stopped, so a resume can continue. */
        val partialBytes: Long = 0L,
    ) : DownloadResult
}

/** Observable lifecycle for a single catalog artifact. */
sealed interface DownloadState {
    data object NotInstalled : DownloadState
    data object Queued : DownloadState
    data object Preparing : DownloadState

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long?,
        val progress: Float?,
        val resumedFrom: Long = 0L,
    ) : DownloadState

    data class Verifying(
        val downloadedBytes: Long,
        val totalBytes: Long?,
    ) : DownloadState

    data object Installed : DownloadState

    /** A resumable partial exists on disk. Never treated as installed. */
    data class Paused(
        val downloadedBytes: Long,
        val totalBytes: Long?,
    ) : DownloadState

    data class Failed(
        val kind: ModelDownloadFailure,
        val userMessage: String,
        val technicalMessage: String? = null,
        /** True when a partial remains on disk and "Resume" is meaningful. */
        val resumable: Boolean = false,
    ) : DownloadState
}

/**
 * Streams a pinned catalog artifact to a `.part` file and proves its integrity before returning.
 *
 * The integrity authority is the byte stream, never an HTTP header: `Content-Length` may be
 * absent or wrong behind a CDN/redirect and is used for progress reporting only. A resumed
 * transfer is verified over the *whole* file, so a corrupted or alien prefix cannot be
 * mistaken for a good artifact.
 */
interface LiteRtModelDownloader {
    suspend fun download(
        artifact: LiteRtCatalogModel,
        destinationPart: File,
        onProgress: (DownloadProgress) -> Unit,
        /**
         * Byte offset to resume from. The implementation may ignore it (server ignored the Range
         * request) and restart from zero; the caller only trusts the final verified result.
         */
        resumeFrom: Long = 0L,
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
        resumeFrom: Long,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val url = resolveUrl(artifact)
        val requestedResume = resumeFrom > 0L && resumeFrom < artifact.expectedBytes
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .header("Accept-Encoding", "identity")
            .header("User-Agent", userAgent)
        if (requestedResume) builder.header("Range", "bytes=$resumeFrom-")
        val request = builder.build()

        val call = client.newCall(request)
        val cancellationHandle = kotlinx.coroutines.currentCoroutineContext()[Job]
            ?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }

        if (requestedResume) {
            Log.i(
                TAG,
                "$EVENT_RESUME id=${artifact.id} from=$resumeFrom expected=${artifact.expectedBytes} url=$url",
            )
        } else {
            Log.i(TAG, "$EVENT_START id=${artifact.id} revision=${artifact.revision} url=$url")
        }

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
                        partialBytes = destinationPart.takeIf { it.exists() }?.length() ?: 0L,
                    )
                }
                val body = response.body ?: return@withContext failure(
                    ModelDownloadFailure.IO_FAILURE,
                    "The download server returned no data.",
                    "HTTP ${response.code} with an empty body",
                )

                val contentRangeStart = parseContentRangeStart(response.header("Content-Range"))
                val serverHonouredRange = response.code == 206 &&
                    contentRangeStart == resumeFrom &&
                    destinationPart.exists() &&
                    destinationPart.length() == resumeFrom
                val append = requestedResume && serverHonouredRange
                if (requestedResume && !append) {
                    Log.i(
                        TAG,
                        "$EVENT_RESUME_ABANDONED id=${artifact.id} from=$resumeFrom code=${response.code} " +
                            "contentRange=${response.header("Content-Range") ?: "none"} — restarting from 0",
                    )
                }

                // Header value: used for progress only. Never an integrity authority.
                val headerLength = body.contentLength().takeIf { it >= 0L }
                val totalBytes = if (append) headerLength?.let { resumeFrom + it } ?: artifact.expectedBytes
                else headerLength

                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                if (append) {
                    downloaded = hashPrefix(destinationPart, digest)
                } else if (destinationPart.exists() && !destinationPart.delete()) {
                    return@withContext failure(
                        ModelDownloadFailure.IO_FAILURE,
                        "Could not replace the partial model file.",
                        "delete failed for ${destinationPart.absolutePath}",
                    )
                }

                val output = FileOutputStream(destinationPart, append)
                output.use { stream ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            digest.update(buffer, 0, read)
                            stream.write(buffer, 0, read)
                            downloaded += read
                            onProgress(DownloadProgress.of(downloaded, totalBytes ?: artifact.expectedBytes))
                        }
                        stream.flush()
                        stream.fd.sync()
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
                        partialBytes = actualBytes,
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
                        partialBytes = actualBytes,
                    )
                }

                Log.i(
                    TAG,
                    "$EVENT_VERIFIED id=${artifact.id} bytes=$actualBytes sha256=$actualSha " +
                        "resumedFrom=${if (append) resumeFrom else 0L} revision=${artifact.revision}",
                )
                DownloadResult.Success(
                    file = destinationPart,
                    bytes = actualBytes,
                    sha256 = actualSha,
                    resumedFrom = if (append) resumeFrom else 0L,
                    etag = response.header("ETag"),
                    lastModified = response.header("Last-Modified"),
                )
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
                    partialBytes = destinationPart.takeIf { it.exists() }?.length() ?: 0L,
                )
                isDiskFull(io) -> failure(
                    ModelDownloadFailure.DISK_FULL,
                    "Not enough free space to install this model.",
                    io.message,
                    partialBytes = destinationPart.takeIf { it.exists() }?.length() ?: 0L,
                )
                else -> {
                    Log.e(TAG, "$EVENT_FAILED id=${artifact.id} reason=NETWORK", io)
                    failure(
                        ModelDownloadFailure.NETWORK,
                        "Download failed. Check your connection and try again.",
                        io.message,
                        partialBytes = destinationPart.takeIf { it.exists() }?.length() ?: 0L,
                    )
                }
            }
        } catch (error: Throwable) {
            Log.e(TAG, "$EVENT_FAILED id=${artifact.id} reason=IO_FAILURE", error)
            failure(
                ModelDownloadFailure.IO_FAILURE,
                "The model could not be saved to this device.",
                "${error::class.java.simpleName}: ${error.message}",
                partialBytes = destinationPart.takeIf { it.exists() }?.length() ?: 0L,
            )
        } finally {
            cancellationHandle?.dispose()
            call.cancel()
        }
    }

    /** Feeds an existing partial prefix into [digest] and returns its byte count. */
    private fun hashPrefix(file: File, digest: MessageDigest): Long {
        var bytes = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                bytes += read
            }
        }
        return bytes
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
        partialBytes: Long = 0L,
    ): DownloadResult.Failure {
        if (!userCancelled) {
            Log.w(TAG, "$EVENT_FAILED kind=$kind detail=$technicalMessage partialBytes=$partialBytes")
        }
        return DownloadResult.Failure(kind, userMessage, technicalMessage, cause, partialBytes)
    }

    companion object {
        private const val TAG = "HALO_LITERT_DOWNLOAD"

        const val EVENT_START = "HALO_LITERT_DOWNLOAD_START"
        const val EVENT_PROGRESS = "HALO_LITERT_DOWNLOAD_PROGRESS"
        const val EVENT_VERIFIED = "HALO_LITERT_DOWNLOAD_VERIFY"
        const val EVENT_FAILED = "HALO_LITERT_DOWNLOAD_FAILED"
        const val EVENT_RESUME = "HALO_LITERT_RESUME"
        const val EVENT_RESUME_ABANDONED = "HALO_LITERT_RESUME_ABANDONED"

        const val INTEGRITY_USER_MESSAGE = "Downloaded model failed integrity verification."

        const val DEFAULT_USER_AGENT = "Halo-Android/1.0 (LiteRT model downloader)"

        /** `Content-Range: bytes 100-199/200` -> 100, or null when absent/unparseable. */
        internal fun parseContentRangeStart(value: String?): Long? {
            val raw = value?.trim()?.removePrefix("bytes")?.trim() ?: return null
            val start = raw.substringBefore('-').trim().toLongOrNull() ?: return null
            return start.takeIf { it >= 0L }
        }

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
