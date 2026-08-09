package com.halo.app.di

import android.content.Context
import com.halo.providers.llamacpp.ModelDownloadManager

/**
 * Owns provider construction. Android-aware (needs Context for provider instantiation).
 * The [Providers] data class replaces ProviderRegistry — no service locator.
 */
class ProviderGraph(applicationContext: Context) {

    val modelDownloadManager by lazy { ModelDownloadManager(applicationContext) }

    private val providerFactory by lazy {
        AndroidProviderFactory(applicationContext, modelDownloadManager)
    }

    val providers: Providers by lazy { providerFactory.createProviders() }
}
