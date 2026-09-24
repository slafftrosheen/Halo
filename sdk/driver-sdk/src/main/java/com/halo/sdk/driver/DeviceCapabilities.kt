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
        /** Device with no advertised capabilities. */
        val NONE = DeviceCapabilities(
            microphone = false,
            speaker = false,
            buttons = false,
            display = false,
            camera = false
        )
    }
}
