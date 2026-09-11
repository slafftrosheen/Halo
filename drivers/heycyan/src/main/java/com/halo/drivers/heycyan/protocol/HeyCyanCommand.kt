package com.halo.drivers.heycyan.protocol

import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation

/**
 * Commands sent to the HeyCyan glasses over BLE.
 * These map to the QCOperatorDeviceMode values and proprietary vendor commands.
 */
sealed interface HeyCyanCommand {
    // State Queries
    data object GetBattery : HeyCyanCommand
    data object RequestBattery : HeyCyanCommand
    data object GetVersion : HeyCyanCommand
    /** Semantic alias for [GetVersion] — used by the PR-025 capability layer. */
    data object GetDeviceInfo : HeyCyanCommand
    data object GetMediaCounts : HeyCyanCommand
    data object GetMacAddress : HeyCyanCommand

    // Transient Actions (observed to be toggles on hardware)
    data object TakePhoto : HeyCyanCommand
    data object StartVideo : HeyCyanCommand
    data object StopVideo : HeyCyanCommand
    data object StartRecording : HeyCyanCommand
    data object StopRecording : HeyCyanCommand
    data object AiThumbnail : HeyCyanCommand
    data object PrepareVoiceRouting : HeyCyanCommand
    data object RouteAudioToGlasses : HeyCyanCommand

    // Network / Transfer
    data object EnterWifiTransferMode : HeyCyanCommand
    data class SetDateTime(val timestamp: Long = 0L) : HeyCyanCommand

    // Legacy/Fallback Mode Control (maintained for potential explicit state support)
    data class SetMode(val mode: HeyCyanMode) : HeyCyanCommand
}

/**
 * Maps a semantic HeyCyanOperation to the corresponding low-level HeyCyanCommand.
 */
fun HeyCyanOperation.toCommand(): HeyCyanCommand =
    when (this) {
        HeyCyanOperation.GetBattery -> HeyCyanCommand.GetBattery
        HeyCyanOperation.GetDeviceInfo -> HeyCyanCommand.GetDeviceInfo
        HeyCyanOperation.GetMediaInventory -> HeyCyanCommand.GetMediaCounts
        HeyCyanOperation.TakePhoto -> HeyCyanCommand.TakePhoto
        HeyCyanOperation.StartVideo -> HeyCyanCommand.StartVideo
        HeyCyanOperation.StopVideo -> HeyCyanCommand.StopVideo
        HeyCyanOperation.StartRecording -> HeyCyanCommand.StartRecording
        HeyCyanOperation.StopRecording -> HeyCyanCommand.StopRecording
        HeyCyanOperation.PrepareVoiceRouting -> HeyCyanCommand.PrepareVoiceRouting
        HeyCyanOperation.RouteAudioToGlasses -> HeyCyanCommand.RouteAudioToGlasses
        HeyCyanOperation.GenerateAIPhoto -> HeyCyanCommand.AiThumbnail
        HeyCyanOperation.EnterWifiTransferMode -> HeyCyanCommand.EnterWifiTransferMode
        HeyCyanOperation.SynchronizeMedia ->
            throw IllegalStateException(
                "SynchronizeMedia is not a BLE command. " +
                    "It must be handled by HeyCyanMediaSynchronizer."
            )
    }
