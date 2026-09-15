package com.halo.providers.sherpa.stt

import com.halo.sdk.host.AudioFrame
import com.halo.sdk.provider.ProviderHealth
import com.halo.sdk.provider.ProviderMetadata
import com.halo.sdk.provider.WakeWordDetector
import com.halo.sdk.provider.WakeWordResult
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

/** Fully local, file-backed Sherpa keyword spotting adapter. */
class SherpaKeywordSpotterWakeWordDetector(
    modelDirectory: File,
    keywordsFile: File,
    private val keywordThreshold: Float = 0.25f
) : WakeWordDetector {
    override val requiresSpeechGate: Boolean = false

    override val metadata: ProviderMetadata = ProviderMetadata(
        id = "sherpa-kws-zipformer-gigaspeech",
        displayName = "Sherpa local keyword spotter",
        local = true,
        supportsStreaming = true
    )

    private val model: KeywordSpotter
    private var stream: OnlineStream
    private var closed = false

    init {
        require(SherpaSpeechModelCatalog.isInstalled(modelDirectory, SherpaSpeechModelCatalog.kwsEnglish)) {
            "Sherpa KWS model is incomplete: ${SherpaSpeechModelCatalog.missingFiles(modelDirectory, SherpaSpeechModelCatalog.kwsEnglish)}"
        }
        require(keywordsFile.isFile && keywordsFile.length() > 0) {
            "Sherpa KWS keywords file is missing: ${keywordsFile.absolutePath}"
        }

        // The official sherpa-onnx Zipformer KWS model configuration only needs
        // encoder, decoder, joiner, tokens and modelType. It does not use a BPE
        // vocabulary file. See sherpa-onnx's Kotlin getKwsModelConfig().
        val modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = File(modelDirectory, "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx").absolutePath,
                decoder = File(modelDirectory, "decoder-epoch-12-avg-2-chunk-16-left-64.onnx").absolutePath,
                joiner = File(modelDirectory, "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx").absolutePath
            ),
            tokens = File(modelDirectory, "tokens.txt").absolutePath,
            numThreads = 1,
            provider = "cpu",
            modelType = "zipformer2"
        )

        model = KeywordSpotter(
            config = KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                modelConfig = modelConfig,
                maxActivePaths = 4,
                keywordsFile = keywordsFile.absolutePath,
                keywordsScore = 1.5f,
                keywordsThreshold = keywordThreshold,
                numTrailingBlanks = 2
            )
        )
        stream = model.createStream()
    }

    @Synchronized
    override fun process(frame: AudioFrame): WakeWordResult {
        if (closed || frame.samples.isEmpty()) return WakeWordResult(detected = false)

        val samples = FloatArray(frame.samples.size) { index ->
            frame.samples[index] / 32768.0f
        }
        stream.acceptWaveform(samples, frame.sampleRate)

        while (model.isReady(stream)) {
            model.decode(stream)
        }

        val keyword = model.getResult(stream).keyword.trim()
        if (keyword.isEmpty()) return WakeWordResult(detected = false)

        model.reset(stream)
        return WakeWordResult(detected = true, confidence = 0f)
    }

    override suspend fun health(): Boolean = !closed

    override suspend fun healthDetails(): ProviderHealth = ProviderHealth(
        healthy = !closed,
        message = metadata.displayName
    )

    override suspend fun close() {
        synchronized(this) {
            if (closed) return
            closed = true
            stream.release()
            model.release()
        }
    }
}
