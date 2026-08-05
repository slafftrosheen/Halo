package com.halo.host.android

import android.content.Context
import com.halo.host.android.audio.AndroidMicrophone
import com.halo.host.android.audio.AndroidSpeaker
import com.halo.sdk.runtime.EventBus

class HaloHost(
    context: Context,
    eventBus: EventBus
) {
    val microphone = AndroidMicrophone(context, eventBus)
    val speaker = AndroidSpeaker(context, eventBus)

    fun start() {
        speaker.start()
    }

    fun stop() {
        speaker.stop()
    }
}
