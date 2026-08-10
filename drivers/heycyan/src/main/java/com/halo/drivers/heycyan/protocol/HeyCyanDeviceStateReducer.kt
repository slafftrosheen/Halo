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
                    ),
                    lastEventAtMillis = timestamp,
                )

            is HeyCyanEvent.VersionInfo ->
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

            is HeyCyanEvent.ModeChanged ->
                state.copy(
                    mode = event.mode,
                    rawMode = event.rawMode,
                    lastEventAtMillis = timestamp,
                )

            is HeyCyanEvent.WiFiCredentials ->
                state.copy(
                    wifiCredentials = WiFiCredentials(
                        ssid = event.ssid,
                        password = event.password,
                    ),
                    lastEventAtMillis = timestamp,
                )

            is HeyCyanEvent.Ready,
            is HeyCyanEvent.ButtonPressed,
            is HeyCyanEvent.AIImageData,
            is HeyCyanEvent.Malformed,
            is HeyCyanEvent.Unknown,
            is HeyCyanEvent.UnknownEvent ->
                state.copy(
                    lastEventAtMillis = timestamp,
                )
        }
    }
}
