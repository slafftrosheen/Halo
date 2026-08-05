package com.halo.runtime.registry

interface ServiceRegistry {
    fun registerService(name: String, service: Any)
    fun <T> getService(clazz: Class<T>): T?
    fun listServices(): List<String>
}
