package com.halo.drivers.heycyan.capability

/**
 * Canonical HeyCyan device capability surface.
 *
 * Capability IDs are stable Runtime contract identifiers. Do not duplicate these
 * strings elsewhere in the application or driver.
 */
object HeyCyanCapabilities {

    val battery = HeyCyanCapability(
        id = HeyCyanCapabilityIds.DEVICE_BATTERY,
        name = "Battery",
        status = HeyCyanCapabilityStatus.AVAILABLE,
        description = "Read battery percentage and charging state.",
    )

    val deviceInfo = HeyCyanCapability(
        id = HeyCyanCapabilityIds.DEVICE_INFO,
        name = "Device information",
        status = HeyCyanCapabilityStatus.AVAILABLE,
        description = "Read hardware and firmware information.",
    )

    val mediaInventory = HeyCyanCapability(
        id = HeyCyanCapabilityIds.MEDIA_INVENTORY,
        name = "Media inventory",
        status = HeyCyanCapabilityStatus.AVAILABLE,
        description = "Read photo, video and audio counts.",
    )

    val photo = HeyCyanCapability(
        id = HeyCyanCapabilityIds.CAMERA_PHOTO,
        name = "Photo capture",
        status = HeyCyanCapabilityStatus.AVAILABLE,
        description = "Trigger the glasses camera.",
    )

    val video = HeyCyanCapability(
        id = HeyCyanCapabilityIds.CAMERA_VIDEO,
        name = "Video recording",
        status = HeyCyanCapabilityStatus.AVAILABLE,
        description = "Start and stop video recording.",
    )

    val audioRecording = HeyCyanCapability(
        id = HeyCyanCapabilityIds.AUDIO_RECORDING,
        name = "Audio recording",
        status = HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION,
        description = "Record audio on the glasses for later retrieval.",
    )

    val liveAudio = HeyCyanCapability(
        id = HeyCyanCapabilityIds.AUDIO_LIVE_INPUT,
        name = "Live audio input",
        status = HeyCyanCapabilityStatus.STRUCTURAL_VALIDATION,
        description = "The BC59 audio stream is observed, but its complete framing and trigger protocol remain unresolved.",
    )

    val aiPhoto = HeyCyanCapability(
        id = HeyCyanCapabilityIds.CAMERA_AI_PHOTO,
        name = "AI photo",
        status = HeyCyanCapabilityStatus.BLOCKED_BY_PROTOCOL,
        description = "AI photo mode exists, but complete image transfer framing is unresolved.",
    )

    val wifiTransfer = HeyCyanCapability(
        id = HeyCyanCapabilityIds.WIFI_TRANSFER,
        name = "Wi-Fi media transfer",
        status = HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION,
        description = "Switch to transfer mode and retrieve media over Wi-Fi.",
    )

    val all: List<HeyCyanCapability> = listOf(
        battery,
        deviceInfo,
        mediaInventory,
        photo,
        video,
        audioRecording,
        liveAudio,
        aiPhoto,
        wifiTransfer,
    )

    fun find(id: String): HeyCyanCapability? = all.firstOrNull { it.id == id }
}
