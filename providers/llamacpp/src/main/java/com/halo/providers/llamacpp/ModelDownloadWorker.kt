package com.halo.providers.llamacpp

import android.content.Context
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
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val filename = inputData.getString(KEY_FILENAME) ?: return@withContext Result.failure()
        val expectedBytes = inputData.getLong(KEY_BYTES, -1L)
        if (!url.startsWith("https://huggingface.co/") || !filename.endsWith(".gguf", true) ||
            filename.contains('/') || filename.contains('\\') || expectedBytes <= 0L) {
            return@withContext Result.failure()
        }
        val root = File(applicationContext.noBackupFilesDir, "models")
        root.mkdirs()
        val destination = File(root, filename)
        val part = File(root, "$filename.part")
        if (destination.isFile && destination.length() == expectedBytes &&
            GgufMetadataReader.read(destination)?.let {
                BundledLlamaArchitectureSupport.supports(it.architecture)
            } == true) {
            return@withContext Result.success()
        }
        if (part.length() > expectedBytes) part.delete()
        val offset = part.length()
        val request = Request.Builder().url(url)
            .apply { if (offset > 0L) header("Range", "bytes=$offset-") }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code !in listOf(200, 206)) {
                    if (response.code == 416) part.delete()
                    return@withContext if (response.code in 400..499 && response.code != 416)
                        Result.failure() else Result.retry()
                }
                if (response.header("Content-Type").orEmpty().contains("text/html", true)) {
                    part.delete()
                    return@withContext Result.failure()
                }
                val append = response.code == 206 && offset > 0L
                if (response.code == 206) {
                    val range = response.header("Content-Range").orEmpty()
                    if (!range.startsWith("bytes $offset-")) {
                        part.delete()
                        return@withContext Result.retry()
                    }
                }
                val body = response.body ?: return@withContext Result.retry()
                val contentLength = body.contentLength()
                val remaining = expectedBytes - if (append) offset else 0L
                if (contentLength > 0L && contentLength != remaining) {
                    part.delete()
                    return@withContext Result.failure()
                }
                var nextProgressAt = offset + 8L * 1024L * 1024L
                body.byteStream().use { input ->
                    FileOutputStream(part, append).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
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
            return@withContext Result.retry()
        }
        if (part.length() != expectedBytes) return@withContext Result.retry()
        val metadata = GgufMetadataReader.read(part)
        if (metadata == null || !BundledLlamaArchitectureSupport.supports(metadata.architecture)) {
            part.delete()
            return@withContext Result.failure()
        }
        if (destination.exists()) destination.delete()
        if (!part.renameTo(destination)) return@withContext Result.retry()
        Result.success()
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_FILENAME = "filename"
        const val KEY_BYTES = "bytes"
        const val KEY_PROGRESS = "progress"
    }
}
