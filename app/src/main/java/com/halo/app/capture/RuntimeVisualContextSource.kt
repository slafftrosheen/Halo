package com.halo.app.capture

import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.capability.HeyCyanStillImageSource
import com.halo.runtime.cognition.context.*
import com.halo.sdk.driver.*
import com.halo.sdk.runtime.embodiment.DeviceKind
import com.halo.sdk.runtime.embodiment.DeviceRegistry
import com.halo.sdk.runtime.embodiment.SessionState
import com.halo.sdk.runtime.multimodal.*
import com.halo.sdk.runtime.resource.ResourceLeaseManager
import com.halo.sdk.runtime.wearables.NativeWearableSession
import kotlinx.coroutines.CancellationException

/** Exact origin device -> existing lease boundary -> local media store -> Halo context. */
class RuntimeVisualContextSource(
    private val deviceRegistry: DeviceRegistry,
    private val heyCyanDriverProvider: () -> RealHeyCyanDriver,
    private val resourceLeaseManager: ResourceLeaseManager,
    private val mediaStore: MediaAssetStore,
    private val nativeSession: (String) -> NativeWearableSession? = { id ->
        com.halo.app.di.ManualDependencyGraph.deviceSessionManager.getSession(id) as? NativeWearableSession
    },
) : VisualContextSource {
    override suspend fun capture(request: VisualContextCaptureRequest): Result<ImageContext> = try {
        val record = deviceRegistry.getDevice(request.originDeviceId)
            ?: throw MediaAcquisitionException(MediaFailure.SOURCE_UNAVAILABLE)
        val source: StillImageSource
        val provenance: MediaProvenance
        if (record.descriptor.kind == DeviceKind.HEYCYAN_GLASSES) {
            val driver = heyCyanDriverProvider()
            if (driver.activeDeviceId != request.originDeviceId) throw MediaAcquisitionException(MediaFailure.SOURCE_DISCONNECTED)
            source = HeyCyanStillImageSource(driver.thumbnailCaptureCoordinator, request.originDeviceId)
            provenance = MediaProvenance.HEYCYAN_CAMERA
        } else {
            val session = nativeSession(request.originDeviceId)
                ?: throw MediaAcquisitionException(MediaFailure.SOURCE_UNAVAILABLE)
            if (session.state.value != SessionState.READY || session.identity.deviceId != request.originDeviceId ||
                session.identity.deviceType != record.identity.deviceType || !session.driver.state.value.aiPhotoAvailable) {
                throw MediaAcquisitionException(MediaFailure.SOURCE_DISCONNECTED)
            }
            source = object : StillImageSource {
                override suspend fun captureStill(request: StillCaptureRequest): Result<CapturedAsset> = try {
                    val bytes = session.driver.captureAiPhoto()
                    Result.success(CapturedAsset(id = request.captureId.value, originDeviceId = session.identity.deviceId,
                        mimeType = "image/jpeg", capturedAtMs = System.currentTimeMillis(), bytes = bytes,
                        metadata = mapOf("family" to session.driver.family.name)))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Result.failure(error) }
            }
            // Preserve the exact source ID without calling foreign images HeyCyan.
            provenance = MediaProvenance.IMPORTED
        }
        val asset = LeasedStillImageSource(source, request.originDeviceId, resourceLeaseManager).captureStill(
            StillCaptureRequest(VisualCaptureId("context:${request.interactionId.value}:${System.currentTimeMillis()}"),
                VisualCaptureOrigin.SOFTWARE_REQUEST,
                metadata = mapOf("interactionId" to request.interactionId.value, "purpose" to "runtime_context"))
        ).getOrThrow()
        val bytes = asset.bytes ?: throw MediaAcquisitionException(MediaFailure.TRANSFER_FAILED)
        val reference = mediaStore.ingest(MediaAssetContent(bytes, asset.mimeType), provenance,
            request.interactionId.value, request.originDeviceId)
        Result.success(ImageContext(mediaRef = reference, mimeType = asset.mimeType, timestampMs = asset.capturedAtMs,
            assetId = asset.id, interactionId = request.interactionId.value, originDeviceId = request.originDeviceId,
            source = ContextSource.VisualObservation, trust = ContextTrust.EXTERNAL_UNTRUSTED, freshness = request.freshness))
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }
}
