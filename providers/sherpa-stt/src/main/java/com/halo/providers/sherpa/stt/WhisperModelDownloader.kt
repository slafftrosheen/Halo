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
    private val transfers = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
    internal suspend fun <T> withTransferMutation(destination: File, block: suspend () -> T): T {
        val mutex = transfers.computeIfAbsent(destination.canonicalPath) { kotlinx.coroutines.sync.Mutex() }
        mutex.lock()
        return try { block() } finally { mutex.unlock() }
    }

    const val STORAGE_RESERVE_BYTES = 64L * 1024 * 1024

    /** Bytes still required by this exact pinned spec; completed files and resumable partials count. */
    fun remainingBytes(spec: SherpaSpeechModelCatalog.ModelSpec, destination: File,
        verifyInstalledBytes: Boolean = false, checkCancellation: () -> Unit = {}): Long =
        spec.files.sumOf { file ->
            if (if (verifyInstalledBytes) physicalFileMatches(spec, destination, file, checkCancellation)
                else SherpaSpeechModelCatalog.validFile(destination, spec, file)) 0L
            else {
                val partial = File(destination, "${file.name}.${file.sha256 ?: spec.revision}.download")
                val partialBytes = partial.length().takeIf { it <= file.expectedBytes } ?: 0L
                (file.expectedBytes - partialBytes).coerceAtLeast(0L)
            }
        }

    /** Atomic publication copies the downloaded archive file before replacing the target.
     * A complete .download still needs a full temporary copy; existing good files need none.
     * Sequential publication means only the largest pending file needs staging at once.
     */
    fun publicationStagingBytes(spec: SherpaSpeechModelCatalog.ModelSpec, destination: File,
        checkCancellation: () -> Unit = {}): Long = spec.files.maxOfOrNull { file ->
        checkCancellation()
        if (physicalFileMatches(spec, destination, file, checkCancellation)) 0L
        else file.expectedBytes.coerceAtLeast(file.minimumBytes)
    } ?: 0L

    private fun physicalFileMatches(spec: SherpaSpeechModelCatalog.ModelSpec, destination: File,
        file: SherpaSpeechModelCatalog.FileSpec, checkCancellation: () -> Unit): Boolean =
        if (file.expectedBytes > 0 && file.sha256 != null) {
            try { SherpaModelInstaller.verifyPinnedFiles(destination, spec.copy(files = listOf(file)), checkCancellation) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { false }
        } else SherpaSpeechModelCatalog.validFile(destination, spec, file)

    suspend fun install(spec: SherpaSpeechModelCatalog.ModelSpec, destination: File,
        acquisitionOwner: String? = null, onProgress: (SpeechModelDownloadProgress) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) { withTransferMutation(destination) {
        try {
            acquisitionOwner?.let { require(java.util.UUID.fromString(it).toString() == it) }
            check(destination.isDirectory || destination.mkdirs()) { "Unable to create model directory" }
            val downloadContext = coroutineContext
            val remainingBytes = remainingBytes(spec, destination, verifyInstalledBytes = true) { downloadContext.ensureActive() }
            val stagingBytes = publicationStagingBytes(spec, destination) { downloadContext.ensureActive() }
            val requiredBytes = Math.addExact(Math.addExact(remainingBytes, stagingBytes), STORAGE_RESERVE_BYTES)
            check(destination.usableSpace >= requiredBytes) {
                "Not enough storage. Need ${requiredBytes / 1_000_000} MB including publication space and safety reserve; available ${destination.usableSpace / 1_000_000} MB."
            }
            var downloaded = 0L
            for (file in spec.files) {
                coroutineContext.ensureActive()
                if(physicalFileMatches(spec, destination, file) { downloadContext.ensureActive() }) {
                    if (acquisitionOwner == null) SherpaModelInstaller.retainFile(destination, file)
                    downloaded += File(destination,file.name).length()
                    onProgress(SpeechModelDownloadProgress(downloaded,spec.expectedBytes,file.name))
                    continue
                }
                val url = if (spec.repository.isBlank()) spec.sourceUrl else
                    "https://huggingface.co/${spec.repository}/resolve/${spec.revision}/${file.name}"
                val partial=File(destination,"${file.name}.${file.sha256 ?: spec.revision}.download")
                if (acquisitionOwner != null && partial.exists() && file.expectedBytes > 0 && partial.length() > file.expectedBytes)
                    error("An independent oversized partial was retained; manage it before resuming setup")
                val partialReceipt = File(destination, partial.name + ".acquired")
                val identity = acquisitionOwner?.let { "$it:${file.sha256}:${file.expectedBytes}" }
                // An independent prefix is retained when this request completes it.
                val publicationOwner = acquisitionOwner?.takeIf { !partial.exists() || runCatching { partialReceipt.readText() == identity }.getOrDefault(false) }
                if (publicationOwner == null) {
                    check(!partialReceipt.exists() || partialReceipt.delete()) { "Unable to retain independent partial" }
                } else partialReceipt.writeText(identity!!)
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
                    when(val result=SherpaModelInstaller.installFile(partial.inputStream(),destination,file, publicationOwner, requestedOwner = acquisitionOwner,
                        checkCancellation = { downloadContext.ensureActive() })) {
                        is SherpaModelInstaller.Result.Rejected -> { if (acquisitionOwner == null || publicationOwner != null) { partial.delete(); partialReceipt.delete() }; throw IOException(result.reason) }
                        is SherpaModelInstaller.Result.Installed -> { downloaded+=result.file.length(); partial.delete(); partialReceipt.delete() }
                    }
                } finally { connection.disconnect() }
            }
            check(SherpaSpeechModelCatalog.isInstalled(destination, spec)) { "Model installation incomplete" }
            onProgress(SpeechModelDownloadProgress(spec.expectedBytes, spec.expectedBytes, "Complete"))
            Result.success(Unit)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { Result.failure(error) }
    } }
}

/** Compatibility for callers of the original Tiny installer. */
object WhisperModelDownloader {
    suspend fun downloadAndInstall(destinationDirectory: File,
        onProgress: (Float, String) -> Unit = { _, _ -> }): Result<Unit> =
        SpeechModelInstaller.install(SherpaSpeechModelCatalog.whisperTiny, destinationDirectory) {
            onProgress(it.fraction, it.currentFile)
        }
}
