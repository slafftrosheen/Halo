package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.protocol.HeyCyanOperation
import com.halo.drivers.heycyan.transport.HeyCyanTransportSnapshot

/**
 * Diagnostic state model for the Glass Devices UI.
 */
data class HeyCyanDiagnosticsState(
    val transports: HeyCyanTransportSnapshot,
    val lastOperationState: HeyCyanOperationState,
    val protocolWarnings: List<String> = emptyList(),
)

/**
 * Adapter exposing [HeyCyanDeviceController] to the generic [CapabilityProvider] interface.
 *
 * Hides all internal transport details (BLE, RFCOMM, Wi-Fi) from upper Runtime layers.
 */
class HeyCyanCapabilityRuntimeAdapter(
    private val controller: HeyCyanDeviceController,
) : CapabilityProvider {

    override fun capabilities(): List<DeviceCapabilityDescriptor> {
        val registry = HeyCyanCapabilityRegistry()
        return registry.all().map { cap ->
            DeviceCapabilityDescriptor(
                id = cap.id,
                name = cap.name,
                status = cap.status,
                description = cap.description,
            )
        }
    }

    override suspend fun execute(
        capabilityId: String,
        request: CapabilityRequest,
    ): CapabilityOutcome {
        val operation = capabilityIdToOperation(capabilityId)
            ?: return CapabilityOutcome.Failure("Unsupported HeyCyan capability: $capabilityId")

        return controller.execute(operation).fold(
            onSuccess = { CapabilityOutcome.Success },
            onFailure = { err -> CapabilityOutcome.Failure(err.message ?: "Execution failed") }
        )
    }

    private fun capabilityIdToOperation(capabilityId: String): HeyCyanOperation? = when (capabilityId) {
        "heycyan.device.battery" -> HeyCyanOperation.GetBattery
        "heycyan.device.info" -> HeyCyanOperation.GetDeviceInfo
        "heycyan.media.inventory" -> HeyCyanOperation.GetMediaInventory
        "heycyan.camera.photo" -> HeyCyanOperation.TakePhoto
        "heycyan.video.start" -> HeyCyanOperation.StartVideo
        "heycyan.video.stop" -> HeyCyanOperation.StopVideo
        "heycyan.audio.record.start" -> HeyCyanOperation.StartRecording
        "heycyan.audio.record.stop" -> HeyCyanOperation.StopRecording
        "heycyan.camera.ai_photo" -> HeyCyanOperation.GenerateAIPhoto
        "heycyan.transfer.mode" -> HeyCyanOperation.EnterWifiTransferMode
        "heycyan.media.sync" -> HeyCyanOperation.SynchronizeMedia
        else -> null
    }
}
