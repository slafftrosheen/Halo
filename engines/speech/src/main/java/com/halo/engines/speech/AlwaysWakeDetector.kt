package com.halo.engines.speech

import com.halo.sdk.provider.WakeWordDetector
import com.halo.sdk.provider.WakeWordResult
import com.halo.sdk.host.AudioFrame

class AlwaysWakeDetector : WakeWordDetector {
    override fun process(frame: AudioFrame): WakeWordResult {
        // Immediately passes through everything as a detected wake word
        return WakeWordResult(detected = true, confidence = 1.0f)
    }

    override suspend fun health(): Boolean {
        return true
    }
}
