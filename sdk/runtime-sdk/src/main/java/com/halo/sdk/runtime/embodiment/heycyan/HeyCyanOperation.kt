package com.halo.sdk.runtime.embodiment.heycyan

/**
 * Semantic device operations for HeyCyan glasses.
 */
sealed interface HeyCyanOperation {
    data object GetBattery : HeyCyanOperation
    data object GetDeviceInfo : HeyCyanOperation
    data object GetMediaInventory : HeyCyanOperation
    data object TakePhoto : HeyCyanOperation
    data object StartVideo : HeyCyanOperation
    data object StopVideo : HeyCyanOperation
    data object StartRecording : HeyCyanOperation
    data object StopRecording : HeyCyanOperation
    data object PrepareVoiceRouting : HeyCyanOperation
    data object GenerateAIPhoto : HeyCyanOperation
    data object EnterWifiTransferMode : HeyCyanOperation
    data object SynchronizeMedia : HeyCyanOperation
}
