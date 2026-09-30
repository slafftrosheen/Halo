package com.halo.sdk.driver

import com.halo.sdk.runtime.resource.*

class LeasedStillImageSource(
    private val delegate: StillImageSource,
    private val deviceId: String,
    private val resourceLeaseManager: ResourceLeaseManager,
) : StillImageSource {

    override suspend fun captureStill(request: StillCaptureRequest): Result<CapturedAsset> {
        val endpoint = ResourceEndpointId(deviceId)
        val leaseReq = ResourceLeaseRequest(
            requestId = "still_capture:${request.captureId.value}",
            holder = ResourceHolder(
                id = request.captureId.value,
                kind = ResourceHolderKind.CONTEXT_ACQUISITION,
            ),
            claims = setOf(
                ResourceClaim(ResourceKey(HaloResourceIds.CameraSensor, endpoint), ResourceAccess.EXCLUSIVE),
                ResourceClaim(ResourceKey(HaloResourceIds.CameraCapture, endpoint), ResourceAccess.EXCLUSIVE),
            ),
            priority = ResourcePriority.INTERACTIVE,
            waitPolicy = ResourceWaitPolicy.Wait(5_000L),
            preemption = ResourcePreemptionPolicy.COOPERATIVE,
        )

        return when (val acq = resourceLeaseManager.acquire(leaseReq)) {
            is ResourceAcquireResult.Granted -> {
                try {
                    delegate.captureStill(request)
                } finally {
                    acq.lease.release()
                }
            }
            else -> Result.failure(com.halo.sdk.runtime.multimodal.MediaAcquisitionException(com.halo.sdk.runtime.multimodal.MediaFailure.SOURCE_BUSY))
        }
    }
}

class LeasedWirelessTransferCapability(
    private val delegate: WirelessTransferCapability,
    private val deviceId: String,
    private val resourceLeaseManager: ResourceLeaseManager,
) : WirelessTransferCapability {

    @Volatile private var activeLease: ResourceLease? = null

    override suspend fun enableTransfer(): Result<TransferSessionInfo> {
        val endpoint = ResourceEndpointId(deviceId)
        val leaseReq = ResourceLeaseRequest(
            requestId = "media_transfer:$deviceId",
            holder = ResourceHolder(
                id = deviceId,
                kind = ResourceHolderKind.CONTEXT_ACQUISITION,
            ),
            claims = setOf(
                ResourceClaim(ResourceKey(HaloResourceIds.MediaTransfer, endpoint), ResourceAccess.EXCLUSIVE),
            ),
            priority = ResourcePriority.BACKGROUND,
            waitPolicy = ResourceWaitPolicy.Wait(5_000L),
            preemption = ResourcePreemptionPolicy.COOPERATIVE,
        )

        return when (val acq = resourceLeaseManager.acquire(leaseReq)) {
            is ResourceAcquireResult.Granted -> {
                activeLease = acq.lease
                val result = delegate.enableTransfer()
                if (result.isFailure) {
                    acq.lease.release()
                    activeLease = null
                }
                result
            }
            else -> Result.failure(IllegalStateException("Media transfer resource busy on endpoint $deviceId"))
        }
    }

    override suspend fun disableTransfer(): Result<Unit> {
        return try {
            delegate.disableTransfer()
        } finally {
            activeLease?.release()
            activeLease = null
        }
    }
}
