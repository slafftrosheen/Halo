package com.halo.drivers.heycyan.media.sync

import com.halo.drivers.heycyan.media.HeyCyanMedia
import com.halo.drivers.heycyan.media.MediaDownloadProgress
import com.halo.drivers.heycyan.media.repository.HeyCyanMediaRepository
import com.halo.drivers.heycyan.media.repository.toMediaType
import com.halo.drivers.heycyan.wifi.HeyCyanMediaApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Orchestrates full media synchronization:
 *   Manifest -> Diffing -> Queue -> Streaming Downloader -> Local Repository -> StateFlow
 */
class HeyCyanMediaSynchronizer(
    private val mediaApi: HeyCyanMediaApi,
    private val repository: HeyCyanMediaRepository,
    private val differ: HeyCyanMediaDiffer,
    private val downloadStrategy: MediaDownloadStrategy,
) {
    private val _state = MutableStateFlow<HeyCyanMediaSyncState>(HeyCyanMediaSyncState.Idle)
    val state: StateFlow<HeyCyanMediaSyncState> = _state.asStateFlow()

    suspend fun synchronize(
        request: MediaSyncRequest = MediaSyncRequest(),
    ): Result<HeyCyanMediaSyncResult> {
        _state.value = HeyCyanMediaSyncState.FetchingManifest

        val manifest = mediaApi.getMediaConfig().getOrElse { err ->
            val failure = HeyCyanMediaSyncFailure.Manifest(
                message = err.message ?: "Failed fetching media manifest",
                cause = err,
            )
            _state.value = HeyCyanMediaSyncState.Failed(failure)
            return Result.failure(err)
        }

        _state.value = HeyCyanMediaSyncState.Planning(remoteCount = manifest.items.size)

        val local = repository.list()
        val filteredRemote = manifest.items.filter { it.toMediaType() in request.mediaTypes }
        val diff = differ.diff(remote = filteredRemote, local = local)

        val queueItems: List<HeyCyanMedia> = when (request.policy) {
            MediaSyncPolicy.NEW_ONLY -> diff.newItems
            MediaSyncPolicy.NEW_AND_CHANGED -> diff.newItems + diff.changedItems
            MediaSyncPolicy.FORCE_REDOWNLOAD -> manifest.items
        }

        var downloaded = 0
        var failed = 0

        for ((index, media) in queueItems.withIndex()) {
            try {
                _state.value = HeyCyanMediaSyncState.Downloading(
                    current = media,
                    index = index + 1,
                    total = queueItems.size,
                    progress = MediaDownloadProgress(
                        bytesReceived = 0L,
                        totalBytes = media.sizeBytes,
                    ),
                )

                val downloadResult = downloadStrategy.download(media, repository) { p ->
                    _state.value = HeyCyanMediaSyncState.Downloading(
                        current = media,
                        index = index + 1,
                        total = queueItems.size,
                        progress = p,
                    )
                }

                if (downloadResult.isSuccess) {
                    downloaded++
                } else {
                    failed++
                }
            } catch (e: CancellationException) {
                // MANDATORY: Never swallow cancellation exception!
                _state.value = HeyCyanMediaSyncState.Cancelled
                throw e
            } catch (e: Exception) {
                failed++
            }
        }

        val result = HeyCyanMediaSyncResult(
            downloaded = downloaded,
            skipped = diff.alreadyPresent.size,
            failed = failed,
        )

        _state.value = HeyCyanMediaSyncState.Completed(
            downloaded = result.downloaded,
            skipped = result.skipped,
            failed = result.failed,
        )

        return Result.success(result)
    }
}
