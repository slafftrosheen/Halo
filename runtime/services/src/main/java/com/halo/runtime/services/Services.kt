package com.halo.runtime.services

import com.halo.sdk.runtime.ServiceKey

object Services {
    val EventBus = ServiceKey(com.halo.sdk.runtime.EventBus::class)
    val CommandBus = ServiceKey(com.halo.sdk.runtime.CommandBus::class)
    val Driver = ServiceKey(com.halo.sdk.driver.Driver::class)
    val Provider = ServiceKey(com.halo.sdk.provider.Provider::class)
    val PluginManager = ServiceKey(com.halo.runtime.plugins.PluginManager::class)
}
