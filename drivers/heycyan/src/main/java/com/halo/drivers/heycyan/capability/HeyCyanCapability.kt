package com.halo.drivers.heycyan.capability

/**
 * Capability status for the PR-025 semantic capability layer.
 *
 * This enum is distinct from the top-level [com.halo.drivers.heycyan.CapabilityStatus]
 * which uses investigation/implementation-phase language. This one is the surface-facing
 * status presented to the Runtime and UI — describing the operational availability of
 * each capability from the device consumer's perspective.
 */
enum class HeyCyanCapabilityStatus {
    /** Fully available and operational. */
    AVAILABLE,

    /** Available but with documented device or transport limitations. */
    AVAILABLE_WITH_LIMITATION,

    /**
     * The capability exists in the source/SDK but the complete BLE wire protocol
     * to execute it is not yet established. Operations will return ProtocolNotEstablished.
     */
    BLOCKED_BY_PROTOCOL,

    /** The device hardware explicitly does not support this capability. */
    UNSUPPORTED,

    /** Blocked by Android platform constraints (e.g., missing API level or permission). */
    PLATFORM_LIMITED,
}

/**
 * Describes a single HeyCyan device capability as exposed to the Halo Runtime and UI.
 */
data class HeyCyanCapability(
    val id: String,
    val name: String,
    val status: HeyCyanCapabilityStatus,
    val description: String,
)
