package com.halo.drivers.heycyan.protocol

class HeyCyanDeviceStateReducer {

    fun reduce(
        state: HeyCyanDeviceState,
        event: HeyCyanEvent,
        timestamp: Long,
    ): HeyCyanDeviceState {

        return when (event) {

            is HeyCyanEvent.BatteryUpdated ->
                state.copy(
                    battery = BatteryState(
                        level = event.level,
                        charging = event.isCharging,
                        timestampMs = timestamp,
                    ),
                    lastEventAtMillis = timestamp,
                )

            is HeyCyanEvent.VersionUpdated ->
                state.copy(
                    version = VersionInfo(
                        hardware = event.hardwareVersion,
                        firmware = event.firmwareVersion,
                        wifiHardware = event.wifiHardwareVersion,
                        wifiFirmware = event.wifiFirmwareVersion,
                    ),
                    lastEventAtMillis = timestamp,
                )

            is HeyCyanEvent.MediaCountsUpdated ->
                state.copy(
                    mediaCounts = MediaCounts(
                        photos = event.photoCount,
                        videos = event.videoCount,
                        audio = event.audioCount,
                        mediaType = event.mediaType,
                    ),
                    lastEventAtMillis = timestamp,
                )

            is HeyCyanEvent.ModeChanged -> {
                val mappedMode = HeyCyanMode.fromValue(event.mode)
                val videoState = when (mappedMode) {
                    HeyCyanMode.VIDEO -> VideoRecordingState.RECORDING
                    HeyCyanMode.PHOTO, HeyCyanMode.AUDIO -> VideoRecordingState.IDLE
                    else -> state.videoRecordingState
                }
                val audioState = when (mappedMode) {
                    HeyCyanMode.AUDIO -> AudioRecordingState.RECORDING
                    HeyCyanMode.PHOTO, HeyCyanMode.VIDEO -> AudioRecordingState.IDLE
                    else -> state.audioRecordingState
                }
                state.copy(
                    mode = mappedMode,
                    rawMode = event.mode,
                    videoRecordingState = videoState,
                    audioRecordingState = audioState,
                    lastEventAtMillis = timestamp,
                )
            }

            is HeyCyanEvent.WiFiHotspotReady ->
                state.copy(
                    wifiCredentials = WiFiCredentials(
                        ssid = event.ssid,
                        password = event.password,
                    ),
                    lastEventAtMillis = timestamp,
                )

            is HeyCyanEvent.WiFiIpReceived ->
                state.copy(
                    wifiIp = event.ip.takeIf(::isValidIpv4),
                    lastEventAtMillis = timestamp,
                )

            is HeyCyanEvent.MemoryInsufficient ->
                state.copy(
                    healthState = DeviceHealthState.WARNING_MEMORY_INSUFFICIENT,
                    lastEventAtMillis = timestamp,
                )

            is HeyCyanEvent.UnbindApp ->
                state.copy(
                    healthState = DeviceHealthState.UNBOUND,
                    connection = HeyCyanConnectionState.DISCONNECTED,
                    lastEventAtMillis = timestamp,
                )

            is HeyCyanEvent.VolumeChange -> {
                val vol = if (event.payload.size >= 3) {
                    VolumeState(
                        musicCurrent = event.payload.getOrNull(0)?.toInt()?.and(0xFF) ?: 0,
                        callCurrent = event.payload.getOrNull(1)?.toInt()?.and(0xFF) ?: 0,
                        systemCurrent = event.payload.getOrNull(2)?.toInt()?.and(0xFF) ?: 0
                    )
                } else state.volumeState
                state.copy(
                    volumeState = vol,
                    lastEventAtMillis = timestamp,
                )
            }

            is HeyCyanEvent.ButtonPressed ->
                state.copy(
                    hardwareInputState = HardwareInputState(
                        lastButton = event.type.name,
                        lastInputTimestampMs = timestamp
                    ),
                    lastEventAtMillis = timestamp,
                )

            is HeyCyanEvent.Ready,
            is HeyCyanEvent.AiImageData,
            is HeyCyanEvent.AiImageRecognition,
            is HeyCyanEvent.OtaProgress,
            is HeyCyanEvent.VoicePlaybackPause,
            is HeyCyanEvent.ObservedProprietaryFrame,
            is HeyCyanEvent.DecodedProprietaryFrame,
            is HeyCyanEvent.RawAudioFrame,
            is HeyCyanEvent.VoiceActivationStarted,
            is HeyCyanEvent.VoiceActivationEnded,
            is HeyCyanEvent.Unknown ->
                state.copy(
                    lastEventAtMillis = timestamp,
                )
        }
    }

    private fun isValidIpv4(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part ->
            part.toIntOrNull()?.let { octet -> octet in 0..255 } == true
        }
    }

}
