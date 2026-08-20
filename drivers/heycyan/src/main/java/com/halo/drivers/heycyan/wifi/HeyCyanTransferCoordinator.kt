package com.halo.drivers.heycyan.wifi

import com.halo.drivers.heycyan.capability.HeyCyanCapabilityController
import com.halo.drivers.heycyan.media.HeyCyanMedia
import com.halo.drivers.heycyan.media.HeyCyanMediaManifest
import com.halo.drivers.heycyan.media.MediaDownloadResult
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

/**
 * Coordinates the full Wi-Fi media transfer pipeline for HeyCyan glasses.
 *
 * Sequence:
 * 1. Obtain Wi-Fi credentials from device state via [HeyCyanWifiCredentialProvider].
 * 2. Send BLE command to put glasses into transfer mode ([HeyCyanOperation.EnableTransferMode]).
 * 3. Connect to glasses Wi-Fi hotspot ([HeyCyanWifiConnection.connect]).
 * 4. Resolve device IP / create [HeyCyanMediaApi].
 * 5. Fetch media manifest ([HeyCyanMediaApi.getMediaConfig]).
 * 6. Allow downloading individual media files.
 */
class HeyCyanTransferCoordinator(
    private val commandController: HeyCyanCapabilityController,
    private val credentialsProvider: HeyCyanWifiCredentialProvider,
    private val wifiConnection: HeyCyanWifiConnection,
    private val mediaApiFactory: HeyCyanMediaApiFactory,
) {
    private val _state = MutableStateFlow<HeyCyanTransferState>(HeyCyanTransferState.Idle)
    val state: StateFlow<HeyCyanTransferState> = _state.asStateFlow()

    private var activeApi: HeyCyanMediaApi? = null

    /**
     * Connects to Wi-Fi, discovers the device endpoint, and fetches the media manifest.
     * Separated from file downloading so synchronization choices (e.g., download new only) can be handled upstream.
     */
    suspend fun synchronizeManifest(): Result<HeyCyanMediaManifest> {
        // 1. Obtain credentials
        val creds = credentialsProvider.credentials()
            ?: run {
                val failure = HeyCyanTransferFailure.CredentialsUnavailable
                _state.value = HeyCyanTransferState.Failed("Wi-Fi credentials unavailable", failure = failure)
                return Result.failure(IllegalStateException("HeyCyan Wi-Fi credentials unavailable"))
            }

        // 2. Put glasses into transfer mode via BLE
        _state.value = HeyCyanTransferState.EnteringTransferMode
        commandController.execute(HeyCyanOperation.EnterWifiTransferMode).getOrElse { err ->
            val failure = HeyCyanTransferFailure.TransferModeFailed
            _state.value = HeyCyanTransferState.Failed("Failed to enter transfer mode", cause = err, failure = failure)
            return Result.failure(err)
        }

        // 3. Connect Wi-Fi
        _state.value = HeyCyanTransferState.ConnectingWifi
        wifiConnection.connect(creds).getOrElse { err ->
            val failure = HeyCyanTransferFailure.WifiConnectionFailed(err)
            _state.value = HeyCyanTransferState.Failed("Wi-Fi connection failed", cause = err, failure = failure)
            return Result.failure(err)
        }

        // 4. Discover endpoint / create API
        _state.value = HeyCyanTransferState.DiscoveringDevice
        val address: InetAddress = wifiConnection.currentAddress()
            ?: run {
                val failure = HeyCyanTransferFailure.DeviceAddressUnavailable
                _state.value = HeyCyanTransferState.Failed("Device IP address unavailable", failure = failure)
                return Result.failure(IllegalStateException("HeyCyan Wi-Fi address unavailable"))
            }

        val api = mediaApiFactory.create(address)
        activeApi = api

        // 5. Fetch manifest
        _state.value = HeyCyanTransferState.FetchingManifest
        val manifestResult = api.getMediaConfig()
        return manifestResult.onSuccess { manifest ->
            _state.value = HeyCyanTransferState.Ready(manifest)
        }.onFailure { err ->
            val failure = HeyCyanTransferFailure.ManifestParseFailure(err.message ?: "Failed to fetch/parse manifest")
            _state.value = HeyCyanTransferState.Failed("Failed to fetch media manifest", cause = err, failure = failure)
        }
    }

    /**
     * Downloads a single media file using the active API session.
     */
    suspend fun download(
        media: HeyCyanMedia,
        destinationPath: String,
    ): Result<MediaDownloadResult> {
        val api = activeApi
            ?: return Result.failure(IllegalStateException("Transfer session not initialized. Call synchronizeManifest() first."))

        val result = api.download(media, destinationPath) { progress ->
            _state.value = HeyCyanTransferState.Downloading(media, progress)
        }

        return result.onFailure { err ->
            val failure = HeyCyanTransferFailure.DownloadFailure(media.id, err.message ?: "Download failed")
            _state.value = HeyCyanTransferState.Failed("Failed downloading ${media.filename}", cause = err, failure = failure)
        }
    }

    /**
     * Full convenience pipeline to synchronize manifest and return result.
     */
    suspend fun synchronize(): Result<HeyCyanMediaManifest> {
        return synchronizeManifest()
    }
}
