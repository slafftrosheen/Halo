package com.halo.runtime.registry

import com.halo.sdk.runtime.ServiceRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class DefaultServiceRegistry : ServiceRegistry {
    private val services = ConcurrentHashMap<KClass<*>, Any>()

    override fun <T : Any> register(type: KClass<T>, service: T) {
        services[type] = service
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> resolve(type: KClass<T>): T {
        return services[type] as? T 
            ?: throw IllegalStateException("Service not found for type: ${type.qualifiedName}")
    }
}
