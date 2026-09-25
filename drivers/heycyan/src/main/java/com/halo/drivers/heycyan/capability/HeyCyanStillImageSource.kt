package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.thumbnail.HeyCyanThumbnailCapture
import com.halo.sdk.driver.CapturedAsset
import com.halo.sdk.driver.StillCaptureRequest
import com.halo.sdk.driver.StillImageSource

class HeyCyanStillImageSource(
    private val capture: HeyCyanThumbnailCapture,
    private val originDeviceId: String? = null
) : StillImageSource {

    override suspend fun captureStill(request: StillCaptureRequest): Result<CapturedAsset> = runCatching {
        val quality = request.quality ?: 3
        val thumbnail = capture.capture(quality).getOrThrow()
        
        CapturedAsset(
            id = request.captureId.value,
            originDeviceId = originDeviceId,
            mimeType = "image/jpeg",
            capturedAtMs = thumbnail.completedAtMs,
            bytes = thumbnail.bytes,
            width = thumbnail.width,
            height = thumbnail.height,
            metadata = mapOf(
                "quality" to thumbnail.quality.toString(),
                "commandAtMs" to (thumbnail.commandAtMs?.toString() ?: ""),
                "photoReadyAtMs" to thumbnail.photoReadyAtMs.toString(),
                "firstChunkAtMs" to (thumbnail.firstChunkAtMs?.toString() ?: ""),
                "origin" to request.origin.name,
                "hardware" to "HeyCyan"
            )
        )
    }
}
