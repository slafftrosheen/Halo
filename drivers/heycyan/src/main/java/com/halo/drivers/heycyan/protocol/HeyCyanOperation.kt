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

    /** Begin video recording. Mode 0x02. */
    data object StartVideo : HeyCyanOperation

    /** Stop video recording. Mode 0x03. */
    data object StopVideo : HeyCyanOperation

    /**
     * Begin audio recording on the glasses.
     * Audio is stored locally and retrieved via Wi-Fi transfer.
     * Live PCM streaming over BLE is not supported.
     * Mode 0x04.
     */
    data object StartAudioRecording : HeyCyanOperation

    /** Stop audio recording. Mode 0x05. */
    data object StopAudioRecording : HeyCyanOperation

    /**
     * Trigger AI photo capture. Mode 0x06.
     *
     * BLOCKED_BY_PROTOCOL: The trigger mode value is known, but the multi-packet
     * image reassembly protocol for delivery is not yet established.
     * This operation will always return ProtocolNotEstablished.
     */
    data object GenerateAIPhoto : HeyCyanOperation

    /**
     * Switch the glasses to Wi-Fi transfer mode. Mode 0x07.
     *
     * Once active, media retrieval proceeds over Wi-Fi HTTP (see PR-026).
     * This operation activates BLE transfer mode only; HTTP stack is separate.
     */
    data object EnableTransferMode : HeyCyanOperation

    /**
     * High-level media synchronization operation (PR-027).
     * Triggers transfer mode and full manifest -> diff -> download pipeline.
     */
    data object SynchronizeMedia : HeyCyanOperation
}

/**
 * Maps a semantic HeyCyanOperation to the corresponding low-level HeyCyanCommand.
 *
 * This mapping is source-backed from CyanBridge / HeyCyanSmartGlassesSDK.
 * Mode values: PHOTO=0x01, VIDEO=0x02, VIDEO_STOP=0x03, AUDIO=0x04,
 *              AUDIO_STOP=0x05, AI_PHOTO=0x06, TRANSFER=0x07.
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
            HeyCyanCommand.SetMode(HeyCyanMode.PHOTO)

        HeyCyanOperation.StartVideo ->
            HeyCyanCommand.SetMode(HeyCyanMode.VIDEO)

        HeyCyanOperation.StopVideo ->
            HeyCyanCommand.SetMode(HeyCyanMode.VIDEO_STOP)

        HeyCyanOperation.StartAudioRecording ->
            HeyCyanCommand.SetMode(HeyCyanMode.AUDIO)

        HeyCyanOperation.StopAudioRecording ->
            HeyCyanCommand.SetMode(HeyCyanMode.AUDIO_STOP)

        HeyCyanOperation.GenerateAIPhoto ->
            HeyCyanCommand.SetMode(HeyCyanMode.AI_PHOTO)

        HeyCyanOperation.EnableTransferMode,
        HeyCyanOperation.SynchronizeMedia ->
            HeyCyanCommand.SetMode(HeyCyanMode.TRANSFER)
    }
