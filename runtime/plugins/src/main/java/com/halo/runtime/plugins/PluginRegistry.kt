package com.halo.runtime.plugins

import com.halo.runtime.cognition.ICapabilityRegistry

class PluginRegistry(
    private val capabilityRegistry: ICapabilityRegistry? = null
) {
    private val plugins = mutableListOf<HaloPlugin>()

    @Synchronized
    fun register(plugin: HaloPlugin) {
        require(plugins.none { it.descriptor.id == plugin.descriptor.id }) {
            "Plugin ID is already registered: ${plugin.descriptor.id}"
        }
        plugins += plugin
        val registeredCapabilities = mutableListOf<String>()
        try {
            plugin.capabilities().forEach {
                capabilityRegistry?.register(it)
                registeredCapabilities += it.id
            }
        } catch (error: Throwable) {
            registeredCapabilities.forEach { capabilityRegistry?.unregister(it) }
            plugins -= plugin
            throw error
        }
    }

    @Synchronized
    fun unregister(plugin: HaloPlugin) {
        if (plugins.remove(plugin)) {
            plugin.capabilities().forEach { capabilityRegistry?.unregister(it.id) }
        }
    }

    fun plugins(): List<HaloPlugin> = plugins.toList()
}
