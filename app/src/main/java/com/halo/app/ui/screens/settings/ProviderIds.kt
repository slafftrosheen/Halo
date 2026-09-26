package com.halo.app.ui.screens.settings

/**
 * Provider ID constants for UI layer.
 * These must match the IDs defined in provider implementations.
 */
object ProviderIds {
    const val OPENROUTER = "openrouter"
    const val OLLAMA = "provider.ollama"
    const val LITERT = "provider.litert.local"
    @Deprecated("Migrated to LITERT")
    const val LLAMACPP = "provider.litert.local"
}
