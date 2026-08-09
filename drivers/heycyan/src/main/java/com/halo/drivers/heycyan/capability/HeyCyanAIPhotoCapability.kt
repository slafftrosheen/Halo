package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.CapabilityStatus

/**
 * AI Photo capability — explicitly BLOCKED_BY_UNKNOWN_PROTOCOL.
 *
 * Evidence:
 * - SDK `QCOperatorDeviceModeAIPhoto` (mode 0x06) exists
 * - CyanBridge Event 0x06 (AIImageChunk) partially documented
 * - Chunk reassembly protocol and image output format are NOT understood
 * - Cannot implement until live-capture validates the chunk format
 *
 * This capability is represented in the matrix but not functional.
 */
class HeyCyanAIPhotoCapability {

    val status: CapabilityStatus = CapabilityStatus.BLOCKED_BY_UNKNOWN_PROTOCOL

    val reason: String =
        "AI Photo trigger command (mode 0x06) exists in the SDK but the " +
            "chunk reassembly protocol and image output format are not fully " +
            "understood from reference material. Requires live-capture analysis " +
            "to implement. See SDK_API_PARITY.md §8."

    /**
     * Attempting to trigger AI Photo will always fail with a clear error.
     */
    suspend fun trigger(): Result<Unit> = Result.failure(
        UnsupportedOperationException(
            "AI Photo is ${status.name}: $reason"
        )
    )

    /**
     * Returns false — this capability is not available.
     */
    val isAvailable: Boolean = false
}
