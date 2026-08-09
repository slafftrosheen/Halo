package com.halo.drivers.heycyan.protocol

/**
 * Commands sent to the HeyCyan glasses over BLE.
 * These map to the QCOperatorDeviceMode values and proprietary vendor commands.
 */
sealed interface HeyCyanCommand {
    
    // Media Capture
    data object TakePhoto : HeyCyanCommand
    data object StartVideo : HeyCyanCommand
    data object StopVideo : HeyCyanCommand
    data object StartAudio : HeyCyanCommand
    data object StopAudio : HeyCyanCommand
    
    // AI Features
    data object AIPhoto : HeyCyanCommand
    
    // State Queries
    data object RequestBattery : HeyCyanCommand
    data object RequestMediaCounts : HeyCyanCommand
    data object RequestDeviceInfo : HeyCyanCommand
    
    // Network / Transfer
    data object EnableWiFiTransfer : HeyCyanCommand
}
