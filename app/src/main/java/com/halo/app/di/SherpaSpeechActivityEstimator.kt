package com.halo.app.di

import com.halo.engines.speech.EnergySpeechActivityEstimator
import com.halo.engines.speech.SpeechActivityEstimate
import com.halo.engines.speech.SpeechActivityEstimator
import com.halo.engines.speech.SpeechQualityEstimator
import com.halo.sdk.host.AudioBuffer
import com.halo.sdk.runtime.HaloDispatchers
import com.halo.sdk.runtime.productionHaloDispatchers
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Whole-capture Silero analysis used for admission, independent of capture segmentation. */
class SherpaSpeechActivityEstimator(
    private val modelFile: File,
    private val dispatchers: HaloDispatchers = productionHaloDispatchers(),
    private val sampleRate: Int = 16_000,
    private val threshold: Float = 0.5f,
    private val minSilenceDuration: Float = 0.35f,
    private val minSpeechDuration: Float = 0.12f
) : SpeechActivityEstimator {
    private val mutex = Mutex()
    private var vad: Vad? = null

    override suspend fun estimate(audio: AudioBuffer): SpeechActivityEstimate =
        withContext(dispatchers.default) {
            mutex.withLock {
                val analyzer = vad ?: createVad().also { vad = it }
                analyzer.reset()
                var durationMs = 0L
                var voicedDurationMs = 0L
                var peak = 0
                var segmentCount = 0
                var currentSegmentMs = 0L
                var longestSegmentMs = 0L
                var wasVoiced = false

                audio.frames.forEach { frame ->
                    require(frame.channels == 1) { "Sherpa Silero VAD requires mono audio" }
                    require(frame.sampleRate == sampleRate) {
                        "Sherpa Silero VAD requires $sampleRate Hz audio, got ${frame.sampleRate} Hz"
                    }
                    val frameDurationMs = frame.samples.size * 1_000L / frame.sampleRate
                    durationMs += frameDurationMs
                    val samples = FloatArray(frame.samples.size)
                    frame.samples.forEachIndexed { index, sample ->
                        peak = maxOf(peak, abs(sample.toInt()))
                        samples[index] = sample / 32768.0f
                    }
                    analyzer.acceptWaveform(samples)
                    val voiced = analyzer.isSpeechDetected()
                    if (voiced) {
                        voicedDurationMs += frameDurationMs
                        currentSegmentMs += frameDurationMs
                        if (!wasVoiced) segmentCount++
                        longestSegmentMs = maxOf(longestSegmentMs, currentSegmentMs)
                    } else {
                        currentSegmentMs = 0L
                    }
                    wasVoiced = voiced
                }

                SpeechActivityEstimate(
                    durationMs = durationMs,
                    voicedDurationMs = voicedDurationMs,
                    voicedRatio = if (durationMs == 0L) 0f else voicedDurationMs.toFloat() / durationMs,
                    peakAmplitude = peak,
                    segmentCount = segmentCount,
                    longestSegmentMs = longestSegmentMs,
                    estimator = SpeechQualityEstimator.SILERO
                )
            }
        }

    private fun createVad(): Vad = Vad(
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = modelFile.absolutePath,
                threshold = threshold,
                minSilenceDuration = minSilenceDuration,
                minSpeechDuration = minSpeechDuration,
                windowSize = 512,
                maxSpeechDuration = 8.0f
            ),
            sampleRate = sampleRate,
            numThreads = 1,
            provider = "cpu",
            debug = false
        )
    )
}

/** Loads Silero only on the worker dispatcher and makes fallback visible in quality metrics. */
class SherpaOrEnergySpeechActivityEstimator(
    private val modelFile: () -> File,
    private val dispatchers: HaloDispatchers = productionHaloDispatchers(),
    private val fallback: SpeechActivityEstimator = EnergySpeechActivityEstimator()
) : SpeechActivityEstimator {
    @Volatile private var primary: SpeechActivityEstimator? = null

    override suspend fun estimate(audio: AudioBuffer): SpeechActivityEstimate {
        val estimator = primary ?: withContext(dispatchers.io) {
            val file = modelFile()
            if (!file.isFile || file.length() == 0L) {
                null
            } else {
                SherpaSpeechActivityEstimator(file, dispatchers).also { primary = it }
            }
        }
        return runCatching { estimator?.estimate(audio) ?: fallback.estimate(audio) }
            .getOrElse {
                primary = null
                fallback.estimate(audio)
            }
    }
}
