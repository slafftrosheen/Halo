package com.halo.app.capture

import com.halo.drivers.heycyan.wifi.DefaultHeyCyanMediaApi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.Proxy

class TransferHttpConnectionTest {
    @Test fun selectedSocketRoutePreservesChunkedOriginalBytesAndDigest() = runBlocking {
        MockWebServer().use { server ->
            val local = InetAddress.getByName("127.0.0.1")
            server.start(local, 0)
            val bytes = ByteArray(16385) { (it % 251).toByte() }
            server.enqueue(MockResponse().setBody("photo.jpg\n"))
            server.enqueue(MockResponse().setChunkedBody(okio.Buffer().write(bytes), 127))
            val client = OkHttpClient.Builder().socketFactory(LocalAddressSocketFactory(local)).proxy(Proxy.NO_PROXY).build()
            val api = DefaultHeyCyanMediaApi(local, server.port, connectionFactory = { TransferHttpConnection(it, client) })
            val media = api.getMediaConfig().getOrThrow().items.single()
            assertEquals("photo.jpg", media.filename)
            val directory = java.nio.file.Files.createTempDirectory("halo-transfer-test").toFile()
            try {
                val destination = File(directory, "download.jpg")
                val result = api.download(media, destination.path) {}.getOrThrow()
                assertEquals(16385L, result.bytesWritten)
                assertEquals("ba4f9b37402df1e3ad948a794ab43a9ed887d63e3a389c208ca4314fdd5add58", result.integrity.sha256)
                assertArrayEquals(bytes, destination.readBytes())
                assertEquals("/files/media.config", server.takeRequest().path)
                assertEquals("/files/photo.jpg", server.takeRequest().path)
            } finally { directory.deleteRecursively(); client.connectionPool.evictAll() }
        }
    }

    @Test fun transferNeverFollowsRedirectToAnotherEndpoint() = runBlocking {
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/unrelated")))
            server.enqueue(MockResponse().setBody("wrong.jpg"))
            val client = OkHttpClient()
            val api = DefaultHeyCyanMediaApi(InetAddress.getByName("127.0.0.1"), server.port,
                connectionFactory = { TransferHttpConnection(it, client) })
            assertTrue(api.getMediaConfig().isFailure)
            assertEquals(1, server.requestCount)
            client.connectionPool.evictAll()
        }
    }
}
