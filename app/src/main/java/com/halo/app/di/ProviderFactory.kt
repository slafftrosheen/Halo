package com.halo.app.di

import com.halo.sdk.provider.InferenceProvider
import com.halo.sdk.provider.SpeechRecognitionProvider
import com.halo.sdk.provider.SpeechSynthesisProvider
import com.halo.sdk.provider.WakeWordDetector
import com.halo.sdk.provider.AiProvider

/**
 * Typed container for all provider instances created by a [ProviderFactory].
 * Replaces ProviderRegistry in the composition root — no service locator needed.
 */
data class Providers(
    val inference: InferenceProvider,
    val speechRecognition: SpeechRecognitionProvider,
    val speechSynthesis: SpeechSynthesisProvider,
    val wakeWord: WakeWordDetector,
    val aiProviders: List<AiProvider> = emptyList()
)

interface ProviderFactory {
    fun createProviders(): Providers
}
