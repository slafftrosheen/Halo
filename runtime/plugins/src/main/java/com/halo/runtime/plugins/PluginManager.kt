package com.halo.runtime.plugins

import com.halo.sdk.runtime.HaloIntent

class PluginManager(
    private val registry: PluginRegistry,
    private val context: PluginContext
) {
    suspend fun dispatch(intent: HaloIntent): PluginResult {
        registry.plugins().firstOrNull { it.supports(intent) }?.let {
            return it.execute(intent, context)
        }
        return PluginFailure("No plugin found.")
    }
}
