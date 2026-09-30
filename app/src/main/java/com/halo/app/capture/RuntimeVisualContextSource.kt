package com.halo.app.capture

import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.capability.HeyCyanStillImageSource
import com.halo.runtime.cognition.context.ContextSource
import com.halo.runtime.cognition.context.ContextTrust
import com.halo.runtime.cognition.context.ImageContext
import com.halo.runtime.cognition.context.VisualContextCaptureRequest
import com.halo.runtime.cognition.context.VisualContextSource
import com.halo.sdk.driver.LeasedStillImageSource
import com.halo.sdk.driver.StillCaptureRequest
import com.halo.sdk.driver.VisualCaptureId
import com.halo.sdk.driver.VisualCaptureOrigin
import com.halo.sdk.runtime.embodiment.DeviceKind
import com.halo.sdk.runtime.embodiment.DeviceRegistry
import com.halo.sdk.runtime.resource.ResourceLeaseManager

/**
 * Android-host composition adapter. Runtime cognition asks only for a visual
 * context from a semantic origin device; concrete driver selection stays here.
 */
class RuntimeVisualContextSource(
    private val deviceRegistry: DeviceRegistry,
    private val heyCyanDriverProvider: () -> RealHeyCyanDriver,
    private val resourceLeaseManager: ResourceLeaseManager,
    private val mediaStore: com.halo.sdk.runtime.multimodal.MediaAssetStore,
) : VisualContextSource {

    override suspend fun capture(request: VisualContextCaptureRequest): Result<ImageContext> = runCatching {
        val record = deviceRegistry.getDevice(request.originDeviceId)
            ?: throw com.halo.sdk.runtime.multimodal.MediaAcquisitionException(com.halo.sdk.runtime.multimodal.MediaFailure.SOURCE_UNAVAILABLE)

        when (record.descriptor.kind) {
            DeviceKind.HEYCYAN_GLASSES -> {
                val selected = com.halo.sdk.runtime.multimodal.VisualSourceResolver().resolve(
                    com.halo.sdk.runtime.multimodal.VisualOrigin.HEYCYAN, false, false, true,
                    setOf(com.halo.sdk.runtime.multimodal.VisualSource.HEYCYAN_CAMERA), false)
                println("[HALO_VISUAL_SOURCE_RESOLVED] origin=HEYCYAN requested=${request.freshness} source=${selected.source} reason=interaction_origin")
                captureHeyCyan(request)
            }
            else -> throw com.halo.sdk.runtime.multimodal.MediaAcquisitionException(com.halo.sdk.runtime.multimodal.MediaFailure.SOURCE_UNAVAILABLE)
        }
    }

    private suspend fun captureHeyCyan(request: VisualContextCaptureRequest): ImageContext {
        val driver = heyCyanDriverProvider()
        val activeId = driver.activeDeviceId
            ?: throw com.halo.sdk.runtime.multimodal.MediaAcquisitionException(com.halo.sdk.runtime.multimodal.MediaFailure.SOURCE_DISCONNECTED)
        if (activeId != request.originDeviceId) throw com.halo.sdk.runtime.multimodal.MediaAcquisitionException(com.halo.sdk.runtime.multimodal.MediaFailure.SOURCE_DISCONNECTED)

        val source = LeasedStillImageSource(
            delegate = HeyCyanStillImageSource(
                capture = driver.thumbnailCaptureCoordinator,
                originDeviceId = activeId,
            ),
            deviceId = activeId,
            resourceLeaseManager = resourceLeaseManager,
        )

        val asset = source.captureStill(
            StillCaptureRequest(
                captureId = VisualCaptureId(
                    "context:${request.interactionId.value}:${System.currentTimeMillis()}"
                ),
                origin = VisualCaptureOrigin.SOFTWARE_REQUEST,
                metadata = mapOf(
                    "interactionId" to request.interactionId.value,
                    "purpose" to "runtime_context",
                ),
            )
        ).getOrThrow()

        val bytes = asset.bytes ?: throw com.halo.sdk.runtime.multimodal.MediaAcquisitionException(com.halo.sdk.runtime.multimodal.MediaFailure.TRANSFER_FAILED)
        val ref = mediaStore.ingest(
            com.halo.sdk.runtime.multimodal.MediaAssetContent(bytes, asset.mimeType),
            com.halo.sdk.runtime.multimodal.MediaProvenance.HEYCYAN_CAMERA,
            request.interactionId.value, activeId,
        )
        return ImageContext(
            mediaRef = ref,
            mimeType = asset.mimeType,
            timestampMs = asset.capturedAtMs,
            assetId = asset.id,
            interactionId = request.interactionId.value,
            originDeviceId = asset.originDeviceId ?: activeId,
            source = ContextSource.VisualObservation,
            trust = ContextTrust.EXTERNAL_UNTRUSTED,
            freshness = request.freshness,
        )
    }
}
