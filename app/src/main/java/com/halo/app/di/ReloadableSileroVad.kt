package com.halo.app.di

import com.halo.engines.speech.*
import java.io.File

/** Detect an explicitly installed model at the next capture, reusing one native detector. */
class ReloadableSileroVad(private val modelFile: () -> File) : VoiceActivityDetector {
    private var primary: SherpaSileroVad? = null
    private var modelSuspendedForRemoval = false
    private val fallback = EnergyVAD(threshold = 1000)
    @Synchronized override fun isSpeech(frame: AudioFrame): Boolean = primary?.isSpeech(frame) ?: fallback.isSpeech(frame)
    @Synchronized override fun reset() {
        if (primary == null && !modelSuspendedForRemoval) {
            val file = modelFile()
            if (file.isFile && file.length() >= 100_000L) primary = runCatching { SherpaSileroVad(file) }.getOrNull()
        }
        primary?.reset(); fallback.reset()
    }
    @Synchronized fun suspendModelForRemoval() {
        modelSuspendedForRemoval = true
        primary?.close()
        primary = null
    }
    @Synchronized fun allowModelReload() { modelSuspendedForRemoval = false }
    @Synchronized override fun close() { primary?.close(); primary = null; fallback.close() }
}
