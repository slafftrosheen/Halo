package com.halo.runtime.registry

import com.halo.sdk.runtime.ServiceKey
import com.halo.sdk.runtime.ServiceRegistry
import java.util.concurrent.ConcurrentHashMap

class DefaultServiceRegistry : ServiceRegistry {
    private val services = ConcurrentHashMap<ServiceKey<*>, Any>()

    override fun <T : Any> register(key: ServiceKey<T>, service: T) {
        services[key] = service
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> resolve(key: ServiceKey<T>): T {
        return services[key] as? T 
            ?: throw IllegalStateException("Service not found for type: ${key.name}")
    }
}
