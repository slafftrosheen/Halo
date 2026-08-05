package com.halo.runtime.plugins

data class PluginDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String
)
