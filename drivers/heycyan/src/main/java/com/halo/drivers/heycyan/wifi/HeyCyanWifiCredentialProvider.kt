package com.halo.drivers.heycyan.wifi

import com.halo.drivers.heycyan.protocol.HeyCyanDeviceState
import kotlinx.coroutines.flow.StateFlow

/**
 * Provides Wi-Fi credentials for the transfer coordinator.
 *
 * Credentials are sourced from [HeyCyanDeviceState.wifiCredentials], which
 * is populated by [com.halo.drivers.heycyan.protocol.HeyCyanEvent.WiFiCredentials]
 * received over BLE.
 *
 * SECURITY RULES — enforced at every implementation:
 *   - Never persist the password
 *   - Never log credentials (SSID, password, or both together)
 *   - Never put credentials into Timeline, Replay, Metrics, RuntimeSnapshot
 *   - [HeyCyanWifiCredentials.toString] is redacted to enforce this
 */
interface HeyCyanWifiCredentialProvider {
    fun credentials(): HeyCyanWifiCredentials?
}

/**
 * Reads credentials from the live [HeyCyanDeviceState] state flow.
 *
 * This is a pure read — no persistence, no transformation beyond mapping
 * the existing [com.halo.drivers.heycyan.protocol.WiFiCredentials] model.
 */
class DeviceStateWifiCredentialProvider(
    private val state: StateFlow<HeyCyanDeviceState>,
) : HeyCyanWifiCredentialProvider {

    override fun credentials(): HeyCyanWifiCredentials? {
        return state.value.wifiCredentials?.let { stored ->
            HeyCyanWifiCredentials(
                ssid = stored.ssid,
                password = stored.password,
            )
        }
    }
}
