package com.halo.app.audio

internal object KokoroAudioValidation {
    fun validate(samples: FloatArray, sampleRate: Int, engine: String = "Kokoro") {
        require(samples.isNotEmpty() && sampleRate > 0) { "$engine returned no audio" }
        require(samples.all { it.isFinite() }) { "$engine returned invalid audio. Download the full voice pack again." }
        require(samples.any { kotlin.math.abs(it) >= 1f / 32767f }) {
            "$engine returned silent audio. Download the full voice pack again."
        }
    }
}
