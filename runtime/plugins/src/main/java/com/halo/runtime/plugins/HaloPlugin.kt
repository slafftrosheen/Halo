package com.halo.runtime.plugins

import com.halo.sdk.runtime.HaloIntent

interface HaloPlugin {
    val descriptor: PluginDescriptor
    val permissions: Set<PluginPermission>

    suspend fun supports(intent: HaloIntent): Boolean

    suspend fun execute(
        intent: HaloIntent,
        context: PluginContext
    ): PluginResult
}
