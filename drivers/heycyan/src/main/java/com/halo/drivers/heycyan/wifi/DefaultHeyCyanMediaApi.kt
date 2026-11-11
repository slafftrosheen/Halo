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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLConnection
import java.net.URLEncoder
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
 * Path-based downloads are staged in a `.part` file and atomically committed on success.
 */
class DefaultHeyCyanMediaApi(
    private val deviceIp: InetAddress,
    private val port: Int = 80,
    private val parser: HeyCyanMediaConfigParser = DefaultHeyCyanMediaConfigParser(),
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 30_000,
    private val bufferSize: Int = 8 * 1024,
    private val connectionFactory: (URL) -> URLConnection = URL::openConnection,
    private val maxDownloadBytes: Long = 256L * 1024 * 1024,
) : HeyCyanMediaApi {

    companion object {
        /** Source-established manifest path: /files/media.config */
        private const val MANIFEST_PATH = "/files/media.config"

        /** Source-established download path prefix: /files/ */
        private const val FILES_PATH = "/files/"
        private const val VIDEO_READ_TIMEOUT_MILLIS = 120_000

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
                val conn = connectionFactory(url) as? HttpURLConnection
                    ?: throw IllegalStateException("HeyCyan connection factory did not return HTTP")
                conn.connectTimeout = connectTimeoutMillis
                conn.readTimeout = readTimeoutMillis
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = false

                withConnection(conn) {
                    val code = conn.responseCode
                    if (code != 200) {
                        throw HeyCyanHttpException(
                            statusCode = code,
                            message = "GET $MANIFEST_PATH returned HTTP $code",
                        )
                    }

                    require(conn.contentLengthLong <= 1024 * 1024) { "Device manifest exceeds 1 MiB" }
                    val body = conn.inputStream.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val bytes = ByteArray(8192)
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val n = input.read(bytes); if (n < 0) break
                            require(out.size() + n <= 1024 * 1024) { "Device manifest exceeds 1 MiB" }
                            out.write(bytes, 0, n)
                        }
                        out.toString(Charsets.UTF_8.name())
                    }
                    parser.parse(body).getOrThrow()
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
        val file = java.io.File(destinationPath)
        val partial = java.io.File(file.parentFile, "${file.name}.${java.util.UUID.randomUUID()}.part")
        runCatching {
            file.parentFile?.mkdirs()
            if (partial.exists()) partial.delete()
            val result = java.io.FileOutputStream(partial).use { outputStream ->
                downloadInternal(media, destinationPath, progress) { buffer, offset, len ->
                    outputStream.write(buffer, offset, len)
                }.getOrThrow().also { outputStream.flush(); outputStream.fd.sync() }
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            java.nio.file.Files.move(partial.toPath(), file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            result.copy(destination = MediaLocation(destinationPath))
        }.onFailure {
            partial.delete()
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
        validateFilename(filename)
        val encodedFilename = URLEncoder.encode(filename, Charsets.UTF_8.name()).replace("+", "%20")
        val url = URL("$baseUrl$FILES_PATH$encodedFilename")
        val conn = connectionFactory(url) as? HttpURLConnection
            ?: throw IllegalStateException("HeyCyan connection factory did not return HTTP")
        conn.connectTimeout = connectTimeoutMillis
        conn.readTimeout = if (media is HeyCyanMedia.Video) {
            maxOf(readTimeoutMillis, VIDEO_READ_TIMEOUT_MILLIS)
        } else {
            readTimeoutMillis
        }
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = false

        var bytesReceived = 0L
        val expectedBytes = media.sizeBytes
        require(expectedBytes == null || expectedBytes in 1..maxDownloadBytes) { "Device file exceeds size limit" }

        withConnection(conn) { try {
            val code = conn.responseCode
            if (code != 200) {
                throw HeyCyanHttpException(
                    statusCode = code,
                    message = "GET $FILES_PATH$filename returned HTTP $code",
                )
            }

            val headerTotalBytes = conn.contentLengthLong.let { if (it > 0) it else null }
            require(headerTotalBytes == null || headerTotalBytes <= maxDownloadBytes) { "Device file exceeds size limit" }
            if (expectedBytes != null && headerTotalBytes != null && expectedBytes != headerTotalBytes)
                throw HeyCyanIntegrityException(media.id, expectedBytes, headerTotalBytes)
            val finalExpectedBytes = headerTotalBytes ?: expectedBytes
            val digest = MessageDigest.getInstance("SHA-256")

            conn.inputStream.use { input ->
                val buffer = ByteArray(bufferSize)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    if (!kotlinx.coroutines.currentCoroutineContext().isActive) throw CancellationException("Download cancelled: $filename")
                    require(bytesReceived <= maxDownloadBytes - read) { "Device file exceeds size limit" }
                    writeBlock(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    bytesReceived += read
                    progress(MediaDownloadProgress(bytesReceived, finalExpectedBytes))
                }
            }

            require(bytesReceived > 0) { "Empty device media" }
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
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
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
        } }
    }

    /** Disconnect a blocking HTTP read when its owning operation is cancelled. */
    private suspend fun <T> withConnection(connection: HttpURLConnection, block: suspend () -> T): T = coroutineScope {
        val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { connection.disconnect() }
        }
        try { block() } finally { withContext(NonCancellable) { watcher.cancelAndJoin() } }
    }

    private fun validateFilename(filename: String) {
        require(filename.isNotBlank()) { "HeyCyan media filename is blank" }
        require(filename.length <= 255) { "HeyCyan media filename is too long" }
        require(filename != "." && filename != "..") { "Invalid HeyCyan media filename" }
        require('/' !in filename && '\\' !in filename && filename.none(Char::isISOControl)) {
            "HeyCyan media filename contains a path separator or control character"
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
