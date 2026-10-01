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
    private val threshold: Float = com.halo.engines.speech.SpeechCaptureProfile.Production.vadThreshold,
    private val minSilenceDuration: Float = com.halo.engines.speech.SpeechCaptureProfile.Production.trailingSilenceMs / 1000f,
    private val minSpeechDuration: Float = com.halo.engines.speech.SpeechCaptureProfile.Production.minSpeechMs / 1000f
) : SpeechActivityEstimator {
    private val mutex = Mutex()
    private var vad: Vad? = null

    override suspend fun estimate(audio: AudioBuffer): SpeechActivityEstimate =
        withContext(dispatchers.default) {
            mutex.withLock {
                val analyzer = vad ?: createVad().also { vad = it }
                analyzer.reset()
                val totalSamples = audio.frames.sumOf { it.samples.size }
                val durationMs = totalSamples * 1000L / sampleRate
                var peak = 0
                audio.frames.forEach { frame ->
                    require(frame.channels == 1 && frame.sampleRate == sampleRate) { "Silero requires mono $sampleRate Hz audio" }
                    val samples = FloatArray(frame.samples.size) { index ->
                        val sample = frame.samples[index]
                        peak = maxOf(peak, abs(sample.toInt()))
                        sample / 32768.0f
                    }
                    analyzer.acceptWaveform(samples)
                }
                analyzer.flush()
                var voicedSamples = 0L
                var longestSamples = 0
                var segmentCount = 0
                while (!analyzer.empty()) {
                    val segment = analyzer.front()
                    val count = segment.samples.size.coerceAtMost((totalSamples - segment.start).coerceAtLeast(0))
                    voicedSamples += count
                    longestSamples = maxOf(longestSamples, count)
                    if (count > 0) segmentCount++
                    analyzer.pop()
                }
                val voicedDurationMs = voicedSamples.coerceAtMost(totalSamples.toLong()) * 1000L / sampleRate
                val longestSegmentMs = longestSamples * 1000L / sampleRate

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
                maxSpeechDuration = com.halo.engines.speech.SpeechCaptureProfile.Production.maxSpeechMs / 1000f
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
        return withContext(dispatchers.default) {
            try { estimator?.estimate(audio) ?: fallback.estimate(audio) }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { fallback.estimate(audio) }
        }
    }
}
