package com.halo.app.di

import com.halo.engines.speech.*
import java.io.File

/** Detect an explicitly installed model at the next capture, reusing one native detector. */
class ReloadableSileroVad(private val modelFile: () -> File) : VoiceActivityDetector {
    private var primary: SherpaSileroVad? = null
    private val fallback = EnergyVAD(threshold = 1000)
    override fun isSpeech(frame: AudioFrame): Boolean = primary?.isSpeech(frame) ?: fallback.isSpeech(frame)
    override fun reset() {
        if (primary == null) {
            val file = modelFile()
            if (file.isFile && file.length() >= 100_000L) primary = runCatching { SherpaSileroVad(file) }.getOrNull()
        }
        primary?.reset(); fallback.reset()
    }
    override fun close() { primary?.close(); primary = null; fallback.close() }
}
