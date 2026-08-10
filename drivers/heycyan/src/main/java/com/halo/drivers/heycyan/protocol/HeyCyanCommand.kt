package com.halo.drivers.heycyan.protocol

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

    // Media Capture
    data object TakePhoto : HeyCyanCommand
    data object StartVideo : HeyCyanCommand
    data object StopVideo : HeyCyanCommand

    // Audio Capture
    data object StartAudio : HeyCyanCommand
    data object StopAudio : HeyCyanCommand

    // Network / Transfer
    data object EnableTransfer : HeyCyanCommand
    data class SetDateTime(val timestamp: Long = 0L) : HeyCyanCommand

    // Mode Control
    data class SetMode(val mode: HeyCyanMode) : HeyCyanCommand
}