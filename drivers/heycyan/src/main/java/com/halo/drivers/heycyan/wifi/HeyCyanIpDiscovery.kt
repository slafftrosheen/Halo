package com.halo.drivers.heycyan.wifi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * Discovers the HeyCyan glasses IP address by probing candidate IPs.
 *
 * The glasses start a WiFi hotspot with an HTTP server. Since the device
 * can appear on different subnet ranges, we probe a set of candidate IPs
 * concurrently and return the first one that responds.
 *
 * Evidence: CyanBridge `GlassesWiFiHandler.m` lines 172-176, `testIPs` method
 *
 * Candidate IPs from CyanBridge:
 * - 192.168.43.1   (Android hotspot default)
 * - 192.168.4.1    (Alternative hotspot range)
 * - 192.168.31.1   (Xiaomi default)
 * - 192.168.1.1    (Router default)
 * - 192.168.0.1    (Alternative subnet)
 * - 192.168.100.1  (Alternative range)
 * - 192.168.123.1  (Alternative range)
 * - 192.168.137.1  (Windows ICS)
 * - 10.0.0.1       (Private network range)
 * - 172.20.10.1    (iOS hotspot)
 *
 * Connection test: HTTP GET to /files/media.config with 3s timeout per IP.
 * Total discovery timeout: 10s.
 */
class HeyCyanIpDiscovery(
    private val perIpTimeoutMillis: Long = DEFAULT_PER_IP_TIMEOUT,
    private val totalTimeoutMillis: Long = DEFAULT_TOTAL_TIMEOUT,
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) {
    companion object {
        private const val DEFAULT_PER_IP_TIMEOUT = 3_000L
        private const val DEFAULT_TOTAL_TIMEOUT = 10_000L

        val CANDIDATE_IPS = listOf(
            "192.168.43.1",
            "192.168.4.1",
            "192.168.31.1",
            "192.168.1.1",
            "192.168.0.1",
            "192.168.100.1",
            "192.168.123.1",
            "192.168.137.1",
            "10.0.0.1",
            "172.20.10.1"
        )

        private const val MANIFEST_PATH = "/files/media.config"
    }

    /**
     * Discovers the device IP by probing the announced IP first, then candidate IPs.
     *
     * @param announcedIp The IP address announced via BLE (if any)
     * @return The first reachable IP, or null if no candidate responds
     */
    suspend fun discover(announcedIp: String? = null): String? {
        return withTimeoutOrNull(totalTimeoutMillis) {
            if (announcedIp != null) {
                if (testConnection(announcedIp)) {
                    return@withTimeoutOrNull announcedIp
                }
            }
            discoverConcurrently()
        }
    }

    private suspend fun discoverConcurrently(): String? = coroutineScope {
        val results = CANDIDATE_IPS.map { ip ->
            async(Dispatchers.IO) {
                if (testConnection(ip)) ip else null
            }
        }

        // Return the first successful result
        for (result in results) {
            val ip = result.await()
            if (ip != null) {
                // Cancel remaining probes
                results.forEach { it.cancel() }
                return@coroutineScope ip
            }
        }
        null
    }

    /**
     * Tests whether a specific IP hosts the HeyCyan HTTP server.
     *
     * Tests by requesting the manifest endpoint. A successful HTTP response
     * (any 2xx status) confirms the device is at this IP.
     */
    private suspend fun testConnection(ip: String): Boolean {
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(perIpTimeoutMillis) {
                try {
                    val url = URL("http://$ip$MANIFEST_PATH")
                    val connection = connectionFactory(url)
                    connection.connectTimeout = perIpTimeoutMillis.toInt()
                    connection.readTimeout = perIpTimeoutMillis.toInt()
                    connection.requestMethod = "GET"

                    try {
                        val responseCode = connection.responseCode
                        responseCode in 200..299
                    } finally {
                        connection.disconnect()
                    }
                } catch (e: Exception) {
                    false
                }
            } ?: false
        }
    }
}
