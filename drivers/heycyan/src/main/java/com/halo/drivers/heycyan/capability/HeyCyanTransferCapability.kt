package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.media.HeyCyanMediaManifest
import com.halo.drivers.heycyan.wifi.HeyCyanTransferCoordinator

/**
 * Capability wrapper integrating the Wi-Fi media transfer pipeline with the driver capability layer.
 *
 * Rather than simply issuing a fire-and-forget BLE `EnableTransferMode` command,
 * this capability orchestrates entering transfer mode, connecting to Wi-Fi,
 * discovering the IP address, and acquiring the media manifest.
 */
class HeyCyanTransferCapability(
    private val transferCoordinator: HeyCyanTransferCoordinator,
) {
    /**
     * Executes the full Wi-Fi media transfer synchronization sequence.
     *
     * @return [Result.success] with [HeyCyanMediaManifest] when Wi-Fi connects and manifest is retrieved.
     */
    suspend fun synchronizeMedia(deviceId: String): Result<HeyCyanMediaManifest> {
        return transferCoordinator.synchronizeManifest(deviceId)
    }
}
