package com.halo.runtime.plugins

class PluginRegistry {
    private val plugins = mutableListOf<HaloPlugin>()

    fun register(plugin: HaloPlugin) {
        plugins += plugin
    }

    fun unregister(plugin: HaloPlugin) {
        plugins -= plugin
    }

    fun plugins(): List<HaloPlugin> = plugins.toList()
}
