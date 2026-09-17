package com.halo.engines.speech

import kotlin.math.abs

interface VoiceActivityDetector {
    fun isSpeech(frame: AudioFrame): Boolean

    /** Reset any temporal detector state at the end of an audio session. */
    fun reset() = Unit

    /** Release native/model resources when the owning pipeline is disposed. */
    fun close() = Unit
}

/**
 * Lightweight deterministic VAD for the fallback path.
 *
 * Hangover is measured in audio time rather than frame count so endpointing is
 * stable even when the Android audio HAL changes read-buffer sizes.
 */
class EnergyVAD(
    private val threshold: Int = 1000,
    private val hangoverMillis: Long = 600L
) : VoiceActivityDetector {
    private var remainingHangoverMillis = 0L

    init {
        require(hangoverMillis >= 0L) { "hangoverMillis must be >= 0" }
    }

    override fun isSpeech(frame: AudioFrame): Boolean {
        if (frame.samples.isEmpty()) return remainingHangoverMillis > 0L

        var sum = 0L
        for (sample in frame.samples) {
            sum += abs(sample.toInt())
        }
        val averageEnergy = sum / frame.samples.size

        if (averageEnergy > threshold) {
            remainingHangoverMillis = hangoverMillis
            return true
        }

        if (remainingHangoverMillis > 0L) {
            remainingHangoverMillis =
                (remainingHangoverMillis - frame.durationMillis()).coerceAtLeast(0L)
            return true
        }

        return false
    }

    override fun reset() {
        remainingHangoverMillis = 0L
    }

    private fun AudioFrame.durationMillis(): Long {
        val safeChannels = channels.coerceAtLeast(1)
        val samplesPerChannel = samples.size / safeChannels
        if (sampleRate <= 0 || samplesPerChannel <= 0) return 1L
        return maxOf(1L, samplesPerChannel * 1000L / sampleRate)
    }
}
