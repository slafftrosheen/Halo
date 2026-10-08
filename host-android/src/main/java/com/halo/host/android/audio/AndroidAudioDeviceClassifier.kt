package com.halo.host.android.audio

import android.media.AudioDeviceInfo
import com.halo.sdk.runtime.AudioDeviceKind

object AndroidAudioDeviceClassifier {
    fun kind(type: Int): AudioDeviceKind = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET -> AudioDeviceKind.HEADSET
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> AudioDeviceKind.USB_AUDIO
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioDeviceKind.HOST_EXTERNAL
        else -> AudioDeviceKind.UNKNOWN_EXTERNAL
    }
    fun isAccessory(type: Int) = kind(type) != AudioDeviceKind.UNKNOWN_EXTERNAL
}
