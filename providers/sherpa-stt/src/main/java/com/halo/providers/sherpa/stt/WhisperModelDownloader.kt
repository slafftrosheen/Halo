package com.halo.providers.sherpa.stt

import android.util.Log
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WhisperModelDownloader {
    private const val TAG = "WhisperModelDownloader"
    private const val BASE_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main"

    // Estimated total byte size (~81.5 MB across the 3 HuggingFace files)
    private const val ESTIMATED_TOTAL_BYTES = 81_500_000L

    suspend fun downloadAndInstall(
        destinationDirectory: File,
        onProgress: (progress: Float, currentFile: String) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val spec = SherpaSpeechModelCatalog.whisperTiny
        var totalDownloadedSoFar = 0L

        for (fileSpec in spec.files) {
            val fileUrl = "$BASE_URL/${fileSpec.name}"

            onProgress(
                (totalDownloadedSoFar.toFloat() / ESTIMATED_TOTAL_BYTES).coerceIn(0f, 0.99f),
                fileSpec.name
            )

            var connection: HttpURLConnection? = null
            try {
                connection = openConnectionWithRedirects(fileUrl)
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(
                        IOException("HTTP $responseCode downloading ${fileSpec.name}")
                    )
                }

                val inputStream = connection.inputStream
                val progressStream = object : InputStream() {
                    override fun read(): Int {
                        val b = inputStream.read()
                        if (b != -1) {
                            totalDownloadedSoFar++
                            onProgress(
                                (totalDownloadedSoFar.toFloat() / ESTIMATED_TOTAL_BYTES).coerceIn(0f, 0.99f),
                                fileSpec.name
                            )
                        }
                        return b
                    }

                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val count = inputStream.read(b, off, len)
                        if (count > 0) {
                            totalDownloadedSoFar += count
                            onProgress(
                                (totalDownloadedSoFar.toFloat() / ESTIMATED_TOTAL_BYTES).coerceIn(0f, 0.99f),
                                fileSpec.name
                            )
                        }
                        return count
                    }

                    override fun close() {
                        inputStream.close()
                    }
                }

                val installResult = SherpaModelInstaller.installFile(
                    input = progressStream,
                    destinationDirectory = destinationDirectory,
                    fileSpec = fileSpec
                )

                when (installResult) {
                    is SherpaModelInstaller.Result.Installed -> {
                        Log.i(TAG, "Successfully installed ${fileSpec.name}")
                    }
                    is SherpaModelInstaller.Result.Rejected -> {
                        return@withContext Result.failure(
                            IOException("Installation rejected for ${fileSpec.name}: ${installResult.reason}")
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed downloading ${fileSpec.name}", e)
                return@withContext Result.failure(e)
            } finally {
                connection?.disconnect()
            }
        }

        if (SherpaSpeechModelCatalog.isInstalled(destinationDirectory, spec)) {
            onProgress(1.0f, "Complete")
            Result.success(Unit)
        } else {
            val missing = SherpaSpeechModelCatalog.missingFiles(destinationDirectory, spec)
            Result.failure(IOException("Model installation incomplete. Missing: ${missing.joinToString()}"))
        }
    }

    private fun openConnectionWithRedirects(urlStr: String): HttpURLConnection {
        var currentUrl = urlStr
        var redirects = 0
        while (redirects < 10) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Halo-Android")
            }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 || code == 308
            ) {
                val loc = connection.getHeaderField("Location")
                if (!loc.isNullOrBlank()) {
                    currentUrl = loc
                    redirects++
                    connection.disconnect()
                    continue
                }
            }
            return connection
        }
        throw IOException("Too many redirects downloading $urlStr")
    }
}
