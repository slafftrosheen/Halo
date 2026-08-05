package com.halo.sdk.plugin

data class PluginPermission(val name: String)

sealed interface PluginResult

data class Success(
    val output: Any?
) : PluginResult

data class Failure(
    val reason: String
) : PluginResult

data class NeedsPermission(
    val permission: PluginPermission
) : PluginResult

data class RetryLater(
    val afterMillis: Long
) : PluginResult
