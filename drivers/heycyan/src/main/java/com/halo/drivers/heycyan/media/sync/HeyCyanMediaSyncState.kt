package com.halo.drivers.heycyan.media.sync

import com.halo.drivers.heycyan.media.HeyCyanMedia
import com.halo.drivers.heycyan.media.MediaDownloadProgress

/**
 * State machine for observing media synchronization lifecycle.
 */
sealed interface HeyCyanMediaSyncState {

    data object Idle : HeyCyanMediaSyncState

    data object FetchingManifest : HeyCyanMediaSyncState

    data class Planning(
        val remoteCount: Int,
    ) : HeyCyanMediaSyncState

    data class Downloading(
        val current: HeyCyanMedia,
        val index: Int,
        val total: Int,
        val progress: MediaDownloadProgress,
    ) : HeyCyanMediaSyncState

    data class Completed(
        val downloaded: Int,
        val skipped: Int,
        val failed: Int,
    ) : HeyCyanMediaSyncState

    data class Failed(
        val reason: HeyCyanMediaSyncFailure,
    ) : HeyCyanMediaSyncState

    data object Cancelled : HeyCyanMediaSyncState
}

/**
 * Structured failure classifications for media synchronization.
 */
sealed interface HeyCyanMediaSyncFailure {

    data class Manifest(
        val message: String,
        val cause: Throwable? = null,
    ) : HeyCyanMediaSyncFailure

    data class Download(
        val mediaId: String,
        val message: String,
        val cause: Throwable? = null,
    ) : HeyCyanMediaSyncFailure

    data class Storage(
        val mediaId: String,
        val message: String,
        val cause: Throwable? = null,
    ) : HeyCyanMediaSyncFailure

    data class Integrity(
        val mediaId: String,
        val expectedBytes: Long?,
        val actualBytes: Long,
    ) : HeyCyanMediaSyncFailure

    data object Cancelled : HeyCyanMediaSyncFailure
}

/**
 * Overall summary result of a completed media synchronization operation.
 */
data class HeyCyanMediaSyncResult(
    val downloaded: Int,
    val skipped: Int,
    val failed: Int,
)
