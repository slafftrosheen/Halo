package com.halo.app.audio

import kotlinx.coroutines.*
import okhttp3.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Publisher-owned complete pack; individual ONNX files cannot install a working voice. */
object KokoroModelDownload {
    const val MULTI_BYTES = 364816464L
    const val MULTI_HASH = "a3f4c73d043860e3fd2e5b06f36795eb81de0fc8e8de6df703245edddd87dbad"
    const val MULTI_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_1.tar.bz2"
    val PACK_FOLDERS = setOf("kokoro-en-v0_19", "kokoro-multi-lang-v1_1", "kokoro-int8-multi-lang-v1_1")
    const val BYTES = 319625534L
    const val HASH = "912804855a04745fa77a30be545b3f9a5d15c4d66db00b88cbcd4921df605ac7"
    const val URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2"
    private val client = OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).build()
    suspend fun acquire(part: File, progress: (Long) -> Unit, url: String = URL,
        expectedBytes: Long = BYTES, hash: String = HASH): File = withContext(Dispatchers.IO) {
        part.parentFile?.mkdirs()
        if (part.length() > expectedBytes) part.delete()
        if(part.length()==expectedBytes) {
            val digest=MessageDigest.getInstance("SHA-256")
            part.inputStream().use { input -> val buffer=ByteArray(65536); while(true) { ensureActive(); val n=input.read(buffer); if(n<0) break; digest.update(buffer,0,n) } }
            if(digest.digest().joinToString("") { "%02x".format(it) } == hash) return@withContext part
            part.delete()
        }
        var offset = part.length()
        val call = client.newCall(Request.Builder().url(url).header("Accept-Encoding","identity")
            .apply { if(offset > 0) header("Range","bytes=$offset-") }.build())
        val response = suspendCancellableCoroutine<Response> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object: Callback {
                override fun onFailure(call: Call, e: java.io.IOException) { if(continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        val watcher=CoroutineScope(currentCoroutineContext()).launch(start=CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try { response.use { r ->
            check(r.code == 200 || r.code == 206) { "Model download unavailable (${r.code})" }
            if(r.code == 200) offset=0
            if(r.code == 206) {
                val range=Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(r.header("Content-Range").orEmpty())
                check(range != null && range.groupValues[1].toLong()==offset && range.groupValues[3].toLong()==expectedBytes) { "Invalid resumed model response" }
            }
            val body=r.body ?: error("Empty model download")
            body.byteStream().use { input -> java.io.FileOutputStream(part,offset>0).use { output ->
                val buffer=ByteArray(65536); var count=offset
                while(true) { ensureActive(); val n=input.read(buffer); if(n<0) break
                    count+=n; check(count<=expectedBytes) { "Model exceeds expected size" }; output.write(buffer,0,n); progress(count) }
                output.fd.sync()
            } }
        }
        check(part.length()==expectedBytes) { "Model download incomplete; resume to continue" }
        val digest=MessageDigest.getInstance("SHA-256")
        part.inputStream().use { input -> val buffer=ByteArray(65536); while(true) { ensureActive(); val n=input.read(buffer); if(n<0) break; digest.update(buffer,0,n) } }
        if(digest.digest().joinToString("") { "%02x".format(it) } != hash) { part.delete(); error("Model checksum mismatch; download again") }
        part
        } finally { watcher.cancel(); call.cancel() }
    }
    suspend fun toImportZip(archive: File, zipFile: File) = withContext(Dispatchers.IO) {
        try {
            TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
                ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                    var count=0; var total=0L; val names=mutableSetOf<String>()
                    while(true) {
                        ensureActive(); val entry=tar.nextEntry ?: break
                        require(++count<=4096 && !entry.isSymbolicLink && !entry.isLink && !entry.isSparse) { "Unsafe model pack" }
                        if(entry.isDirectory) continue
                        require(entry.isFile && entry.name.substringBefore('/') in PACK_FOLDERS) { "Unexpected model file" }
                        val name=entry.name.substringAfter('/')
                        require(!name.startsWith('/') && '\\' !in name && name.split('/').none { it==".." } && names.add(name)) { "Unsafe model path" }
                        zip.putNextEntry(ZipEntry(entry.name))
                        val buffer=ByteArray(65536)
                        while(true) { ensureActive(); val n=tar.read(buffer); if(n<0) break; total+=n
                            require(total<=768L*1024*1024) { "Model pack exceeds storage budget" }; zip.write(buffer,0,n) }
                        zip.closeEntry()
                    }
                }
            }
        } catch(e: Exception) { zipFile.delete(); throw e }
    }
}
