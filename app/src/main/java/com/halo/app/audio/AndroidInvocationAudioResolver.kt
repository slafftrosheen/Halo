package com.halo.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.halo.sdk.runtime.AudioDeviceKind
import com.halo.sdk.runtime.SpeechInputRoute

class AndroidInvocationAudioResolver(private val context: Context, private val registry: AndroidAudioAccessoryRegistry? = null, private val enabled: () -> Boolean = { true }) {
    fun resolve(inputDeviceId: Int?): SpeechInputRoute {
        if (inputDeviceId == null || !enabled()) return SpeechInputRoute.Phone

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val device = devices.find { it.id == inputDeviceId } ?: return SpeechInputRoute.Phone

        if (device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) return SpeechInputRoute.Phone
        val kind = com.halo.host.android.audio.AndroidAudioDeviceClassifier.kind(device.type)
        registry?.refresh()
        val accessory = registry?.findInput(device.id)
        return SpeechInputRoute.Device(
            deviceId = accessory?.id ?: "platform-audio-session:${device.id}",
            platformEndpointId = device.id,
            displayName = device.productName?.toString(),
            kind = kind
        )
    }
}
