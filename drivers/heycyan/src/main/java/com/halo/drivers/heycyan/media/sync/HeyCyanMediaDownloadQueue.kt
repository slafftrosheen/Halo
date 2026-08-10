package com.halo.drivers.heycyan.media.sync

import com.halo.drivers.heycyan.media.HeyCyanMedia
import com.halo.drivers.heycyan.media.MediaDownloadProgress
import com.halo.drivers.heycyan.media.MediaDownloadResult
import com.halo.drivers.heycyan.media.repository.FileHeyCyanMediaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DownloadQueueResult(
    val downloaded: Int,
    val failed: Int,
)

/**
 * Sequential download queue for HeyCyan media items.
 * Processes 1 download at a time to maintain wearable Wi-Fi connection stability.
 */
class HeyCyanMediaDownloadQueue(
    private val downloadStrategy: MediaDownloadStrategy,
    private val repository: FileHeyCyanMediaRepository,
) {
    private val queueMutex = Mutex()

    suspend fun process(
        items: List<HeyCyanMedia>,
        onProgress: suspend (
            media: HeyCyanMedia,
            index: Int,
            total: Int,
            progress: MediaDownloadProgress,
        ) -> Unit,
    ): DownloadQueueResult = queueMutex.withLock {
        var downloaded = 0
        var failed = 0

        for ((index, media) in items.withIndex()) {
            try {
                onProgress(
                    media,
                    index + 1,
                    items.size,
                    MediaDownloadProgress(bytesReceived = 0L, totalBytes = media.sizeBytes)
                )

                val result = downloadStrategy.download(media, repository) { p ->
                    // Progress updates can be handled asynchronously or passed upstream
                }

                if (result.isSuccess) {
                    downloaded++
                } else {
                    failed++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
            }
        }

        DownloadQueueResult(downloaded = downloaded, failed = failed)
    }
}
