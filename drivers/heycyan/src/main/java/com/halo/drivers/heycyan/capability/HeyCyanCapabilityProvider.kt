package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.debug.HeyCyanLogger
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation

/** Runtime-facing HeyCyan capability adapter. */
class HeyCyanCapabilityProvider(
    private val controller: HeyCyanCapabilityController,
) : CapabilityProvider {

    override fun capabilities(): List<DeviceCapabilityDescriptor> =
        HeyCyanCapabilities.all.map { capability ->
            DeviceCapabilityDescriptor(
                id = capability.id,
                name = capability.name,
                status = capability.status,
                description = capability.description,
            )
        }

    override suspend fun execute(
        capabilityId: String,
        request: CapabilityRequest,
    ): CapabilityOutcome {
        HeyCyanLogger.i("HALO-TX-TRACE", "Capability execution requested: $capabilityId")

        val capability = HeyCyanCapabilities.find(capabilityId)
            ?: return CapabilityOutcome.Failure("Unknown HeyCyan capability: $capabilityId")

        return when (capability.status) {
            HeyCyanCapabilityStatus.UNSUPPORTED -> CapabilityOutcome.Failure("Unsupported HeyCyan capability: ${capability.id}")
            HeyCyanCapabilityStatus.BLOCKED_BY_PROTOCOL -> CapabilityOutcome.Failure("Capability blocked by unresolved protocol: ${capability.id}")
            HeyCyanCapabilityStatus.STRUCTURAL_VALIDATION -> CapabilityOutcome.Failure("Capability is still under structural validation: ${capability.id}")
            HeyCyanCapabilityStatus.PLATFORM_LIMITED -> CapabilityOutcome.Failure("Capability is limited by the host platform: ${capability.id}")
            HeyCyanCapabilityStatus.AVAILABLE,
            HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION -> {
                val operation = directOperationFor(capability.id)
                    ?: return CapabilityOutcome.Failure("Capability ${capability.id} requires a concrete semantic operation")
                controller.execute(operation).fold(
                    onSuccess = { CapabilityOutcome.Success },
                    onFailure = { CapabilityOutcome.Failure(it.message ?: "HeyCyan operation failed") },
                )
            }
        }
    }

    private fun directOperationFor(capabilityId: String): HeyCyanOperation? = when (capabilityId) {
        HeyCyanCapabilityIds.BATTERY -> HeyCyanOperation.GetBattery
        HeyCyanCapabilityIds.DEVICE_INFO -> HeyCyanOperation.GetDeviceInfo
        HeyCyanCapabilityIds.MEDIA_INVENTORY -> HeyCyanOperation.GetMediaInventory
        HeyCyanCapabilityIds.CAMERA_PHOTO -> HeyCyanOperation.TakePhoto
        HeyCyanCapabilityIds.WIFI_TRANSFER -> HeyCyanOperation.EnterWifiTransferMode
        HeyCyanCapabilityIds.AUDIO_SPEAKER_OUTPUT -> HeyCyanOperation.RouteAudioToGlasses
        HeyCyanCapabilityIds.CAMERA_VIDEO,
        HeyCyanCapabilityIds.AUDIO_RECORDING,
        HeyCyanCapabilityIds.AUDIO_LIVE_INPUT,
        HeyCyanCapabilityIds.AI_PHOTO -> null
        else -> null
    }
}
