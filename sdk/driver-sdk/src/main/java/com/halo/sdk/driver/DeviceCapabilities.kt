package com.halo.sdk.driver

/**
 * Declares the capabilities a device supports.
 *
 * The runtime uses this to determine what operations are available without
 * attempting commands that will fail.
 *
 * Extended from the original to cover all capability types discovered
 * during HeyCyan forensic analysis.
 */
data class DeviceCapabilities(
    val microphone: Boolean,
    val speaker: Boolean,
    val buttons: Boolean,
    val display: Boolean,
    val camera: Boolean,
    val videoRecording: Boolean = false,
    val audioRecording: Boolean = false,
    val mediaLibrary: Boolean = false,
    val wirelessTransfer: Boolean = false,
    val aiPhoto: Boolean = false,
    val liveMicrophone: Boolean = false
) {
    companion object {
        /** Device with no capabilities (placeholder). */
        val NONE = DeviceCapabilities(
            microphone = false,
            speaker = false,
            buttons = false,
            display = false,
            camera = false
        )

        /** HeyCyan smart glasses capabilities. */
        val HEYCYAN_GLASSES = DeviceCapabilities(
            microphone = false,       // No live BLE mic (UNSUPPORTED_BY_DEVICE)
            speaker = false,          // No speaker on glasses
            buttons = true,           // Single/double/long press
            display = false,          // No display
            camera = true,            // Photo capture
            videoRecording = true,    // Video recording (start/stop)
            audioRecording = true,    // On-device audio recording
            mediaLibrary = true,      // Media inventory + sync
            wirelessTransfer = true,  // WiFi hotspot transfer
            aiPhoto = false,          // BLOCKED_BY_UNKNOWN_PROTOCOL
            liveMicrophone = false    // UNSUPPORTED_BY_DEVICE
        )
    }
}
