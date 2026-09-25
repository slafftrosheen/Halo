package com.halo.drivers.heycyan.wifi

import com.halo.drivers.heycyan.arbiter.HeyCyanOperationLease
import com.halo.drivers.heycyan.arbiter.HeyCyanResource
import com.halo.drivers.heycyan.arbiter.HeyCyanResourceArbiter
import com.halo.drivers.heycyan.capability.HeyCyanCapabilityController
import com.halo.drivers.heycyan.media.HeyCyanMedia
import com.halo.drivers.heycyan.media.HeyCyanMediaManifest
import com.halo.drivers.heycyan.media.HeyCyanOpusMediaNormalizer
import com.halo.drivers.heycyan.media.MediaDownloadResult
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.File
import java.net.InetAddress

/**
 * Coordinates the full Wi-Fi media transfer pipeline for HeyCyan glasses.
 *
 * Sequence:
 * 1. Acquires WIFI_TRANSFER resource from [HeyCyanResourceArbiter].
 * 2. Obtain Wi-Fi credentials from device state via [HeyCyanWifiCredentialProvider].
 * 3. Send BLE command to put glasses into transfer mode ([HeyCyanOperation.EnterWifiTransferMode]).
 * 4. Connect to glasses Wi-Fi hotspot ([HeyCyanWifiConnection.connect]).
 * 5. Resolve device IP / create [HeyCyanMediaApi].
 * 6. Fetch media manifest ([HeyCyanMediaApi.getMediaConfig]).
 * 7. Allow downloading individual media files, normalizing .opus recordings.
 * 8. Releases hardware lease deterministically on session close or failure.
 */
class HeyCyanTransferCoordinator(
    private val commandController: HeyCyanCapabilityController,
    private val credentialsProvider: HeyCyanWifiCredentialProvider,
    private val wifiConnection: HeyCyanWifiConnection,
    private val mediaApiFactory: HeyCyanMediaApiFactory,
    private val resourceArbiter: HeyCyanResourceArbiter? = null,
) {
    private val _state = MutableStateFlow<HeyCyanTransferState>(HeyCyanTransferState.Idle)
    val state: StateFlow<HeyCyanTransferState> = _state.asStateFlow()

    private var activeApi: HeyCyanMediaApi? = null
    private var activeLease: HeyCyanOperationLease? = null

    /**
     * Connects to Wi-Fi, discovers the device endpoint, and fetches the media manifest.
     * Separated from file downloading so synchronization choices (e.g., download new only) can be handled upstream.
     */
    suspend fun synchronizeManifest(deviceId: String): Result<HeyCyanMediaManifest> {
        // 0. Acquire hardware lease if arbiter present
        val lease = resourceArbiter?.tryAcquire(
            operation = "wifiTransfer",
            resources = setOf(HeyCyanResource.WIFI_TRANSFER),
        )
        if (resourceArbiter != null && lease == null) {
            val failure = HeyCyanTransferFailure.TransferModeFailed
            _state.value = HeyCyanTransferState.Failed("HeyCyan glasses are busy with a conflicting operation", failure = failure)
            return Result.failure(IllegalStateException("Glasses are busy with a conflicting operation"))
        }
        activeLease = lease

        // 1. Put glasses into transfer mode via BLE FIRST
        _state.value = HeyCyanTransferState.EnteringTransferMode
        commandController.execute(HeyCyanOperation.EnterWifiTransferMode).getOrElse { err ->
            val failure = HeyCyanTransferFailure.TransferModeFailed
            _state.value = HeyCyanTransferState.Failed("Failed to enter transfer mode", cause = err, failure = failure)
            activeLease?.release()
            activeLease = null
            return Result.failure(err)
        }

        // 2. Obtain credentials (await credentials from device state observation)
        _state.value = HeyCyanTransferState.WaitingForTransferSignal
        val creds = credentialsProvider.await(deviceId, timeoutMs = 30_000L)
            ?: run {
                _state.value = HeyCyanTransferState.WaitingForCredentials
                activeLease?.release()
                activeLease = null
                return Result.failure(IllegalStateException("Awaiting Wi-Fi transfer credentials"))
            }

        // 3. Connect Wi-Fi
        var connectionError: Throwable? = null
        var connected = false
        repeat(MAX_STARTUP_ATTEMPTS) { attempt ->
            if (connected) return@repeat
            _state.value = HeyCyanTransferState.ConnectingP2p
            val attemptResult = wifiConnection.connect(creds)
            if (attemptResult.isSuccess) {
                connected = true
            } else {
                connectionError = attemptResult.exceptionOrNull()
                if (attempt + 1 < MAX_STARTUP_ATTEMPTS) delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        if (!connected) {
            val err = connectionError ?: IllegalStateException("Wi-Fi connection failed")
            val failure = HeyCyanTransferFailure.WifiConnectionFailed(err)
            return failAndRelease("Wi-Fi connection failed", err, failure)
        }
        _state.value = HeyCyanTransferState.NetworkAvailable

        // 4. Discover endpoint / create API
        _state.value = HeyCyanTransferState.WaitingForDeviceIp
        var address: InetAddress? = null
        repeat(MAX_IP_ATTEMPTS) { attempt ->
            if (address == null) address = wifiConnection.currentAddress()
            if (address == null && attempt + 1 < MAX_IP_ATTEMPTS) delay(RETRY_DELAY_MS)
        }
        val resolvedAddress = address ?: run {
            val error = IllegalStateException("HeyCyan Wi-Fi address unavailable")
            return failAndRelease("Device IP address unavailable", error, HeyCyanTransferFailure.DeviceAddressUnavailable)
        }

        val api = mediaApiFactory.create(resolvedAddress)
        activeApi = api

        // 5. Fetch manifest
        _state.value = HeyCyanTransferState.ProbingDeviceHttp
        var manifestResult: Result<HeyCyanMediaManifest> = Result.failure(IllegalStateException("Manifest was not probed"))
        repeat(MAX_HTTP_PROBE_ATTEMPTS) { attempt ->
            if (manifestResult.isSuccess) return@repeat
            _state.value = if (attempt == 0) HeyCyanTransferState.ProbingDeviceHttp else HeyCyanTransferState.FetchingManifest
            manifestResult = api.getMediaConfig()
            if (manifestResult.isFailure && attempt + 1 < MAX_HTTP_PROBE_ATTEMPTS) {
                delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        manifestResult.getOrNull()?.let { manifest ->
            _state.value = HeyCyanTransferState.Ready(manifest)
            return Result.success(manifest)
        }
        val manifestError = manifestResult.exceptionOrNull()
            ?: IllegalStateException("Failed to fetch media manifest")
        return failAndRelease(
            message = "Failed to fetch media manifest",
            error = manifestError,
            failure = HeyCyanTransferFailure.ManifestParseFailure(
                manifestError.message ?: "Failed to fetch/parse manifest",
            ),
        )
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

        return result.onSuccess {
            // Normalize Opus recording if applicable
            if (media.filename.endsWith(".opus", ignoreCase = true) || destinationPath.endsWith(".opus", ignoreCase = true)) {
                runCatching {
                    val file = File(destinationPath)
                    if (file.exists() && file.isFile) {
                        val raw = file.readBytes()
                        val normalized = HeyCyanOpusMediaNormalizer.normalize(raw)
                        if (!normalized.contentEquals(raw)) {
                            file.writeBytes(normalized)
                        }
                    }
                }
            }
        }.onFailure { err ->
            val failure = HeyCyanTransferFailure.DownloadFailure(media.id, err.message ?: "Download failed")
            _state.value = HeyCyanTransferState.Failed("Failed downloading ${media.filename}", cause = err, failure = failure)
        }
    }

    /**
     * Terminates the Wi-Fi media transfer session, commanding the glasses
     * to exit transfer mode and releasing all hardware resource locks.
     */
    suspend fun closeSession(): Result<Unit> {
        _state.value = HeyCyanTransferState.Restoring
        val result = runCatching {
            commandController.execute(HeyCyanOperation.ExitWifiTransferMode).getOrThrow()
        }
        activeLease?.release()
        activeLease = null
        activeApi = null
        _state.value = if (result.isSuccess) HeyCyanTransferState.Idle else {
            HeyCyanTransferState.Failed("Failed to restore HeyCyan after transfer", result.exceptionOrNull())
        }
        return result
    }

    /**
     * Full convenience pipeline to synchronize manifest and return result.
     */
    suspend fun synchronize(deviceId: String): Result<HeyCyanMediaManifest> {
        return synchronizeManifest(deviceId)
    }

    private suspend fun <T> failAndRelease(
        message: String,
        error: Throwable,
        failure: HeyCyanTransferFailure,
    ): Result<T> {
        _state.value = HeyCyanTransferState.Failed(message, error, failure)
        activeApi = null
        runCatching { commandController.execute(HeyCyanOperation.ExitWifiTransferMode) }
        activeLease?.release()
        activeLease = null
        runCatching { wifiConnection.disconnect() }
        return Result.failure(error)
    }

    private companion object {
        const val MAX_STARTUP_ATTEMPTS = 2
        const val MAX_IP_ATTEMPTS = 5
        const val MAX_HTTP_PROBE_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 500L
    }
}
