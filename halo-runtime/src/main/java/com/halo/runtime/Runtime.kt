package com.halo.runtime

import com.halo.core.driver.Driver
import com.halo.core.provider.Provider
import com.halo.core.plugin.Plugin

interface RuntimeContext

interface RuntimeEngine {
    suspend fun start(context: RuntimeContext)
    suspend fun stop()
}

interface DeviceService : RuntimeEngine {
    fun registerDriver(driver: Driver)
}

interface ProviderService : RuntimeEngine {
    fun registerProvider(provider: Provider)
}

interface PluginService : RuntimeEngine {
    fun registerPlugin(plugin: Plugin)
}
