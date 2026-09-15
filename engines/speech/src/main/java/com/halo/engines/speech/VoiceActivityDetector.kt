package com.halo.engines.speech

import kotlin.math.abs

interface VoiceActivityDetector {
    fun isSpeech(frame: AudioFrame): Boolean
}

/**
 * Lightweight deterministic VAD for the fallback path.
 *
 * The detector deliberately has a small silence hangover. A single low-energy
 * PCM frame is not enough to terminate an utterance, which prevents normal
 * word-boundary dips from repeatedly ending buffered recognition.
 */
class EnergyVAD(
    private val threshold: Int = 1000,
    private val hangoverFrames: Int = 4
) : VoiceActivityDetector {
    private var remainingHangoverFrames = 0

    override fun isSpeech(frame: AudioFrame): Boolean {
        if (frame.samples.isEmpty()) return remainingHangoverFrames > 0

        var sum = 0L
        for (sample in frame.samples) {
            sum += abs(sample.toInt())
        }
        val averageEnergy = sum / frame.samples.size

        if (averageEnergy > threshold) {
            remainingHangoverFrames = hangoverFrames.coerceAtLeast(0)
            return true
        }

        if (remainingHangoverFrames > 0) {
            remainingHangoverFrames--
            return true
        }

        return false
    }
}
