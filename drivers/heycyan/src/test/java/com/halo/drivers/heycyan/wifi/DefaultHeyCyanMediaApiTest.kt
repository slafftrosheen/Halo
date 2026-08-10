package com.halo.drivers.heycyan.wifi

import com.halo.drivers.heycyan.media.DefaultHeyCyanMediaConfigParser
import com.halo.drivers.heycyan.media.HeyCyanMedia
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

class DefaultHeyCyanMediaApiTest {

    private lateinit var server: HttpServer
    private var serverPort: Int = 0
    private lateinit var api: DefaultHeyCyanMediaApi

    @Before
    fun setUp() {
        // Start lightweight local HTTP server for hardware-free testing
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        serverPort = server.address.port
        server.start()

        api = DefaultHeyCyanMediaApi(
            deviceIp = server.address.address,
            port = serverPort,
            parser = DefaultHeyCyanMediaConfigParser(),
            connectTimeoutMillis = 2000,
            readTimeoutMillis = 2000,
        )
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun getMediaConfigFetchesAndParsesManifest() = runTest {
        val manifestJson = """{"files": ["photo1.jpg", "video1.mp4"]}"""
        server.createContext("/files/media.config") { exchange ->
            val responseBytes = manifestJson.toByteArray()
            exchange.sendResponseHeaders(200, responseBytes.size.toLong())
            exchange.responseBody.write(responseBytes)
            exchange.close()
        }

        // Re-bind API to mock server port
        val testApi = DefaultHeyCyanMediaApi(
            deviceIp = InetSocketAddress("127.0.0.1", serverPort).address,
            port = serverPort,
        )
        
        // Test standard path resolution
        val result = testApi.getMediaConfig()
        assertTrue(result.isSuccess)
        val manifest = result.getOrThrow()
        assertEquals(2, manifest.items.size)
    }

    @Test
    fun downloadStreamsInsteadOfBufferingAndVerifiesIntegrity() = runTest {
        val testData = "Hello World! HeyCyan Media Download Stream Test Content".toByteArray()
        server.createContext("/files/sample.jpg") { exchange ->
            exchange.sendResponseHeaders(200, testData.size.toLong())
            exchange.responseBody.write(testData)
            exchange.close()
        }

        val testApi = DefaultHeyCyanMediaApi(
            deviceIp = InetSocketAddress("127.0.0.1", serverPort).address,
            port = serverPort,
        )

        val media = HeyCyanMedia.Photo("sample.jpg", "sample.jpg", testData.size.toLong(), null, null, null)
        var progressReported = false

        val result = testApi.download(media, "/tmp/sample.jpg") { progress ->
            progressReported = true
            assertTrue(progress.bytesReceived > 0)
        }

        assertTrue(result.isSuccess)
        val downloadResult = result.getOrThrow()
        assertEquals(testData.size.toLong(), downloadResult.bytesWritten)
        assertEquals(testData.size.toLong(), downloadResult.integrity.actualBytes)
        assertTrue(progressReported)
    }

    @Test
    fun byteCountMismatchThrowsIntegrityException() = runTest {
        val testData = "Partial payload".toByteArray()
        server.createContext("/files/corrupt.jpg") { exchange ->
            // Claim size 1000 but send fewer bytes
            exchange.sendResponseHeaders(200, 1000L)
            exchange.responseBody.write(testData)
            exchange.close()
        }

        val testApi = DefaultHeyCyanMediaApi(
            deviceIp = InetSocketAddress("127.0.0.1", serverPort).address,
            port = serverPort,
        )

        val media = HeyCyanMedia.Photo("corrupt.jpg", "corrupt.jpg", 1000L, null, null, null)
        val result = testApi.download(media, "/tmp/corrupt.jpg")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is HeyCyanIntegrityException)
    }
}
