package com.halo.providers.android.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
) : com.halo.sdk.provider.PcmSpeechSynthesisProvider, TextToSpeech.OnInitListener {
    override val metadata = com.halo.sdk.provider.ProviderMetadata("provider.android.tts", "Android TTS", local = true)

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
    private var initGeneration = 0L

    private suspend fun ensureTts(): Boolean {
        val ready = withTimeoutOrNull(10_000L) { awaitTts() } == true
        if (!ready) synchronized(initLock) {
            initGeneration++
            tts?.shutdown()
            tts = null
            isInitialized = false
            initCallback = null
        }
        return ready
    }

    private suspend fun awaitTts(): Boolean = withContext(Dispatchers.Main) {
        if (isInitialized && tts != null) return@withContext true
        suspendCancellableCoroutine { continuation ->
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
                    val generation = ++initGeneration
                    tts = TextToSpeech(appContext) { status ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            synchronized(initLock) {
                                if (generation == initGeneration && tts != null) onInit(status)
                            }
                        }
                    }
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
        val chunks: List<ByteArray>,
        val ranges: List<com.halo.sdk.provider.SpeechPcmRange> = emptyList(),
    )

    /** Produces PCM without letting TextToSpeech own playback or routing. */
    override suspend fun synthesizeToPcm(request: SpeechRequest): com.halo.sdk.provider.PcmSpeech? {
        val pcm = synthesizeLegacyPcm(request) ?: return null
        val encoding = when (pcm.audioFormat) {
            AudioFormat.ENCODING_PCM_8BIT -> com.halo.sdk.provider.PcmEncoding.UNSIGNED_8
            AudioFormat.ENCODING_PCM_16BIT -> com.halo.sdk.provider.PcmEncoding.SIGNED_16_LE
            AudioFormat.ENCODING_PCM_FLOAT -> com.halo.sdk.provider.PcmEncoding.FLOAT_32_LE
            else -> return null
        }
        return com.halo.sdk.provider.PcmSpeech(pcm.sampleRate, pcm.channelCount, encoding, pcm.chunks, pcm.ranges)
    }

    private suspend fun synthesizeLegacyPcm(request: SpeechRequest): PcmSynthesis? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(90_000L) {
            if (request.text.isBlank() || request.text.length > 24_000) return@withTimeoutOrNull null
            val chunks = mutableListOf<ByteArray>()
            val ranges = mutableListOf<com.halo.sdk.provider.SpeechPcmRange>()
            var frames = 0L
            var format: PcmSynthesis? = null
            var bytes = 0L
            var offset = 0
            val maximum = TextToSpeech.getMaxSpeechInputLength() - 1
            while (offset < request.text.length) {
                var end = minOf(offset + maximum, request.text.length)
                if (end < request.text.length) {
                    val boundary = request.text.lastIndexOf(' ', end - 1)
                    if (boundary > offset) end = boundary
                    else if (request.text[end - 1].isHighSurrogate()) end--
                }
                val part = synthesizePcm(request.copy(text = request.text.substring(offset, end))) ?: return@withTimeoutOrNull null
                val first = format
                if (first != null && (part.sampleRate != first.sampleRate || part.channelCount != first.channelCount || part.audioFormat != first.audioFormat))
                    return@withTimeoutOrNull null
                val bytesPerSample = when (part.audioFormat) {
                    AudioFormat.ENCODING_PCM_8BIT -> 1
                    AudioFormat.ENCODING_PCM_16BIT -> 2
                    AudioFormat.ENCODING_PCM_FLOAT -> 4
                    else -> return@withTimeoutOrNull null
                }
                ranges.addAll(part.ranges.map { it.copy(start = it.start + offset, end = it.end + offset, frame = it.frame + frames) })
                frames += part.chunks.sumOf { it.size.toLong() } / (bytesPerSample * part.channelCount)
                format = part
                bytes += part.chunks.sumOf { it.size.toLong() }
                if (bytes > 32L * 1024 * 1024) return@withTimeoutOrNull null
                chunks.addAll(part.chunks)
                offset = end
                while (offset < request.text.length && request.text[offset].isWhitespace()) offset++
            }
            format?.copy(chunks = chunks, ranges = ranges)
        }
    }

    private suspend fun synthesizePcm(request: SpeechRequest): PcmSynthesis? =
        synthesizeMutex.withLock {
            if (request.text.isBlank() || request.text.length > TextToSpeech.getMaxSpeechInputLength()) return@withLock null
            if (!ensureTts() || !configureVoice(request)) return@withLock null
            suspendCancellableCoroutine { continuation ->
            val engine = tts
            if (engine == null || !isInitialized) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            val utteranceId = java.util.UUID.randomUUID().toString()
            val chunks = Collections.synchronizedList(mutableListOf<ByteArray>())
            val ranges = Collections.synchronizedList(mutableListOf<com.halo.sdk.provider.SpeechPcmRange>())
            var sampleRate = 0
            var audioFormat = 0
            var channelCount = 0
            val outputFile = File.createTempFile("halo-tts-", ".pcm", appContext.cacheDir)

            val finished = AtomicBoolean(false)
            val bytes = AtomicLong(0)
            fun finish(result: PcmSynthesis?) {
                if (!finished.compareAndSet(false, true)) return
                outputFile.delete()
                if (continuation.isActive) continuation.resume(result)
            }

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                    if(id == utteranceId && !finished.get() && start >= 0 && end > start && end <= request.text.length && frame >= 0)
                        ranges += com.halo.sdk.provider.SpeechPcmRange(start,end,frame.toLong())
                }
                override fun onStart(id: String?) = Unit
                override fun onStop(id: String?, interrupted: Boolean) { if (id == utteranceId) finish(null) }

                override fun onBeginSynthesis(id: String?, rate: Int, format: Int, channels: Int) {
                    if (id == utteranceId) {
                        sampleRate = rate
                        audioFormat = format
                        channelCount = channels
                    }
                }

                override fun onAudioAvailable(id: String?, audio: ByteArray) {
                    if (id == utteranceId && !finished.get()) {
                        if (bytes.addAndGet(audio.size.toLong()) > 32L * 1024 * 1024) {
                            finish(null)
                            engine.stop()
                        } else chunks += audio.copyOf()
                    }
                }

                override fun onDone(id: String?) {
                    if (id != utteranceId) return
                    val snapshot = synchronized(chunks) { chunks.toList() }
                    finish(
                        if (sampleRate > 0 && channelCount > 0 && snapshot.isNotEmpty()) {
                            PcmSynthesis(sampleRate, audioFormat, channelCount, snapshot, synchronized(ranges) { ranges.toList().sortedBy { it.frame } })
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
                finished.set(true)
                engine.stop()
                outputFile.delete()
            }
            }
        }

    suspend fun synthesize(request: SpeechRequest, onStarted: (() -> Unit)? = null): SpeechResult = withContext(Dispatchers.IO) {
        withTimeoutOrNull(120_000L) { speak(request, onStarted) } ?: SpeechResult(0, false)
    }

    private suspend fun speak(request: SpeechRequest, onStarted: (() -> Unit)?): SpeechResult =
        synthesizeMutex.withLock {
            if (request.text.isBlank() || request.text.length > TextToSpeech.getMaxSpeechInputLength()) return@withLock SpeechResult(0, false)
            val ready = ensureTts()
            if (!ready || !configureVoice(request)) return@withLock SpeechResult(0, false)
            suspendCancellableCoroutine { continuation ->
                val ttsEngine = tts
                if (ttsEngine == null || !isInitialized) {
                    continuation.resume(SpeechResult(0, false))
                    return@suspendCancellableCoroutine
                }

                val utteranceId = java.util.UUID.randomUUID().toString()
                val startTime = System.currentTimeMillis()

                val finished = AtomicBoolean(false)
                fun finish(success: Boolean) {
                    if (!finished.compareAndSet(false, true) || !continuation.isActive) return
                    val duration = if (success) System.currentTimeMillis() - startTime else 0L
                    continuation.resume(SpeechResult(duration, success))
                }

                ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStop(id: String?, interrupted: Boolean) { if (id == utteranceId) finish(false) }
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
                if (result == TextToSpeech.ERROR) finish(false)

                continuation.invokeOnCancellation {
                    finished.set(true)
                    ttsEngine.stop()
                }
            }
        }

    private fun configureVoice(request: SpeechRequest): Boolean {
        val engine = tts ?: return false
        if(engine.setSpeechRate((request.voiceSettings["speed"] ?: 1.0).coerceIn(0.6,1.5).toFloat()) == TextToSpeech.ERROR) return false
        val locale = request.language?.let(Locale::forLanguageTag) ?: Locale.getDefault()
        if (request.voiceSettings["_haloOfflineOnly"] == 1.0) {
            // Android TextToSpeech is not always offline. Only accept an engine
            // voice explicitly marked as NOT requiring a network connection;
            // refuse rather than sending children's/workspace text to a
            // vendor network voice. Never silently change language.
            val candidates = engine.voices.orEmpty().filter { voice ->
                !voice.isNetworkConnectionRequired &&
                    voice.locale.language.equals(locale.language, ignoreCase = true)
            }
            val matched = if(request.voiceId!=null)
                candidates.firstOrNull { it.name == request.voiceId }
            else candidates.firstOrNull { it.locale == locale } ?: candidates.firstOrNull()
            return matched != null && engine.setVoice(matched) != TextToSpeech.ERROR
        }
        request.voiceId?.let { id ->
            val voice = engine.voices?.firstOrNull { it.name == id } ?: return false
            return engine.setVoice(voice) != TextToSpeech.ERROR
        }
        val status = engine.setLanguage(locale)
        if (status >= TextToSpeech.LANG_AVAILABLE) return true
        return request.language == null && engine.setLanguage(Locale.US) >= TextToSpeech.LANG_AVAILABLE
    }

    override fun stream(text: String): Flow<AudioFrame> = emptyFlow()

    override suspend fun health(): Boolean = true

    override suspend fun close() {
        // Do not acquire synthesizeMutex here: synthesize() may be waiting for
        // an Android TTS callback while holding it. stop() is intentionally
        // allowed to trigger that callback and release the suspended call.
        synchronized(initLock) {
            initGeneration++
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
            initCallback?.invoke(false)
            initCallback = null
        }
    }

    private companion object {
        private const val TAG = "AndroidTTSProvider"
    }
}
