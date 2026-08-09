package com.halo.drivers.heycyan.protocol

import java.io.File

/**
 * Represents the JSON manifest of media files available on the glasses.
 */
data class MediaManifest(
    val files: List<String>
)

/**
 * Abstraction for the WiFi Direct / HTTP transfer protocol.
 * Once BLE triggers `EnableWiFiTransfer`, this handles the heavy lifting.
 */
interface HeyCyanWiFiTransfer {
    
    /**
     * Connects to the device's WiFi Hotspot.
     * @return The IP address of the glasses on the new subnet (e.g. 192.168.43.1).
     */
    suspend fun connectToHotspot(ssid: String, pass: String): Result<String>
    
    /**
     * Fetches the `manifest.json` from the HTTP server on the glasses.
     */
    suspend fun fetchManifest(ip: String): Result<MediaManifest>
    
    /**
     * Downloads a specific media file (jpg, mp4, opus).
     */
    suspend fun downloadMedia(ip: String, remotePath: String, destination: File): Result<File>
}
