package com.halo.app.di

import android.content.Context
import com.halo.app.ai.DefaultSpeechProviderSelector
import com.halo.app.ai.SpeechSelectionRepository
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
import com.halo.providers.groq.GroqProvider
import com.halo.providers.cerebras.CerebrasProvider
import com.halo.providers.opencode.zen.OpenCodeZenProvider
import com.halo.providers.xai.XaiProvider
import com.halo.providers.custom.openaicompatible.CustomOpenAiCompatibleProvider
import com.halo.providers.custom.openaicompatible.CustomOpenAiCompatibleCredentialProvider
import com.halo.providers.opencode.companion.OpenCodeCompanionProvider

import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.OpenAiCompatibleTransport
import com.halo.sdk.provider.ProviderAuthInfo
import com.halo.sdk.provider.ProviderAuthKind
import com.halo.sdk.provider.ProviderAuthState
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.RemoteProviderPresence
import com.halo.sdk.provider.RemoteProviderPresenceRegistry
import com.halo.sdk.provider.SpeechRecognitionProvider
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.sdk.runtime.Storage
import java.io.File
import java.util.Locale

class AndroidProviderFactory(
    private val context: Context,
    private val modelManager: LiteRtModelManager,
    private val storage: Storage,
    private val nativeResourceCoordinator: NativeResourceCoordinator,
    private val secretStore: com.halo.app.security.SecretStore? = null,
    private val speechLanguageSettings: com.halo.app.ui.screens.settings.SpeechLanguageSettingsRepository? = null,
    /** Live presence for the remote fabric; populated when providers are created. */
    val remoteProviderPresenceRegistry: RemoteProviderPresenceRegistry = RemoteProviderPresenceRegistry(),
) : ProviderFactory {

    override fun createProviders(): Providers {
        val localProvider = LiteRtLocalProvider(
            context = context,
            inventoryRepository = modelManager,
            storage = storage,
            nativeResourceCoordinator = nativeResourceCoordinator,
            catalog = modelManager.getAllCatalogModels(),
        )

        val speechRoot = haloStorageLayout(context).speechModels
        val sherpaModelDirectory = File(speechRoot, SherpaSpeechModelCatalog.WHISPER_TINY_DIRECTORY)
        val speechSelector = DefaultSpeechProviderSelector(SpeechSelectionRepository(storage))
        val androidTts = AndroidTTSProvider(context)
        val elevenLabsTts = com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider(storage)
        val speechRecognition: SpeechRecognitionProvider = ReloadableSpeechRecognitionProvider(
            modelDirectory = sherpaModelDirectory,
            getLanguage = {
                val configuredLocale = speechLanguageSettings?.languageMode?.value ?: storage.getString("speech_locale")
                    ?.takeIf { it.isNotBlank() }
                    ?: Locale.getDefault().toLanguageTag()
                configuredLocale.substringBefore('-').substringBefore('_')
            },
            selector = speechSelector,
            languageChanges = speechLanguageSettings?.languageMode,
            prepareLanguage = { speechLanguageSettings?.loadAndMigrate(); Unit },
            nativeResourceCoordinator = nativeResourceCoordinator,
        )

        val sherpaKwsDirectory = File(speechRoot, SherpaSpeechModelCatalog.KWS_ENGLISH_DIRECTORY)
        val sherpaKwsKeywords = File(sherpaKwsDirectory, SherpaSpeechModelCatalog.KWS_ENGLISH_KEYWORDS_FILE)
        val wakeWord = runCatching {
            if (!SherpaSpeechModelCatalog.isInstalled(sherpaKwsDirectory, SherpaSpeechModelCatalog.kwsEnglish)) {
                throw IllegalStateException("Sherpa KWS model is not installed")
            }
            SherpaKeywordSpotterWakeWordDetector(modelDirectory = sherpaKwsDirectory, keywordsFile = sherpaKwsKeywords)
        }.getOrElse { AlwaysWakeDetector() }

        // Remote fabric presets (RUNTIME-055). Construction is guarded so one
        // failing preset can never prevent local inference from coming up.
        val remoteProviders = buildList<AiInferenceProvider> {
            listOf(
                runCatching { GroqProvider(storage) }.getOrNull(),
                runCatching { CerebrasProvider(storage) }.getOrNull(),
                runCatching { OpenCodeZenProvider(storage) }.getOrNull(),
                runCatching { XaiProvider(storage) }.getOrNull(),
            ).forEach { provider -> provider?.let(::add) }
        
            runCatching {
                CustomOpenAiCompatibleProvider(
                    storage = storage,
                    transport = OpenAiCompatibleTransport(
                        OpenAiCompatibleTransport.defaultClient(),
                        storage.getString("custom_openai_compatible_endpoint")?.trim()?.takeIf { it.isNotBlank() }
                            ?: "http://localhost:8080/v1",
                        com.halo.sdk.provider.AiWireProtocol.OPENAI_CHAT_COMPLETIONS,
                    ),
                    credentialProvider = CustomOpenAiCompatibleCredentialProvider(storage),
                )
            }.getOrNull()?.let { add(it) }
            if (secretStore != null) {
                runCatching {
                    OpenCodeCompanionProvider(
                        storage = storage,
                        secretStore = secretStore,
                    )
                }.getOrNull()?.let { add(it) }
            }
        }

        // Register presence so System Check / provider matrix can surface
        // auth state without triggering provider IO on every composition.
        for (provider in remoteProviders) {
            runCatching {
                remoteProviderPresenceRegistry.register(
                    RemoteProviderPresence(
                        descriptor = providerRemoteDescriptor(provider.id),
                        authInfo = ProviderAuthInfo(
                            authKind = providerAuthKind(provider.id),
                            authState = if (provider.isCredentialPresent()) ProviderAuthState.CONFIGURED else ProviderAuthState.NOT_CONFIGURED,
                        ),
                        status = ProviderStatus(ProviderStatus.State.UNCONFIGURED),
                        isConfigured = provider.isCredentialPresent(),
                        isSelected = false,
                    ),
                )
            }
        }

        return Providers(
            inference = localProvider,
            speechRecognition = speechRecognition,
            speechSynthesis = androidTts,
            ttsProviders = listOf(androidTts, elevenLabsTts),
            speechSelector = speechSelector,
            wakeWord = wakeWord,
            aiProviders = listOfNotNull(
                localProvider,
                runCatching { OllamaProvider(storage) }.getOrNull(),
                runCatching { OpenRouterProvider(storage) }.getOrNull(),
                runCatching { OpenAiProvider(storage) }.getOrNull(),
                runCatching { AnthropicProvider(storage) }.getOrNull(),
                runCatching { GeminiProvider(storage) }.getOrNull(),
                *remoteProviders.toTypedArray(),
            ),
        )
    }

    private fun AiInferenceProvider.isCredentialPresent(): Boolean =
        (this as? com.halo.sdk.provider.CredentialProvider)?.hasCredential() == true

    private fun providerRemoteDescriptor(providerId: String) = when (providerId) {
        com.halo.sdk.provider.RemoteProviderIds.GROQ -> com.halo.sdk.provider.RemoteProviderPresets.groq
        com.halo.sdk.provider.RemoteProviderIds.CEREBRAS -> com.halo.sdk.provider.RemoteProviderPresets.cerebras
        com.halo.sdk.provider.RemoteProviderIds.OPENCODE_ZEN -> com.halo.sdk.provider.RemoteProviderPresets.opencodeZen
        com.halo.sdk.provider.RemoteProviderIds.XAI -> com.halo.sdk.provider.RemoteProviderPresets.xai
        com.halo.sdk.provider.RemoteProviderIds.OPENCODE_COMPANION -> com.halo.sdk.provider.RemoteProviderPresets.opencodeCompanion
        else -> com.halo.sdk.provider.RemoteProviderPresets.customOpenAiCompatible
    }

    private fun providerAuthKind(providerId: String): ProviderAuthKind = when (providerId) {
        com.halo.sdk.provider.RemoteProviderIds.OPENCODE_COMPANION -> ProviderAuthKind.COMPANION
        com.halo.sdk.provider.RemoteProviderIds.CUSTOM_OPENAI_COMPATIBLE -> ProviderAuthKind.NONE
        else -> ProviderAuthKind.API_KEY
    }
}
