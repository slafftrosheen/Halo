package com.halo.drivers.heycyan.capability

/**
 * The complete HeyCyan device capability surface.
 *
 * Every capability — including those that are blocked, unsupported, or limited — is
 * explicitly represented here. Nothing is silently omitted.
 *
 * Capability surface as of PR-025:
 *
 *   Battery                 AVAILABLE
 *   Device information      AVAILABLE
 *   Media inventory         AVAILABLE
 *   Photo                   AVAILABLE
 *   Video                   AVAILABLE
 *   Audio recording         AVAILABLE_WITH_LIMITATION  (recorded, not live)
 *   Live audio input        STRUCTURAL_VALIDATION
 *   AI Photo                BLOCKED_BY_PROTOCOL
 *   Wi-Fi media transfer    AVAILABLE_WITH_LIMITATION  (BLE trigger only; HTTP in PR-026)
 */
object HeyCyanCapabilities {

    val battery = HeyCyanCapability(
        id = "heycyan.battery",
        name = "Battery",
        status = HeyCyanCapabilityStatus.AVAILABLE,
        description = "Read battery percentage and charging state.",
    )

    val deviceInfo = HeyCyanCapability(
        id = "heycyan.device_info",
        name = "Device information",
        status = HeyCyanCapabilityStatus.AVAILABLE,
        description = "Read hardware and firmware information.",
    )

    val mediaInventory = HeyCyanCapability(
        id = "heycyan.media_inventory",
        name = "Media inventory",
        status = HeyCyanCapabilityStatus.AVAILABLE,
        description = "Read photo, video and audio counts.",
    )

    val photo = HeyCyanCapability(
        id = "heycyan.camera.photo",
        name = "Photo capture",
        status = HeyCyanCapabilityStatus.AVAILABLE,
        description = "Trigger the glasses camera.",
    )

    val video = HeyCyanCapability(
        id = "heycyan.camera.video",
        name = "Video recording",
        status = HeyCyanCapabilityStatus.AVAILABLE,
        description = "Start and stop video recording.",
    )

    /**
     * Audio recording: the glasses record audio locally.
     * Files are retrieved via Wi-Fi transfer (PR-026).
     * Live BLE PCM streaming is NOT supported — see [liveAudio].
     */
    val audioRecording = HeyCyanCapability(
        id = "heycyan.audio.recording",
        name = "Audio recording",
        status = HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION,
        description = "Record audio on the glasses for later retrieval.",
    )

    /**
     * Live audio input over BLE is currently under STRUCTURAL_VALIDATION.
     * The `BC 59` packet streams real-time Opus-compatible frames, but
     * the proprietary `4B 41` framing requires structural validation and
     * the trigger for initiating the stream is currently unknown.
     */
    val liveAudio = HeyCyanCapability(
        id = "heycyan.audio.live_input",
        name = "Live audio input",
        status = HeyCyanCapabilityStatus.STRUCTURAL_VALIDATION,
        description = "HeyCyan exposes a BC59 audio stream, currently under structural validation.",
    )

    /**
     * AI Photo: mode 0x06 is known, but the multi-packet image transfer
     * reassembly protocol is NOT yet established from captures or authoritative source.
     * This capability is BLOCKED_BY_PROTOCOL until PR-017/capture analysis resolves it.
     */
    val aiPhoto = HeyCyanCapability(
        id = "heycyan.ai_photo",
        name = "AI photo",
        status = HeyCyanCapabilityStatus.BLOCKED_BY_PROTOCOL,
        description = "AI photo mode exists, but complete image transfer framing is unresolved.",
    )

    /**
     * Wi-Fi transfer: enables transfer mode over BLE (mode 0x07).
     * The full Wi-Fi HTTP media download stack (media.config, JPG/MP4/OPUS) is PR-026.
     */
    val wifiTransfer = HeyCyanCapability(
        id = "heycyan.wifi_transfer",
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

    /** Looks up a capability by ID. Returns null if not found. */
    fun find(id: String): HeyCyanCapability? = all.firstOrNull { it.id == id }
}
