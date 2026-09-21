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
    val peakAmplitude: Int
)

class EnergySpeechActivityEstimator(
    private val averageAmplitudeThreshold: Int = 300
) : SpeechActivityEstimator {
    override suspend fun estimate(audio: AudioBuffer): SpeechActivityEstimate {
        var durationMs = 0L
        var voicedDurationMs = 0L
        var peak = 0
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
                if (total / frame.samples.size >= averageAmplitudeThreshold) {
                    voicedDurationMs += frameDuration
                }
            }
        }
        return SpeechActivityEstimate(
            durationMs = durationMs,
            voicedDurationMs = voicedDurationMs,
            voicedRatio = if (durationMs == 0L) 0f else voicedDurationMs.toFloat() / durationMs,
            peakAmplitude = peak
        )
    }
}
