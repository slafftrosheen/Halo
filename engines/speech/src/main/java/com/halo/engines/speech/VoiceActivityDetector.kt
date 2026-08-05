package com.halo.engines.speech
import kotlin.math.abs

interface VoiceActivityDetector {
    fun isSpeech(frame: AudioFrame): Boolean
}

class EnergyVoiceActivityDetector(private val threshold: Int = 1000) : VoiceActivityDetector {
    override fun isSpeech(frame: AudioFrame): Boolean {
        if (frame.samples.isEmpty()) return false
        var sum = 0L
        for (sample in frame.samples) {
            sum += abs(sample.toInt())
        }
        val averageEnergy = sum / frame.samples.size
        return averageEnergy > threshold
    }
}
