package com.halo.drivers.heycyan.transport

import com.halo.drivers.heycyan.protocol.EvidenceKind
import com.halo.drivers.heycyan.protocol.HeyCyanOperation
import com.halo.drivers.heycyan.protocol.ProtocolConfidence
import com.halo.drivers.heycyan.protocol.ProtocolEvidenceReference

/**
 * Transport types available in the HeyCyan driver architecture.
 */
enum class HeyCyanTransportType {
    BLE_GATT,
    RFCOMM,
    WIFI,
}

/**
 * Explicit route mapping a semantic operation to its preferred and fallback transports,
 * with evidence and retry safety classifications.
 */
data class HeyCyanOperationRoute(
    val operation: HeyCyanOperation,
    val preferred: HeyCyanTransportType,
    val fallback: HeyCyanTransportType?,
    val retrySafety: RetrySafety,
    val evidence: List<ProtocolEvidenceReference>,
    val reason: String,
)

/**
 * Transport preference and optional fallback for a device capability.
 */
data class HeyCyanCapabilityTransport(
    val capabilityId: String,
    val preferred: HeyCyanTransportType,
    val fallback: HeyCyanTransportType? = null,
)

/**
 * Source-backed transport routes establishing preferred paths per operation and capability.
 */
object HeyCyanTransportRoutes {

    private val sdkEvidence = ProtocolEvidenceReference(
        source = "HeyCyanSmartGlassesSDK",
        locator = "MemoMindGattClient / setDeviceMode",
        kind = EvidenceKind.AUTHORITATIVE_SOURCE,
        confidence = ProtocolConfidence.HIGH,
    )

    private val wifiEvidence = ProtocolEvidenceReference(
        source = "CyanBridge",
        locator = "AlbumDownloader / WiFiTransferManager",
        kind = EvidenceKind.AUTHORITATIVE_SOURCE,
        confidence = ProtocolConfidence.HIGH,
    )

    val operationRoutes: Map<HeyCyanOperation, HeyCyanOperationRoute> = mapOf(
        HeyCyanOperation.GetBattery to HeyCyanOperationRoute(
            operation = HeyCyanOperation.GetBattery,
            preferred = HeyCyanTransportType.BLE_GATT,
            fallback = HeyCyanTransportType.RFCOMM,
            retrySafety = RetrySafety.IDEMPOTENT,
            evidence = listOf(sdkEvidence),
            reason = "Idempotent device state query.",
        ),
        HeyCyanOperation.GetDeviceInfo to HeyCyanOperationRoute(
            operation = HeyCyanOperation.GetDeviceInfo,
            preferred = HeyCyanTransportType.BLE_GATT,
            fallback = HeyCyanTransportType.RFCOMM,
            retrySafety = RetrySafety.IDEMPOTENT,
            evidence = listOf(sdkEvidence),
            reason = "Idempotent device info query.",
        ),
        HeyCyanOperation.GetMediaInventory to HeyCyanOperationRoute(
            operation = HeyCyanOperation.GetMediaInventory,
            preferred = HeyCyanTransportType.BLE_GATT,
            fallback = HeyCyanTransportType.RFCOMM,
            retrySafety = RetrySafety.IDEMPOTENT,
            evidence = listOf(sdkEvidence),
            reason = "Idempotent media count query.",
        ),
        HeyCyanOperation.TakePhoto to HeyCyanOperationRoute(
            operation = HeyCyanOperation.TakePhoto,
            preferred = HeyCyanTransportType.BLE_GATT,
            fallback = null, // NOT_IDEMPOTENT — no fallback to prevent duplicate photo capture!
            retrySafety = RetrySafety.NOT_IDEMPOTENT,
            evidence = listOf(sdkEvidence),
            reason = "Camera control capture action.",
        ),
        HeyCyanOperation.ToggleVideo to HeyCyanOperationRoute(
            operation = HeyCyanOperation.ToggleVideo,
            preferred = HeyCyanTransportType.BLE_GATT,
            fallback = null,
            retrySafety = RetrySafety.NOT_IDEMPOTENT,
            evidence = listOf(sdkEvidence),
            reason = "Video recording toggle action.",
        ),
        HeyCyanOperation.PrepareVoiceRouting to HeyCyanOperationRoute(
            operation = HeyCyanOperation.PrepareVoiceRouting,
            preferred = HeyCyanTransportType.BLE_GATT,
            fallback = null,
            retrySafety = RetrySafety.NOT_IDEMPOTENT,
            evidence = listOf(sdkEvidence),
            reason = "Audio recording toggle action.",
        ),
        HeyCyanOperation.GenerateAIPhoto to HeyCyanOperationRoute(
            operation = HeyCyanOperation.GenerateAIPhoto,
            preferred = HeyCyanTransportType.BLE_GATT,
            fallback = null,
            retrySafety = RetrySafety.NOT_IDEMPOTENT,
            evidence = listOf(sdkEvidence),
            reason = "AI photo trigger.",
        ),
        HeyCyanOperation.EnterWifiTransferMode to HeyCyanOperationRoute(
            operation = HeyCyanOperation.EnterWifiTransferMode,
            preferred = HeyCyanTransportType.BLE_GATT,
            fallback = HeyCyanTransportType.RFCOMM,
            retrySafety = RetrySafety.UNKNOWN,
            evidence = listOf(sdkEvidence),
            reason = "Enables Wi-Fi transfer mode.",
        ),
        HeyCyanOperation.SynchronizeMedia to HeyCyanOperationRoute(
            operation = HeyCyanOperation.SynchronizeMedia,
            preferred = HeyCyanTransportType.WIFI,
            fallback = null,
            retrySafety = RetrySafety.IDEMPOTENT,
            evidence = listOf(wifiEvidence),
            reason = "Full Wi-Fi media synchronization.",
        ),
    )

    val capabilityRoutes = listOf(
        HeyCyanCapabilityTransport("heycyan.battery", HeyCyanTransportType.BLE_GATT, HeyCyanTransportType.RFCOMM),
        HeyCyanCapabilityTransport("heycyan.device_info", HeyCyanTransportType.BLE_GATT, HeyCyanTransportType.RFCOMM),
        HeyCyanCapabilityTransport("heycyan.media_inventory", HeyCyanTransportType.BLE_GATT, HeyCyanTransportType.RFCOMM),
        HeyCyanCapabilityTransport("heycyan.camera.photo", HeyCyanTransportType.BLE_GATT, null),
        HeyCyanCapabilityTransport("heycyan.camera.video", HeyCyanTransportType.BLE_GATT, null),
        HeyCyanCapabilityTransport("heycyan.audio.recording", HeyCyanTransportType.BLE_GATT, null),
        HeyCyanCapabilityTransport("heycyan.wifi_transfer", HeyCyanTransportType.WIFI, null),
    )

    fun routeForOperation(operation: HeyCyanOperation): HeyCyanOperationRoute? = operationRoutes[operation]
}
