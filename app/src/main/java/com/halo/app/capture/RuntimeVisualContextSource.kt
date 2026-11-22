package com.halo.app.capture

import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.capability.HeyCyanStillImageSource
import com.halo.runtime.cognition.context.*
import com.halo.sdk.driver.*
import com.halo.sdk.runtime.embodiment.DeviceKind
import com.halo.sdk.runtime.embodiment.DeviceRecord
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
    private data class ResolvedCamera(val source: StillImageSource, val provenance: MediaProvenance)

    private fun resolveCamera(record: DeviceRecord): ResolvedCamera {
        val originDeviceId = record.identity.deviceId
        if (record.descriptor.kind == DeviceKind.HEYCYAN_GLASSES) {
            val driver = heyCyanDriverProvider()
            if (driver.activeDeviceId != originDeviceId) throw MediaAcquisitionException(MediaFailure.SOURCE_DISCONNECTED)
            return ResolvedCamera(HeyCyanStillImageSource(driver.thumbnailCaptureCoordinator, originDeviceId), MediaProvenance.HEYCYAN_CAMERA)
        } else {
            val session = nativeSession(originDeviceId)
                ?: throw MediaAcquisitionException(MediaFailure.SOURCE_UNAVAILABLE)
            val snapshot = session.driver.state.value
            if (session.state.value != SessionState.READY || session.identity.deviceId != originDeviceId ||
                session.identity.deviceType != record.identity.deviceType || !snapshot.aiPhotoAvailable || snapshot.cameraAvailable == false) {
                throw MediaAcquisitionException(MediaFailure.SOURCE_DISCONNECTED)
            }
            val source = object : StillImageSource {
                override suspend fun captureStill(request: StillCaptureRequest): Result<CapturedAsset> = try {
                    val bytes = session.driver.captureAiPhoto()
                    Result.success(CapturedAsset(id = request.captureId.value, originDeviceId = session.identity.deviceId,
                        mimeType = "image/jpeg", capturedAtMs = System.currentTimeMillis(), bytes = bytes,
                        metadata = mapOf("family" to session.driver.protocolId)))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Result.failure(error) }
            }
            // Preserve the exact source ID without calling foreign images HeyCyan.
            return ResolvedCamera(source, MediaProvenance.IMPORTED)
        }
    }

    private fun DeviceRecord.isGlasses(): Boolean = descriptor.kind !in setOf(DeviceKind.BUDDYBOT, DeviceKind.GENERIC_AUDIO, DeviceKind.UNKNOWN)

    internal suspend fun connectedHomeworkGlasses(): List<DeviceRecord> = deviceRegistry.getSavedDevices()
        .filter { it.isGlasses() && runCatching { resolveCamera(it) }.isSuccess }
        .take(12)

    internal suspend fun isSavedHomeworkGlasses(deviceId: String): Boolean = deviceRegistry.getDevice(deviceId)?.isGlasses() == true

    internal suspend fun captureHomeworkStill(deviceId: String, request: StillCaptureRequest, checkAuthority: () -> Unit): CapturedAsset {
        checkAuthority()
        val source = object : StillImageSource {
            override suspend fun captureStill(request: StillCaptureRequest): Result<CapturedAsset> {
                val record = deviceRegistry.getDevice(deviceId)?.takeIf { it.isGlasses() }
                    ?: throw MediaAcquisitionException(MediaFailure.SOURCE_UNAVAILABLE)
                val camera = resolveCamera(record)
                checkAuthority()
                return camera.source.captureStill(request)
            }
        }
        val asset = LeasedStillImageSource(source, deviceId, resourceLeaseManager).captureStill(request).getOrThrow()
        try {
            checkAuthority()
            check(asset.originDeviceId == deviceId && asset.id == request.captureId.value)
            return asset
        } catch (error: Exception) {
            asset.bytes?.fill(0)
            throw error
        }
    }

    override suspend fun capture(request: VisualContextCaptureRequest): Result<ImageContext> = try {
        val record = deviceRegistry.getDevice(request.originDeviceId)
            ?: throw MediaAcquisitionException(MediaFailure.SOURCE_UNAVAILABLE)
        val camera = resolveCamera(record)
        val asset = LeasedStillImageSource(camera.source, request.originDeviceId, resourceLeaseManager).captureStill(
            StillCaptureRequest(VisualCaptureId("context:${request.interactionId.value}:${System.currentTimeMillis()}"),
                VisualCaptureOrigin.SOFTWARE_REQUEST,
                metadata = mapOf("interactionId" to request.interactionId.value, "purpose" to "runtime_context"))
        ).getOrThrow()
        val bytes = asset.bytes ?: throw MediaAcquisitionException(MediaFailure.TRANSFER_FAILED)
        val reference = mediaStore.ingest(MediaAssetContent(bytes, asset.mimeType), camera.provenance,
            request.interactionId.value, request.originDeviceId)
        Result.success(ImageContext(mediaRef = reference, mimeType = asset.mimeType, timestampMs = asset.capturedAtMs,
            assetId = asset.id, interactionId = request.interactionId.value, originDeviceId = request.originDeviceId,
            source = ContextSource.VisualObservation, trust = ContextTrust.EXTERNAL_UNTRUSTED, freshness = request.freshness))
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }
}
