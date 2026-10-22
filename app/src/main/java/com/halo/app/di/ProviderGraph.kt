package com.halo.app.di

import android.content.Context
import com.halo.app.storage.haloStorageLayout
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.LiteRtModelManager
import com.halo.providers.sherpa.stt.ReloadableSpeechRecognitionProvider
import com.halo.providers.sherpa.stt.DefaultSpeechModelInventoryRepository
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.sdk.runtime.Storage
import com.halo.sdk.provider.RemoteProviderPresenceRegistry

/**
 * Owns provider construction. Android-aware (needs Context for provider instantiation).
 * The [Providers] data class replaces ProviderRegistry — no service locator.
 */
class ProviderGraph(
    applicationContext: Context,
    private val storage: Storage,
    private val infra: InfrastructureGraph? = null,
    private val secretStore: com.halo.app.security.SecretStore? = null,
    private val speechLanguageSettings: com.halo.app.ui.screens.settings.SpeechLanguageSettingsRepository? = null,
    private val releaseSileroVad: suspend () -> Unit = {},
    private val allowSileroVadReload: () -> Unit = {},
) {

    private val storageLayout by lazy { haloStorageLayout(applicationContext) }
    val speechModelRoot: java.io.File get() = storageLayout.speechModels
    val modelManager by lazy { LiteRtModelManager(applicationContext, modelRoot = storageLayout.models, backgroundDownloads = true) }
    val nativeResourceCoordinator by lazy { NativeResourceCoordinator(infra?.resourceLeaseManager) }

    /** Presence for the remote provider fabric, populated when providers are created. */
    val remoteProviderPresenceRegistry: RemoteProviderPresenceRegistry
        get() = providerFactory.remoteProviderPresenceRegistry
    val speechModelInventory by lazy {
        DefaultSpeechModelInventoryRepository(
            speechRoot = { storageLayout.speechModels },
            selectedProviderId = { _providers?.speechSelector?.sttSelection?.value?.id ?: "AUTOMATIC" },
            activeProviderId = { _providers?.speechRecognition?.metadata?.id },
            releaseSileroVad = releaseSileroVad,
            allowSileroVadReload = allowSileroVadReload
        )
    }

    private val providerFactory by lazy {
        AndroidProviderFactory(
            applicationContext,
            modelManager,
            storage,
            nativeResourceCoordinator,
            secretStore = secretStore,
            speechLanguageSettings = speechLanguageSettings,
        )
    }

    private var _providers: Providers? = null

    val providers: Providers
        get() = synchronized(this) {
            _providers ?: providerFactory.createProviders().also { created ->
                (created.inference as? LiteRtLocalProvider)?.let { provider ->
                    modelManager.observeResidentModel { provider.modelSession.ownedModelId }
                }
                _providers = created
            }
        }

    /**
     * Replace providers for testing. Must be called before any lazy access.
     * Accepts a transform so callers can selectively override individual providers
     * using [Providers.copy].
     */
    @Synchronized
    fun replaceProviders(transform: (Providers) -> Providers) {
        _providers = transform(providers)
    }

    @Synchronized
    fun reloadPersistedSpeechSettings() {
        val existing = _providers ?: return
        (existing.speechSelector as? com.halo.app.ai.DefaultSpeechProviderSelector)?.reloadSelection()
        existing.ttsProviders.filterIsInstance<com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider>()
            .forEach { it.reloadConfiguration() }
    }

    fun reloadSpeechProvider() {
        val reloadable = providers.speechRecognition as? ReloadableSpeechRecognitionProvider
        reloadable?.refresh()
    }

    suspend fun preloadLocalModel(): Boolean {
        val provider = providers.inference as? LiteRtLocalProvider
            ?: return false
        return provider.loadModelIfNeeded()
    }
}
