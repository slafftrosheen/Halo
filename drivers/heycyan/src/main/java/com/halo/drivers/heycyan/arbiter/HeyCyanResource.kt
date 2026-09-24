package com.halo.drivers.heycyan.arbiter

/**
 * Hardware and communication resources on HeyCyan glasses.
 *
 * Models physical conflicts identified in hardware forensics:
 * - CAMERA and VIDEO_RECORDER conflict on the co-processor sensor.
 * - BLE_BULK_TRANSFER (e.g. thumbnail transfer) and WIFI_TRANSFER conflict on media bus.
 * - OTA locks all resources until firmware flash completes.
 * - Non-conflicting operations (e.g., CONTROL_CHANNEL queries while AUDIO_RECORDER runs)
 *   are permitted concurrently.
 */
enum class HeyCyanResource {
    CONTROL_CHANNEL,
    CAMERA,
    BLE_BULK_TRANSFER,
    WIFI_TRANSFER,
    VIDEO_RECORDER,
    AUDIO_RECORDER,
    OTA;

    fun conflictsWith(other: HeyCyanResource): Boolean {
        if (this == other) return true
        if (this == OTA || other == OTA) return true
        if ((this == CAMERA && other == VIDEO_RECORDER) || (this == VIDEO_RECORDER && other == CAMERA)) return true
        if ((this == BLE_BULK_TRANSFER && other == WIFI_TRANSFER) || (this == WIFI_TRANSFER && other == BLE_BULK_TRANSFER)) return true
        if ((this == AUDIO_RECORDER && other == VIDEO_RECORDER) || (this == VIDEO_RECORDER && other == AUDIO_RECORDER)) return true
        return false
    }
}
