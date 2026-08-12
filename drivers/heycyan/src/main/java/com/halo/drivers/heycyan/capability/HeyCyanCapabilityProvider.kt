package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.debug.HeyCyanLogger
import com.halo.drivers.heycyan.protocol.HeyCyanOperation

/**
 * Adapts [HeyCyanCapabilityController] to the generic [CapabilityProvider] interface.
 *
 * This class is the boundary between the Halo Runtime / Devices UI and the HeyCyan
 * driver internals. Consumers see [DeviceCapabilityDescriptor]s and issue string-keyed
 * execute calls; they never reference BLE, GATT, or HeyCyan-specific types.
 *
 * Capability surface exposed:
 *
 *   heycyan.battery           AVAILABLE
 *   heycyan.device_info       AVAILABLE
 *   heycyan.media_inventory   AVAILABLE
 *   heycyan.camera.photo      AVAILABLE
 *   heycyan.camera.video      AVAILABLE
 *   heycyan.audio.recording   AVAILABLE_WITH_LIMITATION
 *   heycyan.audio.live_input  UNSUPPORTED
 *   heycyan.ai_photo          BLOCKED_BY_PROTOCOL
 *   heycyan.wifi_transfer     AVAILABLE_WITH_LIMITATION
 */
class HeyCyanCapabilityProvider(
    private val controller: HeyCyanCapabilityController,
) : CapabilityProvider {

    override fun capabilities(): List<DeviceCapabilityDescriptor> =
        HeyCyanCapabilities.all.map { it.toDescriptor() }

    override suspend fun execute(
        capabilityId: String,
        request: CapabilityRequest,
    ): CapabilityOutcome {
        HeyCyanLogger.i("HALO-TX-TRACE", "Capability execution requested: $capabilityId")
        val operation = capabilityIdToOperation(capabilityId)
            ?: return CapabilityOutcome.Failure("Unknown HeyCyan capability: $capabilityId")

        return controller.execute(operation).fold(
            onSuccess = { CapabilityOutcome.Success },
            onFailure = { CapabilityOutcome.Failure(it.message ?: "HeyCyan operation failed") },
        )
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun HeyCyanCapability.toDescriptor() = DeviceCapabilityDescriptor(
        id = id,
        name = name,
        status = status,
        description = description,
    )

    /**
     * Maps a capability ID string to its corresponding [HeyCyanOperation].
     *
     * Note: the live_input capability has no corresponding operation because it is
     * UNSUPPORTED — there is no valid execution path for it.
     */
    private fun capabilityIdToOperation(capabilityId: String): HeyCyanOperation? =
        when (capabilityId) {
            "heycyan.battery" -> HeyCyanOperation.GetBattery
            "heycyan.device_info" -> HeyCyanOperation.GetDeviceInfo
            "heycyan.media_inventory" -> HeyCyanOperation.GetMediaInventory
            "heycyan.camera.photo" -> HeyCyanOperation.TakePhoto
            "heycyan.camera.video" -> HeyCyanOperation.ToggleVideo
            "heycyan.audio.recording" -> HeyCyanOperation.PrepareVoiceRouting
            "heycyan.ai_photo" -> HeyCyanOperation.GenerateAIPhoto
            "heycyan.wifi_transfer" -> HeyCyanOperation.EnterWifiTransferMode
            // heycyan.audio.live_input has no operation — device doesn't support it
            else -> null
        }
}
