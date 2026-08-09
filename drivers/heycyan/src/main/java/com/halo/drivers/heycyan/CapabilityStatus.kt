package com.halo.drivers.heycyan

/**
 * Classification for every HeyCyan device capability.
 *
 * Every feature exposed by the SDK or reference implementation must be classified
 * with exactly one of these statuses. This prevents silently omitting functionality
 * and confusing "not yet implemented" with "not supported by hardware."
 *
 * See: docs/protocols/heycyan/HEYCYAN_FUNCTIONALITY_MATRIX.md
 */
enum class CapabilityStatus {
    /** Fully implemented and functional in Halo. */
    IMPLEMENTED,

    /** Implemented but with known limitations documented in the functionality matrix. */
    IMPLEMENTED_WITH_LIMITATION,

    /** The capability exists in the SDK but the protocol details are incomplete. */
    BLOCKED_BY_UNKNOWN_PROTOCOL,

    /** The capability requires platform features not available (e.g., API level). */
    BLOCKED_BY_PLATFORM,

    /** The device hardware/firmware does not support this capability. */
    UNSUPPORTED_BY_DEVICE,

    /** The capability has not yet been investigated in the SDK/reference. */
    NOT_YET_INVESTIGATED
}

/**
 * Describes a single device capability with its implementation status and evidence.
 */
data class CapabilityDescriptor(
    val name: String,
    val status: CapabilityStatus,
    val evidence: String,
    val limitation: String? = null
)
