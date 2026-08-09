package com.halo.runtime.plugins

import com.halo.sdk.runtime.HaloIntent
import com.halo.runtime.cognition.Capability

interface HaloPlugin {
    val descriptor: PluginDescriptor
    val permissions: Set<PluginPermission>

    /** Capabilities contributed while this plugin is registered. */
    fun capabilities(): List<Capability> = emptyList()

    suspend fun supports(intent: HaloIntent): Boolean

    suspend fun execute(
        intent: HaloIntent,
        context: PluginContext
    ): PluginResult
}
