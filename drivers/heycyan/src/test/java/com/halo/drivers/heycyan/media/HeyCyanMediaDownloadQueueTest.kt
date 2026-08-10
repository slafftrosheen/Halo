package com.halo.drivers.heycyan.media

import com.halo.drivers.heycyan.media.repository.DefaultHeyCyanMediaIdentityResolver
import com.halo.drivers.heycyan.media.repository.DefaultMediaPathResolver
import com.halo.drivers.heycyan.media.repository.FileHeyCyanMediaRepository
import com.halo.drivers.heycyan.media.sync.HeyCyanMediaDownloadQueue
import com.halo.drivers.heycyan.media.sync.MediaDownloadStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HeyCyanMediaDownloadQueueTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    class FakeDownloadStrategy(var shouldSucceed: Boolean = true) : MediaDownloadStrategy {
        val downloadedMedia = mutableListOf<HeyCyanMedia>()

        override suspend fun download(
            media: HeyCyanMedia,
            repository: FileHeyCyanMediaRepository,
            progress: (MediaDownloadProgress) -> Unit
        ): Result<MediaDownloadResult> {
            downloadedMedia.add(media)
            return if (shouldSucceed) {
                val session = repository.beginWrite(media)
                val bytes = "TEST_BYTES".toByteArray()
                session.write(bytes, 0, bytes.size)
                val stored = repository.commit(session, "fakehash").getOrThrow()
                Result.success(
                    MediaDownloadResult(
                        media = media,
                        bytesWritten = bytes.size.toLong(),
                        destination = MediaLocation(stored.localPath),
                        integrity = MediaIntegrity(bytes.size.toLong(), bytes.size.toLong(), "fakehash")
                    )
                )
            } else {
                Result.failure(RuntimeException("Download error"))
            }
        }
    }

    @Test
    fun queueProcessesItemsSequentially() = runTest {
        val rootDir = tempFolder.newFolder("queue_storage")
        val repository = FileHeyCyanMediaRepository(
            storageDir = rootDir,
            pathResolver = DefaultMediaPathResolver(rootDir.absolutePath, "test_device"),
            identityResolver = DefaultHeyCyanMediaIdentityResolver(),
        )

        val strategy = FakeDownloadStrategy(shouldSucceed = true)
        val queue = HeyCyanMediaDownloadQueue(strategy, repository)

        val items = listOf(
            HeyCyanMedia.Photo("p1.jpg", "p1.jpg", 100L, 100L, null, null),
            HeyCyanMedia.Video("v1.mp4", "v1.mp4", 500L, 200L, null),
        )

        val queueResult = queue.process(items) { _, _, _, _ -> }
        assertEquals(2, queueResult.downloaded)
        assertEquals(0, queueResult.failed)
        assertEquals(2, strategy.downloadedMedia.size)
        assertEquals("p1.jpg", strategy.downloadedMedia[0].filename)
        assertEquals("v1.mp4", strategy.downloadedMedia[1].filename)
    }

    @Test
    fun queueContinuesOnIndividualFailure() = runTest {
        val rootDir = tempFolder.newFolder("queue_storage_fail")
        val repository = FileHeyCyanMediaRepository(
            storageDir = rootDir,
            pathResolver = DefaultMediaPathResolver(rootDir.absolutePath, "test_device"),
            identityResolver = DefaultHeyCyanMediaIdentityResolver(),
        )

        val strategy = FakeDownloadStrategy(shouldSucceed = false)
        val queue = HeyCyanMediaDownloadQueue(strategy, repository)

        val items = listOf(
            HeyCyanMedia.Photo("p1.jpg", "p1.jpg", 100L, 100L, null, null),
        )

        val queueResult = queue.process(items) { _, _, _, _ -> }
        assertEquals(0, queueResult.downloaded)
        assertEquals(1, queueResult.failed)
    }
}
