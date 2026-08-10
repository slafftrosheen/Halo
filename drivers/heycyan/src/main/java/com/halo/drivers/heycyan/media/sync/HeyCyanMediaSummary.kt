package com.halo.drivers.heycyan.media.sync

/**
 * UI-facing media count and synchronization status summary.
 */
data class HeyCyanMediaSummary(
    val total: Int,
    val photos: Int,
    val videos: Int,
    val audio: Int,
    val downloaded: Int,
    val pending: Int,
)

/**
 * Combined sync state and media summary for Glass Devices UI state binding.
 */
data class HeyCyanSyncSummary(
    val state: HeyCyanMediaSyncState,
    val summary: HeyCyanMediaSummary,
)
