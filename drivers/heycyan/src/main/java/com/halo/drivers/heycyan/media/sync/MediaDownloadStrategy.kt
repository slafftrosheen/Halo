package com.halo.drivers.heycyan.media.sync

import com.halo.drivers.heycyan.media.HeyCyanMedia
import com.halo.drivers.heycyan.media.MediaDownloadProgress
import com.halo.drivers.heycyan.media.MediaDownloadResult
import com.halo.drivers.heycyan.media.repository.HeyCyanMediaRepository
import com.halo.drivers.heycyan.media.repository.MediaWriteSession
import com.halo.drivers.heycyan.wifi.HeyCyanMediaApi
import kotlinx.coroutines.CancellationException

/**
 * Strategy interface for downloading media files with streaming and resume support.
 */
interface MediaDownloadStrategy {
    suspend fun download(
        media: HeyCyanMedia,
        repository: HeyCyanMediaRepository,
        progress: (MediaDownloadProgress) -> Unit,
    ): Result<MediaDownloadResult>
}

/**
 * Streaming download strategy.
 * Writes to a temporary `.part` session and atomically commits on verified download completion.
 * Discards partial files on failure if server range/resume support is not established.
 */
class StreamingMediaDownloadStrategy(
    private val mediaApi: HeyCyanMediaApi,
) : MediaDownloadStrategy {

    override suspend fun download(
        media: HeyCyanMedia,
        repository: HeyCyanMediaRepository,
        progress: (MediaDownloadProgress) -> Unit,
    ): Result<MediaDownloadResult> {
        val session: MediaWriteSession = repository.beginWrite(media)
        try {
            // Initiate HTTP download via mediaApi directly into session
            val result = mediaApi.download(media, session) { p ->
                progress(p)
            }

            return if (result.isSuccess) {
                val downloadRes = result.getOrThrow()
                repository.commit(session, sha256 = downloadRes.integrity.sha256).getOrThrow()
                result
            } else {
                repository.abort(session)
                result
            }
        } catch (e: CancellationException) {
            repository.abort(session)
            throw e
        } catch (e: Exception) {
            repository.abort(session)
            return Result.failure(e)
        }
    }
}
