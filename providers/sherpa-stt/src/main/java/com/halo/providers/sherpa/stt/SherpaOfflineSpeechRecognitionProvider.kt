package com.halo.providers.sherpa.stt

import com.halo.sdk.host.AudioBuffer
import com.halo.sdk.provider.BufferedSpeechRecognitionProvider
import com.halo.sdk.provider.ProviderHealth
import com.halo.sdk.provider.RecognitionResult
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.sdk.runtime.NativeResourceKind
import com.halo.sdk.runtime.NativeResourceOwner
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Fully local, buffered ASR backed by sherpa-onnx. */
class SherpaOfflineSpeechRecognitionProvider(
    private val modelDirectory: File,
    private val language: String = "",
    private val numThreads: Int = 2,
    private val nativeResourceCoordinator: NativeResourceCoordinator? = null
) : BufferedSpeechRecognitionProvider, NativeResourceOwner {

    companion object {
        private const val SAMPLE_RATE = 16_000

        fun isInstalled(modelDirectory: File): Boolean =
            SherpaSpeechModelCatalog.isInstalled(
                modelDirectory,
                SherpaSpeechModelCatalog.whisperTiny
            )
    }

    override val metadata = com.halo.sdk.provider.ProviderMetadata(
        id = "speech.sherpa_onnx.whisper_tiny",
        supportsStreaming = false
    )

    override val nativeResourceKind: NativeResourceKind = NativeResourceKind.HEAVY_ASR

    @Volatile
    private var recognizer: OfflineRecognizer? = null
    private val recognizerLock = Any()
    private val recognitionMutex = Mutex()

    override suspend fun health(): Boolean = isInstalled(modelDirectory)

    override suspend fun healthDetails(): ProviderHealth {
        val missing = SherpaSpeechModelCatalog.missingFiles(
            modelDirectory,
            SherpaSpeechModelCatalog.whisperTiny
        )
        return ProviderHealth(
            healthy = missing.isEmpty(),
            message = if (missing.isEmpty()) {
                "Sherpa-ONNX Whisper tiny multilingual model is installed"
            } else {
                "Sherpa-ONNX Whisper tiny model is incomplete; missing: ${missing.joinToString()}"
            }
        )
    }

    override suspend fun recognize(audio: AudioBuffer): RecognitionResult = recognitionMutex.withLock {
        require(audio.frames.isNotEmpty()) { "Cannot recognize an empty audio buffer" }
        check(isInstalled(modelDirectory)) {
            "Sherpa-ONNX model is not installed at ${modelDirectory.absolutePath}"
        }
        require(audio.frames.all { it.sampleRate == SAMPLE_RATE && it.channels == 1 }) {
            "Sherpa-ONNX provider requires mono ${SAMPLE_RATE} Hz PCM audio"
        }
        check(nativeResourceCoordinator?.acquire(this) ?: true) {
            "Local Whisper recognition deferred: another heavyweight native model is busy"
        }

        val samples = FloatArray(audio.frames.sumOf { it.samples.size })
        var offset = 0
        audio.frames.forEach { frame ->
            frame.samples.forEach { sample ->
                samples[offset++] = sample / 32768.0f
            }
        }

        try {
            val localRecognizer = ensureRecognizer()
            val stream = localRecognizer.createStream()
            return try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                localRecognizer.decode(stream)
                val result = localRecognizer.getResult(stream)
                RecognitionResult(
                    text = result.text.trim(),
                    language = language.ifBlank { "auto" },
                    confidence = 0.0f
                )
            } finally {
                stream.release()
            }
        } catch (error: Throwable) {
            nativeResourceCoordinator?.release(this)
            throw error
        }
    }

    override suspend fun releaseIfIdle(): Boolean {
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
        recognitionMutex.withLock {
            synchronized(recognizerLock) {
                recognizer?.release()
                recognizer = null
            }
        }
        nativeResourceCoordinator?.unregister(this)
    }

    private fun ensureRecognizer(): OfflineRecognizer {
        synchronized(recognizerLock) {
            recognizer?.let { return it }

            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = SAMPLE_RATE,
                    featureDim = 80
                ),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = File(modelDirectory, "tiny-encoder.int8.onnx").absolutePath,
                        decoder = File(modelDirectory, "tiny-decoder.int8.onnx").absolutePath,
                        language = language,
                        task = "transcribe"
                    ),
                    tokens = File(modelDirectory, "tiny-tokens.txt").absolutePath,
                    modelType = "whisper",
                    numThreads = numThreads,
                    provider = "cpu"
                )
            )

            return OfflineRecognizer(config = config).also { recognizer = it }
        }
    }
}
