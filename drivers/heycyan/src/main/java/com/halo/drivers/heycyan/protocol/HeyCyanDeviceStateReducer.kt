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

            is HeyCyanEvent.ModeChanged ->
                state.copy(
                    mode = HeyCyanMode.fromValue(event.mode),
                    rawMode = event.mode,
                    lastEventAtMillis = timestamp,
                )

            is HeyCyanEvent.WiFiHotspotReady ->
                state.copy(
                    wifiCredentials = WiFiCredentials(
                        ssid = event.ssid,
                        password = event.password,
                    ),
                    lastEventAtMillis = timestamp,
                )

            is HeyCyanEvent.Ready,
            is HeyCyanEvent.ButtonPressed,
            is HeyCyanEvent.AiImageData,
            is HeyCyanEvent.ObservedProprietaryFrame,
            is HeyCyanEvent.DecodedProprietaryFrame,
            is HeyCyanEvent.Unknown ->
                state.copy(
                    lastEventAtMillis = timestamp,
                )
        }
    }
}
