package com.halo.drivers.heycyan.protocol

/**
 * The types of button interactions supported by HeyCyan hardware.
 */
enum class ButtonType {
    SINGLE_TAP,
    DOUBLE_TAP,
    LONG_PRESS,
    VOICE_BUTTON,
    CAMERA_BUTTON
}

/**
 * Notifications and callbacks emitted by the HeyCyan glasses over BLE.
 */
sealed interface HeyCyanEvent {
    // Hardware Inputs
    data class ButtonPressed(val type: ButtonType) : HeyCyanEvent
    
    // Status Updates
    data class BatteryUpdated(val level: Int, val isCharging: Boolean) : HeyCyanEvent
    data class MediaCountsUpdated(val photos: Int, val videos: Int, val audio: Int) : HeyCyanEvent
    data class DeviceInfoReceived(val hardwareVersion: String, val firmwareVersion: String) : HeyCyanEvent
    
    // Capture Callbacks
    data object PhotoCaptured : HeyCyanEvent
    data object RecordingStarted : HeyCyanEvent
    data object RecordingStopped : HeyCyanEvent
    
    // Connectivity
    data class WiFiHotspotReady(val ssid: String, val pass: String) : HeyCyanEvent
}
