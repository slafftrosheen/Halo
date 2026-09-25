package com.halo.app.di

import android.content.Context
import com.halo.app.storage.haloStorageLayout
import com.halo.providers.llamacpp.ModelDownloadManager
import com.halo.providers.sherpa.stt.ReloadableSpeechRecognitionProvider
import com.halo.providers.sherpa.stt.DefaultSpeechModelInventoryRepository
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.sdk.runtime.Storage

/**
 * Owns provider construction. Android-aware (needs Context for provider instantiation).
 * The [Providers] data class replaces ProviderRegistry — no service locator.
 */
class ProviderGraph(
    applicationContext: Context,
    private val storage: Storage,
    private val infra: InfrastructureGraph? = null,
) {

    private val storageLayout by lazy { haloStorageLayout(applicationContext) }
    val modelDownloadManager by lazy { ModelDownloadManager(applicationContext, modelRoot = storageLayout.models) }
    val nativeResourceCoordinator by lazy { NativeResourceCoordinator(infra?.resourceLeaseManager) }
    val speechModelInventory by lazy {
        DefaultSpeechModelInventoryRepository(
            speechRoot = { storageLayout.speechModels }
        )
    }

    private val providerFactory by lazy {
        AndroidProviderFactory(
            applicationContext,
            modelDownloadManager,
            storage,
            nativeResourceCoordinator
        )
    }

    private var _providers: Providers? = null

    val providers: Providers
        get() = _providers ?: providerFactory.createProviders().also { _providers = it }

    /**
     * Replace providers for testing. Must be called before any lazy access.
     * Accepts a transform so callers can selectively override individual providers
     * using [Providers.copy].
     */
    fun replaceProviders(transform: (Providers) -> Providers) {
        _providers = transform(providers)
    }

    fun reloadSpeechProvider() {
        val reloadable = providers.speechRecognition as? ReloadableSpeechRecognitionProvider
        reloadable?.refresh()
    }

    suspend fun preloadLocalModel(): Boolean {
        val provider = providers.inference as? com.halo.providers.llamacpp.NativeLlamaProvider
            ?: return false
        val selected = storage.getString("active_ai_provider") == provider.id
        return provider.loadModelIfNeeded(providerSelected = selected)
    }
}
