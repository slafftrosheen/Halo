package com.halo.app.audio

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
import java.security.MessageDigest

class KokoroModelDownloadTest {
    private fun hash(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    @Test fun `interrupted bytes resume and full file is verified`() = runBlocking {
        val server=MockWebServer(); server.start(); val root=Files.createTempDirectory("kokoro-test").toFile()
        try {
            val part=java.io.File(root,"voice.part"); part.writeText("abc")
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range","bytes 3-5/6").setBody("def"))
            KokoroModelDownload.acquire(part,{},server.url("/model").toString(),6,hash("abcdef".toByteArray()))
            assertEquals("abcdef",part.readText()); assertEquals("bytes=3-",server.takeRequest().getHeader("Range"))
        } finally { server.shutdown(); root.deleteRecursively() }
    }
    @Test fun `checksum failure removes poisoned partial`() = runBlocking {
        val server=MockWebServer(); server.start(); val root=Files.createTempDirectory("kokoro-test").toFile()
        try {
            val part=java.io.File(root,"voice.part")
            server.enqueue(MockResponse().setBody("broken"))
            try { KokoroModelDownload.acquire(part,{},server.url("/model").toString(),6,hash("abcdef".toByteArray())); fail("must reject") }
            catch(_: IllegalStateException) { assertFalse(part.exists()) }
        } finally { server.shutdown(); root.deleteRecursively() }
    }
    @Test fun `ignored range restarts rather than appends`() = runBlocking {
        val server=MockWebServer(); server.start(); val root=Files.createTempDirectory("kokoro-test").toFile()
        try {
            val part=java.io.File(root,"voice.part"); part.writeText("abc")
            server.enqueue(MockResponse().setBody("abcdef"))
            KokoroModelDownload.acquire(part,{},server.url("/model").toString(),6,hash("abcdef".toByteArray()))
            assertEquals("abcdef",part.readText())
        } finally { server.shutdown(); root.deleteRecursively() }
    }
    @Test fun `complete verified archive is reused without a network request`() = runBlocking {
        val root=Files.createTempDirectory("kokoro-complete").toFile()
        try {
            val part=java.io.File(root,"voice.part"); part.writeText("abcdef")
            KokoroModelDownload.acquire(part,{},"http://127.0.0.1:1/unreachable",6,hash("abcdef".toByteArray()))
            assertEquals("abcdef",part.readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun `multilingual pack conversion preserves lexicon and quantized model`() = runBlocking {
        val root=Files.createTempDirectory("kokoro-multilingual").toFile()
        try {
            val archive=java.io.File(root,"pack.tar.bz2"); val zip=java.io.File(root,"pack.zip")
            org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(
                org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream(archive.outputStream())).use { tar ->
                listOf("model.int8.onnx","lexicon-zh.txt").forEach { name ->
                    val entry=org.apache.commons.compress.archivers.tar.TarArchiveEntry("kokoro-int8-multi-lang-v1_1/$name")
                    entry.size=1; tar.putArchiveEntry(entry); tar.write(byteArrayOf(1)); tar.closeArchiveEntry()
                }
            }
            KokoroModelDownload.toImportZip(archive,zip)
            java.util.zip.ZipFile(zip).use { contents ->
                assertNotNull(contents.getEntry("kokoro-int8-multi-lang-v1_1/model.int8.onnx"))
                assertNotNull(contents.getEntry("kokoro-int8-multi-lang-v1_1/lexicon-zh.txt"))
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun `archive conversion rejects traversal before creating an importable pack`() = runBlocking {
        val root=Files.createTempDirectory("kokoro-traversal").toFile()
        try {
            val archive=java.io.File(root,"bad.tar.bz2"); val zip=java.io.File(root,"pack.zip")
            org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(
                org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream(archive.outputStream())).use { tar ->
                val entry=org.apache.commons.compress.archivers.tar.TarArchiveEntry("kokoro-en-v0_19/../escape")
                entry.size=1; tar.putArchiveEntry(entry); tar.write(byteArrayOf(1)); tar.closeArchiveEntry()
            }
            try { KokoroModelDownload.toImportZip(archive,zip); fail("must reject traversal") }
            catch(_: IllegalArgumentException) { assertFalse(zip.exists()); assertFalse(java.io.File(root,"escape").exists()) }
        } finally { root.deleteRecursively() }
    }

}
