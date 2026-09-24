package com.halo.drivers.heycyan.capability

/**
 * Canonical semantic capability surface for HeyCyan.
 *
 * A capability describes what the device can expose.
 * HeyCyanOperation describes what the runtime can ask it to do.
 * These are intentionally not one-to-one.
 */
object HeyCyanCapabilities {
    val battery = HeyCyanCapability(
        id = HeyCyanCapabilityIds.BATTERY,
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
        status = HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION,
        description = "Live microphone input over the physically validated HeyCyan voice stream.",
    )

    val speakerOutput = HeyCyanCapability(
        id = HeyCyanCapabilityIds.AUDIO_SPEAKER_OUTPUT,
        name = "Speaker output",
        status = HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION,
        description = "Select the glasses as the speech playback target through the verified Classic Bluetooth audio path.",
    )

    val aiPhoto = HeyCyanCapability(
        id = HeyCyanCapabilityIds.AI_PHOTO,
        name = "AI photo",
        status = HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION,
        description = "Fresh AI thumbnail capture gated by photo-ready evidence and transferred over BLE.",
    )

    val wifiTransfer = HeyCyanCapability(
        id = HeyCyanCapabilityIds.WIFI_TRANSFER,
        name = "Wi-Fi media transfer",
        status = HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION,
        description = "Enter device transfer mode for Wi-Fi media retrieval.",
    )

    val all: List<HeyCyanCapability> = listOf(
        battery,
        deviceInfo,
        mediaInventory,
        photo,
        video,
        audioRecording,
        liveAudio,
        speakerOutput,
        aiPhoto,
        wifiTransfer,
    )

    fun find(id: String): HeyCyanCapability? = all.firstOrNull { it.id == id }
}
