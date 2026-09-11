package com.halo.providers.android.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.halo.sdk.host.AudioFrame
import com.halo.sdk.provider.SpeechRequest
import com.halo.sdk.provider.SpeechResult
import com.halo.sdk.provider.SpeechSynthesisProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Android TTS host implementation.
 *
 * Speech is tagged as assistant speech so Android's normal Bluetooth audio policy
 * can route it to the currently selected Classic Bluetooth output (including the
 * HeyCyan glasses). The HeyCyan BLE speaker-switch command is prepared by the
 * runtime SpeechOutputRouter before this provider is invoked.
 */
class AndroidTTSProvider(
    private val context: Context
) : SpeechSynthesisProvider, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            isInitialized = true
        }
    }

    override suspend fun synthesize(request: SpeechRequest): SpeechResult =
        suspendCancellableCoroutine { continuation ->
            val ttsEngine = tts
            if (ttsEngine == null || !isInitialized) {
                continuation.resume(SpeechResult(0, false))
                return@suspendCancellableCoroutine
            }

            val utteranceId = java.util.UUID.randomUUID().toString()
            val startTime = System.currentTimeMillis()

            ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}

                override fun onDone(id: String?) {
                    if (id == utteranceId && continuation.isActive) {
                        val duration = System.currentTimeMillis() - startTime
                        continuation.resume(SpeechResult(duration, true))
                    }
                }

                override fun onError(id: String?) {
                    if (id == utteranceId && continuation.isActive) {
                        continuation.resume(SpeechResult(0, false))
                    }
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId && continuation.isActive) {
                        continuation.resume(SpeechResult(0, false))
                    }
                }
            })

            val result = ttsEngine.speak(request.text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            if (result == TextToSpeech.ERROR && continuation.isActive) {
                continuation.resume(SpeechResult(0, false))
            }

            continuation.invokeOnCancellation {
                ttsEngine.stop()
            }
        }

    override fun stream(text: String): Flow<AudioFrame> = emptyFlow()

    override suspend fun health(): Boolean = isInitialized

    override suspend fun close() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
