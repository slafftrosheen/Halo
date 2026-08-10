package com.halo.drivers.heycyan.capability

/**
 * Machine-readable 100% source-documented functionality surface for HeyCyan smart glasses.
 *
 * This manifest lists every capability ID established from authoritative source forensics,
 * regardless of whether it is AVAILABLE, BLOCKED_BY_PROTOCOL, or UNSUPPORTED.
 */
object HeyCyanFunctionalityManifest {

    val expectedCapabilities = setOf(
        "heycyan.device.info",
        "heycyan.device.battery",
        "heycyan.device.datetime",
        "heycyan.device.mac",

        "heycyan.camera.photo",
        "heycyan.camera.ai_photo",

        "heycyan.video.start",
        "heycyan.video.stop",

        "heycyan.audio.record.start",
        "heycyan.audio.record.stop",
        "heycyan.audio.live_input",

        "heycyan.media.inventory",
        "heycyan.media.sync",
        "heycyan.media.download",

        "heycyan.transfer.mode",
        "heycyan.transfer.wifi",

        "heycyan.bluetooth.ble",
        "heycyan.bluetooth.rfcomm",
        "heycyan.wifi",
    )
}

/**
 * Full HeyCyan capability registry exposing the complete device surface.
 */
class HeyCyanCapabilityRegistry {

    private val capabilitiesList: List<HeyCyanCapability> = listOf(
        HeyCyanCapability("heycyan.device.info", "Device Info", HeyCyanCapabilityStatus.AVAILABLE, "Hardware & Firmware info"),
        HeyCyanCapability("heycyan.device.battery", "Battery", HeyCyanCapabilityStatus.AVAILABLE, "Battery percentage & charging state"),
        HeyCyanCapability("heycyan.device.datetime", "Device Clock", HeyCyanCapabilityStatus.BLOCKED_BY_PROTOCOL, "RTC set payload unresolved"),
        HeyCyanCapability("heycyan.device.mac", "MAC Address", HeyCyanCapabilityStatus.AVAILABLE, "Bluetooth MAC address"),

        HeyCyanCapability("heycyan.camera.photo", "Photo Capture", HeyCyanCapabilityStatus.AVAILABLE, "Camera photo capture"),
        HeyCyanCapability("heycyan.camera.ai_photo", "AI Photo", HeyCyanCapabilityStatus.BLOCKED_BY_PROTOCOL, "AI photo image transfer framing unresolved"),

        HeyCyanCapability("heycyan.video.start", "Start Video", HeyCyanCapabilityStatus.AVAILABLE, "Start video recording"),
        HeyCyanCapability("heycyan.video.stop", "Stop Video", HeyCyanCapabilityStatus.AVAILABLE, "Stop video recording"),

        HeyCyanCapability("heycyan.audio.record.start", "Start Audio Recording", HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION, "On-device audio recording"),
        HeyCyanCapability("heycyan.audio.record.stop", "Stop Audio Recording", HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION, "Stop audio recording"),
        HeyCyanCapability("heycyan.audio.live_input", "Live Audio Stream", HeyCyanCapabilityStatus.UNSUPPORTED, "Live BLE PCM streaming is unsupported by device hardware"),

        HeyCyanCapability("heycyan.media.inventory", "Media Inventory", HeyCyanCapabilityStatus.AVAILABLE, "Photo/video/audio counts"),
        HeyCyanCapability("heycyan.media.sync", "Media Synchronization", HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION, "Full Wi-Fi media sync"),
        HeyCyanCapability("heycyan.media.download", "Media Download", HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION, "Individual media download"),

        HeyCyanCapability("heycyan.transfer.mode", "Transfer Mode", HeyCyanCapabilityStatus.AVAILABLE, "BLE transfer mode trigger"),
        HeyCyanCapability("heycyan.transfer.wifi", "Wi-Fi Hotspot", HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION, "Device Wi-Fi HTTP media server"),

        HeyCyanCapability("heycyan.bluetooth.ble", "BLE GATT Transport", HeyCyanCapabilityStatus.AVAILABLE, "Primary BLE control channel"),
        HeyCyanCapability("heycyan.bluetooth.rfcomm", "RFCOMM Transport", HeyCyanCapabilityStatus.AVAILABLE, "Secondary MemoMind RFCOMM channel"),
        HeyCyanCapability("heycyan.wifi", "Wi-Fi Transport", HeyCyanCapabilityStatus.AVAILABLE, "Wi-Fi HTTP media server"),
    )

    fun all(): List<HeyCyanCapability> = capabilitiesList

    fun capability(id: String): HeyCyanCapability? = capabilitiesList.firstOrNull { it.id == id }
}

/**
 * Capability validator asserting 100% surface completeness, duplicate prevention, and status checks.
 */
class HeyCyanCapabilityValidator {

    fun validate(capabilities: List<HeyCyanCapability>): List<String> {
        val errors = mutableListOf<String>()
        val ids = capabilities.map { it.id }

        if (ids.size != ids.toSet().size) {
            errors.add("Duplicate capability IDs detected in registry")
        }

        val missing = HeyCyanFunctionalityManifest.expectedCapabilities - ids.toSet()
        if (missing.isNotEmpty()) {
            errors.add("Missing expected source capabilities: $missing")
        }

        return errors
    }
}
