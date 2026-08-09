package com.halo.drivers.heycyan.wifi

import com.halo.drivers.heycyan.media.DownloadProgress
import com.halo.drivers.heycyan.media.HeyCyanMediaSync
import com.halo.drivers.heycyan.media.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP file downloader for HeyCyan media files.
 *
 * Downloads files from the device's HTTP server at `http://<IP>/files/<filename>`.
 *
 * Features:
 * - Progress reporting via Flow
 * - Concurrent downloads (configurable)
 * - Partial download recovery (resumable if server supports Range)
 * - Cancellation support
 *
 * Evidence: CyanBridge `AlbumDownloader.fetchOne()`, iOS `GlassesMediaDownloader.m`
 */
class HeyCyanMediaDownloader(
    private val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT,
    private val readTimeoutMillis: Long = DEFAULT_READ_TIMEOUT,
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE
) : HeyCyanMediaSync.MediaFileDownloader {

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT = 10_000L
        private const val DEFAULT_READ_TIMEOUT = 30_000L
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024
        private const val FILES_PATH = "/files/"
    }

    private var deviceIp: String? = null
    private var destinationDir: String? = null

    private val _downloadProgress = MutableSharedFlow<DownloadProgress>(extraBufferCapacity = 64)
    val downloadProgress: Flow<DownloadProgress> = _downloadProgress.asSharedFlow()

    /**
     * Configures the downloader with the device IP and destination directory.
     */
    fun configure(deviceIp: String, destinationDir: String) {
        this.deviceIp = deviceIp
        this.destinationDir = destinationDir
    }

    /**
     * Downloads a media file from the device.
     *
     * @param ip The device IP address
     * @param filename The filename to download
     * @param destDir Local directory to save the file
     * @return Local file path of the downloaded file
     */
    suspend fun download(ip: String, filename: String, destDir: String): String {
        return withContext(Dispatchers.IO) {
            val url = URL("http://$ip$FILES_PATH$filename")
            val destFile = File(destDir, filename)
            destFile.parentFile?.mkdirs()

            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = connectTimeoutMillis.toInt()
            connection.readTimeout = readTimeoutMillis.toInt()
            connection.requestMethod = "GET"

            // Support partial download recovery
            if (destFile.exists() && destFile.length() > 0) {
                connection.setRequestProperty("Range", "bytes=${destFile.length()}-")
            }

            try {
                val responseCode = connection.responseCode
                val isPartial = responseCode == HttpURLConnection.HTTP_PARTIAL
                check(responseCode in 200..299) {
                    "Download failed with HTTP $responseCode for $filename"
                }

                val totalBytes = connection.contentLengthLong.let {
                    if (it > 0 && isPartial) it + destFile.length() else it
                }

                val outputStream = if (isPartial) {
                    FileOutputStream(destFile, true) // Append for partial
                } else {
                    FileOutputStream(destFile) // Overwrite for full
                }

                connection.inputStream.use { input ->
                    outputStream.use { output ->
                        val buffer = ByteArray(bufferSize)
                        var bytesRead: Int
                        var totalRead = if (isPartial) destFile.length() else 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive) throw CancellationException("Download cancelled")
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead

                            _downloadProgress.tryEmit(
                                DownloadProgress(
                                    item = MediaItem(filename = filename, type = com.halo.drivers.heycyan.media.MediaType.fromExtension(filename)),
                                    bytesDownloaded = totalRead,
                                    totalBytes = if (totalBytes > 0) totalBytes else null
                                )
                            )
                        }
                    }
                }

                destFile.absolutePath
            } catch (e: CancellationException) {
                // Don't delete partial file — can resume later
                throw e
            } catch (e: Exception) {
                // Delete partial file on error (non-recoverable)
                destFile.delete()
                throw e
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * Implementation of MediaFileDownloader for the sync engine.
     */
    override suspend fun download(item: MediaItem): Result<HeyCyanMediaSync.DownloadResult> = runCatching {
        val ip = deviceIp ?: throw IllegalStateException("Downloader not configured with device IP")
        val dir = destinationDir ?: throw IllegalStateException("Downloader not configured with destination directory")

        val localPath = download(ip, item.filename, dir)
        val file = File(localPath)

        HeyCyanMediaSync.DownloadResult(
            localPath = localPath,
            sizeBytes = file.length(),
            valid = file.exists() && file.length() > 0
        )
    }
}
