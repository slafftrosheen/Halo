package com.halo.app.di

import android.content.Context
import com.halo.drivers.heycyan.HeyCyanAudioOutput
import com.halo.drivers.heycyan.HeyCyanHost
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.simulator.SimulatedWearableHost
import com.halo.sdk.host.WearableHost
import com.halo.sdk.runtime.Storage

/**
 * Creates host adapters from explicit dependencies.
 *
 * HostFactory is intentionally stateless. Device/driver lifetime belongs to
 * HostGraph, which is the composition root for the current host instance.
 */
class HostFactory(
    private val context: Context,
    private val storage: Storage,
    private val heyCyanDriver: RealHeyCyanDriver
) {
    fun create(type: HostType): WearableHost {
        return when (type) {
            HostType.HEYCYAN -> HeyCyanHost(
                driver = heyCyanDriver,
                audioOutput = HeyCyanAudioOutput(heyCyanDriver),
                storage = storage
            )
            HostType.ANDROID -> com.halo.host.android.AndroidHost()
            HostType.REPLAY, HostType.SIMULATOR -> SimulatedWearableHost()
        }
    }
}

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
