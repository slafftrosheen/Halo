package com.halo.sdk.plugin

import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.sdk.runtime.RuntimeContext
import kotlinx.coroutines.flow.StateFlow

data class PluginManifest(
    val id: Identifier,
    val version: Version,
    val author: String
)

sealed interface PluginState {
    data object Installed : PluginState
    data object Validating : PluginState
    data object Loaded : PluginState
    data object Initialized : PluginState
    data object Running : PluginState
    data object Stopping : PluginState
    data object Unloaded : PluginState
    data class Failed(val reason: String) : PluginState
}

interface Plugin {
    val manifest: PluginManifest
    val state: StateFlow<PluginState>
    
    suspend fun initialize(context: RuntimeContext)
    suspend fun start()
    suspend fun stop()
}
