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
 *
 * This factory never creates or caches physical drivers. Driver lifetime is
 * owned by the composition root and injected here explicitly.
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
}

enum class HostType {
    ANDROID,
    HEYCYAN,
    REPLAY,
    SIMULATOR
}
