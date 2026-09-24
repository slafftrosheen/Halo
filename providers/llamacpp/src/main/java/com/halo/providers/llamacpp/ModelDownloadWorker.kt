package com.halo.providers.llamacpp

import android.content.Context
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.ForegroundInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Resumes a revision-pinned GGUF into one app-owned .part file. */
class ModelDownloadWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    private val client = OkHttpClient.Builder().callTimeout(0, TimeUnit.MILLISECONDS).build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: return@withContext failure("Download URL is missing")
        val filename = inputData.getString(KEY_FILENAME) ?: return@withContext failure("Download filename is missing")
        val expectedBytes = inputData.getLong(KEY_BYTES, -1L)
        if (!url.startsWith("https://huggingface.co/") || !filename.endsWith(".gguf", true) ||
            filename.contains('/') || filename.contains('\\') || expectedBytes <= 0L) {
            return@withContext failure("Download request metadata is invalid")
        }
        val root = File(applicationContext.noBackupFilesDir, "models")
        root.mkdirs()
        val destination = File(root, filename)
        val part = File(root, "$filename.part")
        if (GgufMetadataReader.isCompleteDownload(destination, expectedBytes)) {
            return@withContext Result.success()
        }
        if (part.length() > expectedBytes) part.delete()
        // A completed transfer may have been interrupted before rename. Never discard it on HTTP 416.
        if (GgufMetadataReader.isCompleteDownload(part, expectedBytes)) {
            if (destination.exists() && !destination.delete()) return@withContext failure("Unable to replace the previous model file")
            return@withContext if (part.renameTo(destination)) Result.success() else failure("Unable to finish model installation")
        }
        if (root.usableSpace < expectedBytes - part.length() + 32L * 1024 * 1024) {
            return@withContext failure("Not enough free storage for this model")
        }
        try { setForeground(foregroundInfo(filename)) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { return@withContext failure("Unable to start background download: ${e.message}") }
        val offset = part.length()
        val request = Request.Builder().url(url)
            .apply { if (offset > 0L) header("Range", "bytes=$offset-") }
            .build()
        val call = client.newCall(request)
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            call.execute().use { response ->
                if (response.code !in listOf(200, 206)) {
                    if (response.code == 416) part.delete()
                    return@withContext if (response.code == 429 || response.code >= 500 || response.code == 416)
                        retryOrFail("Download server returned HTTP ${response.code}")
                    else failure("Download rejected (HTTP ${response.code}). The model may be gated, private, or no longer available.")
                }
                if (response.header("Content-Type").orEmpty().contains("text/html", true)) {
                    part.delete()
                    return@withContext failure("Server returned a web page instead of a GGUF file")
                }
                val append = response.code == 206 && offset > 0L
                if (response.code == 206) {
                    val range = response.header("Content-Range").orEmpty()
                    if (!range.startsWith("bytes $offset-")) {
                        part.delete()
                        return@withContext retryOrFail("Unable to complete the model transfer")
                    }
                }
                val body = response.body ?: return@withContext retryOrFail("Unable to complete the model transfer")
                val contentLength = body.contentLength()
                val remaining = expectedBytes - if (append) offset else 0L
                if (contentLength > 0L && contentLength != remaining) {
                    part.delete()
                    return@withContext failure("File size differs from catalog metadata; refresh the catalog")
                }
                var nextProgressAt = (if (append) offset else 0L) + 8L * 1024L * 1024L
                body.byteStream().use { input ->
                    FileOutputStream(part, append).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (part.length() + count > expectedBytes) return@withContext failure("Download exceeds the advertised file size; refresh the model catalog")
                            output.write(buffer, 0, count)
                            if (part.length() >= nextProgressAt) {
                                setProgress(workDataOf(KEY_PROGRESS to part.length()))
                                nextProgressAt = part.length() + 8L * 1024L * 1024L
                            }
                        }
                        output.fd.sync()
                    }
                }
            }
        } catch (error: IOException) {
            coroutineContext.ensureActive()
            return@withContext retryOrFail(error.message ?: "Download connection interrupted")
        } finally { cancellation.cancel() }
        if (part.length() != expectedBytes) return@withContext retryOrFail("Incomplete model transfer")
        val metadata = GgufMetadataReader.read(part)
        if (metadata == null) {
            part.delete()
            return@withContext failure("Downloaded file is not a complete, valid GGUF")
        }
        if (metadata.architecture.isNullOrBlank()) {
            Log.w(
                TAG,
                "GGUF transfer is complete but Halo metadata projection did not resolve general.architecture; " +
                    "retaining the file and deferring compatibility validation to native llama.cpp"
            )
        }
        if (destination.exists()) destination.delete()
        if (!part.renameTo(destination)) return@withContext retryOrFail("Unable to complete the model transfer")
        Result.success()
    }

    private fun failure(message: String): Result {
        Log.e(TAG, "GGUF download failed: $message")
        return Result.failure(workDataOf(KEY_ERROR to message.take(1000)))
    }

    private fun retryOrFail(message: String): Result {
        if (runAttemptCount < MAX_ATTEMPTS) {
            Log.w(TAG, "GGUF download interrupted; retry ${runAttemptCount + 1}/$MAX_ATTEMPTS: $message")
            return Result.retry()
        }
        return failure(message)
    }
    private fun foregroundInfo(filename: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("halo_model_downloads", "Model downloads", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(applicationContext, "halo_model_downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Downloading local model")
            .setContentText(filename).setOngoing(true).setProgress(0, 0, true).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(id.hashCode(), notification)
    }
    companion object {
        private const val TAG = "HaloGgufDownload"
        private const val MAX_ATTEMPTS = 5
        const val KEY_ERROR = "error"
        const val KEY_URL = "url"
        const val KEY_FILENAME = "filename"
        const val KEY_BYTES = "bytes"
        const val KEY_PROGRESS = "progress"
    }
}
