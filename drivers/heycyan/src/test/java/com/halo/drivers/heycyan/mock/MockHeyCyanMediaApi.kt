package com.halo.drivers.heycyan.mock

import com.halo.drivers.heycyan.media.HeyCyanMedia
import com.halo.drivers.heycyan.media.HeyCyanMediaManifest
import com.halo.drivers.heycyan.media.MediaDownloadModels
import com.halo.drivers.heycyan.media.MediaDownloadProgress
import com.halo.drivers.heycyan.media.MediaDownloadResult
import com.halo.drivers.heycyan.media.MediaIntegrity
import com.halo.drivers.heycyan.media.MediaLocation
import com.halo.drivers.heycyan.wifi.HeyCyanMediaApi

/**
 * In-process mock [HeyCyanMediaApi] for unit testing.
 *
 * Provides a static [manifest] and simulates streaming downloads without any
 * HTTP, Wi-Fi, or Android SDK dependency.
 *
 * Usage:
 * ```kotlin
 * val mock = MockHeyCyanMediaApi(
 *     manifest = HeyCyanMediaManifest(deviceId = null, generatedAt = null, items = listOf(photo))
 * )
 * mock.downloadBehaviour = MockHeyCyanMediaApi.DownloadBehaviour.Succeed
 * ```
 */
class MockHeyCyanMediaApi(
    var manifest: HeyCyanMediaManifest = EMPTY_MANIFEST,
    var manifestBehaviour: ManifestBehaviour = ManifestBehaviour.Succeed,
    var downloadBehaviour: DownloadBehaviour = DownloadBehaviour.Succeed,
) : HeyCyanMediaApi {

    /** All `getMediaConfig` call timestamps, for verification */
    var manifestFetchCount: Int = 0
        private set

    /** All download calls received, in order */
    val downloadedItems: MutableList<HeyCyanMedia> = mutableListOf()

    override suspend fun getMediaConfig(): Result<HeyCyanMediaManifest> {
        manifestFetchCount++
        return when (manifestBehaviour) {
            ManifestBehaviour.Succeed -> Result.success(manifest)
            is ManifestBehaviour.Fail ->
                Result.failure((manifestBehaviour as ManifestBehaviour.Fail).cause)
        }
    }

    override suspend fun download(
        media: HeyCyanMedia,
        destinationPath: String,
        progress: (MediaDownloadProgress) -> Unit,
    ): Result<MediaDownloadResult> {
        downloadedItems.add(media)

        return when (downloadBehaviour) {
            DownloadBehaviour.Succeed -> {
                val bytes = media.sizeBytes ?: 1024L
                progress(MediaDownloadProgress(bytesReceived = bytes, totalBytes = bytes))
                Result.success(
                    MediaDownloadResult(
                        media = media,
                        bytesWritten = bytes,
                        destination = MediaLocation(destinationPath),
                        integrity = MediaIntegrity(
                            expectedBytes = bytes,
                            actualBytes = bytes,
                            sha256 = "deadbeef",
                        ),
                    )
                )
            }
            is DownloadBehaviour.Fail ->
                Result.failure((downloadBehaviour as DownloadBehaviour.Fail).cause)
        }
    }

    fun reset() {
        manifestFetchCount = 0
        downloadedItems.clear()
        manifestBehaviour = ManifestBehaviour.Succeed
        downloadBehaviour = DownloadBehaviour.Succeed
        manifest = EMPTY_MANIFEST
    }

    sealed class ManifestBehaviour {
        data object Succeed : ManifestBehaviour()
        data class Fail(val cause: Throwable = RuntimeException("Mock manifest fetch failed")) : ManifestBehaviour()
    }

    sealed class DownloadBehaviour {
        data object Succeed : DownloadBehaviour()
        data class Fail(val cause: Throwable = RuntimeException("Mock download failed")) : DownloadBehaviour()
    }

    companion object {
        val EMPTY_MANIFEST = HeyCyanMediaManifest(
            deviceId = null,
            generatedAt = null,
            items = emptyList(),
        )

        /** Convenience: 3-item manifest with one of each media type */
        fun threeItemManifest(): HeyCyanMediaManifest = HeyCyanMediaManifest(
            deviceId = "mock-device",
            generatedAt = 1_000_000L,
            items = listOf(
                HeyCyanMedia.Photo(
                    id = "img001.jpg",
                    filename = "img001.jpg",
                    sizeBytes = 512_000L,
                    createdAt = null,
                    width = null,
                    height = null,
                ),
                HeyCyanMedia.Video(
                    id = "vid001.mp4",
                    filename = "vid001.mp4",
                    sizeBytes = 8_388_608L,
                    createdAt = null,
                    durationMs = null,
                ),
                HeyCyanMedia.Audio(
                    id = "aud001.opus",
                    filename = "aud001.opus",
                    sizeBytes = 204_800L,
                    createdAt = null,
                    durationMs = null,
                ),
            ),
        )
    }
}
