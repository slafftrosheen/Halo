package com.halo.drivers.heycyan.protocol

/**
 * Semantic device operations for HeyCyan glasses.
 *
 * This is the pure capability API layer. It does NOT reference ByteArray, UUID, or
 * BluetoothGatt. Transport concerns are fully hidden behind HeyCyanCommandExecutor.
 *
 * Operations map to HeyCyanCommand via HeyCyanOperation.toCommand().
 */
sealed interface HeyCyanOperation {

    /** Query the current battery level and charging state. */
    data object GetBattery : HeyCyanOperation

    /** Query hardware and firmware version information. */
    data object GetDeviceInfo : HeyCyanOperation

    /** Query photo, video, and audio file counts stored on the glasses. */
    data object GetMediaInventory : HeyCyanOperation

    /** Trigger a still photo capture. Mode 0x01. */
    data object TakePhoto : HeyCyanOperation

    /** Execute video-related action (observed toggle). Action 0x02. */
    data object ToggleVideo : HeyCyanOperation

    /**
     * Prepare voice routing (Bluetooth SCO activation).
     * Action 0x0B (previously incorrectly labelled ToggleAudio).
     */
    data object PrepareVoiceRouting : HeyCyanOperation

    /**
     * Trigger AI photo capture. Mode 0x06.
     *
     * BLOCKED_BY_PROTOCOL: The trigger mode value is known, but the multi-packet
     * image reassembly protocol for delivery is not yet established.
     * This operation will always return ProtocolNotEstablished.
     */
    data object GenerateAIPhoto : HeyCyanOperation

    /**
     * Switch the glasses to Wi-Fi transfer mode. Trigger 02 01 04 01.
     *
     * Once active, media retrieval proceeds over Wi-Fi HTTP (see PR-026).
     * This operation activates BLE transfer mode only; HTTP stack is separate.
     */
    data object EnterWifiTransferMode : HeyCyanOperation

    /**
     * High-level media synchronization operation (PR-027).
     * Triggers transfer mode and full manifest -> diff -> download pipeline.
     */
    data object SynchronizeMedia : HeyCyanOperation
}

/**
 * Maps a semantic HeyCyanOperation to the corresponding low-level HeyCyanCommand.
 *
 * This mapping is source-backed from live device observations.
 */
fun HeyCyanOperation.toCommand(): HeyCyanCommand =
    when (this) {
        HeyCyanOperation.GetBattery ->
            HeyCyanCommand.GetBattery

        HeyCyanOperation.GetDeviceInfo ->
            HeyCyanCommand.GetDeviceInfo

        HeyCyanOperation.GetMediaInventory ->
            HeyCyanCommand.GetMediaCounts

        HeyCyanOperation.TakePhoto ->
            HeyCyanCommand.TakePhoto

        HeyCyanOperation.ToggleVideo ->
            HeyCyanCommand.ToggleVideo

        HeyCyanOperation.PrepareVoiceRouting ->
            HeyCyanCommand.PrepareVoiceRouting

        HeyCyanOperation.GenerateAIPhoto ->
            HeyCyanCommand.SetMode(HeyCyanMode.AI_PHOTO)

        HeyCyanOperation.EnterWifiTransferMode,
        HeyCyanOperation.SynchronizeMedia ->
            HeyCyanCommand.EnterWifiTransferMode
    }
