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
 * AI inference and external-assistant handoff are represented explicitly by
 * [AiProviderRegistry]; the legacy [aiProviders] list remains as a compatibility
 * surface for components that have not migrated to the typed registry yet.
 */
data class Providers(
    val inference: AiInferenceProvider,
    val speechRecognition: SpeechRecognitionProvider,
    val speechSynthesis: SpeechSynthesisProvider,
    val wakeWord: WakeWordDetector,
    val aiProviders: List<AiProvider> = emptyList()
) {
    val aiRegistry: AiProviderRegistry
        get() = AiProviderRegistry.from(aiProviders)
}

interface ProviderFactory {
    fun createProviders(): Providers
}
