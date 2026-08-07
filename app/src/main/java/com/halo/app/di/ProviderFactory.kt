package com.halo.app.di

import com.halo.sdk.provider.ProviderRegistry

interface ProviderFactory {
    fun createProviders(): ProviderRegistry
}
