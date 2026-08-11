package com.halo.app.di

import android.content.Context
import com.halo.sdk.host.WearableHost
import com.halo.drivers.heycyan.MockHeyCyanDriver
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.HeyCyanHost
import com.halo.drivers.heycyan.HeyCyanAudioOutput
import com.halo.drivers.simulator.SimulatedWearableHost


enum class HostType {
    ANDROID,
    HEYCYAN,
    REPLAY,
    SIMULATOR
}

object HostFactory {
    private var heyCyanDriver: RealHeyCyanDriver? = null

    fun getHeyCyanDriver(context: Context): RealHeyCyanDriver {
        return heyCyanDriver ?: RealHeyCyanDriver(context).also { heyCyanDriver = it }
    }

    fun create(type: HostType, context: Context): WearableHost {
        return when (type) {
            HostType.HEYCYAN -> {
                val driver = getHeyCyanDriver(context)
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
