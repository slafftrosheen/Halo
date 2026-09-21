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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull

interface HeyCyanWifiCredentialProvider {
    fun credentials(): HeyCyanWifiCredentials?
    suspend fun await(deviceId: String, timeoutMs: Long = 30_000L): HeyCyanWifiCredentials?
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

    override suspend fun await(deviceId: String, timeoutMs: Long): HeyCyanWifiCredentials? {
        val current = credentials()
        if (current != null) return current
        return withTimeoutOrNull(timeoutMs) {
            state.mapNotNull { it.wifiCredentials }.first()
        }?.let { stored ->
            HeyCyanWifiCredentials(
                ssid = stored.ssid,
                password = stored.password,
            )
        }
    }
}
