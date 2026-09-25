package com.halo.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.halo.sdk.runtime.AudioDeviceKind
import com.halo.sdk.runtime.SpeechInputRoute

class AndroidInvocationAudioResolver(private val context: Context) {
    fun resolve(inputDeviceId: Int?): SpeechInputRoute {
        if (inputDeviceId == null) return SpeechInputRoute.Phone

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val device = devices.find { it.id == inputDeviceId } ?: return SpeechInputRoute.Phone

        val kind = when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET -> AudioDeviceKind.HEADSET // Map to generic headset initially
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET -> AudioDeviceKind.USB_AUDIO
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioDeviceKind.HOST_EXTERNAL
            else -> AudioDeviceKind.UNKNOWN_EXTERNAL
        }

        // We could look up known MAC addresses here to distinguish EARBUDS or WEARABLE.
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            device.address.takeIf { it.isNotBlank() }
        } else {
            null
        }
        
        return SpeechInputRoute.Device(
            deviceId = address ?: device.id.toString(),
            platformEndpointId = device.id,
            displayName = device.productName?.toString(),
            kind = kind
        )
    }
}
