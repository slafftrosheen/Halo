package com.halo.sdk.driver

data class DeviceCapabilities(
    val microphone: Boolean,
    val speaker: Boolean,
    val buttons: Boolean,
    val display: Boolean,
    val camera: Boolean
)
