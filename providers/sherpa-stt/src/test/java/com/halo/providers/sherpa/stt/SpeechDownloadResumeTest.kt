package com.halo.providers.sherpa.stt

import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest

class SpeechDownloadResumeTest {
    @Test fun `speech prefix resumes and installed files avoid redownload`() = runBlocking {
        val root=Files.createTempDirectory("speech-resume").toFile()
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        val bytes="abcdef".toByteArray()
        val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        var requested: String?=null; var count=0
        server.createContext("/artifact") { exchange ->
            count++; requested=exchange.requestHeaders.getFirst("Range")
            exchange.responseHeaders.add("Content-Range","bytes 3-5/6")
            exchange.sendResponseHeaders(206,3); exchange.responseBody.use { it.write("def".toByteArray()) }
        }
        server.start()
        try {
            val file=SherpaSpeechModelCatalog.FileSpec("test.onnx",1,6,hash)
            val spec=SherpaSpeechModelCatalog.whisperBase.copy(files=listOf(file),repository="",
                sourceUrl="http://127.0.0.1:${server.address.port}/artifact")
            java.io.File(root,"test.onnx.$hash.download").writeText("abc")
            assertTrue(SpeechModelInstaller.install(spec,root).isSuccess)
            assertEquals("bytes=3-",requested); assertEquals("abcdef",java.io.File(root,"test.onnx").readText())
            assertTrue(SpeechModelInstaller.install(spec,root).isSuccess); assertEquals(1,count)
        } finally { server.stop(0); root.deleteRecursively() }
    }
}
