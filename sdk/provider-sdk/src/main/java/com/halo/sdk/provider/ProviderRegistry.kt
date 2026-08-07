package com.halo.sdk.provider

import kotlin.reflect.KClass

interface ProviderRegistry {
    fun <T : Any> resolve(type: KClass<T>): T
    fun <T : Any> register(type: KClass<T>, provider: T)
}

class DefaultProviderRegistry : ProviderRegistry {
    private val providers = mutableMapOf<KClass<*>, Any>()

    override fun <T : Any> register(type: KClass<T>, provider: T) {
        providers[type] = provider
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> resolve(type: KClass<T>): T {
        val provider = providers[type] ?: throw IllegalArgumentException("No provider registered for type: ${type.simpleName}")
        return provider as T
    }
}
