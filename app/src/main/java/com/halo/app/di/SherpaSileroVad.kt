package com.halo.app.di

import com.halo.engines.speech.AudioFrame
import com.halo.engines.speech.VoiceActivityDetector
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File

/** Composition-layer adapter from sherpa-onnx VAD to Halo's speech VAD contract. */
class SherpaSileroVad(
    modelFile: File,
    threshold: Float = com.halo.engines.speech.SpeechCaptureProfile.Production.vadThreshold,
    minSilenceDuration: Float = com.halo.engines.speech.SpeechCaptureProfile.Production.trailingSilenceMs / 1000f,
    minSpeechDuration: Float = com.halo.engines.speech.SpeechCaptureProfile.Production.minSpeechMs / 1000f,
    private val sampleRate: Int = 16_000
) : VoiceActivityDetector {

    private val vad = Vad(
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = modelFile.absolutePath,
                threshold = threshold,
                minSilenceDuration = minSilenceDuration,
                minSpeechDuration = minSpeechDuration,
                windowSize = 512,
                maxSpeechDuration = com.halo.engines.speech.SpeechCaptureProfile.Production.maxSpeechMs / 1000f
            ),
            sampleRate = sampleRate,
            numThreads = 1,
            provider = "cpu",
            debug = false
        )
    )

    override fun isSpeech(frame: AudioFrame): Boolean {
        if (frame.samples.isEmpty()) return false
        require(frame.channels == 1) { "Sherpa Silero VAD requires mono audio" }
        require(frame.sampleRate == sampleRate) {
            "Sherpa Silero VAD requires ${sampleRate} Hz audio, got ${frame.sampleRate} Hz"
        }
        val samples = FloatArray(frame.samples.size)
        for (index in frame.samples.indices) {
            samples[index] = frame.samples[index] / 32768.0f
        }
        vad.acceptWaveform(samples)
        return vad.isSpeechDetected()
    }

    override fun reset() {
        vad.reset()
    }

    override fun close() {
        vad.release()
    }
}
