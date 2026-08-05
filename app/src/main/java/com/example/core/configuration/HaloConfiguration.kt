package com.example.core.configuration

data class HaloConfiguration(
    val speechEngine: String,
    val wakeWordEnabled: Boolean,
    val primaryProviderId: String,
    val fallbackProviderId: String,
    val privacyLevel: String,
    val allowedDrivers: List<String>,
    val allowedPlugins: List<String>,
    val theme: String,
    val language: String
)
