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
import com.halo.providers.sherpa.stt.SherpaOfflineSpeechRecognitionProvider
import java.io.File
import java.util.Locale

class AndroidProviderFactory(
    private val context: Context,
    private val modelDownloadManager: ModelDownloadManager,
    private val storage: com.halo.sdk.runtime.Storage
) : ProviderFactory {

    override fun createProviders(): Providers {
        val llamaProvider = NativeLlamaProvider(
            context = context,
            downloadManager = modelDownloadManager,
            storage = storage
        )

        // Local Whisper is opt-in through model presence. Do not download or
        // initialize a 100+ MB ASR model during application startup.
        val sherpaModelDirectory = File(context.filesDir, "speech/sherpa-whisper-tiny")
        val configuredLocale = storage.getString("speech_locale")
            ?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().toLanguageTag()
        val speechLanguage = configuredLocale.substringBefore('-').substringBefore('_')

        val speechRecognition: SpeechRecognitionProvider = if (
            SherpaOfflineSpeechRecognitionProvider.isInstalled(sherpaModelDirectory)
        ) {
            SherpaOfflineSpeechRecognitionProvider(
                modelDirectory = sherpaModelDirectory,
                language = speechLanguage
            )
        } else {
            AndroidSpeechRecognitionProvider(context) {
                configuredLocale
            }
        }

        return Providers(
            inference = llamaProvider,
            speechRecognition = speechRecognition,
            speechSynthesis = AndroidTTSProvider(context),
            wakeWord = AlwaysWakeDetector(),
            aiProviders = listOfNotNull(
                runCatching { AndroidAssistantProvider(context) }.getOrNull(),
                runCatching { OpenRouterProvider(storage) }.getOrNull(),
                runCatching { OllamaProvider(storage) }.getOrNull(),
                llamaProvider
            )
        )
    }
}
