package com.halo.drivers.heycyan.media

import com.halo.drivers.heycyan.media.repository.DefaultHeyCyanMediaIdentityResolver
import com.halo.drivers.heycyan.media.repository.DefaultMediaPathResolver
import com.halo.drivers.heycyan.media.repository.FileHeyCyanMediaRepository
import com.halo.drivers.heycyan.media.sync.HeyCyanMediaDiffer
import com.halo.drivers.heycyan.media.sync.HeyCyanMediaSyncState
import com.halo.drivers.heycyan.media.sync.HeyCyanMediaSynchronizer
import com.halo.drivers.heycyan.media.sync.MediaDownloadStrategy
import com.halo.drivers.heycyan.media.sync.MediaSyncPolicy
import com.halo.drivers.heycyan.media.sync.MediaSyncRequest
import com.halo.drivers.heycyan.media.sync.StreamingMediaDownloadStrategy
import com.halo.drivers.heycyan.wifi.DefaultHeyCyanMediaApi
import com.halo.drivers.heycyan.wifi.HeyCyanMediaApi
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress

class HeyCyanMediaSynchronizerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: HttpServer
    private var serverPort: Int = 0
    private lateinit var mediaApi: DefaultHeyCyanMediaApi
    private lateinit var storageDir: File
    private lateinit var repository: FileHeyCyanMediaRepository
    private lateinit var identityResolver: DefaultHeyCyanMediaIdentityResolver
    private lateinit var differ: HeyCyanMediaDiffer
    private lateinit var downloadStrategy: StreamingMediaDownloadStrategy
    private lateinit var synchronizer: HeyCyanMediaSynchronizer

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        serverPort = server.address.port
        server.start()

        mediaApi = DefaultHeyCyanMediaApi(
            deviceIp = InetSocketAddress("127.0.0.1", serverPort).address,
            port = serverPort,
        )

        storageDir = tempFolder.newFolder("sync_storage")
        identityResolver = DefaultHeyCyanMediaIdentityResolver()
        val pathResolver = DefaultMediaPathResolver(storageDir.absolutePath, "test_device")
        repository = FileHeyCyanMediaRepository(storageDir, pathResolver, identityResolver)
        differ = HeyCyanMediaDiffer(identityResolver)
        downloadStrategy = StreamingMediaDownloadStrategy(mediaApi)
        synchronizer = HeyCyanMediaSynchronizer(mediaApi, repository, differ, downloadStrategy)
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun fullSyncPipelineDownloadsNewPhotoVideoAudioWithBinaryIntegrity() = runTest {
        val samplePhotoBytes = "PHOTO_FIXTURE_BYTES_9999".toByteArray()
        val sampleVideoBytes = "VIDEO_FIXTURE_BYTES_8888".toByteArray()

        server.createContext("/files/media.config") { exchange ->
            val json = """{"files": ["sample_photo.jpg", "sample_video.mp4"]}"""
            exchange.sendResponseHeaders(200, json.toByteArray().size.toLong())
            exchange.responseBody.write(json.toByteArray())
            exchange.close()
        }

        server.createContext("/files/sample_photo.jpg") { exchange ->
            exchange.sendResponseHeaders(200, samplePhotoBytes.size.toLong())
            exchange.responseBody.write(samplePhotoBytes)
            exchange.close()
        }

        server.createContext("/files/sample_video.mp4") { exchange ->
            exchange.sendResponseHeaders(200, sampleVideoBytes.size.toLong())
            exchange.responseBody.write(sampleVideoBytes)
            exchange.close()
        }

        val result = synchronizer.synchronize(MediaSyncRequest(policy = MediaSyncPolicy.NEW_ONLY))
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(2, summary.downloaded)
        assertEquals(0, summary.failed)

        // Verify stored files match remote binary fixture bytes exactly
        val storedList = repository.list()
        assertEquals(2, storedList.size)

        val storedPhoto = storedList.first { it.filename == "sample_photo.jpg" }
        assertArrayEquals(samplePhotoBytes, File(storedPhoto.localPath).readBytes())

        val storedVideo = storedList.first { it.filename == "sample_video.mp4" }
        assertArrayEquals(sampleVideoBytes, File(storedVideo.localPath).readBytes())

        assertTrue(synchronizer.state.value is HeyCyanMediaSyncState.Completed)
    }

    @Test
    fun newOnlyPolicySkipsAlreadyDownloadedFiles() = runTest {
        server.createContext("/files/media.config") { exchange ->
            val json = """{"files": ["photo1.jpg"]}"""
            exchange.sendResponseHeaders(200, json.toByteArray().size.toLong())
            exchange.responseBody.write(json.toByteArray())
            exchange.close()
        }

        val photoBytes = "IMAGE_BYTES".toByteArray()
        server.createContext("/files/photo1.jpg") { exchange ->
            exchange.sendResponseHeaders(200, photoBytes.size.toLong())
            exchange.responseBody.write(photoBytes)
            exchange.close()
        }

        // First sync
        val res1 = synchronizer.synchronize(MediaSyncRequest(policy = MediaSyncPolicy.NEW_ONLY))
        assertEquals(1, res1.getOrThrow().downloaded)

        // Second sync with NEW_ONLY policy
        val res2 = synchronizer.synchronize(MediaSyncRequest(policy = MediaSyncPolicy.NEW_ONLY))
        assertEquals(0, res2.getOrThrow().downloaded)
        assertEquals(1, res2.getOrThrow().skipped)
    }

    @Test
    fun cancellationIsPropagatedAndStateIsCancelled() = runTest {
        server.createContext("/files/media.config") { exchange ->
            val json = """{"files": ["long.jpg"]}"""
            exchange.sendResponseHeaders(200, json.toByteArray().size.toLong())
            exchange.responseBody.write(json.toByteArray())
            exchange.close()
        }

        val cancellingStrategy = object : MediaDownloadStrategy {
            override suspend fun download(
                media: HeyCyanMedia,
                repository: com.halo.drivers.heycyan.media.repository.HeyCyanMediaRepository,
                progress: (MediaDownloadProgress) -> Unit
            ): Result<MediaDownloadResult> {
                throw CancellationException("User cancelled sync")
            }
        }

        val cancellingSync = HeyCyanMediaSynchronizer(mediaApi, repository, differ, cancellingStrategy)

        try {
            cancellingSync.synchronize()
            assertTrue("CancellationException should have been rethrown", false)
        } catch (e: CancellationException) {
            assertTrue(cancellingSync.state.value is HeyCyanMediaSyncState.Cancelled)
        }
    }
}
