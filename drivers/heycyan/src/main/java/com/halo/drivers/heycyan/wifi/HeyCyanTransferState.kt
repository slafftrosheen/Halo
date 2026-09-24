package com.halo.drivers.heycyan.wifi

import com.halo.drivers.heycyan.media.HeyCyanMedia
import com.halo.drivers.heycyan.media.HeyCyanMediaManifest
import com.halo.drivers.heycyan.media.MediaDownloadProgress

/**
 * State machine for observing Wi-Fi media transfer progress.
 *
 * Exposed as [StateFlow] in [HeyCyanTransferCoordinator] to drive UI updates and runtime tracking.
 */
sealed interface HeyCyanTransferState {

    data object Idle : HeyCyanTransferState

    data object EnteringTransferMode : HeyCyanTransferState

    data object WaitingForTransferSignal : HeyCyanTransferState

    data object WaitingForCredentials : HeyCyanTransferState

    data object WaitingForDeviceIp : HeyCyanTransferState

    data class Paused(val reason: String) : HeyCyanTransferState

    data object ConnectingWifi : HeyCyanTransferState

    data object DiscoveringP2p : HeyCyanTransferState

    data object ConnectingP2p : HeyCyanTransferState

    data object WaitingForAndroidNetwork : HeyCyanTransferState

    data object NetworkAvailable : HeyCyanTransferState

    data object DiscoveringDevice : HeyCyanTransferState

    data object ProbingDeviceHttp : HeyCyanTransferState

    data object FetchingManifest : HeyCyanTransferState

    data class Ready(
        val manifest: HeyCyanMediaManifest,
    ) : HeyCyanTransferState

    data class Downloading(
        val media: HeyCyanMedia,
        val progress: MediaDownloadProgress,
    ) : HeyCyanTransferState

    data object Restoring : HeyCyanTransferState

    data class Completed(
        val downloaded: Int,
    ) : HeyCyanTransferState

    data class Failed(
        val message: String,
        val cause: Throwable? = null,
        val failure: HeyCyanTransferFailure? = null,
    ) : HeyCyanTransferState
}
