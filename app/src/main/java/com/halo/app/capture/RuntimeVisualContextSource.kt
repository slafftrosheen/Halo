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
) : VisualContextSource {

    override suspend fun capture(request: VisualContextCaptureRequest): Result<ImageContext> = runCatching {
        val record = deviceRegistry.getDevice(request.originDeviceId)
            ?: error("Origin device is not registered")

        when (record.descriptor.kind) {
            DeviceKind.HEYCYAN_GLASSES -> captureHeyCyan(request)
            else -> error("Origin device does not expose a still-image source")
        }
    }

    private suspend fun captureHeyCyan(request: VisualContextCaptureRequest): ImageContext {
        val driver = heyCyanDriverProvider()
        val activeId = driver.activeDeviceId
            ?: error("HeyCyan device is not connected")
        require(activeId == request.originDeviceId) {
            "Requested visual origin is not the active HeyCyan device"
        }

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

        val bytes = asset.bytes ?: error("Still-image source returned no bytes")
        return ImageContext(
            imageBytes = bytes.copyOf(),
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
