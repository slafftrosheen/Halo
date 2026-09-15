package com.halo.engines.speech

import com.halo.sdk.host.AudioBuffer
import com.halo.sdk.provider.BufferedSpeechRecognitionProvider
import com.halo.sdk.provider.ProviderHealth
import com.halo.sdk.provider.RecognitionResult

/**
 * Explicitly unavailable local ASR used when no approved offline model is installed.
 *
 * This is intentionally not a fallback to Android SpeechRecognizer: system speech
 * services may take microphone ownership and can produce OEM audio feedback or
 * leave the device's privacy boundary. Voice setup must be explicit instead.
 */
class UnavailableSpeechRecognitionProvider(
    private val reason: String = "No offline speech model is installed"
) : BufferedSpeechRecognitionProvider {

    override val metadata = com.halo.sdk.provider.ProviderMetadata(
        id = "speech.unavailable",
        supportsStreaming = false
    )

    override suspend fun health(): Boolean = false

    override suspend fun healthDetails(): ProviderHealth = ProviderHealth(
        healthy = false,
        message = reason
    )

    override suspend fun recognize(audio: AudioBuffer): RecognitionResult {
        throw IllegalStateException(reason)
    }
}
