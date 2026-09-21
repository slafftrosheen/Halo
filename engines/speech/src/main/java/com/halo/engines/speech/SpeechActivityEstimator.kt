package com.halo.engines.speech

import com.halo.sdk.host.AudioBuffer
import kotlin.math.abs

interface SpeechActivityEstimator {
    suspend fun estimate(audio: AudioBuffer): SpeechActivityEstimate
}

data class SpeechActivityEstimate(
    val durationMs: Long,
    val voicedDurationMs: Long,
    val voicedRatio: Float,
    val peakAmplitude: Int,
    val segmentCount: Int,
    val longestSegmentMs: Long,
    val estimator: SpeechQualityEstimator
)

enum class SpeechQualityEstimator { SILERO, SIMPLE_ENERGY }

class EnergySpeechActivityEstimator(
    private val averageAmplitudeThreshold: Int = 300
) : SpeechActivityEstimator {
    override suspend fun estimate(audio: AudioBuffer): SpeechActivityEstimate {
        var durationMs = 0L
        var voicedDurationMs = 0L
        var peak = 0
        var segmentCount = 0
        var currentSegmentMs = 0L
        var longestSegmentMs = 0L
        var wasVoiced = false
        audio.frames.forEach { frame ->
            val frameDuration = frame.samples.size * 1_000L / frame.sampleRate
            durationMs += frameDuration
            if (frame.samples.isNotEmpty()) {
                var total = 0L
                frame.samples.forEach { sample ->
                    val amplitude = abs(sample.toInt())
                    total += amplitude
                    if (amplitude > peak) peak = amplitude
                }
                val voiced = total / frame.samples.size >= averageAmplitudeThreshold
                if (voiced) {
                    voicedDurationMs += frameDuration
                    currentSegmentMs += frameDuration
                    if (!wasVoiced) segmentCount++
                    longestSegmentMs = maxOf(longestSegmentMs, currentSegmentMs)
                } else {
                    currentSegmentMs = 0L
                }
                wasVoiced = voiced
            }
        }
        return SpeechActivityEstimate(
            durationMs = durationMs,
            voicedDurationMs = voicedDurationMs,
            voicedRatio = if (durationMs == 0L) 0f else voicedDurationMs.toFloat() / durationMs,
            peakAmplitude = peak,
            segmentCount = segmentCount,
            longestSegmentMs = longestSegmentMs,
            estimator = SpeechQualityEstimator.SIMPLE_ENERGY
        )
    }
}
