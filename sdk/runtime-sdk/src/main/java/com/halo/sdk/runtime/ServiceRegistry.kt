package com.halo.sdk.runtime

import kotlin.reflect.KClass

interface ServiceRegistry {
    fun <T : Any> register(type: KClass<T>, service: T)
    fun <T : Any> resolve(type: KClass<T>): T
}

inline fun <reified T : Any> ServiceRegistry.resolve(): T = resolve(T::class)
