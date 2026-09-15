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
import com.halo.providers.sherpa.stt.SherpaKeywordSpotterWakeWordDetector
import com.halo.providers.sherpa.stt.SherpaOfflineSpeechRecognitionProvider
import com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog
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

        // Local Whisper is selected only when the complete, explicitly installed
        // model is present. Startup never downloads or initializes the model.
        val sherpaModelDirectory = File(
            context.filesDir,
            "speech/${SherpaSpeechModelCatalog.WHISPER_TINY_DIRECTORY}"
        )
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

        val sherpaKwsDirectory = File(
            context.filesDir,
            "speech/${SherpaSpeechModelCatalog.KWS_ENGLISH_DIRECTORY}"
        )
        val sherpaKwsKeywords = File(
            sherpaKwsDirectory,
            SherpaSpeechModelCatalog.KWS_ENGLISH_KEYWORDS_FILE
        )
        val wakeWord = runCatching {
            if (!SherpaSpeechModelCatalog.isInstalled(
                    sherpaKwsDirectory,
                    SherpaSpeechModelCatalog.kwsEnglish
                )
            ) {
                throw IllegalStateException("Sherpa KWS model is not installed")
            }
            SherpaKeywordSpotterWakeWordDetector(
                modelDirectory = sherpaKwsDirectory,
                keywordsFile = sherpaKwsKeywords
            )
        }.getOrElse {
            AlwaysWakeDetector()
        }

        return Providers(
            inference = llamaProvider,
            speechRecognition = speechRecognition,
            speechSynthesis = AndroidTTSProvider(context),
            wakeWord = wakeWord,
            aiProviders = listOfNotNull(
                runCatching { AndroidAssistantProvider(context) }.getOrNull(),
                runCatching { OpenRouterProvider(storage) }.getOrNull(),
                runCatching { OllamaProvider(storage) }.getOrNull(),
                llamaProvider
            )
        )
    }
}
