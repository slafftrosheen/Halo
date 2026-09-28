package com.halo.app.di

import android.content.Context
import com.halo.app.storage.haloStorageLayout
import com.halo.engines.speech.AlwaysWakeDetector
import com.halo.providers.android.tts.AndroidTTSProvider
import com.halo.providers.anthropic.AnthropicProvider
import com.halo.providers.gemini.GeminiProvider
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.LiteRtModelManager
import com.halo.providers.ollama.OllamaProvider
import com.halo.providers.openai.OpenAiProvider
import com.halo.providers.openrouter.OpenRouterProvider
import com.halo.providers.sherpa.stt.ReloadableSpeechRecognitionProvider
import com.halo.providers.sherpa.stt.SherpaKeywordSpotterWakeWordDetector
import com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog
import com.halo.sdk.provider.SpeechRecognitionProvider
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.sdk.runtime.Storage
import java.io.File
import java.util.Locale

class AndroidProviderFactory(
    private val context: Context,
    private val modelManager: LiteRtModelManager,
    private val storage: Storage,
    private val nativeResourceCoordinator: NativeResourceCoordinator
) : ProviderFactory {

    override fun createProviders(): Providers {
        val localProvider = LiteRtLocalProvider(
            context = context,
            inventoryRepository = modelManager,
            storage = storage,
            nativeResourceCoordinator = nativeResourceCoordinator,
            // The manager owns the pinned development catalog (including gated entries used for
            // import canonicalization); the provider resolves per-artifact runtime profiles from it.
            catalog = modelManager.getAllCatalogModels()
        )

        val speechRoot = haloStorageLayout(context).speechModels
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
            AlwaysWakeDetector()
        }

        return Providers(
            inference = localProvider,
            speechRecognition = speechRecognition,
            speechSynthesis = AndroidTTSProvider(context),
            wakeWord = wakeWord,
            aiProviders = listOfNotNull(
                localProvider,
                runCatching { OllamaProvider(storage) }.getOrNull(),
                runCatching { OpenRouterProvider(storage) }.getOrNull(),
                runCatching { OpenAiProvider(storage) }.getOrNull(),
                runCatching { AnthropicProvider(storage) }.getOrNull(),
                runCatching { GeminiProvider(storage) }.getOrNull(),
            )
        )
    }
}
