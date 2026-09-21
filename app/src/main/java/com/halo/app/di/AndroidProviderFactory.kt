package com.halo.app.di

import android.content.Context
import com.halo.engines.speech.AlwaysWakeDetector
import com.halo.engines.speech.UnavailableSpeechRecognitionProvider
import com.halo.providers.llamacpp.ModelDownloadManager
import com.halo.providers.llamacpp.NativeLlamaProvider
import com.halo.providers.android.assistant.AndroidAssistantProvider
import com.halo.providers.android.tts.AndroidTTSProvider
import com.halo.providers.openrouter.OpenRouterProvider
import com.halo.providers.llamacpp.OllamaProvider
import com.halo.providers.sherpa.stt.ReloadableSpeechRecognitionProvider
import com.halo.providers.sherpa.stt.SherpaKeywordSpotterWakeWordDetector
import com.halo.providers.sherpa.stt.SherpaOfflineSpeechRecognitionProvider
import com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog
import com.halo.sdk.provider.SpeechRecognitionProvider
import com.halo.sdk.runtime.NativeResourceCoordinator
import java.io.File
import java.util.Locale

class AndroidProviderFactory(
    private val context: Context,
    private val modelDownloadManager: ModelDownloadManager,
    private val storage: com.halo.sdk.runtime.Storage,
    private val nativeResourceCoordinator: NativeResourceCoordinator
) : ProviderFactory {

    override fun createProviders(): Providers {
        val llamaProvider = NativeLlamaProvider(
            context = context,
            downloadManager = modelDownloadManager,
            storage = storage,
            nativeResourceCoordinator = nativeResourceCoordinator
        )

        // Voice remains local-first. If the approved offline model is absent,
        // expose an explicit unavailable provider instead of silently falling
        // back to Android SpeechRecognizer, which can invoke an OEM/system
        // recognition service and take microphone ownership outside Halo's path.
        val speechRoot = com.halo.app.storage.haloStorageLayout(context).speechModels
        val sherpaModelDirectory = File(speechRoot, SherpaSpeechModelCatalog.WHISPER_TINY_DIRECTORY)
        val speechRecognition: SpeechRecognitionProvider = ReloadableSpeechRecognitionProvider(
            modelDirectory = sherpaModelDirectory,
            getLanguage = {
                val configuredLocale = storage.getString("speech_locale")
                    ?.takeIf { it.isNotBlank() }
                    ?: Locale.getDefault().toLanguageTag()
                configuredLocale.substringBefore('-').substringBefore('_')
            },
            nativeResourceCoordinator = nativeResourceCoordinator
        )

        val sherpaKwsDirectory = File(speechRoot, SherpaSpeechModelCatalog.KWS_ENGLISH_DIRECTORY)
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
            // Never activate upstream demo keywords implicitly. Until a Halo-owned
            // keyword file is explicitly installed, retain the compatibility
            // pass-through detector and explicit listening controls.
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
