package com.halo.drivers.heycyan.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Full media synchronization engine for HeyCyan glasses.
 *
 * Pipeline:
 * ```
 * device media inventory → compare local inventory → identify new files
 *   → download → validate → persist metadata → mark synchronized
 * ```
 *
 * Supports:
 * - Duplicate detection
 * - Partial download recovery (FAILED → retry)
 * - Download failure handling
 * - Device disappearance (cancellation)
 * - Progress reporting
 * - Cancellation
 */
class HeyCyanMediaSync(
    private val inventory: HeyCyanMediaInventory,
    private val downloader: MediaFileDownloader
) {

    /**
     * Abstraction for the actual file download mechanism.
     * Implemented by HeyCyanMediaDownloader (WiFi) in PR-021.
     */
    interface MediaFileDownloader {
        suspend fun download(item: MediaItem): Result<DownloadResult>
    }

    data class DownloadResult(
        val localPath: String,
        val sizeBytes: Long,
        val valid: Boolean
    )

    enum class SyncStatus {
        IDLE,
        SYNCING,
        COMPLETED,
        CANCELLED,
        ERROR
    }

    data class SyncProgress(
        val status: SyncStatus,
        val totalFiles: Int,
        val completedFiles: Int,
        val failedFiles: Int,
        val currentFile: String?,
        val bytesDownloaded: Long
    )

    private val _progress = MutableStateFlow(
        SyncProgress(
            status = SyncStatus.IDLE,
            totalFiles = 0,
            completedFiles = 0,
            failedFiles = 0,
            currentFile = null,
            bytesDownloaded = 0L
        )
    )
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    private val _syncEvents = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 32)
    val syncEvents: Flow<SyncEvent> = _syncEvents.asSharedFlow()

    @Volatile
    private var cancelled = false

    /**
     * Runs a full sync cycle: discovers unsynced files and downloads them.
     *
     * @param manifestFiles Current file list from the device manifest
     * @return Summary of the sync operation
     */
    suspend fun sync(manifestFiles: List<String>): SyncSummary {
        cancelled = false
        var completed = 0
        var failed = 0
        var bytesTotal = 0L

        try {
            // Update inventory with manifest
            val newFiles = inventory.updateFromManifest(manifestFiles)
            _syncEvents.tryEmit(SyncEvent.ManifestUpdated(newFiles.size))

            // Get all unsynced items
            val toSync = inventory.getUnsyncedItems()
            _progress.value = SyncProgress(
                status = SyncStatus.SYNCING,
                totalFiles = toSync.size,
                completedFiles = 0,
                failedFiles = 0,
                currentFile = null,
                bytesDownloaded = 0L
            )

            for (item in toSync) {
                if (cancelled) {
                    _progress.value = _progress.value.copy(status = SyncStatus.CANCELLED)
                    _syncEvents.tryEmit(SyncEvent.Cancelled)
                    break
                }

                // Skip duplicates
                if (inventory.isDuplicate(item.filename)) {
                    completed++
                    continue
                }

                _progress.value = _progress.value.copy(currentFile = item.filename)
                _syncEvents.tryEmit(SyncEvent.DownloadStarted(item.filename))

                // Mark as downloading
                inventory.updateSyncState(item.filename, SyncState.DOWNLOADING)

                // Attempt download
                val result = downloader.download(item)

                result.onSuccess { downloadResult ->
                    if (downloadResult.valid) {
                        inventory.updateSyncState(
                            item.filename,
                            SyncState.SYNCED,
                            downloadResult.localPath
                        )
                        completed++
                        bytesTotal += downloadResult.sizeBytes
                        _syncEvents.tryEmit(SyncEvent.DownloadCompleted(item.filename, downloadResult.sizeBytes))
                    } else {
                        inventory.updateSyncState(item.filename, SyncState.FAILED)
                        failed++
                        _syncEvents.tryEmit(SyncEvent.DownloadFailed(item.filename, "Validation failed"))
                    }
                }.onFailure { error ->
                    inventory.updateSyncState(item.filename, SyncState.FAILED)
                    failed++
                    _syncEvents.tryEmit(
                        SyncEvent.DownloadFailed(item.filename, error.message ?: "Unknown error")
                    )
                }

                _progress.value = _progress.value.copy(
                    completedFiles = completed,
                    failedFiles = failed,
                    bytesDownloaded = bytesTotal
                )
            }

            val finalStatus = if (cancelled) SyncStatus.CANCELLED
            else if (failed > 0 && completed == 0) SyncStatus.ERROR
            else SyncStatus.COMPLETED

            _progress.value = _progress.value.copy(
                status = finalStatus,
                currentFile = null
            )
            _syncEvents.tryEmit(SyncEvent.SyncCompleted(completed, failed))

            return SyncSummary(
                totalFiles = toSync.size,
                completedFiles = completed,
                failedFiles = failed,
                bytesDownloaded = bytesTotal,
                cancelled = cancelled
            )
        } catch (e: CancellationException) {
            _progress.value = _progress.value.copy(status = SyncStatus.CANCELLED)
            throw e
        } catch (e: Exception) {
            _progress.value = _progress.value.copy(status = SyncStatus.ERROR)
            throw e
        }
    }

    /**
     * Cancels the current sync operation.
     */
    fun cancel() {
        cancelled = true
    }

    data class SyncSummary(
        val totalFiles: Int,
        val completedFiles: Int,
        val failedFiles: Int,
        val bytesDownloaded: Long,
        val cancelled: Boolean
    )

    sealed class SyncEvent {
        data class ManifestUpdated(val newFiles: Int) : SyncEvent()
        data class DownloadStarted(val filename: String) : SyncEvent()
        data class DownloadCompleted(val filename: String, val sizeBytes: Long) : SyncEvent()
        data class DownloadFailed(val filename: String, val reason: String) : SyncEvent()
        data class SyncCompleted(val completed: Int, val failed: Int) : SyncEvent()
        data object Cancelled : SyncEvent()
    }
}
