package com.halo.app.di

import android.content.Context
import com.halo.sdk.provider.*
import com.halo.engines.speech.AlwaysWakeDetector
import com.halo.providers.android.stt.AndroidSpeechRecognitionProvider
import com.halo.providers.android.tts.AndroidTTSProvider
import com.halo.providers.android.assistant.AndroidAssistantProvider
import com.halo.providers.openrouter.OpenRouterProvider
import com.halo.providers.llamacpp.NativeLlamaProvider
import com.halo.providers.llamacpp.ModelDownloadManager

import com.halo.providers.llamacpp.OllamaProvider

class AndroidProviderFactory(
    private val context: Context,
    private val modelDownloadManager: ModelDownloadManager,
    private val storage: com.halo.sdk.runtime.Storage
) : ProviderFactory {

    override fun createProviders(): Providers {
        return Providers(
            inference = NativeLlamaProvider(context, modelDownloadManager, storage),
            speechRecognition = AndroidSpeechRecognitionProvider(context),
            speechSynthesis = AndroidTTSProvider(context),
            wakeWord = AlwaysWakeDetector(),
            aiProviders = listOfNotNull(
                runCatching { AndroidAssistantProvider(context) }.getOrNull(),
                runCatching { OpenRouterProvider(storage) }.getOrNull(),
                runCatching { OllamaProvider(storage) }.getOrNull(),
                runCatching { NativeLlamaProvider(context, modelDownloadManager, storage) }.getOrNull()
            )
        )
    }
}
