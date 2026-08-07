package com.halo.drivers.heycyan

import com.halo.sdk.driver.DeviceEvent
import com.halo.sdk.driver.Driver
import com.halo.sdk.host.AudioOutput

/** Converts runtime speech to the driver SDK's output event. */
class HeyCyanAudioOutput(private val driver: Driver) : AudioOutput {
    override suspend fun speak(text: String) {
        driver.emit(DeviceEvent.Output(System.currentTimeMillis(), text))
    }
}
