package com.halo.drivers.heycyan.wifi

import com.halo.drivers.heycyan.media.DefaultHeyCyanMediaConfigParser
import com.halo.drivers.heycyan.media.HeyCyanMedia
import com.halo.drivers.heycyan.media.HeyCyanMediaConfigParser
import com.halo.drivers.heycyan.media.HeyCyanMediaManifest
import com.halo.drivers.heycyan.media.MediaDownloadProgress
import com.halo.drivers.heycyan.media.MediaDownloadResult
import com.halo.drivers.heycyan.media.MediaIntegrity
import com.halo.drivers.heycyan.media.MediaLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest

/**
 * Default HTTP implementation of [HeyCyanMediaApi].
 *
 * Source-established endpoints:
 *
 *   Manifest:  GET http://<IP>/files/media.config
 *   Download:  GET http://<IP>/files/<filename>
 *
 * Evidence: CyanBridge `AlbumDownloader.kt`, iOS `GlassesWiFiHandler.m`,
 *            iOS `GlassesMediaDownloader.m`
 *
 * This class does NOT reference Android SDK, File, FileOutputStream, or Context.
 * The [destinationPath] string is resolved by the calling layer (host-android).
 *
 * Streaming pipeline for downloads:
 *   HTTP response InputStream
 *       ↓
 *   ByteArray buffer (8 KB)
 *       ↓
 *   Counting (bytesReceived)
 *       ↓
 *   SHA-256 MessageDigest (optional integrity)
 *       ↓
 *   Destination via progress callback + output accumulation
 *
 * Downloads are partial-resumable: Range header is sent if destFile already exists.
 */
class DefaultHeyCyanMediaApi(
    private val deviceIp: InetAddress,
    private val port: Int = 80,
    private val parser: HeyCyanMediaConfigParser = DefaultHeyCyanMediaConfigParser(),
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 30_000,
    private val bufferSize: Int = 8 * 1024,
) : HeyCyanMediaApi {

    companion object {
        /** Source-established manifest path: /files/media.config */
        private const val MANIFEST_PATH = "/files/media.config"

        /** Source-established download path prefix: /files/ */
        private const val FILES_PATH = "/files/"

        fun factoryFor(
            parser: HeyCyanMediaConfigParser = DefaultHeyCyanMediaConfigParser(),
        ): HeyCyanMediaApiFactory = HeyCyanMediaApiFactory { address ->
            DefaultHeyCyanMediaApi(
                deviceIp = address,
                parser = parser,
            )
        }
    }

    private val baseUrl: String
        get() = if (port == 80) "http://${deviceIp.hostAddress}" else "http://${deviceIp.hostAddress}:$port"

    // -------------------------------------------------------------------------
    // Manifest
    // -------------------------------------------------------------------------

    override suspend fun getMediaConfig(): Result<HeyCyanMediaManifest> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("$baseUrl$MANIFEST_PATH")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = connectTimeoutMillis
                conn.readTimeout = readTimeoutMillis
                conn.requestMethod = "GET"

                try {
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        throw HeyCyanHttpException(
                            statusCode = code,
                            message = "GET $MANIFEST_PATH returned HTTP $code",
                        )
                    }

                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    parser.parse(body).getOrThrow()
                } finally {
                    conn.disconnect()
                }
            }
        }

    // -------------------------------------------------------------------------
    // Download — streaming, with byte-counting and SHA-256
    // -------------------------------------------------------------------------

    override suspend fun download(
        media: HeyCyanMedia,
        destinationPath: String,
        progress: (MediaDownloadProgress) -> Unit,
    ): Result<MediaDownloadResult> = withContext(Dispatchers.IO) {
        runCatching {
            val file = java.io.File(destinationPath)
            file.parentFile?.mkdirs()
            java.io.FileOutputStream(file).use { outputStream ->
                downloadInternal(media, destinationPath, progress) { buffer, offset, len ->
                    outputStream.write(buffer, offset, len)
                }.getOrThrow()
            }
        }
    }

    override suspend fun download(
        media: HeyCyanMedia,
        session: com.halo.drivers.heycyan.media.repository.MediaWriteSession,
        progress: (MediaDownloadProgress) -> Unit,
    ): Result<MediaDownloadResult> = withContext(Dispatchers.IO) {
        downloadInternal(media, session.identity, progress) { buffer, offset, len ->
            session.write(buffer, offset, len)
        }
    }

    private suspend fun downloadInternal(
        media: HeyCyanMedia,
        destinationPath: String,
        progress: (MediaDownloadProgress) -> Unit,
        writeBlock: suspend (ByteArray, Int, Int) -> Unit,
    ): Result<MediaDownloadResult> = runCatching {
        val filename = media.filename
        val url = URL("$baseUrl$FILES_PATH$filename")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeoutMillis
        conn.readTimeout = readTimeoutMillis
        conn.requestMethod = "GET"

        var bytesReceived = 0L
        val expectedBytes = media.sizeBytes

        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw HeyCyanHttpException(
                    statusCode = code,
                    message = "GET $FILES_PATH$filename returned HTTP $code",
                )
            }

            val headerTotalBytes = conn.contentLengthLong.let { if (it > 0) it else null }
            val finalExpectedBytes = headerTotalBytes ?: expectedBytes
            val digest = MessageDigest.getInstance("SHA-256")

            conn.inputStream.use { input ->
                val buffer = ByteArray(bufferSize)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    if (!kotlinx.coroutines.currentCoroutineContext().isActive) throw CancellationException("Download cancelled: $filename")
                    writeBlock(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    bytesReceived += read
                    progress(MediaDownloadProgress(bytesReceived, finalExpectedBytes))
                }
            }

            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            val integrity = MediaIntegrity(
                expectedBytes = finalExpectedBytes,
                actualBytes = bytesReceived,
                sha256 = sha256,
            )

            if (finalExpectedBytes != null && bytesReceived != finalExpectedBytes) {
                throw HeyCyanIntegrityException(
                    mediaId = media.id,
                    expected = finalExpectedBytes,
                    actual = bytesReceived,
                )
            }

            MediaDownloadResult(
                media = media,
                bytesWritten = bytesReceived,
                destination = MediaLocation(destinationPath),
                integrity = integrity,
            )
        } catch (e: Exception) {
            if (e is CancellationException || e is HeyCyanHttpException || e is HeyCyanIntegrityException) throw e
            val targetExpected = expectedBytes ?: runCatching { conn.contentLengthLong.let { if (it > 0) it else null } }.getOrNull()
            if (targetExpected != null && bytesReceived != targetExpected) {
                throw HeyCyanIntegrityException(
                    mediaId = media.id,
                    expected = targetExpected,
                    actual = bytesReceived,
                )
            }
            throw e
        } finally {
            conn.disconnect()
        }
    }

}

/** Thrown when the HTTP server returns a non-2xx response. */
class HeyCyanHttpException(
    val statusCode: Int,
    override val message: String,
) : RuntimeException(message)

/** Thrown when the byte count does not match Content-Length. */
class HeyCyanIntegrityException(
    val mediaId: String,
    val expected: Long?,
    val actual: Long,
) : RuntimeException("Integrity failure for $mediaId: expected=$expected actual=$actual")
