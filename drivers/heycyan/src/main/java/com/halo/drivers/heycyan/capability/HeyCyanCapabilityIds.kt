package com.halo.drivers.heycyan.capability

/**
 * Stable Runtime-facing identifiers for HeyCyan capabilities.
 *
 * These IDs are part of the device capability contract.
 * Do not use raw string literals outside this object.
 */
object HeyCyanCapabilityIds {
    const val BATTERY = "heycyan.battery"
    const val DEVICE_INFO = "heycyan.device_info"
    const val MEDIA_INVENTORY = "heycyan.media_inventory"
    const val CAMERA_PHOTO = "heycyan.camera.photo"
    const val CAMERA_VIDEO = "heycyan.camera.video"
    const val AUDIO_RECORDING = "heycyan.audio.recording"
    const val AUDIO_LIVE_INPUT = "heycyan.audio.live_input"
    const val AI_PHOTO = "heycyan.ai_photo"
    const val WIFI_TRANSFER = "heycyan.wifi_transfer"

    val all: Set<String> = setOf(
        BATTERY,
        DEVICE_INFO,
        MEDIA_INVENTORY,
        CAMERA_PHOTO,
        CAMERA_VIDEO,
        AUDIO_RECORDING,
        AUDIO_LIVE_INPUT,
        AI_PHOTO,
        WIFI_TRANSFER,
    )
}
