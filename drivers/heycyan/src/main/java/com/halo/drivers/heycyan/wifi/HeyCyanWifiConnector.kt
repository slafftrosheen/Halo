package com.halo.drivers.heycyan.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Connects the Android device to the HeyCyan glasses WiFi hotspot.
 *
 * Uses WifiNetworkSpecifier (API 29+) for targeted WiFi connection.
 * For API 26-28, falls back to WifiManager.addNetwork() (deprecated but functional).
 *
 * CyanBridge evidence:
 * - `WifiP2pController.kt` uses WifiP2pManager for WiFi Direct
 * - `GlassesWiFiHandler.m` uses NEHotspotConfiguration (iOS)
 * - Default password forced to "123456789" (glasses may return wrong password)
 *
 * The glasses expose a standard WiFi hotspot (not WiFi Direct P2P in most cases).
 */
class HeyCyanWifiConnector(
    private val context: Context,
    private val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT
) {
    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT = 15_000L
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var activeNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Connects to the glasses WiFi hotspot.
     *
     * @param ssid The SSID from the device's WiFi credentials response
     * @param password The password (forced to "123456789" if blank)
     * @return true if connection was successful
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(ssid: String, password: String): Boolean {
        val effectivePassword = password.ifBlank { "123456789" }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWithSpecifier(ssid, effectivePassword)
        } else {
            connectLegacy(ssid, effectivePassword)
        }
    }

    /**
     * Connects using WifiNetworkSpecifier (API 29+).
     *
     * This is the recommended approach on modern Android. The system
     * will prompt the user to approve the connection.
     */
    private suspend fun connectWithSpecifier(ssid: String, password: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

        return try {
            withTimeout(connectTimeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    val specifier = WifiNetworkSpecifier.Builder()
                        .setSsid(ssid)
                        .setWpa2Passphrase(password)
                        .build()

                    val request = NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .setNetworkSpecifier(specifier)
                        .build()

                    val callback = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            activeNetwork = network
                            if (continuation.isActive) {
                                continuation.resume(true)
                            }
                        }

                        override fun onUnavailable() {
                            if (continuation.isActive) {
                                continuation.resume(false)
                            }
                        }

                        override fun onLost(network: Network) {
                            if (activeNetwork == network) {
                                activeNetwork = null
                            }
                        }
                    }

                    networkCallback = callback
                    connectivityManager.requestNetwork(request, callback)

                    continuation.invokeOnCancellation {
                        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                        networkCallback = null
                    }
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Legacy WiFi connection for API 26-28.
     *
     * Uses deprecated WifiManager APIs. Returns false on newer APIs
     * where these methods are no longer functional.
     */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun connectLegacy(ssid: String, password: String): Boolean {
        // Legacy implementation using WifiManager.addNetwork()
        // This is deprecated in API 29+ but works on 26-28
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            ?: return false

        if (!wifiManager.isWifiEnabled) {
            return false
        }

        val config = android.net.wifi.WifiConfiguration().apply {
            SSID = "\"$ssid\""
            preSharedKey = "\"$password\""
            allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK)
        }

        val netId = wifiManager.addNetwork(config)
        if (netId == -1) return false

        wifiManager.disconnect()
        val enabled = wifiManager.enableNetwork(netId, true)
        if (!enabled) return false

        return wifiManager.reconnect()
    }

    /**
     * Opens an HTTP connection on the glasses network without changing process-wide routing.
     *
     * On API 29+ the Network returned for the WifiNetworkSpecifier owns the socket. This
     * prevents unrelated Halo traffic (Ollama/OpenRouter/plugins) from being routed toward
     * the glasses while media transfer is active.
     */
    fun openHttpConnection(url: URL): HttpURLConnection {
        val connection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val network = activeNetwork
                ?: throw IllegalStateException("HeyCyan Wi-Fi network is not available")
            network.openConnection(url)
        } else {
            url.openConnection()
        }
        return connection as HttpURLConnection
    }

    /**
     * Disconnects from the device WiFi hotspot.
     */
    fun disconnect() {
        networkCallback?.let { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        activeNetwork = null
    }

    /**
     * Returns the currently connected network, if any.
     */
    fun getActiveNetwork(): Network? = activeNetwork
}
