package com.halo.app.di

import android.content.Context
import com.halo.drivers.heycyan.HeyCyanAudioOutput
import com.halo.drivers.heycyan.HeyCyanHost
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.simulator.SimulatedWearableHost
import com.halo.sdk.host.WearableHost
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.embodiment.DeviceSessionManager

/**
 * Creates host adapters from explicit dependencies.
 */
class HostFactory(
    private val context: Context,
    private val storage: Storage,
    private val heyCyanDriver: RealHeyCyanDriver,
    private val sessionManager: DeviceSessionManager? = null,
    private val eventBus: EventBus? = null
) {
    fun create(type: HostType): WearableHost {
        return when (type) {
            HostType.HEYCYAN -> HeyCyanHost(
                driver = heyCyanDriver,
                audioOutput = HeyCyanAudioOutput(heyCyanDriver),
                storage = storage,
                sessionManager = sessionManager,
                eventBus = eventBus
            )
            HostType.ANDROID -> com.halo.host.android.AndroidHost()
            HostType.REPLAY, HostType.SIMULATOR -> SimulatedWearableHost()
        }
    }

    companion object {
        private var sharedDriver: RealHeyCyanDriver? = null

        fun getHeyCyanDriver(context: Context, eventBus: EventBus? = null): RealHeyCyanDriver {
            return sharedDriver ?: RealHeyCyanDriver(context, eventBus).also { sharedDriver = it }
        }

        fun create(
            type: HostType,
            context: Context,
            storage: Storage,
            sessionManager: DeviceSessionManager? = null,
            eventBus: EventBus? = null
        ): WearableHost {
            val driver = getHeyCyanDriver(context, eventBus)
            return HostFactory(context, storage, driver, sessionManager, eventBus).create(type)
        }
    }
}

enum class HostType {
    ANDROID,
    HEYCYAN,
    REPLAY,
    SIMULATOR
}
