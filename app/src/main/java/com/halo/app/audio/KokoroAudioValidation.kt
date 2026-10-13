package com.halo.app.audio

internal object KokoroAudioValidation {
    fun validate(samples: FloatArray, sampleRate: Int) {
        require(samples.isNotEmpty() && sampleRate > 0) { "Kokoro returned no audio" }
        require(samples.all { it.isFinite() }) { "Kokoro returned invalid audio. Download the full voice pack again." }
        require(samples.any { kotlin.math.abs(it) >= 1f / 32767f }) {
            "Kokoro returned silent audio. Download the full voice pack again."
        }
    }
}
