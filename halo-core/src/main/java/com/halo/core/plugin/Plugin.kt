package com.halo.core.plugin

interface Plugin {
    fun manifest(): PluginManifest
    fun capabilities(): List<PluginCapability>
    suspend fun commands(command: PluginCommand): CommandResult
    suspend fun events(event: PluginEvent)
    fun configuration(): PluginConfiguration
    fun permissions(): List<PluginPermission>
}

interface PluginManifest
interface PluginCapability
interface PluginCommand
interface CommandResult
interface PluginEvent
interface PluginConfiguration
interface PluginPermission
