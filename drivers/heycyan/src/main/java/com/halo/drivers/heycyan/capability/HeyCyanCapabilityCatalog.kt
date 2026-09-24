package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.CapabilityStatus
import com.halo.drivers.heycyan.protocol.EvidenceKind
import com.halo.drivers.heycyan.protocol.ProtocolConfidence
import com.halo.drivers.heycyan.protocol.ProtocolEvidenceReference

object HeyCyanCapabilityCatalog {

    private val referenceEvidence = ProtocolEvidenceReference(
        source = "CyanBridge + HeyCyanSmartGlassesSDK",
        // NOTE: MemoMindPacketEncoder is the RFCOMM control-frame encoder (FA 00 ...),
        // not the BLE GATT command encoder. BLE framing comes from the separately
        // inspected vendor large-data and GATT implementations.
        locator = "MemoMindGattClient / HeyCyanSmartGlassesSDK device operations",
        kind = EvidenceKind.AUTHORITATIVE_SOURCE,
        confidence = ProtocolConfidence.HIGH,
    )

    val all: List<HeyCyanCapabilityDescriptor> = listOf(
        HeyCyanCapabilityDescriptor(
            id = "device.battery",
            name = "Battery status query",
            status = CapabilityStatus.IMPLEMENTED,
            evidence = listOf(referenceEvidence),
        ),

        HeyCyanCapabilityDescriptor(
            id = "device.version",
            name = "Firmware and hardware version query",
            status = CapabilityStatus.IMPLEMENTED,
            evidence = listOf(referenceEvidence),
        ),

        HeyCyanCapabilityDescriptor(
            id = "device.mac",
            name = "Device MAC address query",
            status = CapabilityStatus.IMPLEMENTED,
            evidence = listOf(referenceEvidence),
        ),

        HeyCyanCapabilityDescriptor(
            id = "device.datetime",
            name = "Device DateTime synchronization",
            status = CapabilityStatus.BLOCKED_BY_UNKNOWN_PROTOCOL,
            evidence = listOf(referenceEvidence),
            reason = "Binary structure of DateTime sync payload is unverified",
        ),

        HeyCyanCapabilityDescriptor(
            id = "camera.photo",
            name = "Still photo capture",
            status = CapabilityStatus.IMPLEMENTED_WITH_LIMITATION,
            evidence = listOf(referenceEvidence),
            reason = "Command and wire checksum are implemented; Halo hardware revalidation remains explicit",
        ),

        HeyCyanCapabilityDescriptor(
            id = "camera.ai_photo",
            name = "AI Photo",
            status = CapabilityStatus.IMPLEMENTED_WITH_LIMITATION,
            evidence = listOf(referenceEvidence),
            reason = "Reference-confirmed Chat-AI capture and BLE thumbnail transfer; Halo hardware revalidation remains explicit",
        ),

        HeyCyanCapabilityDescriptor(
            id = "video.record",
            name = "Video recording control",
            status = CapabilityStatus.IMPLEMENTED_WITH_LIMITATION,
            evidence = listOf(referenceEvidence),
            reason = "Start/stop video modes established; media transfer requires Wi-Fi Direct",
        ),

        HeyCyanCapabilityDescriptor(
            id = "audio.record",
            name = "Audio recording control",
            status = CapabilityStatus.IMPLEMENTED_WITH_LIMITATION,
            evidence = listOf(referenceEvidence),
            reason = "Start/stop audio modes established; media sync requires Wi-Fi Direct",
        ),

        HeyCyanCapabilityDescriptor(
            id = "audio.live_input",
            name = "Live microphone input",
            status = CapabilityStatus.IMPLEMENTED,
            evidence = listOf(referenceEvidence),
            reason = "Physically validated HeyCyan microphone stream",
        ),

        HeyCyanCapabilityDescriptor(
            id = "media.inventory",
            name = "Media counts query",
            status = CapabilityStatus.IMPLEMENTED,
            evidence = listOf(referenceEvidence),
        ),

        HeyCyanCapabilityDescriptor(
            id = "media.download",
            name = "Media download via Wi-Fi Direct",
            status = CapabilityStatus.IMPLEMENTED_WITH_LIMITATION,
            evidence = listOf(referenceEvidence),
            reason = "Network-scoped HTTP manifest and streaming downloads are implemented; transport availability remains device/platform dependent",
        ),

        HeyCyanCapabilityDescriptor(
            id = "wifi.transfer",
            name = "Wi-Fi transfer mode toggle",
            status = CapabilityStatus.IMPLEMENTED,
            evidence = listOf(referenceEvidence),
        ),

        HeyCyanCapabilityDescriptor(
            id = "bluetooth.ble",
            name = "GATT primary service transport",
            status = CapabilityStatus.IMPLEMENTED,
            evidence = listOf(referenceEvidence),
        ),

        HeyCyanCapabilityDescriptor(
            id = "bluetooth.rfcomm",
            name = "Secondary RFCOMM serial transport",
            status = CapabilityStatus.IMPLEMENTED_WITH_LIMITATION,
            evidence = listOf(referenceEvidence),
            reason = "RFCOMM channel discovery complete; control frame protocol in analysis",
        ),
    )

    fun find(id: String): HeyCyanCapabilityDescriptor? =
        all.firstOrNull { it.id == id }
}
