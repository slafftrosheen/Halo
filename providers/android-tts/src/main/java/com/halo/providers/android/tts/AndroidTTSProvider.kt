package com.halo.providers.android.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.halo.sdk.host.AudioFrame
import com.halo.sdk.provider.SpeechRequest
import com.halo.sdk.provider.SpeechResult
import com.halo.sdk.provider.SpeechSynthesisProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import android.os.Bundle
import java.io.File
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android TTS host implementation.
 *
 * HeyCyan speaker selection has two layers:
 * 1. The BLE host sends the verified HeyCyan speaker-switch command.
 * 2. Android routes the actual TTS stream through its Bluetooth audio path.
 *
 * TTS and route verification share USAGE_ASSISTANT/CONTENT_TYPE_SPEECH.
 * Communication-device APIs are intentionally not used for normal assistant
 * output; an HFP-only route needs its own explicit compatibility path.
 */
class AndroidTTSProvider(
    private val context: Context,
    private val assistantSpeechAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
) : SpeechSynthesisProvider, TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext

    /**
     * TextToSpeech owns one process-wide utterance listener per engine.
     * Serialize synthesize() so concurrent calls cannot replace that listener,
     * restore a previous route while another utterance is playing, or leave
     * QUEUE_ADD utterances associated with the wrong route state.
     */
    private val synthesizeMutex = Mutex()

    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false
    private val initLock = Any()
    private var initCallback: ((Boolean) -> Unit)? = null

    private suspend fun ensureTts(): Boolean {
        if (isInitialized && tts != null) return true
        return suspendCancellableCoroutine { continuation ->
            synchronized(initLock) {
                if (isInitialized && tts != null) {
                    continuation.resume(true)
                    return@suspendCancellableCoroutine
                }
                val prev = initCallback
                initCallback = { ok ->
                    prev?.invoke(ok)
                    if (continuation.isActive) continuation.resume(ok)
                }
                if (tts == null) {
                    Log.i(TAG, "Initializing TextToSpeech lazily on demand")
                    tts = TextToSpeech(appContext, this)
                }
            }
        }
    }

    override fun onInit(status: Int) {
        synchronized(initLock) {
            val ok = status == TextToSpeech.SUCCESS
            if (ok) {
                tts?.language = Locale.US
                tts?.setAudioAttributes(
                    assistantSpeechAudioAttributes
                )
                isInitialized = true
            }
            val cb = initCallback
            initCallback = null
            cb?.invoke(ok)
        }
    }

    override fun stopCurrent() {
        tts?.stop()
    }

    override suspend fun synthesize(request: SpeechRequest): SpeechResult = synthesize(request, null)

    data class PcmSynthesis(
        val sampleRate: Int,
        val audioFormat: Int,
        val channelCount: Int,
        val chunks: List<ByteArray>
    )

    /** Produces PCM without letting TextToSpeech own playback or routing. */
    suspend fun synthesizeToPcm(request: SpeechRequest): PcmSynthesis? = withContext(Dispatchers.IO) {
        synthesizeMutex.withLock {
            if (!ensureTts()) return@withLock null
            suspendCancellableCoroutine { continuation ->
            val engine = tts
            if (engine == null || !isInitialized) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            val utteranceId = java.util.UUID.randomUUID().toString()
            val chunks = Collections.synchronizedList(mutableListOf<ByteArray>())
            var sampleRate = 0
            var audioFormat = 0
            var channelCount = 0
            val outputFile = File.createTempFile("halo-tts-", ".pcm", appContext.cacheDir)

            fun finish(result: PcmSynthesis?) {
                outputFile.delete()
                if (continuation.isActive) continuation.resume(result)
            }

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit

                override fun onBeginSynthesis(id: String?, rate: Int, format: Int, channels: Int) {
                    if (id == utteranceId) {
                        sampleRate = rate
                        audioFormat = format
                        channelCount = channels
                    }
                }

                override fun onAudioAvailable(id: String?, audio: ByteArray) {
                    if (id == utteranceId) chunks += audio.copyOf()
                }

                override fun onDone(id: String?) {
                    if (id != utteranceId) return
                    val snapshot = synchronized(chunks) { chunks.toList() }
                    finish(
                        if (sampleRate > 0 && channelCount > 0 && snapshot.isNotEmpty()) {
                            PcmSynthesis(sampleRate, audioFormat, channelCount, snapshot)
                        } else null
                    )
                }

                override fun onError(id: String?) {
                    if (id == utteranceId) finish(null)
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId) finish(null)
                }
            })

            val result = engine.synthesizeToFile(request.text, Bundle(), outputFile, utteranceId)
            if (result == TextToSpeech.ERROR) finish(null)
            continuation.invokeOnCancellation {
                engine.stop()
                outputFile.delete()
            }
            }
        }
    }

    suspend fun synthesize(request: SpeechRequest, onStarted: (() -> Unit)? = null): SpeechResult =
        synthesizeMutex.withLock {
            val ready = ensureTts()
            if (!ready) return@withLock SpeechResult(0, false)
            suspendCancellableCoroutine { continuation ->
                val ttsEngine = tts
                if (ttsEngine == null || !isInitialized) {
                    continuation.resume(SpeechResult(0, false))
                    return@suspendCancellableCoroutine
                }

                val utteranceId = java.util.UUID.randomUUID().toString()
                val startTime = System.currentTimeMillis()

                fun finish(success: Boolean) {
                    if (!continuation.isActive) return
                    val duration = if (success) System.currentTimeMillis() - startTime else 0L
                    continuation.resume(SpeechResult(duration, success))
                }

                ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {
                        if (id == utteranceId) {
                            Log.d(TAG, "TTS started utterance=$utteranceId")
                            onStarted?.invoke()
                        }
                    }

                    override fun onDone(id: String?) {
                        if (id == utteranceId) finish(true)
                    }

                    override fun onError(id: String?) {
                        if (id == utteranceId) finish(false)
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        if (id == utteranceId) {
                            Log.w(TAG, "TTS failed utterance=$utteranceId errorCode=$errorCode")
                            finish(false)
                        }
                    }
                })

                val result = ttsEngine.speak(
                    request.text,
                    TextToSpeech.QUEUE_ADD,
                    null,
                    utteranceId
                )
                if (result == TextToSpeech.ERROR && continuation.isActive) {
                    continuation.resume(SpeechResult(0, false))
                }

                continuation.invokeOnCancellation {
                    ttsEngine.stop()
                }
            }
        }

    override fun stream(text: String): Flow<AudioFrame> = emptyFlow()

    override suspend fun health(): Boolean = true

    override suspend fun close() {
        // Do not acquire synthesizeMutex here: synthesize() may be waiting for
        // an Android TTS callback while holding it. stop() is intentionally
        // allowed to trigger that callback and release the suspended call.
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    private companion object {
        private const val TAG = "AndroidTTSProvider"
    }
}
