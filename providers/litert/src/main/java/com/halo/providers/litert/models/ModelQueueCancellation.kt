package com.halo.providers.litert.models

/** A cancelled unregistered intent has no WorkInfo callback to clear its earlier Queued state. */
internal fun cancelledQueuePresentation(current: DownloadState?, registeredWork: Boolean, newerIntent: Boolean,
    partialBytes: Long, totalBytes: Long?): DownloadState? {
    if (registeredWork || newerIntent) return null
    if (current !is DownloadState.Queued && current !is DownloadState.Preparing && current !is DownloadState.Downloading) return null
    return DownloadState.Paused(partialBytes, totalBytes)
}
