package com.halo.app.di

import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiProvider
import com.halo.sdk.provider.AiProviderRegistry
import com.halo.sdk.provider.SpeechRecognitionProvider
import com.halo.sdk.provider.SpeechSynthesisProvider
import com.halo.sdk.provider.WakeWordDetector

/**
 * Typed container for all provider instances created by a [ProviderFactory].
 *
 * The registry is the canonical AI composition boundary. [aiProviders] remains
 * temporarily available for UI/diagnostics and legacy callers.
 */
data class Providers(
    val inference: AiInferenceProvider,
    val speechRecognition: SpeechRecognitionProvider,
    val speechSynthesis: SpeechSynthesisProvider,
    val wakeWord: WakeWordDetector,
    val aiProviders: List<AiProvider> = emptyList(),
    val aiRegistry: AiProviderRegistry = AiProviderRegistry.from(aiProviders)
)

interface ProviderFactory {
    fun createProviders(): Providers
}
