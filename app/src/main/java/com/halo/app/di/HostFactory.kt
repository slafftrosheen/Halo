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

    fun getHeyCyanDriver(context: Context, eventBus: com.halo.sdk.runtime.EventBus? = null): RealHeyCyanDriver {
        return heyCyanDriver ?: RealHeyCyanDriver(context, eventBus).also { heyCyanDriver = it }
    }

    fun create(
        type: HostType,
        context: Context,
        storage: com.halo.sdk.runtime.Storage,
        sessionManager: com.halo.sdk.runtime.embodiment.DeviceSessionManager? = null,
        eventBus: com.halo.sdk.runtime.EventBus? = null
    ): WearableHost {
        return when (type) {
            HostType.HEYCYAN -> {
                val driver = getHeyCyanDriver(context, eventBus)
                HeyCyanHost(
                    driver = driver,
                    audioOutput = HeyCyanAudioOutput(driver),
                    storage = storage,
                    sessionManager = sessionManager,
                    eventBus = eventBus
                )
            }
            HostType.ANDROID -> com.halo.host.android.AndroidHost()
            HostType.REPLAY, HostType.SIMULATOR -> SimulatedWearableHost()
        }
    }
}
