package com.halo.app.audio

import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.protocol.HeyCyanConnectionState
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation

interface WearableAudioRouteAdapter {
    val deviceId: String?
    suspend fun prepareAudioTransport(): Result<Unit>
    suspend fun requestOutputRoute(): Result<Unit>
    suspend fun releaseOutputRoute(): Result<Unit>
}

class HeyCyanWearableAudioRouteAdapter(
    private val driver: RealHeyCyanDriver
) : WearableAudioRouteAdapter {
    override val deviceId: String?
        get() = driver.activeDeviceId

    override suspend fun prepareAudioTransport(): Result<Unit> {
        if (driver.activeDeviceId == null) return Result.failure(IllegalStateException("No active HeyCyan device"))
        if (driver.deviceState.value.connection != HeyCyanConnectionState.READY) {
            return Result.failure(IllegalStateException("HeyCyan device is disconnected"))
        }
        return driver.capabilityController.execute(HeyCyanOperation.EnableClassicBluetooth).map { Unit }
    }

    override suspend fun requestOutputRoute(): Result<Unit> =
        driver.capabilityController.execute(HeyCyanOperation.RouteAudioToGlasses).map { Unit }

    override suspend fun releaseOutputRoute(): Result<Unit> =
        driver.capabilityController.execute(HeyCyanOperation.RouteAudioToPhone).map { Unit }
}
