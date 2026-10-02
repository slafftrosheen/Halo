package com.halo.providers.litert.models

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The integrity authority is the byte stream, never an HTTP header. Every case here runs against
 * MockWebServer so the failure taxonomy is deterministic and no real network is touched.
 */
@RunWith(RobolectricTestRunner::class)
class LiteRtModelDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    /** Deliberately above the catalog's minimum plausible artifact size. */
    private val payload = deterministicPayload(PAYLOAD_BYTES)
    private val payloadSha = sha256(payload)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ---------------------------------------------------------------------------------------
    // Downloader-level taxonomy
    // ---------------------------------------------------------------------------------------

    @Test
    fun `404 maps to HTTP_NOT_FOUND`() = runBlocking {
        alwaysRespond { MockResponse().setResponseCode(404) }
        val result = downloader().download(artifact(), partFile(), {})
        assertTrue(result is DownloadResult.Failure)
        assertEquals(ModelDownloadFailure.HTTP_NOT_FOUND, (result as DownloadResult.Failure).kind)
    }

    @Test
    fun `403 maps to AUTH_REQUIRED`() = runBlocking {
        alwaysRespond { MockResponse().setResponseCode(403) }
        val result = downloader().download(artifact(), partFile(), {})
        assertEquals(
            ModelDownloadFailure.AUTH_REQUIRED,
            (result as DownloadResult.Failure).kind,
        )
    }

    @Test
    fun `a truncated stream fails with SIZE_MISMATCH`() = runBlocking {
        alwaysRespond { okResponse(payload.copyOf(payload.size / 2)) }
        val result = downloader().download(artifact(), partFile(), {})
        val failure = result as DownloadResult.Failure
        assertEquals(ModelDownloadFailure.SIZE_MISMATCH, failure.kind)
        assertEquals("Downloaded model failed integrity verification.", failure.userMessage)
    }

    @Test
    fun `an altered stream fails with HASH_MISMATCH`() = runBlocking {
        val altered = payload.copyOf()
        altered[altered.size / 2] = (altered[altered.size / 2].toInt() xor 0xFF).toByte()
        alwaysRespond { okResponse(altered) }
        val result = downloader().download(artifact(), partFile(), {})
        val failure = result as DownloadResult.Failure
        assertEquals(ModelDownloadFailure.HASH_MISMATCH, failure.kind)
        // Diagnostics carry the expected/actual fingerprint but never a response body.
        assertTrue(failure.technicalMessage!!.contains(artifact().sha256))
        assertTrue(failure.technicalMessage!!.length < 512)
    }

    @Test
    fun `a missing content-length header still installs a valid artifact`() = runBlocking {
        alwaysRespond { MockResponse().setChunkedBody(Buffer().write(payload), 64 * 1024) }
        val result = downloader().download(artifact(), partFile(), {})
        val success = result as DownloadResult.Success
        assertEquals(payload.size.toLong(), success.bytes)
        assertEquals(payloadSha, success.sha256)
    }

    @Test
    fun `a bogus content-length header cannot prevent installation`() = runBlocking {
        // Chunked transfer with a deliberately absurd Content-Length alongside it. The transport
        // discards the bogus header (OkHttp removes Content-Length when chunked is present), so no
        // header-based decision is possible at all: installation rests on the streamed bytes.
        alwaysRespond {
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", "1")
                .setChunkedBody(Buffer().write(payload), 64 * 1024)
        }

        val observed = client.newCall(Request.Builder().url(server.url("/artifact")).build())
            .execute().use { response -> response.header("Content-Length") }
        assertNull("a lying header must not survive to the client", observed)

        val result = downloader().download(artifact(), partFile(), {})
        val success = result as DownloadResult.Success
        assertEquals(payload.size.toLong(), success.bytes)
        assertEquals(payloadSha, success.sha256)
    }

    @Test
    fun `the pinned request carries the required headers and follows redirects`() = runBlocking {
        alwaysRespond { okResponse(payload) }
        downloader().download(artifact(), partFile(), {})
        val recorded = server.takeRequest()

        assertEquals("application/octet-stream", recorded.getHeader("Accept"))
        assertEquals("identity", recorded.getHeader("Accept-Encoding"))
        assertTrue(recorded.getHeader("User-Agent")!!.startsWith("Halo-Android/"))
        assertTrue(client.followRedirects)
        assertTrue(recorded.path!!.startsWith("/artifact"))
    }

    @Test
    fun `a truncated body is rejected on stream evidence, not on a header comparison`() = runBlocking {
        val truncated = payload.copyOf(payload.size / 2)
        val wrongLength = truncated.size.toString()
        alwaysRespond { okResponse(truncated).setHeader("Content-Length", wrongLength) }

        val result = downloader().download(artifact(), partFile(), {})
        val failure = result as DownloadResult.Failure
        // The authority is the byte count Halo actually counted, never the (self-consistent) header.
        assertEquals(ModelDownloadFailure.SIZE_MISMATCH, failure.kind)
        assertTrue(failure.technicalMessage!!.contains("Expected ${payload.size} bytes"))
    }

    // ---------------------------------------------------------------------------------------
    // Manager-level finalization
    // ---------------------------------------------------------------------------------------

    @Test
    fun `a verified download installs only after the inventory resolves it`() {
        alwaysRespond { okResponse(payload) }
        val root = tempRoot("install")
        val manager = manager(root)

        manager.downloadCatalogModel(entry())
        val state = awaitTerminalState(manager, ENTRY_ID)

        assertEquals(DownloadState.Installed, state)
        val finalFile = File(root, "$ENTRY_ID.litertlm")
        val partFile = File(root, "$ENTRY_ID.litertlm.part")
        assertTrue(finalFile.exists())
        assertEquals(payload.size.toLong(), finalFile.length())
        assertFalse(partFile.exists())
        assertTrue(File(root, "$ENTRY_ID.halo-model.json").exists())

        val descriptor = runBlocking { manager.resolveModel(ENTRY_ID) }
        assertNotNull(descriptor)
        assertEquals(LocalModelValidation.VERIFIED_ARTIFACT, descriptor!!.validation)
        assertEquals(payloadSha, descriptor.sha256)
        assertFalse(descriptor.useModelDefaultContext)
    }

    @Test
    fun `a hash mismatch never produces a final artifact`() {
        alwaysRespond { okResponse(payload) }
        val root = tempRoot("hash")
        // Pin the wrong (but well-formed) fingerprint: the stream must be rejected.
        val manager = manager(root, entries = listOf(catalogEntry(sha256 = "a".repeat(64))))

        manager.downloadCatalogModel(entry(sha256 = "a".repeat(64)))
        val state = awaitTerminalState(manager, ENTRY_ID)

        assertTrue(state is DownloadState.Failed)
        assertEquals(ModelDownloadFailure.HASH_MISMATCH, (state as DownloadState.Failed).kind)
        assertFalse(File(root, "$ENTRY_ID.litertlm").exists())
        assertFalse(File(root, "$ENTRY_ID.litertlm.part").exists())
        assertFalse(File(root, "$ENTRY_ID.halo-model.json").exists())
    }

    @Test
    fun `a byte count mismatch fails with SIZE_MISMATCH and leaves no final file`() {
        alwaysRespond { okResponse(payload) }
        val root = tempRoot("size")
        val shortBy = 1L
        val expected = payload.size.toLong() - shortBy
        assertTrue("test payload must stay above the catalog minimum", expected > 1_048_576L)
        val manager = manager(root, entries = listOf(catalogEntry(expectedBytes = expected)))

        manager.downloadCatalogModel(entry(expectedBytes = expected))
        val state = awaitTerminalState(manager, ENTRY_ID)

        assertEquals(ModelDownloadFailure.SIZE_MISMATCH, (state as DownloadState.Failed).kind)
        assertFalse(File(root, "$ENTRY_ID.litertlm").exists())
    }

    @Test
    fun `cancelling a download stops it and never installs an artifact`() {
        // Slow but real streaming so cancellation is observed promptly.
        alwaysRespond {
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(payload))
                .throttleBody(16 * 1024, 100, TimeUnit.MILLISECONDS)
        }
        val root = tempRoot("cancel")
        val manager = manager(root)

        manager.downloadCatalogModel(entry())
        awaitState(manager, ENTRY_ID) { it is DownloadState.Downloading && it.downloadedBytes > 0L }
        manager.cancelDownload(ENTRY_ID)

        val state = awaitNotDownloading(manager, ENTRY_ID)
        assertTrue("expected cancellation, got $state", state !is DownloadState.Installed)
        Thread.sleep(100)
        assertFalse(File(root, "$ENTRY_ID.litertlm").exists())
        // A cancellation never installs. When bytes had already landed they are kept as a resumable
        // partial (and its pinned metadata); otherwise nothing is left behind.
        val partFile = File(root, "$ENTRY_ID.litertlm.part")
        when (state) {
            is DownloadState.Paused -> {
                assertTrue(partFile.exists())
                assertTrue(partFile.length() > 0L)
                assertTrue(LiteRtPartialDownloadMetadata.sidecarFor(partFile).exists())
            }
            else -> assertFalse("a non-resumable cancellation must not leave a partial", partFile.exists())
        }
    }

    @Test
    fun `a second download for the same model is refused while one is in flight`() {
        alwaysRespond { okResponse(payload).setBodyDelay(10, TimeUnit.SECONDS) }
        val manager = manager(tempRoot("lock"))

        manager.downloadCatalogModel(entry())
        awaitDownloading(manager, ENTRY_ID)
        assertTrue(manager.isDownloading(ENTRY_ID))
        manager.downloadCatalogModel(entry())
        assertTrue(manager.isDownloading(ENTRY_ID))
        manager.cancelDownload(ENTRY_ID)
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private fun manager(root: File, entries: List<LiteRtCatalogModel> = listOf(catalogEntry())) =
        LiteRtModelManager(
            context = context,
            modelRoot = root,
            downloader = downloader(),
            catalog = entries,
        )

    private fun downloader() = OkHttpLiteRtModelDownloader(
        client = client,
        resolveUrl = { server.url("/artifact").toString() },
    )

    private fun entry(
        sha256: String = payloadSha,
        expectedBytes: Long = payload.size.toLong(),
    ) = catalogEntry(sha256 = sha256, expectedBytes = expectedBytes)

    private fun catalogEntry(
        sha256: String = payloadSha,
        expectedBytes: Long = payload.size.toLong(),
    ) = LiteRtCatalogModel(
        id = ENTRY_ID,
        displayName = "Test Artifact",
        repoId = "halo/test-artifact",
        revision = "a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76",
        filename = ENTRY_FILENAME,
        expectedBytes = expectedBytes,
        sha256 = sha256,
        contextWindowTokens = 4096,
        supportsVision = false,
        supportsTools = false,
        minLiteRtLmVersion = "0.17.1",
    )

    private fun artifact() = catalogEntry()

    private fun partFile() = File.createTempFile("halo-litert-download", ".litertlm.part").also { it.delete() }

    private fun tempRoot(name: String): File =
        File(context.cacheDir, "litert-downloader-$name-${System.nanoTime()}").also { it.deleteRecursively(); it.mkdirs() }

    private fun okResponse(body: ByteArray) = MockResponse().setResponseCode(200).setBody(Buffer().write(body))

    private fun alwaysRespond(build: () -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = build()
        }
    }

    private fun awaitTerminalState(
        manager: LiteRtModelManager,
        id: String,
        timeoutMs: Long = 60_000L,
    ): DownloadState = awaitState(manager, id, timeoutMs) {
        it is DownloadState.Installed || it is DownloadState.Failed
    }

    private fun awaitDownloading(manager: LiteRtModelManager, id: String, timeoutMs: Long = 60_000L): DownloadState =
        awaitState(manager, id, timeoutMs) { it is DownloadState.Downloading }

    private fun awaitNotDownloading(manager: LiteRtModelManager, id: String, timeoutMs: Long = 15_000L) =
        awaitState(manager, id, timeoutMs) { it !is DownloadState.Downloading }

    private fun awaitState(
        manager: LiteRtModelManager,
        id: String,
        timeoutMs: Long = 60_000L,
        predicate: (DownloadState?) -> Boolean,
    ): DownloadState {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val state = manager.downloadStates.value[id]
            if (predicate(state)) return state!!
            Thread.sleep(25)
        }
        fail("timed out waiting for download state of $id")
        error("unreachable")
    }

    private fun deterministicPayload(size: Int): ByteArray {
        val out = ByteArray(size)
        var seed = 0x5EED1234
        for (index in out.indices) {
            seed = seed * 1103515245 + 12345
            out[index] = (seed ushr 16).toByte()
        }
        return out
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val PAYLOAD_BYTES = 1_200_000
        const val ENTRY_ID = "test-artifact"
        const val ENTRY_FILENAME = "test-artifact.litertlm"
    }
}
