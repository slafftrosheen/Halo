package com.halo.engines.speech

import com.halo.sdk.host.AudioBuffer

data class DiagnosticAudioCapture(val audio: AudioBuffer, val source: String,
    val originalSampleRate: Int, val processedSampleRate: Int) : AutoCloseable {
    override fun close() { audio.frames.forEach { it.samples.fill(0) } }
}
