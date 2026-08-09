package com.halo.app.di

import android.content.Context
import com.halo.sdk.host.WearableHost
import com.halo.drivers.heycyan.MockHeyCyanDriver
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.HeyCyanHost
import com.halo.drivers.heycyan.HeyCyanAudioOutput
import com.halo.drivers.simulator.SimulatedWearableHost
import kotlinx.coroutines.runBlocking

enum class HostType {
    ANDROID,
    HEYCYAN,
    REPLAY,
    SIMULATOR
}

object HostFactory {
    fun create(type: HostType, context: Context): WearableHost {
        return when (type) {
            HostType.HEYCYAN -> {
                val driver = RealHeyCyanDriver(context)
                // Initialize the driver synchronously during DI setup for now
                runBlocking { driver.initialize() }
                HeyCyanHost(
                    driver = driver,
                    audioOutput = HeyCyanAudioOutput(driver)
                )
            }
            HostType.ANDROID -> com.halo.host.android.AndroidHost()
            HostType.REPLAY, HostType.SIMULATOR -> SimulatedWearableHost()
        }
    }
}
