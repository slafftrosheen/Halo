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

        val sherpaModelDirectory = File(context.filesDir, "speech/sherpa-whisper-tiny-en")
        val speechRecognition: SpeechRecognitionProvider = if (
            SherpaOfflineSpeechRecognitionProvider.isInstalled(sherpaModelDirectory)
        ) {
            SherpaOfflineSpeechRecognitionProvider(sherpaModelDirectory)
        } else {
            AndroidSpeechRecognitionProvider(context) {
                storage.getString("speech_locale") ?: java.util.Locale.getDefault().toLanguageTag()
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
