package com.halo.providers.litert.models

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A 1-2 GB artifact must never be thrown away and restarted because the socket dropped. Resume is
 * only attempted against an exactly matching pinned artifact, and the resumed file is verified over
 * its *whole* content so a corrupted prefix can never be accepted.
 */
@RunWith(RobolectricTestRunner::class)
class LiteRtDownloadResumeTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    private val payload = deterministicPayload(PAYLOAD_BYTES)
    private val payloadSha = sha256(payload)
    private val half = payload.size / 2

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

    @Test
    fun `a 206 response resumes from the stored offset and verifies the whole file`() = runBlocking {
        val part = partFile()
        part.writeBytes(payload.copyOfRange(0, half))

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                assertEquals("bytes=$half-", request.getHeader("Range"))
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $half-${payload.size - 1}/${payload.size}")
                    .setBody(Buffer().write(payload.copyOfRange(half, payload.size)))
            }
        }

        val result = downloader().download(artifact(), part, {}, resumeFrom = half.toLong())

        val success = result as DownloadResult.Success
        assertEquals(payload.size.toLong(), success.bytes)
        assertEquals(payloadSha, success.sha256)
        assertEquals(half.toLong(), success.resumedFrom)
        assertTrue(part.readBytes().contentEquals(payload))
    }

    @Test
    fun `a server that ignores the Range request restarts from zero`() = runBlocking {
        val part = partFile()
        // Junk prefix of the right length: a restart must fully replace it.
        part.writeBytes(ByteArray(half) { 0x7F })

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                assertNotNull(request.getHeader("Range"))
                // 200 with the full body: the Range request was ignored.
                return MockResponse().setResponseCode(200).setBody(Buffer().write(payload))
            }
        }

        val result = downloader().download(artifact(), part, {}, resumeFrom = half.toLong())

        val success = result as DownloadResult.Success
        assertEquals(0L, success.resumedFrom)
        assertEquals(payloadSha, success.sha256)
        assertTrue(part.readBytes().contentEquals(payload))
    }

    @Test
    fun `a corrupt resumed prefix is rejected by whole-file hash verification`() = runBlocking {
        val part = partFile()
        part.writeBytes(ByteArray(half) { 0x11 })

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $half-${payload.size - 1}/${payload.size}")
                    .setBody(Buffer().write(payload.copyOfRange(half, payload.size)))
        }

        val result = downloader().download(artifact(), part, {}, resumeFrom = half.toLong())

        val failure = result as DownloadResult.Failure
        assertEquals(ModelDownloadFailure.HASH_MISMATCH, failure.kind)
        assertTrue(failure.partialBytes > 0L)
    }

    @Test
    fun `content range parsing is strict`() {
        assertEquals(100L, OkHttpLiteRtModelDownloader.parseContentRangeStart("bytes 100-199/200"))
        assertEquals(0L, OkHttpLiteRtModelDownloader.parseContentRangeStart("bytes 0-9/10"))
        assertEquals(null, OkHttpLiteRtModelDownloader.parseContentRangeStart(null))
        assertEquals(null, OkHttpLiteRtModelDownloader.parseContentRangeStart("items 1-2/3"))
    }

    @Test
    fun `a stored partial surfaces as resumable and discard removes it`() {
        val root = tempRoot("partial")
        val part = File(root, "$ENTRY_ID.litertlm.part")
        part.writeBytes(payload.copyOfRange(0, half))
        LiteRtPartialDownloadMetadata
            .sidecarFor(part)
            .writeText(LiteRtPartialDownloadMetadata.forArtifact(catalogEntry(), half.toLong()).toJson())

        val manager = manager(root)

        assertEquals(half.toLong(), manager.partialDownloadBytes(ENTRY_ID))
        val state = awaitState(manager, ENTRY_ID) { it is DownloadState.Paused }
        assertTrue(state is DownloadState.Paused)

        manager.discardPartial(ENTRY_ID)
        assertFalse(part.exists())
        assertFalse(LiteRtPartialDownloadMetadata.sidecarFor(part).exists())
        assertEquals(null, manager.partialDownloadBytes(ENTRY_ID))
    }

    @Test
    fun `partial metadata from a different pin is never resumed`() {
        val root = tempRoot("unpinned")
        val part = File(root, "$ENTRY_ID.litertlm.part")
        part.writeBytes(payload.copyOfRange(0, half))
        LiteRtPartialDownloadMetadata
            .sidecarFor(part)
            .writeText(
                LiteRtPartialDownloadMetadata
                    .forArtifact(catalogEntry(), half.toLong())
                    .copy(revision = "b".repeat(40))
                    .toJson()
            )

        val manager = manager(root)
        // The stored bytes survive, but the metadata proves they belong to a different pin, so a
        // resume against this artifact must be refused (resolveResumeOffset restarts from zero).
        awaitState(manager, ENTRY_ID) { it is DownloadState.Paused }
        assertEquals(half.toLong(), manager.partialDownloadBytes(ENTRY_ID))

        val metadata = LiteRtPartialDownloadMetadata.fromFile(LiteRtPartialDownloadMetadata.sidecarFor(part))
        assertNotNull(metadata)
        assertFalse(metadata!!.matches(catalogEntry()))
    }

    @Test
    fun `a transient network loss preserves the partial for resume`() = runBlocking {
        val root = tempRoot("network-loss")
        writePartial(root, half)

        val manager = managerWith(
            root,
            FailingDownloader(
                DownloadResult.Failure(
                    ModelDownloadFailure.NETWORK,
                    "Download failed. Check your connection and try again.",
                    "java.net.SocketTimeoutException: timeout",
                    partialBytes = half.toLong(),
                )
            ),
        )
        manager.downloadCatalogModel(catalogEntry())

        val state = awaitState(manager, ENTRY_ID) { it is DownloadState.Failed }
        val failure = state as DownloadState.Failed
        assertEquals(ModelDownloadFailure.NETWORK, failure.kind)
        assertTrue("a transient failure must stay resumable", failure.resumable)
        assertTrue("the partial must be preserved", partOf(root).exists())
        assertEquals(half.toLong(), partOf(root).length())
        assertTrue(LiteRtPartialDownloadMetadata.sidecarFor(partOf(root)).exists())
        assertEquals(half.toLong(), manager.partialDownloadBytes(ENTRY_ID))
    }

    @Test
    fun `an integrity failure discards the partial instead of resuming it`() = runBlocking {
        val root = tempRoot("integrity-discard")
        writePartial(root, half)

        val manager = managerWith(
            root,
            FailingDownloader(
                DownloadResult.Failure(
                    ModelDownloadFailure.SIZE_MISMATCH,
                    OkHttpLiteRtModelDownloader.INTEGRITY_USER_MESSAGE,
                    "Expected ${payload.size} bytes",
                    partialBytes = half.toLong(),
                )
            ),
        )
        manager.downloadCatalogModel(catalogEntry())

        val state = awaitState(manager, ENTRY_ID) { it is DownloadState.Failed }
        assertFalse((state as DownloadState.Failed).resumable)
        assertFalse(partOf(root).exists())
        assertFalse(LiteRtPartialDownloadMetadata.sidecarFor(partOf(root)).exists())
    }

    @Test
    fun `a gated artifact never enters the downloader`() {
        val root = tempRoot("gated")
        val manager = LiteRtModelManager(
            context = context,
            modelRoot = root,
            downloader = downloader(),
            catalog = listOf(LiteRtModelCatalog.GEMMA3_1B_IT_INT4),
        )

        manager.downloadCatalogModel(LiteRtModelCatalog.GEMMA3_1B_IT_INT4)
        val state = awaitState(manager, LiteRtModelCatalog.GEMMA3_1B_IT_INT4_ID) {
            it is DownloadState.Failed
        }
        val failure = state as DownloadState.Failed
        assertEquals(ModelDownloadFailure.AUTH_REQUIRED, failure.kind)
        assertFalse(failure.resumable)
        assertEquals(0, server.requestCount)
    }

    // ---------------------------------------------------------------------------------------

    private fun manager(root: File) = managerWith(root, downloader())

    private fun managerWith(root: File, downloader: LiteRtModelDownloader) = LiteRtModelManager(
        context = context,
        modelRoot = root,
        downloader = downloader,
        catalog = listOf(catalogEntry()),
    )

    private fun writePartial(root: File, bytes: Int) {
        val part = partOf(root)
        part.writeBytes(payload.copyOfRange(0, bytes))
        LiteRtPartialDownloadMetadata
            .sidecarFor(part)
            .writeText(LiteRtPartialDownloadMetadata.forArtifact(catalogEntry(), bytes.toLong()).toJson())
    }

    private fun partOf(root: File): File = File(root, "$ENTRY_ID.litertlm.part")

    /** Never touches the network: lets the manager's keep/discard policy be tested directly. */
    private class FailingDownloader(private val failure: DownloadResult.Failure) : LiteRtModelDownloader {
        override suspend fun download(
            artifact: LiteRtCatalogModel,
            destinationPart: File,
            onProgress: (DownloadProgress) -> Unit,
            resumeFrom: Long,
        ): DownloadResult = failure
    }

    private fun downloader() = OkHttpLiteRtModelDownloader(
        client = client,
        resolveUrl = { server.url("/artifact").toString() },
    )

    private fun artifact() = catalogEntry()

    private fun catalogEntry() = LiteRtCatalogModel(
        id = ENTRY_ID,
        displayName = "Test Artifact",
        repoId = "halo/test-artifact",
        revision = "a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76",
        filename = ENTRY_FILENAME,
        expectedBytes = payload.size.toLong(),
        sha256 = payloadSha,
        contextWindowTokens = 4096,
        supportsVision = false,
        supportsTools = false,
        minLiteRtLmVersion = "0.17.1",
    )

    private fun partFile(): File =
        File(context.cacheDir, "resume-${System.nanoTime()}.litertlm.part").also { it.delete() }

    private fun tempRoot(name: String): File =
        File(context.cacheDir, "litert-resume-$name-${System.nanoTime()}").also {
            it.deleteRecursively()
            it.mkdirs()
        }

    private fun awaitState(
        manager: LiteRtModelManager,
        id: String,
        timeoutMs: Long = 30_000L,
        predicate: (DownloadState?) -> Boolean,
    ): DownloadState {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val state = manager.downloadStates.value[id]
            if (predicate(state)) return state!!
            Thread.sleep(25)
        }
        error("timed out waiting for download state of $id")
    }

    private fun deterministicPayload(size: Int): ByteArray {
        val out = ByteArray(size)
        var seed = 0x1234ABCD
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
