package com.halo.app.audio

import android.media.AudioDeviceInfo

/** Admit the existing microphone owner only against this currently verified playback route. */
internal fun speechReplyCaptureSafe(state: HaloAudioSessionState, transactionId: String,
    echoCancellationActive: Boolean): Boolean {
    val speaking = state as? HaloAudioSessionState.Speaking ?: return false
    if (speaking.transactionId != transactionId || speaking.route.androidDeviceId == null) return false
    return when (speaking.route.androidDeviceType) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLE_HEADSET -> true
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> echoCancellationActive
        else -> false
    }
}
