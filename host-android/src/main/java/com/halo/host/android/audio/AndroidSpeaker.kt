package com.halo.host.android.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.TtsRequested
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class AndroidSpeaker(
    private val context: Context,
    private val eventBus: EventBus
) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        tts = TextToSpeech(context, this)
        scope.launch {
            eventBus.events().collect { event ->
                if (event is TtsRequested) {
                    speak(event.text)
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isReady = true
        }
    }

    private fun speak(text: String) {
        if (isReady) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        }
    }

    fun stop() {
        tts?.stop()
        tts?.shutdown()
    }
}
