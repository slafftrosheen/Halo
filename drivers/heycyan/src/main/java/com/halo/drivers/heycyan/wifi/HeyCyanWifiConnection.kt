package com.halo.drivers.heycyan.wifi

import kotlinx.coroutines.flow.StateFlow
import java.net.InetAddress

/**
 * Wi-Fi connection abstraction for HeyCyan transfer mode.
 *
 * This interface contains NO Android SDK references. The Android implementation
 * Implementations may use a credential-based hotspot or Wi-Fi Direct, but Android
 * network objects must not leak through this interface.
 *
 * Source evidence: CyanBridge `WifiP2pController.kt`, iOS `GlassesWiFiHandler.m`
 */
interface HeyCyanWifiConnection {

    /** Current connection state. Collect to observe transitions. */
    val state: StateFlow<WifiConnectionState>

    /**
     * Connects to the glasses Wi-Fi hotspot using the provided credentials.
     *
     * Established fallback: if the password is blank, the device responds to
     * `"123456789"` — this is sourced from CyanBridge `HeyCyanWifiConnector`.
     *
     * @return [Result.success] when the network is available.
     * @return [Result.failure] on timeout or connection rejection.
     */
    suspend fun connect(credentials: HeyCyanWifiCredentials): Result<Unit>

    /** Disconnects from the transfer network. Implementations must not bind the whole process. */
    suspend fun disconnect()

    /**
     * Returns the gateway/device IP address when [state] is [WifiConnectionState.CONNECTED],
     * or null otherwise.
     *
     * The IP is discovered concurrently across 10 source-established candidate IPs.
     * See [HeyCyanIpDiscovery.CANDIDATE_IPS].
     */
    suspend fun currentAddress(): InetAddress?
}

/**
 * Wi-Fi connection lifecycle states.
 */
enum class WifiConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    FAILED,
}

/**
 * Wi-Fi credentials received from the glasses via BLE [com.halo.drivers.heycyan.protocol.HeyCyanEvent.WiFiCredentials].
 *
 * SECURITY: Never persist, never log, never include in toString(), Timeline, Metrics,
 * or RuntimeSnapshot.
 */
data class HeyCyanWifiCredentials(
    val ssid: String,
    val password: String,
) {
    /**
     * Deliberately overrides toString() to prevent accidental credential logging.
     * The SSID is reported in hashed form; the password is always redacted.
     */
    override fun toString(): String =
        "HeyCyanWifiCredentials(ssid=<redacted>, password=<redacted>)"
}
