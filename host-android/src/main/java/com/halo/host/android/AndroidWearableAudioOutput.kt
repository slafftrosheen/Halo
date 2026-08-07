package com.halo.host.android

import android.content.Context
import com.halo.sdk.host.AudioOutput

class AndroidWearableAudioOutput : AudioOutput {
    override suspend fun speak(text: String) {
        // Implementation provided by SpeechCapability
    }
}
