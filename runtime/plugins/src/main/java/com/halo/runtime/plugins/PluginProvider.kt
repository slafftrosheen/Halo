package com.halo.runtime.plugins

interface PluginProvider {
    fun load(): List<HaloPlugin>
}
