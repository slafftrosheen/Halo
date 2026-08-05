package com.halo.runtime.plugins

sealed interface PluginResult

object PluginSuccess : PluginResult

data class PluginFailure(
    val reason: String
) : PluginResult
