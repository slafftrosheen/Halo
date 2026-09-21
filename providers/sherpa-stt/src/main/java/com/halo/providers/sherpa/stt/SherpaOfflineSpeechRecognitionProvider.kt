package com.halo.providers.sherpa.stt

import android.os.SystemClock
import android.util.Log
import com.halo.sdk.host.AudioBuffer
import com.halo.sdk.provider.BufferedSpeechRecognitionProvider
import com.halo.sdk.provider.ProviderHealth
import com.halo.sdk.provider.RecognitionResult
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.sdk.runtime.NativeResourceKind
import com.halo.sdk.runtime.NativeResourceOwner
import com.halo.sdk.runtime.NativeResourceState
import com.halo.sdk.runtime.NativeNextUseHint
import com.halo.sdk.runtime.RecognitionQuality
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Fully local, buffered ASR backed by sherpa-onnx. */
class SherpaOfflineSpeechRecognitionProvider(
    private val modelDirectory: File,
    private val language: String = "en",
    private val numThreads: Int = 2,
    private val nativeResourceCoordinator: NativeResourceCoordinator? = null
) : BufferedSpeechRecognitionProvider, NativeResourceOwner {

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val TAG = "HaloSTT"
        private const val MULTILINGUAL_TAIL_PADDINGS = 300

        fun isInstalled(modelDirectory: File): Boolean =
            SherpaSpeechModelCatalog.isInstalled(modelDirectory, SherpaSpeechModelCatalog.whisperTiny)
    }

    override val metadata = com.halo.sdk.provider.ProviderMetadata(
        id = "speech.sherpa_onnx.whisper_tiny",
        supportsStreaming = false
    )

    override val nativeResourceKind: NativeResourceKind = NativeResourceKind.HEAVY_ASR

    override val nativeResourceId = "whisper:" + java.util.UUID.randomUUID().toString()
    private val closeRequested = AtomicBoolean(false)
    @Volatile private var recognizer: OfflineRecognizer? = null
    private val recognizerLock = Any()
    private val recognitionMutex = Mutex()

    /*
     * sherpa-onnx 1.13.8 can return a decoded result and then block while
     * OfflineStream.release() destroys the native stream on the decoding thread.
     * Never keep the speech transaction behind native teardown: the transcript
     * is already fully materialized by getResult().
     *
     * Cleanup is deliberately single-owner and out-of-band. If one native
     * delete gets stuck, subsequent stream objects are left to sherpa's own
     * finalizer instead of growing an unbounded cleanup queue.
     */
    private val streamCleanupBusy = AtomicBoolean(false)
    private val streamCleanupExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HaloSTT-Cleanup").apply { isDaemon = true }
    }

    private val whisperLanguage = language.trim().ifBlank { "en" }
        .takeUnless { it.equals("auto", ignoreCase = true) }
        .orEmpty()

    override suspend fun health(): Boolean = isInstalled(modelDirectory)

    override suspend fun healthDetails(): ProviderHealth {
        val missing = SherpaSpeechModelCatalog.missingFiles(modelDirectory, SherpaSpeechModelCatalog.whisperTiny)
        return ProviderHealth(
            healthy = missing.isEmpty(),
            message = if (missing.isEmpty()) "Sherpa-ONNX Whisper tiny multilingual model is installed"
            else "Sherpa-ONNX Whisper tiny model is incomplete; missing: ${missing.joinToString()}"
        )
    }

    override suspend fun recognize(audio: AudioBuffer): RecognitionResult = recognitionMutex.withLock {
        check(!closeRequested.get()) { "Recognizer is closed" }
        require(audio.frames.isNotEmpty()) { "Cannot recognize an empty audio buffer" }
        check(isInstalled(modelDirectory)) { "Sherpa-ONNX model is not installed at ${modelDirectory.absolutePath}" }
        require(audio.frames.all { it.sampleRate == SAMPLE_RATE && it.channels == 1 }) {
            "Sherpa-ONNX provider requires mono ${SAMPLE_RATE} Hz PCM audio"
        }

        val samples = FloatArray(audio.frames.sumOf { it.samples.size })
        var offset = 0
        var peak = 0
        audio.frames.forEach { frame ->
            frame.samples.forEach { sample ->
                samples[offset++] = sample / 32768.0f
                peak = maxOf(peak, kotlin.math.abs(sample.toInt()))
            }
        }

        val audioDurationMs = samples.size * 1000L / SAMPLE_RATE
        Log.i(
            TAG,
            "STT_BEGIN frames=${audio.frames.size} samples=${samples.size} " +
                "durationMs=$audioDurationMs peak=$peak language=${whisperLanguage.ifBlank { "auto" }}"
        )

        var stage = "resource_acquire"
        val startedAt = SystemClock.elapsedRealtime()
        try {
            check(nativeResourceCoordinator?.acquire(this) ?: true) {
                "Local Whisper recognition deferred: another heavyweight native model is busy"
            }

            val wasResident = recognizer != null
            nativeResourceCoordinator?.mark(
                this,
                if (wasResident) NativeResourceState.IN_USE else NativeResourceState.LOADING,
                if (wasResident) "recognition started" else "loading Whisper recognizer"
            )

            stage = "recognizer_load"
            val loadStartedAt = SystemClock.elapsedRealtime()
            val localRecognizer = ensureRecognizer()
            Log.i(
                TAG,
                "RECOGNIZER_READY resident=$wasResident elapsedMs=${SystemClock.elapsedRealtime() - loadStartedAt}"
            )

            nativeResourceCoordinator?.mark(this, NativeResourceState.IN_USE, "recognition active")

            stage = "stream_create"
            val stream = localRecognizer.createStream()
            return try {
                stage = "decode"
                val decodeStartedAt = SystemClock.elapsedRealtime()
                Log.i(TAG, "DECODE_BEGIN durationMs=$audioDurationMs")
                stream.acceptWaveform(samples, SAMPLE_RATE)
                localRecognizer.decode(stream)

                stage = "result"
                val result = localRecognizer.getResult(stream)
                val text = result.text.trim()
                Log.i(
                    TAG,
                    "STT_DONE decodeMs=${SystemClock.elapsedRealtime() - decodeStartedAt} " +
                        "totalMs=${SystemClock.elapsedRealtime() - startedAt} chars=${text.length}"
                )
                nativeResourceCoordinator?.hint(NativeNextUseHint.EXPECT_LLM)

                // getResult() has copied the transcript into Kotlin. From this
                // point on, native stream destruction must not delay handoff to
                // SpeechRecognized/cognition.
                RecognitionResult(
                    text = text,
                    language = whisperLanguage.ifBlank { "auto" },
                    confidence = null,
                    quality = RecognitionQuality(
                        audioDurationMs = audioDurationMs,
                        peakAmplitude = peak
                    )
                )
            } finally {
                releaseStreamOutOfBand(stream)
            }
        } catch (error: Throwable) {
            Log.e(
                TAG,
                "STT_FAILED stage=$stage elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
                error
            )
            nativeResourceCoordinator?.release(this)
            throw error
        }
    }

    override suspend fun releaseIfIdle(): Boolean {
        if (streamCleanupBusy.get()) return false
        if (!recognitionMutex.tryLock()) return false
        return try {
            synchronized(recognizerLock) {
                recognizer?.release()
                recognizer = null
            }
            true
        } finally {
            recognitionMutex.unlock()
        }
    }

    override suspend fun close() {
        closeRequested.set(true)
        val released = recognitionMutex.withLock {
            if (streamCleanupBusy.get()) false else {
                synchronized(recognizerLock) { recognizer?.release(); recognizer = null }
                true
            }
        }
        // JNI deletion is not interruptible. Its owner releases the recognizer after
        // stream deletion returns; never unregister an object that still owns native memory.
        streamCleanupExecutor.shutdown()
        if (released) nativeResourceCoordinator?.unregister(this)
    }

    private fun releaseStreamOutOfBand(stream: OfflineStream) {
        if (!streamCleanupBusy.compareAndSet(false, true)) {
            Log.w(
                TAG,
                "STREAM_RELEASE_DEFERRED cleanup already busy; native finalizer will own this stream"
            )
            return
        }

        streamCleanupExecutor.execute {
            val cleanupStartedAt = SystemClock.elapsedRealtime()
            Log.i(TAG, "STREAM_RELEASE_BEGIN")
            try {
                stream.release()
                Log.i(
                    TAG,
                    "STREAM_RELEASE_DONE elapsedMs=${SystemClock.elapsedRealtime() - cleanupStartedAt}"
                )
                nativeResourceCoordinator?.let { coordinator ->
                    runBlocking {
                        coordinator.mark(
                            this@SherpaOfflineSpeechRecognitionProvider,
                            NativeResourceState.RESIDENT,
                            "recognition idle"
                        )
                    }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "STREAM_RELEASE_FAILED", error)
            } finally {
                streamCleanupBusy.set(false)
                if (closeRequested.get()) {
                    synchronized(recognizerLock) { recognizer?.release(); recognizer = null }
                    nativeResourceCoordinator?.let { coordinator -> runBlocking { coordinator.unregister(this@SherpaOfflineSpeechRecognitionProvider) } }
                }
            }
        }
    }

    private fun ensureRecognizer(): OfflineRecognizer {
        synchronized(recognizerLock) {
            recognizer?.let { return it }
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = File(modelDirectory, "tiny-encoder.int8.onnx").absolutePath,
                        decoder = File(modelDirectory, "tiny-decoder.int8.onnx").absolutePath,
                        language = whisperLanguage,
                        task = "transcribe",
                        // sherpa-onnx recommends 300 tail frames for multilingual Whisper.
                        // Its Kotlin default is 1000, which makes short phone utterances
                        // unnecessarily expensive on CPU.
                        tailPaddings = MULTILINGUAL_TAIL_PADDINGS
                    ),
                    tokens = File(modelDirectory, "tiny-tokens.txt").absolutePath,
                    modelType = "whisper",
                    numThreads = numThreads,
                    provider = "cpu"
                ),
                decodingMethod = "greedy_search",
                maxActivePaths = 1
            )
            return OfflineRecognizer(config = config).also { recognizer = it }
        }
    }
}
