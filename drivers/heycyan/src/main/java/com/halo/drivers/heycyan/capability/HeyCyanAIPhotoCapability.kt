package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.CapabilityStatus
import com.halo.drivers.heycyan.thumbnail.HeyCyanCapturedThumbnail
import com.halo.drivers.heycyan.thumbnail.HeyCyanThumbnailCapture

/**
 * Fast, local-inference-oriented JPEG capture over the glasses BLE bulk channel.
 */
class HeyCyanAIPhotoCapability(
    private val thumbnailCapture: HeyCyanThumbnailCapture,
) {

    val status: CapabilityStatus = CapabilityStatus.IMPLEMENTED_WITH_LIMITATION

    val reason: String =
        "Returns the firmware-generated AI JPEG thumbnail over BLE; it is not the full-resolution media file."

    /** Completes only after the JPEG payload has been reassembled and validated. */
    suspend fun trigger(quality: Int = 3): Result<HeyCyanCapturedThumbnail> =
        thumbnailCapture.capture(quality)

    /** Available on the constrained thumbnail path. */
    val isAvailable: Boolean = true
}
