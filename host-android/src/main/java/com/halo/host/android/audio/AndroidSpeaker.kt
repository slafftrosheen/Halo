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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Android TTS output for the phone host.
 *
 * Starting the speaker only subscribes to TTS requests. The Android TTS engine
 * itself is initialized on the first real TtsRequested event so application
 * startup remains silent and does not touch the audio output stack.
 */
class AndroidSpeaker(
    private val context: Context,
    private val eventBus: EventBus
) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var eventJob: Job? = null
    private var currentTextLength = 0
    private var pendingText: String? = null
    private var activeUtteranceId: String? = null

    fun start() {
        if (eventJob?.isActive == true) return

        eventJob = scope.launch {
            eventBus.events().collect { event ->
                if (event is TtsRequested) {
                    speak(event.text)
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            isReady = false
            val pending = pendingText
            pendingText = null
            if (!pending.isNullOrBlank()) {
                scope.launch {
                    eventBus.publish(TtsFailed(message = "Android TTS initialization failed"))
                }
            }
            return
        }

        tts?.language = Locale.getDefault()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == null || utteranceId != activeUtteranceId) return
                scope.launch {
                    eventBus.publish(TtsStarted(textLength = currentTextLength))
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == null || utteranceId != activeUtteranceId) return
                activeUtteranceId = null
                scope.launch {
                    eventBus.publish(TtsCompleted())
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == null || utteranceId != activeUtteranceId) return
                activeUtteranceId = null
                scope.launch {
                    eventBus.publish(TtsFailed(message = "Android TTS error"))
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == null || utteranceId != activeUtteranceId) return
                activeUtteranceId = null
                scope.launch {
                    eventBus.publish(TtsFailed(message = "Android TTS error code $errorCode"))
                }
            }
        })
        isReady = true

        pendingText?.also {
            pendingText = null
            speak(it)
        }
    }

    private fun speak(text: String) {
        if (text.isBlank()) return

        if (!isReady) {
            pendingText = text
            if (tts == null) {
                tts = TextToSpeech(context.applicationContext, this)
            }
            return
        }

        val utteranceId = "halo-tts-${UUID.randomUUID()}"
        currentTextLength = text.length
        activeUtteranceId = utteranceId

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        pendingText = null
        val wasActive = activeUtteranceId != null
        activeUtteranceId = null
        tts?.stop()
        if (wasActive) {
            scope.launch {
                eventBus.publish(TtsCompleted())
            }
        }
    }

    fun shutdown() {
        eventJob?.cancel()
        eventJob = null
        pendingText = null
        activeUtteranceId = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
