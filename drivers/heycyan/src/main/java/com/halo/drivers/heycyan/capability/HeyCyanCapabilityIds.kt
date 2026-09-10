package com.halo.drivers.heycyan.capability

/**
 * Canonical capability identifiers for the HeyCyan driver.
 *
 * Keep these IDs stable. They are part of the Runtime/device contract and must not
 * be duplicated as string literals across adapters, UI, or tests.
 */
object HeyCyanCapabilityIds {
    const val DEVICE_BATTERY = "heycyan.battery"
    const val DEVICE_INFO = "heycyan.device_info"
    const val MEDIA_INVENTORY = "heycyan.media_inventory"
    const val CAMERA_PHOTO = "heycyan.camera.photo"
    const val CAMERA_VIDEO = "heycyan.camera.video"
    const val AUDIO_RECORDING = "heycyan.audio.recording"
    const val AUDIO_LIVE_INPUT = "heycyan.audio.live_input"
    const val CAMERA_AI_PHOTO = "heycyan.ai_photo"
    const val WIFI_TRANSFER = "heycyan.wifi_transfer"

    val all: Set<String> = setOf(
        DEVICE_BATTERY,
        DEVICE_INFO,
        MEDIA_INVENTORY,
        CAMERA_PHOTO,
        CAMERA_VIDEO,
        AUDIO_RECORDING,
        AUDIO_LIVE_INPUT,
        CAMERA_AI_PHOTO,
        WIFI_TRANSFER,
    )
}
