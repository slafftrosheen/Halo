package com.halo.host.android.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.TtsCompleted
import com.halo.sdk.runtime.TtsFailed
import com.halo.sdk.runtime.TtsRequested
import com.halo.sdk.runtime.TtsStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class AndroidSpeaker(
    private val context: Context,
    private val eventBus: EventBus
) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val scope = CoroutineScope(Dispatchers.IO)
    private var currentTextLength = 0

    fun start() {
        tts = TextToSpeech(context.applicationContext, this)
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
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    scope.launch {
                        eventBus.publish(TtsStarted(textLength = currentTextLength))
                    }
                }

                override fun onDone(utteranceId: String?) {
                    scope.launch {
                        eventBus.publish(TtsCompleted())
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    scope.launch {
                        eventBus.publish(TtsFailed(message = "Android TTS error"))
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    scope.launch {
                        eventBus.publish(TtsFailed(message = "Android TTS error code $errorCode"))
                    }
                }
            })
            isReady = true
        }
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        if (isReady) {
            currentTextLength = text.length
            val utteranceId = "halo-tts-${UUID.randomUUID()}"
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }

    fun stop() {
        tts?.stop()
        _stateStop()
    }

    private fun _stateStop() {
        scope.launch {
            eventBus.publish(TtsCompleted())
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
