package com.halo.core.plugin.protocol

import kotlinx.coroutines.flow.Flow

/**
 * Halo Plugin Protocol (HPP - Plugin).
 * The unified contract for all modular extensions.
 */
interface HaloPluginProtocol {
    val manifest: PluginManifest
    
    suspend fun initialize(context: PluginContext)
    suspend fun shutdown()
    
    val commands: Flow<PluginCommandDef>
    suspend fun executeCommand(command: PluginCommand): CommandResult
}

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val permissions: List<String>,
    val providedCapabilities: List<String>
)

interface PluginContext {
    fun sendEvent(event: PluginEvent)
    suspend fun queryRuntime(query: String): Any
}

data class PluginCommandDef(
    val id: String,
    val name: String,
    val parameters: Map<String, String> // Name -> Type
)

data class PluginCommand(
    val id: String,
    val arguments: Map<String, Any>
)

sealed class CommandResult {
    data class Success(val data: Any? = null) : CommandResult()
    data class Failure(val reason: String) : CommandResult()
}

interface PluginEvent {
    val name: String
    val payload: Map<String, Any>
}
