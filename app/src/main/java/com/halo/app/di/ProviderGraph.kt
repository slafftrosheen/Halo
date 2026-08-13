package com.halo.app.di

import android.content.Context
import com.halo.providers.llamacpp.ModelDownloadManager

/**
 * Owns provider construction. Android-aware (needs Context for provider instantiation).
 * The [Providers] data class replaces ProviderRegistry — no service locator.
 */
class ProviderGraph(
    applicationContext: android.content.Context,
    storage: com.halo.sdk.runtime.Storage
) {

    val modelDownloadManager by lazy { ModelDownloadManager(applicationContext) }

    private val providerFactory by lazy {
        AndroidProviderFactory(applicationContext, modelDownloadManager, storage)
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
}
