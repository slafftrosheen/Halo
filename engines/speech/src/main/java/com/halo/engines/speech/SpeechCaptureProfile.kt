package com.halo.engines.speech

data class SpeechCaptureProfile(
    val vadThreshold: Float = 0.50f,
    val minSpeechMs: Long = 200,
    val trailingSilenceMs: Long = 350,
    val maxSpeechMs: Long = 8_000,
    val preRollMs: Long = 400
) {
    companion object { val Production = SpeechCaptureProfile() }
}
