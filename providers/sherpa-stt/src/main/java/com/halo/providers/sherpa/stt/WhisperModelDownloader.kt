package com.halo.providers.sherpa.stt

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class SpeechModelDownloadProgress(
    val downloadedBytes: Long, val totalBytes: Long, val currentFile: String,
    val verifying: Boolean = false
) {
    val fraction: Float get() = if (totalBytes == 0L) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

/** Only invoked by an explicit install action. Files and totals come from the catalog. */
object SpeechModelInstaller {
    suspend fun install(spec: SherpaSpeechModelCatalog.ModelSpec, destination: File,
        onProgress: (SpeechModelDownloadProgress) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            check(destination.isDirectory || destination.mkdirs()) { "Unable to create model directory" }
            val reserve = 64L * 1024 * 1024
            check(destination.usableSpace >= spec.expectedBytes + reserve) {
                "Not enough storage. Need ${spec.expectedBytes / 1_000_000} MB plus 64 MB safety reserve; available ${destination.usableSpace / 1_000_000} MB."
            }
            val downloadContext = coroutineContext
            var downloaded = 0L
            for (file in spec.files) {
                coroutineContext.ensureActive()
                val url = if (spec.repository.isBlank()) spec.sourceUrl else
                    "https://huggingface.co/${spec.repository}/resolve/${spec.revision}/${file.name}"
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000; readTimeout = 60_000; instanceFollowRedirects = true
                }
                try {
                    check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode} downloading ${file.name}" }
                    val source = object : java.io.FilterInputStream(connection.inputStream) {
                        override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                            downloadContext.ensureActive()
                            val count = `in`.read(bytes, off, len)
                            if (count > 0) {
                                downloaded += count
                                onProgress(SpeechModelDownloadProgress(downloaded, spec.expectedBytes, file.name))
                            } else if (count == -1) {
                                onProgress(SpeechModelDownloadProgress(downloaded, spec.expectedBytes, file.name, verifying = true))
                            }
                            return count
                        }
                    }
                    when (val result = SherpaModelInstaller.installFile(source, destination, file)) {
                        is SherpaModelInstaller.Result.Rejected -> throw IOException(result.reason)
                        is SherpaModelInstaller.Result.Installed -> Unit
                    }
                } finally { connection.disconnect() }
            }
            check(SherpaSpeechModelCatalog.isInstalled(destination, spec)) { "Model installation incomplete" }
            onProgress(SpeechModelDownloadProgress(spec.expectedBytes, spec.expectedBytes, "Complete"))
            Result.success(Unit)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { Result.failure(error) }
    }
}

/** Compatibility for callers of the original Tiny installer. */
object WhisperModelDownloader {
    suspend fun downloadAndInstall(destinationDirectory: File,
        onProgress: (Float, String) -> Unit = { _, _ -> }): Result<Unit> =
        SpeechModelInstaller.install(SherpaSpeechModelCatalog.whisperTiny, destinationDirectory) {
            onProgress(it.fraction, it.currentFile)
        }
}
