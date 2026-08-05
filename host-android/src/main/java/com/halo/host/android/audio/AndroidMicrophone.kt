package com.halo.host.android.audio

import android.content.Context
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.SpeechRecognized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AndroidMicrophone(
    private val context: Context,
    private val eventBus: EventBus
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun simulateSpeech(text: String) {
        scope.launch {
            eventBus.publish(SpeechRecognized(
                timestamp = System.currentTimeMillis(),
                text = text,
                locale = "en-US"
            ))
        }
    }
}
