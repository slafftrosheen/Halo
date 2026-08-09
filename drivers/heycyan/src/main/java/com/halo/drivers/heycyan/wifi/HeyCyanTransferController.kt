package com.halo.drivers.heycyan.wifi

import com.halo.drivers.heycyan.HeyCyanCommandDispatcher
import com.halo.drivers.heycyan.WiFiCredentials
import com.halo.drivers.heycyan.media.MediaItem
import com.halo.drivers.heycyan.media.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Orchestrates the complete WiFi media transfer pipeline for HeyCyan glasses.
 *
 * Pipeline:
 * ```
 * enableTransferMode() → WiFiCredentials → connectWiFi() → discoverDeviceIP()
 *   → fetchMediaManifest() → listMedia() → download()
 * ```
 *
 * Evidence: SDK `openWifiWithMode(Transfer)`, CyanBridge `GlassesWiFiHandler`,
 * `AlbumDownloader`, `WifiP2pController`
 */
class HeyCyanTransferController(
    private val dispatcher: HeyCyanCommandDispatcher,
    private val wifiConnector: HeyCyanWifiConnector,
    private val ipDiscovery: HeyCyanIpDiscovery,
    private val manifestClient: HeyCyanMediaManifestClient,
    private val mediaDownloader: HeyCyanMediaDownloader
) {

    enum class TransferState {
        IDLE,
        ENABLING_TRANSFER_MODE,
        CONNECTING_WIFI,
        DISCOVERING_IP,
        FETCHING_MANIFEST,
        READY,
        TRANSFERRING,
        ERROR,
        COMPLETED
    }

    data class TransferSession(
        val state: TransferState,
        val credentials: WiFiCredentials? = null,
        val deviceIp: String? = null,
        val manifest: List<MediaItem>? = null,
        val error: String? = null
    )

    private val _session = MutableStateFlow(TransferSession(state = TransferState.IDLE))
    val session: StateFlow<TransferSession> = _session.asStateFlow()

    /**
     * Establishes a complete transfer session with the device.
     *
     * Steps:
     * 1. Send transfer mode command via BLE → receive SSID/password
     * 2. Connect to device WiFi hotspot
     * 3. Discover device IP via candidate probing
     * 4. Fetch media manifest
     *
     * @return The established transfer session ready for downloads
     */
    suspend fun establishSession(): Result<TransferSession> = runCatching {
        // Step 1: Enable transfer mode via BLE
        _session.value = TransferSession(state = TransferState.ENABLING_TRANSFER_MODE)
        val credentials = dispatcher.sendTransferMode().getOrThrow()
        val wifiCreds = WiFiCredentials(
            ssid = credentials.ssid,
            // CyanBridge finding: glasses may return wrong password, force "123456789"
            password = credentials.password.ifBlank { "123456789" }
        )
        _session.value = _session.value.copy(
            state = TransferState.CONNECTING_WIFI,
            credentials = wifiCreds
        )

        // Step 2: Connect to WiFi hotspot
        val wifiConnected = wifiConnector.connect(wifiCreds.ssid, wifiCreds.password)
        if (!wifiConnected) {
            val errorSession = _session.value.copy(
                state = TransferState.ERROR,
                error = "Failed to connect to WiFi hotspot: ${wifiCreds.ssid}"
            )
            _session.value = errorSession
            throw IllegalStateException(errorSession.error!!)
        }

        // Step 3: Discover device IP
        _session.value = _session.value.copy(state = TransferState.DISCOVERING_IP)
        val deviceIp = ipDiscovery.discover()
        if (deviceIp == null) {
            val errorSession = _session.value.copy(
                state = TransferState.ERROR,
                error = "Failed to discover device IP from candidate list"
            )
            _session.value = errorSession
            throw IllegalStateException(errorSession.error!!)
        }
        _session.value = _session.value.copy(
            state = TransferState.FETCHING_MANIFEST,
            deviceIp = deviceIp
        )

        // Step 4: Fetch media manifest
        val manifest = manifestClient.fetchManifest(deviceIp)
        val mediaItems = manifest.map { filename ->
            MediaItem(
                filename = filename,
                type = MediaType.fromExtension(filename)
            )
        }
        val readySession = _session.value.copy(
            state = TransferState.READY,
            manifest = mediaItems
        )
        _session.value = readySession
        readySession
    }

    /**
     * Downloads a specific media file from the connected device.
     *
     * @param item The media item to download
     * @param destinationDir Local directory to save the file
     * @return Local file path of the downloaded file
     */
    suspend fun downloadFile(
        item: MediaItem,
        destinationDir: String
    ): Result<String> = runCatching {
        val ip = _session.value.deviceIp
            ?: throw IllegalStateException("No active transfer session")
        check(_session.value.state == TransferState.READY || _session.value.state == TransferState.TRANSFERRING) {
            "Transfer session is not ready for downloads"
        }

        _session.value = _session.value.copy(state = TransferState.TRANSFERRING)
        val localPath = mediaDownloader.download(ip, item.filename, destinationDir)
        localPath
    }

    /**
     * Returns the list of media files available for download.
     */
    fun getAvailableMedia(): List<MediaItem> {
        return _session.value.manifest ?: emptyList()
    }

    /**
     * Disconnects the WiFi transfer session.
     */
    suspend fun disconnect() {
        wifiConnector.disconnect()
        _session.value = TransferSession(state = TransferState.IDLE)
    }

    /**
     * Returns the list of filenames from the manifest (for sync engine).
     */
    fun getManifestFilenames(): List<String> {
        return _session.value.manifest?.map { it.filename } ?: emptyList()
    }
}
