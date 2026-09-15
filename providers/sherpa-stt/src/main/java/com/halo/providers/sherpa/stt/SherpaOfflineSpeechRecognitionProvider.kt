package com.halo.providers.sherpa.stt

import com.halo.sdk.host.AudioBuffer
import com.halo.sdk.provider.BufferedSpeechRecognitionProvider
import com.halo.sdk.provider.ProviderHealth
import com.halo.sdk.provider.RecognitionResult
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fully local, buffered ASR backed by sherpa-onnx.
 *
 * The provider deliberately owns no microphone. Halo's AudioPerceptionEngine
 * supplies the already captured utterance as an AudioBuffer. This keeps audio
 * ownership in one place and avoids the system SpeechRecognizer audio/beep path.
 *
 * The model directory is external to the APK. A later model manager can download
 * and validate it without changing this provider or the runtime contracts.
 */
class SherpaOfflineSpeechRecognitionProvider(
    private val modelDirectory: File,
    private val language: String = "",
    private val numThreads: Int = 2
) : BufferedSpeechRecognitionProvider {

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val MODEL_NAME = "tiny"
        private const val MIN_MODEL_BYTES = 1_024L

        fun isInstalled(modelDirectory: File): Boolean {
            val encoder = File(modelDirectory, "$MODEL_NAME-encoder.int8.onnx")
            val decoder = File(modelDirectory, "$MODEL_NAME-decoder.int8.onnx")
            val tokens = File(modelDirectory, "$MODEL_NAME-tokens.txt")
            return modelDirectory.isDirectory &&
                encoder.isFile && encoder.length() > MIN_MODEL_BYTES &&
                decoder.isFile && decoder.length() > MIN_MODEL_BYTES &&
                tokens.isFile && tokens.length() > MIN_MODEL_BYTES
        }
    }

    override val metadata = com.halo.sdk.provider.ProviderMetadata(
        id = "speech.sherpa_onnx.whisper_tiny",
        supportsStreaming = false
    )

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    private val recognizerLock = Any()
    private val recognitionMutex = Mutex()

    override suspend fun health(): Boolean = isInstalled(modelDirectory)

    override suspend fun healthDetails(): ProviderHealth {
        val installed = isInstalled(modelDirectory)
        return ProviderHealth(
            healthy = installed,
            message = if (installed) {
                "Sherpa-ONNX Whisper tiny multilingual model is installed"
            } else {
                "Sherpa-ONNX Whisper tiny multilingual model is not installed"
            }
        )
    }

    override suspend fun recognize(audio: AudioBuffer): RecognitionResult = recognitionMutex.withLock {
        require(audio.frames.isNotEmpty()) { "Cannot recognize an empty audio buffer" }
        check(isInstalled(modelDirectory)) {
            "Sherpa-ONNX model is not installed at ${modelDirectory.absolutePath}"
        }

        val sampleRate = audio.frames.first().sampleRate
        require(sampleRate == SAMPLE_RATE) {
            "Sherpa-ONNX provider requires ${SAMPLE_RATE} Hz audio, got $sampleRate Hz"
        }
        require(audio.frames.all { it.sampleRate == SAMPLE_RATE && it.channels == 1 }) {
            "Sherpa-ONNX provider requires mono ${SAMPLE_RATE} Hz PCM audio"
        }

        val samples = FloatArray(audio.frames.sumOf { it.samples.size })
        var offset = 0
        audio.frames.forEach { frame ->
            frame.samples.forEach { sample ->
                samples[offset++] = sample / 32768.0f
            }
        }

        val localRecognizer = ensureRecognizer()
        val stream = localRecognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            localRecognizer.decode(stream)
            val result = localRecognizer.getResult(stream)
            RecognitionResult(
                text = result.text.trim(),
                language = language.ifBlank { "auto" },
                // Whisper's offline result does not expose a calibrated confidence
                // through this API. Never report a fabricated 1.0 confidence.
                confidence = 0.0f
            )
        } finally {
            stream.release()
        }
    }

    override suspend fun close() {
        recognitionMutex.withLock {
            synchronized(recognizerLock) {
                recognizer?.release()
                recognizer = null
            }
        }
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
                        encoder = File(modelDirectory, "$MODEL_NAME-encoder.int8.onnx").absolutePath,
                        decoder = File(modelDirectory, "$MODEL_NAME-decoder.int8.onnx").absolutePath,
                        language = language,
                        task = "transcribe"
                    ),
                    tokens = File(modelDirectory, "$MODEL_NAME-tokens.txt").absolutePath,
                    modelType = "whisper",
                    numThreads = numThreads,
                    provider = "cpu"
                )
            )

            return OfflineRecognizer(config = config).also { recognizer = it }
        }
    }
}
