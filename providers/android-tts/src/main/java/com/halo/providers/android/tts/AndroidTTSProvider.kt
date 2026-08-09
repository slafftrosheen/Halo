package com.halo.providers.android.tts

import android.content.Context
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

class AndroidTTSProvider(
    private val context: Context
) : SpeechSynthesisProvider, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isInitialized = true
        }
    }

    override suspend fun synthesize(request: SpeechRequest): SpeechResult = suspendCancellableCoroutine { continuation ->
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
        })

        val result = ttsEngine.speak(request.text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        if (result == TextToSpeech.ERROR && continuation.isActive) {
            continuation.resume(SpeechResult(0, false))
        }

        continuation.invokeOnCancellation {
            ttsEngine.stop()
        }
    }

    override fun stream(text: String): Flow<AudioFrame> {
        // Android TTS plays directly to output, doesn't easily return PCM stream without synthesizing to file first.
        return emptyFlow()
    }

    override suspend fun health(): Boolean {
        return isInitialized
    }

    override suspend fun close() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
