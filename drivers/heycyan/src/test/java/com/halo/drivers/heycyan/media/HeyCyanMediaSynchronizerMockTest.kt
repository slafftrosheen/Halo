package com.halo.drivers.heycyan.media

import com.halo.drivers.heycyan.media.repository.DefaultHeyCyanMediaIdentityResolver
import com.halo.drivers.heycyan.media.repository.HeyCyanMediaType
import com.halo.drivers.heycyan.media.repository.StoredHeyCyanMedia
import com.halo.drivers.heycyan.media.sync.HeyCyanMediaDiffer
import com.halo.drivers.heycyan.media.sync.HeyCyanMediaSyncState
import com.halo.drivers.heycyan.media.sync.HeyCyanMediaSynchronizer
import com.halo.drivers.heycyan.media.sync.MediaSyncRequest
import com.halo.drivers.heycyan.mock.MockDownloadStrategy
import com.halo.drivers.heycyan.mock.MockHeyCyanMediaApi
import com.halo.drivers.heycyan.mock.MockMediaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PR-030 Mock-based criteria tests: MS-01 through MS-06.
 *
 * These tests use only in-process mocks — no filesystem, no HTTP server,
 * no Android SDK required. They complement [HeyCyanMediaSynchronizerTest]
 * which uses a real local HTTP server for binary integrity verification.
 */
class HeyCyanMediaSynchronizerMockTest {

    private lateinit var mockApi: MockHeyCyanMediaApi
    private lateinit var mockRepo: MockMediaRepository
    private lateinit var mockDownload: MockDownloadStrategy
    private lateinit var synchronizer: HeyCyanMediaSynchronizer

    private val identityResolver = DefaultHeyCyanMediaIdentityResolver()

    @Before
    fun setUp() {
        mockApi = MockHeyCyanMediaApi()
        mockRepo = MockMediaRepository()
        mockDownload = MockDownloadStrategy()
        synchronizer = HeyCyanMediaSynchronizer(
            mediaApi = mockApi,
            repository = mockRepo,
            differ = HeyCyanMediaDiffer(identityResolver),
            downloadStrategy = mockDownload,
        )
    }

    // --- MS-01 ---
    @Test
    fun downloadsAllNewItems() = runTest {
        mockApi.manifest = MockHeyCyanMediaApi.threeItemManifest()

        val result = synchronizer.synchronize()

        assertTrue("Expected successful sync", result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals("Expected 3 downloads", 3, summary.downloaded)
        assertEquals("Expected 0 skipped", 0, summary.skipped)
        assertEquals("Expected 0 failed", 0, summary.failed)
        assertEquals("Download strategy should have been called 3 times", 3, mockDownload.downloadedItems.size)
    }

    // --- MS-02 ---
    @Test
    fun skipsAlreadyPresentItems() = runTest {
        val manifest = MockHeyCyanMediaApi.threeItemManifest()
        mockApi.manifest = manifest

        val existingItems = manifest.items.map { media ->
            StoredHeyCyanMedia(
                identity = identityResolver.identity(media),
                deviceId = null,
                remoteId = media.id,
                filename = media.filename,
                mediaType = when (media) {
                    is HeyCyanMedia.Photo -> HeyCyanMediaType.PHOTO
                    is HeyCyanMedia.Video -> HeyCyanMediaType.VIDEO
                    is HeyCyanMedia.Audio -> HeyCyanMediaType.AUDIO
                    is HeyCyanMedia.Unknown -> HeyCyanMediaType.UNKNOWN
                },
                localPath = "/mock/${media.filename}",
                sizeBytes = media.sizeBytes ?: 0L,
                createdAtMillis = null,
                downloadedAtMillis = System.currentTimeMillis(),
                sha256 = null,
            )
        }
        mockRepo.seed(existingItems)

        val result = synchronizer.synchronize()

        assertTrue("Expected successful sync", result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals("Expected 0 downloads", 0, summary.downloaded)
        assertEquals("Expected 3 skipped", 3, summary.skipped)
        assertTrue("Download strategy must not have been called", mockDownload.downloadedItems.isEmpty())
    }

    // --- MS-03 ---
    @Test
    fun manifestFailurePropagated() = runTest {
        mockApi.manifestBehaviour = MockHeyCyanMediaApi.ManifestBehaviour.Fail(
            cause = RuntimeException("HTTP 503")
        )

        val result = synchronizer.synchronize()

        assertTrue("Expected failure from manifest fetch", result.isFailure)
        val state = synchronizer.state.value
        assertTrue("Expected state to be Failed", state is HeyCyanMediaSyncState.Failed)
    }

    // --- MS-04 ---
    @Test
    fun continuesAfterOneDownloadFailure() = runTest {
        val manifest = MockHeyCyanMediaApi.threeItemManifest()
        mockApi.manifest = manifest

        var callCount = 0
        val mixedDownloadStrategy = object : com.halo.drivers.heycyan.media.sync.MediaDownloadStrategy {
            override suspend fun download(
                media: HeyCyanMedia,
                repository: com.halo.drivers.heycyan.media.repository.FileHeyCyanMediaRepository,
                progress: (MediaDownloadProgress) -> Unit,
            ): Result<MediaDownloadResult> {
                callCount++
                return if (callCount == 1) {
                    Result.failure(RuntimeException("Network error"))
                } else {
                    val bytes = media.sizeBytes ?: 1024L
                    Result.success(
                        MediaDownloadResult(
                            media = media,
                            bytesWritten = bytes,
                            destination = MediaLocation("/mock/${media.filename}"),
                            integrity = MediaIntegrity(bytes, bytes, null),
                        )
                    )
                }
            }
        }

        val partialSync = HeyCyanMediaSynchronizer(
            mediaApi = mockApi,
            repository = mockRepo,
            differ = HeyCyanMediaDiffer(identityResolver),
            downloadStrategy = mixedDownloadStrategy,
        )

        val result = partialSync.synchronize()

        assertTrue("Expected overall success (partial)", result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals("Expected 2 successful downloads", 2, summary.downloaded)
        assertEquals("Expected 1 failed", 1, summary.failed)
        assertEquals("Expected 3 total attempts", 3, callCount)
    }

    // --- MS-05 ---
    @Test
    fun cancellationPropagatesToState() = runTest {
        val manyItems = (1..10).map { i ->
            HeyCyanMedia.Photo(
                id = "img${i.toString().padStart(3, '0')}.jpg",
                filename = "img${i.toString().padStart(3, '0')}.jpg",
                sizeBytes = 512_000L,
                createdAt = null,
                width = null,
                height = null,
            )
        }
        mockApi.manifest = HeyCyanMediaManifest(deviceId = null, generatedAt = null, items = manyItems)

        var syncCallCount = 0
        val cancellingStrategy = object : com.halo.drivers.heycyan.media.sync.MediaDownloadStrategy {
            override suspend fun download(
                media: HeyCyanMedia,
                repository: com.halo.drivers.heycyan.media.repository.FileHeyCyanMediaRepository,
                progress: (MediaDownloadProgress) -> Unit,
            ): Result<MediaDownloadResult> {
                syncCallCount++
                if (syncCallCount >= 2) throw CancellationException("Test cancellation")
                return Result.success(
                    MediaDownloadResult(
                        media = media,
                        bytesWritten = 1024L,
                        destination = MediaLocation("/mock/${media.filename}"),
                        integrity = MediaIntegrity(null, 1024L, null),
                    )
                )
            }
        }

        val cancelSync = HeyCyanMediaSynchronizer(
            mediaApi = mockApi,
            repository = mockRepo,
            differ = HeyCyanMediaDiffer(identityResolver),
            downloadStrategy = cancellingStrategy,
        )

        try {
            cancelSync.synchronize()
        } catch (_: CancellationException) {
            // expected
        }

        assertEquals(
            "State should be Cancelled",
            HeyCyanMediaSyncState.Cancelled,
            cancelSync.state.value,
        )
    }

    // --- MS-06 ---
    @Test
    fun stateTransitionsAreCorrect() = runTest {
        mockApi.manifest = MockHeyCyanMediaApi.threeItemManifest()

        val observedStates = mutableListOf<HeyCyanMediaSyncState>()
        val collectJob = launch {
            synchronizer.state.collect { observedStates.add(it) }
        }

        synchronizer.synchronize()
        collectJob.cancel()

        val kinds = observedStates.map { it::class.simpleName }
        assertTrue("Should have Idle", kinds.contains("Idle"))
        assertTrue("Should have FetchingManifest", kinds.contains("FetchingManifest"))
        assertTrue("Should have Planning", kinds.contains("Planning"))
        assertTrue("Should have Downloading", kinds.contains("Downloading"))
        assertTrue("Should have Completed", kinds.contains("Completed"))

        val fetchIdx = kinds.indexOf("FetchingManifest")
        val planIdx = kinds.indexOf("Planning")
        val completedIdx = kinds.lastIndexOf("Completed")
        assertTrue("FetchingManifest < Planning", fetchIdx < planIdx)
        assertTrue("Planning < Completed", planIdx < completedIdx)
    }
}
