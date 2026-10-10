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
                if(SherpaSpeechModelCatalog.validFile(destination,spec,file)) {
                    downloaded += File(destination,file.name).length()
                    onProgress(SpeechModelDownloadProgress(downloaded,spec.expectedBytes,file.name))
                    continue
                }
                val url = if (spec.repository.isBlank()) spec.sourceUrl else
                    "https://huggingface.co/${spec.repository}/resolve/${spec.revision}/${file.name}"
                val partial=File(destination,"${file.name}.${file.sha256 ?: spec.revision}.download")
                if(file.expectedBytes > 0 && partial.length() > file.expectedBytes) partial.delete()
                val prefix=partial.length()
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000; readTimeout = 15_000; instanceFollowRedirects = true
                    setRequestProperty("Accept-Encoding","identity")
                    if(prefix>0 && prefix<file.expectedBytes) setRequestProperty("Range","bytes=$prefix-")
                }
                try {
                    if(partial.length()!=file.expectedBytes || file.expectedBytes<=0) {
                        val code=connection.responseCode
                        check(code==200 || code==206) { "HTTP $code downloading ${file.name}" }
                        val append=code==206 && prefix>0 && connection.getHeaderField("Content-Range")
                            ?.startsWith("bytes $prefix-")==true
                        check(code!=206 || append) { "Unexpected partial model response" }
                        val completedBeforeFile=downloaded
                        var bytes=if(append) prefix else 0L
                        connection.inputStream.use { input -> java.io.FileOutputStream(partial,append).use { output ->
                            val buffer=ByteArray(65536)
                            while(true) { downloadContext.ensureActive(); val n=input.read(buffer); if(n<0) break
                                bytes+=n; check(file.expectedBytes<=0 || bytes<=file.expectedBytes) { "Model exceeds expected size" }
                                output.write(buffer,0,n)
                                onProgress(SpeechModelDownloadProgress(completedBeforeFile+bytes,spec.expectedBytes,file.name)) }
                            output.fd.sync()
                        } }
                    }
                    if(file.expectedBytes>0 && partial.length()<file.expectedBytes)
                        throw IOException("Model download interrupted; retry to resume ${file.name}")
                    onProgress(SpeechModelDownloadProgress(downloaded+partial.length(),spec.expectedBytes,file.name,verifying=true))
                    when(val result=SherpaModelInstaller.installFile(partial.inputStream(),destination,file)) {
                        is SherpaModelInstaller.Result.Rejected -> { partial.delete(); throw IOException(result.reason) }
                        is SherpaModelInstaller.Result.Installed -> { downloaded+=result.file.length(); partial.delete() }
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
