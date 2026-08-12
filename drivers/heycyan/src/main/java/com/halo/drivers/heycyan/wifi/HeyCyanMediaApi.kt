package com.halo.drivers.heycyan.wifi

import com.halo.drivers.heycyan.media.HeyCyanMedia
import com.halo.drivers.heycyan.media.HeyCyanMediaManifest
import com.halo.drivers.heycyan.media.MediaDownloadProgress
import com.halo.drivers.heycyan.media.MediaDownloadResult
import java.net.InetAddress
import java.net.URI

/**
 * HTTP API abstraction for the HeyCyan glasses media server.
 *
 * Source-established endpoints:
 *
 *   Manifest:  GET http://<IP>/files/media.config
 *   Download:  GET http://<IP>/files/<filename>
 *
 * Evidence:
 *   - CyanBridge `AlbumDownloader.fetchConfig()` + `fetchOne()`
 *   - iOS `GlassesWiFiHandler.m` + `GlassesMediaDownloader.m`
 *
 * Do not add endpoints without a real-device capture or authoritative
 * vendor documentation.
 */
interface HeyCyanMediaApi {

    /**
     * Fetches and parses the media manifest from `GET /files/media.config`.
     *
     * @return [HeyCyanMediaManifest] on success.
     * @return Failure with [com.halo.drivers.heycyan.wifi.HeyCyanTransferFailure.HttpFailure]
     *         or [com.halo.drivers.heycyan.wifi.HeyCyanTransferFailure.ManifestParseFailure].
     */
    suspend fun getMediaConfig(): Result<HeyCyanMediaManifest>

    /**
     * Downloads a single media file from `GET /files/<filename>` to [destinationPath].
     *
     * The implementation MUST stream — it must not buffer the entire file in memory.
     * HTTP response → counting stream → SHA-256 stream → destination.
     *
     * @param progress Callback invoked as bytes arrive. May be called on any thread.
     */
    suspend fun download(
        media: HeyCyanMedia,
        destinationPath: String,
        progress: (MediaDownloadProgress) -> Unit = {},
    ): Result<MediaDownloadResult>

    /**
     * Downloads a single media file from `GET /files/<filename>` directly into a [MediaWriteSession].
     */
    suspend fun download(
        media: HeyCyanMedia,
        session: com.halo.drivers.heycyan.media.repository.MediaWriteSession,
        progress: (MediaDownloadProgress) -> Unit = {},
    ): Result<MediaDownloadResult> = download(media, session.identity, progress)
}

/**
 * Resolves the base URL for the HeyCyan HTTP media server.
 *
 * The device does not broadcast its own IP. Resolution probes a source-established
 * set of candidate IPs at the manifest endpoint and returns the first that responds.
 *
 * See [HeyCyanIpDiscovery.CANDIDATE_IPS] for the full probe list.
 */
interface HeyCyanBaseUrlProvider {
    suspend fun resolve(): Result<URI>
}

/**
 * Resolves the base URL from an active [HeyCyanWifiConnection].
 *
 * Delegates IP probe to [HeyCyanIpDiscovery] rather than using the gateway address
 * directly, since the glasses may not respond at the router gateway address.
 */
class HeyCyanWifiAddressResolver(
    private val connection: HeyCyanWifiConnection,
    private val ipDiscovery: HeyCyanIpDiscovery = HeyCyanIpDiscovery(),
) : HeyCyanBaseUrlProvider {

    override suspend fun resolve(): Result<URI> {
        // First, check if the connection provides a known address
        val knownAddress: InetAddress? = connection.currentAddress()
        if (knownAddress != null) {
            return Result.success(
                URI.create("http://${knownAddress.hostAddress}")
            )
        }

        // Otherwise probe candidate IPs
        val ip = ipDiscovery.discover()
            ?: return Result.failure(
                IllegalStateException(
                    "HeyCyan: no candidate IP responded at /files/media.config"
                )
            )

        return Result.success(URI.create("http://$ip"))
    }
}

/**
 * Factory for creating a [HeyCyanMediaApi] bound to a discovered device address.
 */
fun interface HeyCyanMediaApiFactory {
    fun create(deviceAddress: InetAddress): HeyCyanMediaApi
}
