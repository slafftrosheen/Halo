package com.halo.drivers.heycyan.capability

import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation

/**
 * Generic capability provider contract for Halo device drivers.
 *
 * Each driver exposes its device capability surface through this interface.
 * The Runtime and Devices UI consume this without knowing anything about the
 * underlying transport (BLE, Wi-Fi, RFCOMM, etc.).
 *
 * Defined here until a common SDK location is established.
 */
interface CapabilityProvider {

    /** Returns all capabilities exposed by this device, including blocked and unsupported ones. */
    fun capabilities(): List<DeviceCapabilityDescriptor>

    /**
     * Execute a capability by ID.
     *
     * Returns [CapabilityOutcome.Success] on completion.
     * Returns [CapabilityOutcome.Failure] if the capability is unsupported, blocked,
     * or the transport fails.
     */
    suspend fun execute(
        capabilityId: String,
        request: CapabilityRequest = CapabilityRequest.Empty,
    ): CapabilityOutcome
}

/**
 * Descriptor for a single capability as presented to the Runtime and UI.
 */
data class DeviceCapabilityDescriptor(
    val id: String,
    val name: String,
    val status: HeyCyanCapabilityStatus,
    val description: String,
)

/**
 * Request payload for a capability execution.
 * Currently empty — extended in future PRs for parameterised operations.
 */
sealed interface CapabilityRequest {
    data object Empty : CapabilityRequest
}

/** Outcome of a [CapabilityProvider.execute] call. */
sealed interface CapabilityOutcome {
    data object Success : CapabilityOutcome
    data class Failure(val message: String) : CapabilityOutcome
}
