package com.halo.app.di

import android.content.Context
import com.halo.sdk.provider.*
import com.halo.engines.speech.AlwaysWakeDetector
import com.halo.providers.android.stt.StubAndroidSpeechRecognitionProvider
import com.halo.providers.android.tts.AndroidTTSProvider
import com.halo.providers.llamacpp.NativeLlamaProvider
import com.halo.providers.llamacpp.ModelDownloadManager

class AndroidProviderFactory(
    private val context: Context,
    private val modelDownloadManager: ModelDownloadManager
) : ProviderFactory {

    override fun createProviders(): ProviderRegistry {
        val registry = DefaultProviderRegistry()
        
        registry.register(InferenceProvider::class, NativeLlamaProvider(context, modelDownloadManager))
        registry.register(SpeechRecognitionProvider::class, StubAndroidSpeechRecognitionProvider(context))
        registry.register(SpeechSynthesisProvider::class, AndroidTTSProvider(context))
        registry.register(WakeWordDetector::class, AlwaysWakeDetector())
        
        return registry
    }
}
